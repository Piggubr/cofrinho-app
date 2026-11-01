package com.piggu.finance.integration;

import com.piggu.common.web.Imagens;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.Plano;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.domain.Categorias;
import com.piggu.finance.domain.ChaveProduto;
import com.piggu.finance.domain.CotaDeLeituras;
import com.piggu.finance.domain.ProductMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura de recibo: primeiro o leitor proprio (Tesseract + regras), depois o Gemini.
 *
 * <p>O Gemini so e chamado quando a leitura propria nao fecha a conta (itens somados
 * diferentes do total impresso). Assim a maioria das fotos nao sai do servidor e nao
 * custa nada; o Gemini fica para foto torta, cupom sem total ou layout estranho.</p>
 *
 * <p>Com {@code piggu.leitura-de-nota.leitor-proprio=false} (padrao desde 11/10/2026, ate
 * o leitor proprio ser testado com cupons reais) toda foto vai direto para o Gemini,
 * dentro da mesma cota: 10 por mes no gratuito, sem limite no Premium. Para voltar,
 * {@code PIGGU_LEITOR_PROPRIO=true}.</p>
 */
@Service
public class LeitorDeRecibos {

    private static final Logger log = LoggerFactory.getLogger(LeitorDeRecibos.class);
    private static final Pattern PALAVRA = Pattern.compile("\\p{L}+");

    /** Finalidade do consentimento de enviar a foto ao Gemini. */
    public static final String FINALIDADE = "GEMINI";

    private final TesseractOcr ocr;
    private final GeminiReceiptReader gemini;
    private final ProductMemoryRepository memoria;
    private final Consentimentos consentimentos;
    private final CotaDeLeituras cota;
    private final boolean leitorProprio;

    public LeitorDeRecibos(TesseractOcr ocr, GeminiReceiptReader gemini, ProductMemoryRepository memoria,
                           Consentimentos consentimentos, CotaDeLeituras cota) {
        this(ocr, gemini, memoria, consentimentos, cota, true);
    }

    @Autowired
    public LeitorDeRecibos(TesseractOcr ocr, GeminiReceiptReader gemini, ProductMemoryRepository memoria,
                           Consentimentos consentimentos, CotaDeLeituras cota,
                           @Value("${piggu.leitura-de-nota.leitor-proprio:false}") boolean leitorProprio) {
        this.leitorProprio = leitorProprio;
        this.ocr = ocr;
        this.gemini = gemini;
        this.memoria = memoria;
        this.consentimentos = consentimentos;
        this.cota = cota;
    }

    /**
     * No gratuito, a leitura propria vale ate o limite do mes; a reserva pelo Gemini e
     * Premium. A foto so sai do servidor (para o Gemini) depois que a pessoa autorizou,
     * uma vez por versao do aviso. Sem autorizacao o front recebe o codigo e pergunta.
     */
    // Sem transacao: o Gemini leva ate um minuto e prenderia uma conexao do banco. O
    // consentimento grava na hora e fica mesmo que a leitura falhe depois.
    public ReceiptParseResponse ler(ReceiptParseRequest pedido, CurrentUser usuario) {
        Integer restantes = cota.reservar(usuario);
        try {
            return lerDentroDaCota(pedido, usuario).comRestantes(restantes);
        } catch (RuntimeException falhou) {
            // O Gemini ja leu e cobrou: a leitura conta mesmo sem itens (D-156).
            if (!(falhou instanceof GeminiReceiptReader.LeituraCobrada
                    || falhou instanceof GeminiReceiptReader.RespostaCobrada)) {
                cota.devolver(usuario);
            }
            throw falhou;
        }
    }

    private ReceiptParseResponse lerDentroDaCota(ReceiptParseRequest pedido, CurrentUser usuario) {
        if (!leitorProprio) {
            return soPeloGemini(pedido, usuario);
        }
        LocalDate hoje = LocalDate.now(Meses.BRASILIA);
        Optional<LeitorDeCupom.Leitura> propria = ocr.ler(imagem(pedido.imageBase64()))
                .map(texto -> LeitorDeCupom.ler(texto, hoje));

        if (propria.isPresent() && propria.get().fecha()) {
            log.info("Recibo lido pelo OCR: {} itens", propria.get().itens().size());
            return resposta(propria.get(), null);
        }
        boolean temItens = propria.isPresent() && !propria.get().itens().isEmpty();
        if (gemini.habilitado() && !usuario.isPremium()) {
            if (temItens) {
                return resposta(propria.get(), aviso(propria.get())
                        + " Com o Premium, a IA do Google tenta ler o que faltou.");
            }
            throw new BusinessException("Não consegui ler esta foto sozinho. A leitura pela IA faz parte do "
                    + "Piggu Premium; ou tente uma foto mais reta e com boa luz.",
                    HttpStatus.UNPROCESSABLE_CONTENT, Plano.CODIGO_PREMIUM);
        }
        if (gemini.habilitado()) {
            consentimentos.exigir(usuario, FINALIDADE,
                    "Não consegui ler esta foto sozinho. Para tentar com a IA do Google (Gemini), autorize o envio da foto.",
                    pedido.autorizoIa(), pedido.versaoDoAviso());
            try {
                log.info("Recibo segue para o Gemini: leitura propria {}", temItens ? "nao fechou" : "vazia");
                return gemini.ler(pedido);
            } catch (BusinessException erro) {
                if (!temItens) {
                    throw erro;
                }
                log.warn("Gemini falhou; fica a leitura propria parcial ({})", erro.getMessage());
            }
        }
        if (temItens) {
            return resposta(propria.get(), aviso(propria.get()));
        }
        throw new BusinessException("Não consegui ler este recibo. Tente uma foto mais reta e com boa luz, "
                + "ou adicione os itens à mão.");
    }

    /** Leitor proprio desligado: a foto vai direto para o Gemini, com a autorizacao da pessoa. */
    private ReceiptParseResponse soPeloGemini(ReceiptParseRequest pedido, CurrentUser usuario) {
        if (!gemini.habilitado()) {
            throw new BusinessException("A leitura de recibos pela foto está desligada. Adicione os itens à mão.");
        }
        consentimentos.exigir(usuario, FINALIDADE,
                "Para ler a foto, o Piggu envia a imagem para a IA do Google (Gemini). Autorize o envio para continuar.",
                pedido.autorizoIa(), pedido.versaoDoAviso());
        log.info("Recibo segue direto para o Gemini (leitor proprio desligado)");
        return gemini.ler(pedido);
    }

    private ReceiptParseResponse resposta(LeitorDeCupom.Leitura leitura, String aviso) {
        List<ReceiptParseResponse.Item> itens = leitura.itens().stream().map(this::comMemoria).toList();
        return new ReceiptParseResponse(UUID.randomUUID(), leitura.estabelecimento(), leitura.data(),
                itens, "OCR", aviso);
    }

    /** Produto ja comprado antes volta com o nome e a categoria que a pessoa usou. */
    private ReceiptParseResponse.Item comMemoria(LeitorDeCupom.Item item) {
        return memoria.findByProductKey(ChaveProduto.de(item.nome()))
                .map(conhecido -> new ReceiptParseResponse.Item(conhecido.getName(), conhecido.getCategory(), item.valor()))
                .orElseGet(() -> new ReceiptParseResponse.Item(capitalizar(item.nome()), Categorias.PADRAO, item.valor()));
    }

    private static String aviso(LeitorDeCupom.Leitura leitura) {
        return leitura.total() == null
                ? "Não achei o total no cupom. Confira os itens e os valores."
                : "Os itens somam " + virgula(leitura.soma()) + ", mas o cupom diz " + virgula(leitura.total())
                + ". Confira se faltou ou sobrou algum.";
    }

    private static String virgula(java.math.BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    /** Cupom vem em maiusculas: "LEITE NINHO 400G" vira "Leite Ninho 400g". */
    static String capitalizar(String nome) {
        Matcher palavra = PALAVRA.matcher(nome.toLowerCase(Locale.ROOT));
        StringBuilder resultado = new StringBuilder();
        while (palavra.find()) {
            String texto = palavra.group();
            boolean comecoDePalavra = palavra.start() == 0 || !Character.isDigit(nome.charAt(palavra.start() - 1));
            palavra.appendReplacement(resultado, comecoDePalavra
                    ? Character.toUpperCase(texto.charAt(0)) + texto.substring(1)
                    : texto);
        }
        palavra.appendTail(resultado);
        return resultado.toString();
    }

    private static byte[] imagem(String base64) {
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(base64);
            // So JPEG, PNG e WebP chegam ao OCR e ao Gemini: o Leptonica abre TIFF, PNM e
            // outros formatos que ninguem precisa mandar aqui.
            Imagens.tipo(bytes);
            return bytes;
        } catch (IllegalArgumentException erro) {
            throw new BusinessException("A foto do recibo chegou corrompida. Tente de novo.");
        }
    }
}
