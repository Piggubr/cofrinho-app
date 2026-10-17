package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Extrato: so debitos viram gasto; cada linha tem um id estavel para nao importar duas vezes. */
class LeitorDeExtratoTest {

    private static final String OFX = """
            OFXHEADER:100
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260910120000[-3:BRT]<TRNAMT>-45.90<FITID>A1<MEMO>PADARIA PAO QUENTE</STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260911<TRNAMT>5000.00<FITID>A2<MEMO>SALARIO</STMTTRN>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260912<TRNAMT>-12,00<FITID>A3<NAME>UBER</STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
            """;

    @Test
    @DisplayName("OFX: debitos com FITID; credito fica de fora")
    void ofx() {
        List<LeitorDeExtrato.Linha> linhas = LeitorDeExtrato.ler(OFX);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).data()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(linhas.get(0).valor()).isEqualByComparingTo("45.90");
        assertThat(linhas.get(0).idExterno()).isEqualTo("ofx:A1");
        assertThat(linhas.get(1).descricao()).isEqualTo("UBER");
    }

    @Test
    @DisplayName("CSV brasileiro com sinal: so negativos; duas compras iguais no dia ganham ids diferentes")
    void csvComSinal() {
        String csv = "Data;Histórico;Valor\n10/09/2026;Mercado;-1.234,56\n10/09/2026;Café;-5,00\n"
                + "10/09/2026;Café;-5,00\n11/09/2026;Pix recebido;300,00\n";
        List<LeitorDeExtrato.Linha> linhas = LeitorDeExtrato.ler(csv);
        assertThat(linhas).hasSize(3);
        assertThat(linhas.get(0).valor()).isEqualByComparingTo("1234.56");
        assertThat(linhas.get(1).idExterno()).isNotEqualTo(linhas.get(2).idExterno());
        // Mesmo arquivo, mesmos ids: e o que pula a reimportacao.
        assertThat(LeitorDeExtrato.ler(csv).get(1).idExterno()).isEqualTo(linhas.get(1).idExterno());
    }

    @Test
    @DisplayName("planilha sem sinal: tudo e gasto; coluna id e usada; sem colunas certas e recusado")
    void csvSemSinal() {
        List<LeitorDeExtrato.Linha> linhas = LeitorDeExtrato.ler("date,description,amount,id\n2026-09-10,\"Livro, usado\",20.50,x9\n");
        assertThat(linhas.get(0).descricao()).isEqualTo("Livro, usado");
        assertThat(linhas.get(0).idExterno()).isEqualTo("csv:x9");
        assertThatThrownBy(() -> LeitorDeExtrato.ler("a;b\n1;2\n")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("exportacao: celula que comeca com = vira texto (sem formula na planilha)")
    void celulaSegura() {
        assertThat(ExpenseService.celulaCsv("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(ExpenseService.celulaCsv("Pão")).isEqualTo("\"Pão\"");
    }
}
