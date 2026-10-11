package com.piggu.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CPF cifrado de ida e volta, indice estavel e a chave conferida em producao. */
class CofreDeCpfTest {

    private static final String CHAVE = "Y2hhdmUtZGUtdGVzdGUtZG8tY29mcmUtZGUtY3BmLTMyYg==";
    private final CofreDeCpf cofre = new CofreDeCpf(CHAVE, "producao");
    private final Cpf cpf = Cpf.de("52998224725");

    @Test
    @DisplayName("cifra e decifra; duas cifras do mesmo CPF sao diferentes e nenhuma tem o numero")
    void idaEVolta() {
        String primeira = cofre.cifrar(cpf);
        String segunda = cofre.cifrar(cpf);

        assertThat(primeira).isNotEqualTo(segunda).doesNotContain("52998224725");
        assertThat(cofre.decifrar(primeira)).isEqualTo(cpf);
        assertThat(cofre.decifrar(segunda)).isEqualTo(cpf);
    }

    @Test
    @DisplayName("o indice e sempre o mesmo para o mesmo CPF e depende da chave")
    void indice() {
        assertThat(cofre.indice(cpf)).isEqualTo(cofre.indice(Cpf.de("529.982.247-25"))).hasSize(64);
        assertThat(cofre.indice(cpf)).isNotEqualTo(cofre.indice(Cpf.de("11144477735")));
        assertThat(new CofreDeCpf(null, "dev").indice(cpf)).isNotEqualTo(cofre.indice(cpf));
    }

    @Test
    @DisplayName("em producao recusa subir sem chave, com a de desenvolvimento ou com chave curta")
    void chaveEmProducao() {
        assertThatThrownBy(() -> new CofreDeCpf("", "producao")).hasMessageContaining("PIGGU_CPF_CHAVE");
        assertThatThrownBy(() -> new CofreDeCpf(CofreDeCpf.CHAVE_DE_DESENVOLVIMENTO, "producao"))
                .hasMessageContaining("desenvolvimento");
        assertThatThrownBy(() -> new CofreDeCpf("Y3VydGE=", "producao")).hasMessageContaining("32 bytes");
        assertThatNoException().isThrownBy(() -> new CofreDeCpf(null, "dev"));
    }
}
