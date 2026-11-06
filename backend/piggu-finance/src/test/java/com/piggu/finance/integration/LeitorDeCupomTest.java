package com.piggu.finance.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interpretacao do texto do OCR. Os arquivos *-ocr.txt em recibos/ sao saida real do
 * Tesseract sobre cupons gerados em imagem (um limpo e um com ruido, rotacao e desfoque).
 */
class LeitorDeCupomTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("NFC-e: le itens, junta descricao quebrada, aplica desconto e fecha com o valor a pagar")
    void nfce() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("nfce-ocr.txt"), HOJE);

        assertThat(leitura.estabelecimento()).isEqualTo("SUPERMERCADO BOM PRECO LTDA");
        assertThat(leitura.data()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(leitura.itens()).extracting(LeitorDeCupom.Item::nome).containsExactly(
                "LEITE NINHO 400G", "ARROZ TIO JOAO 5KG", "ACUCAR UNIAO REFINADO 1KG", "BANANA PRATA");
        assertThat(leitura.itens()).extracting(LeitorDeCupom.Item::valor).containsExactly(
                new BigDecimal("15.99"), new BigDecimal("49.80"), new BigDecimal("4.79"), new BigDecimal("8.00"));
        assertThat(leitura.total()).isEqualByComparingTo("78.58");
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("cupom europeu: tira a letra do IVA, ignora a linha de quantidade e para no total")
    void europeu() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("europeu.txt"), HOJE);

        assertThat(leitura.estabelecimento()).startsWith("PINGO DOCE");
        assertThat(leitura.data()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(leitura.itens()).extracting(LeitorDeCupom.Item::nome).containsExactly(
                "LEITE MIMOSA M/GORDO 1L", "PAO DE FORMA BIMBO", "IOGURTE GREGO NESTLE", "DETERGENTE FAIRY 500ML");
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("foto ruim: valores trocados pelo OCR nao fecham a conta, entao a leitura nao e confiavel")
    void fotoRuimNaoFecha() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("europeu-foto-ruim-ocr.txt"), HOJE);

        assertThat(leitura.total()).isEqualByComparingTo("7.47");
        assertThat(leitura.fecha()).isFalse();
    }

    @Test
    @DisplayName("sem total impresso nao fecha, mesmo com itens; data no futuro e ignorada")
    void semTotal() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler("""
                PADARIA
                PAO FRANCES 5,00
                CAFE 3,50
                31/12/2099
                """, HOJE);

        assertThat(leitura.itens()).hasSize(2);
        assertThat(leitura.fecha()).isFalse();
        assertThat(leitura.data()).isEqualTo(HOJE);
    }

    @Test
    @DisplayName("milhar com ponto e decimal com virgula")
    void milhar() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler("""
                LOJA X
                TELEVISAO 50 POL 1.234,56
                TOTAL 1.234,56
                """, HOJE);

        assertThat(leitura.itens()).singleElement()
                .satisfies(item -> assertThat(item.valor()).isEqualByComparingTo("1234.56"));
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("NFC-e com linha de desconto solta: abate do item anterior e fecha pelo valor a pagar")
    void nfceComDescontoFechaPeloAPagar() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("nfce-desconto-ocr.txt"), HOJE);

        assertThat(leitura.itens()).extracting(LeitorDeCupom.Item::valor).containsExactly(
                new BigDecimal("12.90"), new BigDecimal("5.50"), new BigDecimal("22.00"));
        assertThat(leitura.total()).isEqualByComparingTo("40.40");
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("item pesado em kg: o valor do item e o total da linha, nao o peso nem o preco por kg")
    void itemPesadoEmKg() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("nfce-kg-ocr.txt"), HOJE);

        assertThat(leitura.itens()).extracting(LeitorDeCupom.Item::valor).containsExactly(
                new BigDecimal("8.63"), new BigDecimal("18.19"));
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("posto: litros e preco por litro nao viram valor, so o total da linha")
    void postoNaoLeLitrosComoValor() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("posto-ocr.txt"), HOJE);

        assertThat(leitura.itens()).singleElement()
                .satisfies(item -> assertThat(item.valor()).isEqualByComparingTo("236.32"));
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("farmacia: desconto de convenio abate do item anterior e a conta fecha")
    void farmaciaComDescontoDeConvenio() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("farmacia-ocr.txt"), HOJE);

        assertThat(leitura.itens()).extracting(LeitorDeCupom.Item::valor).containsExactly(
                new BigDecimal("34.90"), new BigDecimal("13.16"));
        assertThat(leitura.total()).isEqualByComparingTo("48.06");
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("CF-e SAT: le os tres itens e a chave de acesso de 44 digitos nao vira item")
    void cfeSat() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("cfe-sat-ocr.txt"), HOJE);

        assertThat(leitura.itens()).hasSize(3);
        assertThat(leitura.total()).isEqualByComparingTo("29.47");
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("ruido de OCR: valor com letra no lugar do zero e total quebrado em duas linhas nao fecham")
    void ruidoDeOcrNaoFecha() {
        LeitorDeCupom.Leitura leitura = LeitorDeCupom.ler(recibo("ruido-ocr.txt"), HOJE);

        assertThat(leitura.total()).isEqualByComparingTo("31.47");
        assertThat(leitura.fecha()).isFalse();
    }

    private static String recibo(String nome) {
        try (InputStream entrada = LeitorDeCupomTest.class.getResourceAsStream("/recibos/" + nome)) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException erro) {
            throw new IllegalStateException(erro);
        }
    }
}
