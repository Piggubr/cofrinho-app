package com.piggu.common.dados;

import com.piggu.common.dados.DadosDaFamilia.Tabela;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os tres tipos de tabela da LGPD e a validacao dos nomes que vao para o SQL. */
class TabelaTest {

    @Test
    @DisplayName("compartilhada, pessoal e privada: so a privada esconde o dado do titular")
    void tipos() {
        assertThat(Tabela.compartilhada("places", "user_id")).extracting(Tabela::pessoal, Tabela::privada)
                .containsExactly(false, false);
        assertThat(Tabela.pessoal("consents", "user_id")).extracting(Tabela::pessoal, Tabela::privada)
                .containsExactly(true, false);
        assertThat(Tabela.privada("personal_goals", "user_id")).extracting(Tabela::pessoal, Tabela::privada)
                .containsExactly(true, true);
        assertThat(new Tabela("receipt_usage", null, false).privada()).isFalse();
    }

    @Test
    @DisplayName("privada sem coluna de autor e nome fora do padrao sao recusados")
    void validacao() {
        assertThatThrownBy(() -> new Tabela("personal_goals", null, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tabela.compartilhada("places; drop", "user_id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tabela.pessoal("places", "user-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
