package com.piggu.finance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalizacao do nome de produto.
 *
 * <p>E a regra que faz a memoria de precos funcionar: se duas grafias do mesmo
 * produto gerarem chaves diferentes, o historico se parte em dois e a media deixa
 * de fazer sentido. Se duas coisas distintas colidirem, a media mistura produtos.</p>
 */
class ChaveProdutoTest {

    @ParameterizedTest(name = "{0} e {1} sao o mesmo produto")
    @DisplayName("grafias diferentes do mesmo produto caem na mesma chave")
    @CsvSource({
            "Leite Mimosa 1L,          leite mimosa 1 l",
            "Leite Mimosa 1L (Meio Gordo), LEITE MIMOSA 1l",
            "Arroz Agulha 1kg,         arroz agulha 1 kg",
            "Pão de forma,             pao de forma",
            "Racao de gato 2kg,        racao do gato 2 kg"
    })
    void mesmasChaves(String primeiro, String segundo) {
        assertThat(ChaveProduto.de(primeiro)).isEqualTo(ChaveProduto.de(segundo));
    }

    @Test
    @DisplayName("produtos distintos nao colidem")
    void produtosDistintos() {
        assertThat(ChaveProduto.de("Leite Mimosa")).isNotEqualTo(ChaveProduto.de("Leite Agros"));
        assertThat(ChaveProduto.de("Arroz")).isNotEqualTo(ChaveProduto.de("Feijao"));
    }

    @Test
    @DisplayName("remove unidade, acento, parenteses e artigos")
    void limpezaCompleta() {
        assertThat(ChaveProduto.de("Pão de Forma Integral 500g")).isEqualTo("pao forma integral");
        assertThat(ChaveProduto.de("Café (Delta) 250 g")).isEqualTo("cafe");
    }

    @Test
    @DisplayName("nome vazio ou so simbolos nao gera chave")
    void semChave() {
        assertThat(ChaveProduto.de(null)).isEmpty();
        assertThat(ChaveProduto.de("   ")).isEmpty();
        assertThat(ChaveProduto.de("!!!")).isEmpty();
    }

    @Test
    @DisplayName("chave respeita o limite da coluna")
    void respeitaLimiteDaColuna() {
        assertThat(ChaveProduto.de("a".repeat(400))).hasSizeLessThanOrEqualTo(200);
    }
}
