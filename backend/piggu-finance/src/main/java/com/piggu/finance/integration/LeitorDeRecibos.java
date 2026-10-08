package com.piggu.finance.integration;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.domain.Categorias;
import com.piggu.finance.domain.ChaveProduto;
import com.piggu.finance.domain.ProductMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class LeitorDeRecibos {

    private static final Logger log = LoggerFactory.getLogger(LeitorDeRecibos.class);
    private static final Pattern PALAVRA = Pattern.compile("\\p{L}+");

    private final TesseractOcr ocr;
    private final GeminiReceiptReader gemini;
    private final ProductMemoryRepository memoria;
    private final Consentimentos consentimentos;

    public LeitorDeRecibos(TesseractOcr ocr, GeminiReceiptReader gemini, ProductMemoryRepository memoria,
                           Consentimentos consentimentos) {
        this.ocr = ocr;
        this.gemini = gemini;
        this.memoria = memoria;
        this.consentimentos = consentimentos;
    }

    /**
     * A foto so sai do servidor (para o Gemini) depois que a pessoa autorizou, uma vez
     * por versao do aviso. Sem autorizacao o front recebe o codigo e pergunta.
     */
    // O consentimento gravado fica mesmo que a leitura falhe depois: a pessoa autorizou.
    @Transactional(noRollbackFor = BusinessException.class)
    public ReceiptParseResponse ler(ReceiptParseRequest pedido, CurrentUser usuario) {
        LocalDate hoje = LocalDate.now();
        Optional<LeitorDeCupom.Leitura> propria = ocr.ler(imagem(pedido.imageBase64()))
                .map(texto -> LeitorDeCupom.ler(texto, hoje));

        if (propria.isPresent() && propria.get().fecha()) {
            log.info("Recibo lido pelo OCR: {} itens", propria.get().itens().size());
            return resposta(propria.get(), null);
        }
        boolean temItens = propria.isPresent() && !propria.get().itens().isEmpty();
        if (gemini.habilitado()) {
            consentimentos.exigir(usuario, "GEMINI",
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
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException erro) {
            throw new BusinessException("A foto do recibo chegou corrompida. Tente de novo.");
        }
    }
}
