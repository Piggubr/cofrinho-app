package com.piggu.finance.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.Plano;
import com.piggu.finance.api.dto.ConferenciaDaFaturaResponse;
import com.piggu.finance.api.dto.ConferirFaturaRequest;
import com.piggu.finance.integration.GeminiFaturaReader;
import com.piggu.finance.integration.LeitorDeFatura;
import com.piggu.finance.integration.LeitorDeFatura.Leitura;
import com.piggu.finance.integration.LeitorDeFatura.Linha;
import com.piggu.finance.integration.LeitorDeFatura.TipoDaLinha;
import com.piggu.finance.integration.TextoDoPdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Confere a fatura do cartao (PDF) com os gastos lancados: o que ja entrou e o que falta.
 *
 * <p>Primeiro o leitor proprio; se nao fechar, o Gemini (so Premium, com autorizacao, so o
 * texto). O gratuito recebe so a contagem: as linhas nao saem do servidor. Nada e gravado.</p>
 */
@Service
public class ConferenciaDaFaturaService {

    /** Finalidade do consentimento de enviar o texto da fatura ao Gemini. */
    public static final String FINALIDADE = "GEMINI_FATURA";

    static final String SEM_LEITURA = "Não consegui ler esta fatura sozinho. A leitura pela IA faz parte do Piggu Premium.";
    static final String AVISO_DO_CONSENTIMENTO = "Para ler esta fatura, o Piggu envia o texto dela (não o PDF) para a "
            + "IA do Google (Gemini). Autorize o envio para continuar.";
    private static final String SEM_TEXTO = "Não consegui ler esta fatura. Envie o PDF original do banco, "
            + "não uma foto ou digitalização.";
    private static final String PARCIAL_GRATUITO = "Não consegui ler esta fatura inteira sozinho; a contagem pode "
            + "estar incompleta. A leitura pela IA faz parte do Piggu Premium.";
    private static final String NAO_FECHOU = "A soma das linhas lidas não bate com o total da fatura. "
            + "Confira se faltou alguma compra.";

    private static final int MAXIMO_DE_BYTES = 5 * 1024 * 1024;
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("1000000");
    private static final int MAXIMO_DE_PARCELAS = 48;
    /** Folga em volta do periodo para os candidatos: a conciliacao olha ate 10 dias da compra. */
    private static final int FOLGA_EM_DIAS = 10;

    private static final Logger log = LoggerFactory.getLogger(ConferenciaDaFaturaService.class);

    private final GeminiFaturaReader gemini;
    private final Consentimentos consentimentos;
    private final ExpenseRepository gastos;
    private final PaymentAccountRepository contas;
    private final ExpenseService servicoDeGastos;

    public ConferenciaDaFaturaService(GeminiFaturaReader gemini, Consentimentos consentimentos,
                                      ExpenseRepository gastos, PaymentAccountRepository contas,
                                      ExpenseService servicoDeGastos) {
        this.gemini = gemini;
        this.consentimentos = consentimentos;
        this.gastos = gastos;
        this.contas = contas;
        this.servicoDeGastos = servicoDeGastos;
    }

    // Sem transacao: o Gemini leva ate um minuto e prenderia uma conexao do banco (como no LeitorDeRecibos).
    public ConferenciaDaFaturaResponse conferir(ConferirFaturaRequest pedido, CurrentUser usuario) {
        // O filtro da familia faz o findById nao achar o cartao dos outros.
        PaymentAccount cartao = pedido.contaId() == null ? null : contas.findById(pedido.contaId())
                .filter(c -> c.getKind() == PaymentAccount.Tipo.CARTAO)
                .orElseThrow(() -> new NotFoundException("Conta ou cartao nao encontrado."));
        String texto = TextoDoPdf.extrair(pdf(pedido.pdfBase64()), pedido.senha());

        if (texto.isBlank()) {
            // PDF so com imagem: nem o Premium le (o Gemini recebe so o texto).
            throw new BusinessException(SEM_TEXTO);
        }
        Leitura leitura = validada(LeitorDeFatura.ler(texto));
        String origem = "PROPRIA";
        String aviso = null;
        if (!leitura.fecha()) {
            boolean temLinhas = !leitura.linhas().isEmpty();
            if (!usuario.isPremium()) {
                if (!temLinhas) {
                    throw new BusinessException(SEM_LEITURA, HttpStatus.UNPROCESSABLE_CONTENT, Plano.CODIGO_PREMIUM);
                }
                aviso = PARCIAL_GRATUITO;
            } else if (gemini.habilitado()) {
                consentimentos.exigir(usuario, FINALIDADE, AVISO_DO_CONSENTIMENTO,
                        pedido.autorizoIa(), pedido.versaoDoAviso());
                try {
                    Leitura pelaIa = validada(gemini.ler(texto));
                    // Nenhuma fechou: fica a mais perto do total; empate, a com mais linhas.
                    if (pelaIa.fecha() || !maisPerto(leitura, pelaIa)) {
                        leitura = pelaIa;
                        origem = "GEMINI";
                    }
                    aviso = leitura.fecha() ? null : NAO_FECHOU;
                } catch (BusinessException erro) {
                    if (!temLinhas) {
                        throw erro;
                    }
                    log.warn("Gemini falhou na fatura; fica a leitura propria parcial");
                    aviso = NAO_FECHOU;
                }
            } else if (temLinhas) {
                aviso = NAO_FECHOU;
            } else {
                throw new BusinessException(SEM_TEXTO);
            }
        }
        log.info("Fatura conferida: banco={} linhas={} origem={} fecha={}",
                leitura.banco(), leitura.linhas().size(), origem, leitura.fecha());
        return resposta(leitura, origem, aviso, cartao, usuario.isPremium());
    }

    /** Retira a autorizacao de enviar o texto da fatura ao Gemini; a proxima leitura pergunta de novo. */
    public void retirarConsentimento(CurrentUser usuario) {
        consentimentos.retirar(usuario, FINALIDADE);
    }

    private ConferenciaDaFaturaResponse resposta(Leitura leitura, String origem, String aviso,
                                                 PaymentAccount cartao, boolean premium) {
        LocalDate[] periodo = periodo(leitura, cartao);
        List<Linha> compras = compras(leitura.linhas());
        LocalDate de = minimo(periodo[0], compras);
        LocalDate ate = maximo(periodo[1], compras);
        List<Expense> candidatos = periodo[0] == null ? List.of()
                : gastos.findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc(
                        de.minusDays(FOLGA_EM_DIAS), ate.plusDays(FOLGA_EM_DIAS));
        List<ConciliacaoDaFatura.Resultado> resultados = ConciliacaoDaFatura.conciliar(leitura.linhas(), candidatos,
                cartao == null ? null : cartao.getId(), periodo[0], periodo[1]);

        ConferenciaDaFaturaResponse.Contagem contagem = new ConferenciaDaFaturaResponse.Contagem(
                contar(leitura, TipoDaLinha.COMPRA), contar(leitura, TipoDaLinha.TARIFA),
                contar(leitura, TipoDaLinha.IGNORADA), contar(resultados, ConciliacaoDaFatura.Situacao.JA_LANCADA),
                contar(resultados, ConciliacaoDaFatura.Situacao.CONFIRA),
                contar(resultados, ConciliacaoDaFatura.Situacao.FALTA));
        return new ConferenciaDaFaturaResponse(leitura.banco(), leitura.vencimento(), leitura.total(), leitura.soma(),
                origem, aviso, contagem, premium ? linhas(resultados) : null);
    }

    /** Categoria sugerida como na previa do extrato: regras da familia e memoria. */
    private List<ConferenciaDaFaturaResponse.Linha> linhas(List<ConciliacaoDaFatura.Resultado> resultados) {
        List<ExpenseService.LinhaDaPrevia> previa = servicoDeGastos.previa(resultados.stream()
                .map(r -> new LeitorDeExtrato.Linha(r.linha().data(), r.linha().descricao(), r.linha().valor(),
                        r.linha().idExterno()))
                .toList());
        return IntStream.range(0, resultados.size()).mapToObj(i -> {
            ConciliacaoDaFatura.Resultado r = resultados.get(i);
            Linha l = r.linha();
            Expense g = r.gasto();
            return new ConferenciaDaFaturaResponse.Linha(l.data(), l.descricao(), l.valor(), l.tipo().name(),
                    l.parcelaNumero(), l.parcelaTotal(), r.situacao().name(), g == null ? null : g.getId(),
                    g == null ? null : g.getItem(), g == null ? null : g.getExpenseDate(),
                    previa.get(i).categoria(), l.idExterno());
        }).toList();
    }

    /**
     * Com cartao, o periodo de compras da fatura que vence na data lida; sem cartao (ou sem
     * vencimento), da menor a maior data das compras (sem nenhuma, de todas as linhas), a da
     * parcela sendo a data em que o app a lanca (ver {@link #dataEsperada}).
     */
    private static LocalDate[] periodo(Leitura leitura, PaymentAccount cartao) {
        if (cartao != null && leitura.vencimento() != null && cartao.getClosingDay() != null
                && cartao.getDueDay() != null) {
            // A fatura do mes M pode vencer em M ou em M+1 (vencimento antes do fechamento).
            YearMonth mes = YearMonth.from(leitura.vencimento());
            ContasECartoes.Periodo p = ContasECartoes.periodo(cartao.getClosingDay(), cartao.getDueDay(), mes);
            if (p.vencimento().isAfter(leitura.vencimento())) {
                p = ContasECartoes.periodo(cartao.getClosingDay(), cartao.getDueDay(), mes.minusMonths(1));
            }
            return new LocalDate[] {p.inicio(), p.ultimaCompra()};
        }
        List<Linha> base = compras(leitura.linhas()).isEmpty() ? leitura.linhas() : compras(leitura.linhas());
        return new LocalDate[] {minimo(null, base), maximo(null, base)};
    }

    private static List<Linha> compras(List<Linha> linhas) {
        return linhas.stream().filter(l -> l.tipo() == TipoDaLinha.COMPRA).toList();
    }

    /**
     * A parcela n/m vem na fatura com a data da compra; o app grava a parcela n uns n-1 meses
     * depois, e e perto dessa data que ela esta entre os gastos.
     */
    private static LocalDate dataEsperada(Linha l) {
        return l.parcelaNumero() == null ? l.data() : l.data().plusMonths(l.parcelaNumero() - 1L);
    }

    /** A propria fica se esta mais perto do total que a da IA (empate: a com mais linhas). */
    private static boolean maisPerto(Leitura propria, Leitura pelaIa) {
        int cmp = Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()).compare(folga(propria), folga(pelaIa));
        return cmp < 0 || cmp == 0 && propria.linhas().size() > pelaIa.linhas().size();
    }

    private static BigDecimal folga(Leitura l) {
        return l.total() == null ? null : l.soma().subtract(l.total()).abs();
    }

    private static LocalDate minimo(LocalDate inicial, List<Linha> linhas) {
        return linhas.stream().map(ConferenciaDaFaturaService::dataEsperada).filter(d -> inicial == null || d.isBefore(inicial))
                .min(Comparator.naturalOrder()).orElse(inicial);
    }

    private static LocalDate maximo(LocalDate inicial, List<Linha> linhas) {
        return linhas.stream().map(ConferenciaDaFaturaService::dataEsperada).filter(d -> inicial == null || d.isAfter(inicial))
                .max(Comparator.naturalOrder()).orElse(inicial);
    }

    /**
     * Vale para os dois leitores: linha sem data ou com valor fora de (0, 1.000.000] sai; parcela
     * incoerente (so um dos numeros, ou fora de 1 <= n <= m <= 48) vira a vista.
     */
    static Leitura validada(Leitura leitura) {
        List<Linha> boas = leitura.linhas().stream()
                .filter(l -> l.data() != null && l.valor() != null && l.tipo() != null && l.descricao() != null
                        && l.valor().signum() > 0 && l.valor().compareTo(VALOR_MAXIMO) <= 0)
                .map(l -> parcelaCoerente(l) ? l : new Linha(l.data(), l.descricao(), l.valor(), l.tipo(),
                        null, null, l.idExterno()))
                .toList();
        return new Leitura(leitura.banco(), leitura.vencimento(), leitura.total(), boas);
    }

    private static boolean parcelaCoerente(Linha l) {
        Integer n = l.parcelaNumero();
        Integer m = l.parcelaTotal();
        return (n == null && m == null) || (n != null && m != null && 1 <= n && n <= m && m <= MAXIMO_DE_PARCELAS);
    }

    private static int contar(Leitura leitura, TipoDaLinha tipo) {
        return (int) leitura.linhas().stream().filter(l -> l.tipo() == tipo).count();
    }

    private static int contar(List<ConciliacaoDaFatura.Resultado> resultados, ConciliacaoDaFatura.Situacao situacao) {
        return (int) resultados.stream().filter(r -> r.situacao() == situacao).count();
    }

    private static byte[] pdf(String base64) {
        byte[] bytes;
        try {
            // Decodificador estrito (o MIME ignora o que nao e base64); quebras de linha saem antes.
            bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException erro) {
            throw new BusinessException("O PDF chegou corrompido. Tente de novo.");
        }
        if (bytes.length > MAXIMO_DE_BYTES) {
            throw new BusinessException("PDF grande demais (máximo 5 MB).");
        }
        return bytes;
    }
}
