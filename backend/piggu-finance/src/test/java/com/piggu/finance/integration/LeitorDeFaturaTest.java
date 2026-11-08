package com.piggu.finance.integration;

import com.piggu.finance.integration.LeitorDeFatura.Leitura;
import com.piggu.finance.integration.LeitorDeFatura.Linha;
import com.piggu.finance.integration.LeitorDeFatura.TipoDaLinha;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura da fatura por regras, sobre faturas sinteticas (nomes ficticios, sem dado real). */
class LeitorDeFaturaTest {

    @Test
    @DisplayName("Nubank: fecha, vira o ano em dezembro e separa compras, tarifas e ignoradas")
    void nubankFecha() {
        confere("nubank", "nubank", LocalDate.of(2027, 1, 10), "418.70", 5, 0, 2);
    }

    @Test
    @DisplayName("Itau: fecha com 'Total da fatura', anuidade parcelada e devolucao")
    void itauFecha() {
        confere("itau", "itau", LocalDate.of(2027, 3, 15), "502.35", 4, 1, 2);
    }

    @Test
    @DisplayName("Inter: fecha com compra internacional e IOF")
    void interFecha() {
        confere("inter", "inter", LocalDate.of(2027, 2, 5), "1194.88", 4, 1, 1);
    }

    @Test
    @DisplayName("Bradesco: fecha com 'Valor total', seguro e juros como tarifa")
    void bradescoFecha() {
        confere("bradesco", "bradesco", LocalDate.of(2027, 4, 20), "270.40", 3, 2, 1);
    }

    @Test
    @DisplayName("C6: fecha com compras de dezembro numa fatura de janeiro")
    void c6Fecha() {
        confere("c6", "c6", LocalDate.of(2027, 1, 10), "365.50", 4, 0, 1);
    }

    @Test
    @DisplayName("A parcela 03/10 sai da descricao e vira numero e total")
    void parcelaSaiDaDescricao() {
        Linha l = LeitorDeFatura.ler("Vencimento 10/01/2027\nTotal desta fatura R$ 150,00\n03/01 LOJA X 03/10 150,00")
                .linhas().get(0);

        assertThat(l.descricao()).isEqualTo("LOJA X");
        assertThat(l.parcelaNumero()).isEqualTo(3);
        assertThat(l.parcelaTotal()).isEqualTo(10);
        assertThat(l.valor()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Compra de 28/12 numa fatura que vence em 10/01/2027 e de 2026")
    void viradaDeAno() {
        Linha l = LeitorDeFatura.ler("Vencimento 10/01/2027\n28/12 PADARIA SOL 10,00").linhas().get(0);

        assertThat(l.data()).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    @DisplayName("Pagamento recebido e saldo anterior ficam ignorados e nao entram na soma")
    void pagamentoESaldoAnteriorNaoContam() {
        Leitura l = LeitorDeFatura.ler("""
                Vencimento 10/01/2027
                Total desta fatura R$ 100,00
                Saldo anterior 500,00
                05/12 Pagamento recebido -500,00
                02/01 LOJA A 60,00
                03/01 LOJA B 40,00
                """);

        assertThat(l.linhas()).filteredOn(x -> x.tipo() == TipoDaLinha.IGNORADA)
                .allSatisfy(x -> assertThat(x.descricao()).containsIgnoringCase("pagamento"));
        assertThat(l.soma()).isEqualByComparingTo("100.00");
        assertThat(l.fecha()).isTrue();
    }

    @Test
    @DisplayName("Estorno no periodo abate da soma")
    void estornoAbate() {
        Leitura l = LeitorDeFatura.ler("Vencimento 10/01/2027\nTotal desta fatura R$ 60,00\n02/01 LOJA A 100,00\n03/01 Estorno LOJA A -40,00");

        assertThat(l.soma()).isEqualByComparingTo("60.00");
        assertThat(l.fecha()).isTrue();
    }

    @Test
    @DisplayName("Compra internacional vira COMPRA em reais e o IOF vira TARIFA")
    void internacionalComIof() {
        Leitura l = LeitorDeFatura.ler(texto("inter"));

        assertThat(l.linhas()).anySatisfy(x -> {
            assertThat(x.descricao()).isEqualTo("HOTEL MIAMI US");
            assertThat(x.tipo()).isEqualTo(TipoDaLinha.COMPRA);
            assertThat(x.valor()).isEqualByComparingTo("520.00");
        });
        assertThat(l.linhas()).anySatisfy(x -> {
            assertThat(x.descricao()).startsWith("IOF");
            assertThat(x.tipo()).isEqualTo(TipoDaLinha.TARIFA);
        });
    }

    @Test
    @DisplayName("Sem total (so 'total a pagar' com saldo anterior) nao fecha")
    void semTotalNaoFecha() {
        Leitura l = LeitorDeFatura.ler("Vencimento 10/01/2027\nSaldo anterior 50,00\nTotal a pagar R$ 60,00\n02/01 LOJA A 60,00");

        assertThat(l.total()).isNull();
        assertThat(l.fecha()).isFalse();
    }

    @Test
    @DisplayName("Soma diferente do total nao fecha")
    void somaDiferenteNaoFecha() {
        Leitura l = LeitorDeFatura.ler("Vencimento 10/01/2027\nTotal desta fatura R$ 70,00\n02/01 LOJA A 60,00");

        assertThat(l.fecha()).isFalse();
    }

    @Test
    @DisplayName("Texto vazio ou nulo nao fecha")
    void textoVazioNaoFecha() {
        assertThat(LeitorDeFatura.ler("").fecha()).isFalse();
        assertThat(LeitorDeFatura.ler(null).fecha()).isFalse();
    }

    @Test
    @DisplayName("A mesma compra duas vezes no dia tem ids diferentes, e o id e estavel")
    void mesmaCompraDuasVezesTemIdsDiferentes() {
        String t = "Vencimento 10/01/2027\n02/01 CAFE AROMA 9,00\n02/01 CAFE AROMA 9,00";
        var a = LeitorDeFatura.ler(t).linhas();
        var b = LeitorDeFatura.ler(t).linhas();

        assertThat(a.get(0).idExterno()).startsWith("fatura:").hasSize(7 + 32).isNotEqualTo(a.get(1).idExterno());
        assertThat(a.get(0).idExterno()).isEqualTo(b.get(0).idExterno());
    }

    @Test
    @DisplayName("Linha so com data e valor nao estoura: e descartada")
    void linhaSoComValorNaoEstoura() {
        Leitura l = LeitorDeFatura.ler("Vencimento 10/01/2027\n15/01 100,00\n02/01 LOJA A 60,00");

        assertThat(l.linhas()).hasSize(1);
    }

    @Test
    @DisplayName("Mesmo mes do vencimento mas dia depois dele e do ano anterior")
    void diaDepoisDoVencimentoEDoAnoAnterior() {
        Linha l = LeitorDeFatura.ler("Vencimento 10/01/2027\n15/01 LOJA A 10,00").linhas().get(0);

        assertThat(l.data()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    @DisplayName("'Total da fatura anterior' nao e o total")
    void totalDaFaturaAnteriorNaoServe() {
        Leitura l = LeitorDeFatura.ler("Vencimento 10/01/2027\nTotal da fatura anterior R$ 500,00\n02/01 LOJA A 60,00");

        assertThat(l.total()).isNull();
        assertThat(l.fecha()).isFalse();
    }

    @Test
    @DisplayName("Palavra de tarifa so conta no comeco da descricao")
    void tarifaSoNoComeco() {
        var linhas = LeitorDeFatura.ler("Vencimento 10/01/2027\n02/01 PORTO SEGURO AUTO 80,00\n03/01 SEGURO CARTAO 12,00\n04/01 JUROS ROTATIVO 5,00").linhas();

        assertThat(linhas).extracting(Linha::tipo)
                .containsExactly(TipoDaLinha.COMPRA, TipoDaLinha.TARIFA, TipoDaLinha.TARIFA);
    }

    private static void confere(String arquivo, String banco, LocalDate vencimento, String total,
                                int compras, int tarifas, int ignoradas) {
        Leitura l = LeitorDeFatura.ler(texto(arquivo));

        assertThat(l.banco()).isEqualTo(banco);
        assertThat(l.vencimento()).isEqualTo(vencimento);
        assertThat(l.total()).isEqualByComparingTo(new BigDecimal(total));
        assertThat(l.fecha()).isTrue();
        assertThat(l.linhas().stream().filter(x -> x.tipo() == TipoDaLinha.COMPRA)).hasSize(compras);
        assertThat(l.linhas().stream().filter(x -> x.tipo() == TipoDaLinha.TARIFA)).hasSize(tarifas);
        assertThat(l.linhas().stream().filter(x -> x.tipo() == TipoDaLinha.IGNORADA)).hasSize(ignoradas);
    }

    private static String texto(String nome) {
        try (InputStream in = LeitorDeFaturaTest.class.getResourceAsStream("/faturas/" + nome + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
