package com.piggu.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leitura de perfil vinda do banco ou do token.
 *
 * <p>A regra importante e a de seguranca: valor desconhecido cai em MEMBRO, que
 * e o perfil mais restrito. Cair em ADMIN por engano seria uma escalada de acesso.</p>
 */
class PigguRoleTest {

    @Test
    @DisplayName("reconhece os tres papeis, em qualquer caixa")
    void reconhecePerfis() {
        assertThat(PigguRole.of("ADMIN")).isEqualTo(PigguRole.ADMIN);
        assertThat(PigguRole.of("titular")).isEqualTo(PigguRole.TITULAR);
        assertThat(PigguRole.of("  Membro  ")).isEqualTo(PigguRole.MEMBRO);
    }

    @DisplayName("nomes antigos de tokens ainda validos viram os papeis novos")
    @Test
    void nomesAntigos() {
        assertThat(PigguRole.of("BEATRIZ")).isEqualTo(PigguRole.TITULAR);
        assertThat(PigguRole.of("FAMILIAR")).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("valor desconhecido cai no perfil mais restrito")
    void desconhecidoCaiEmMembro() {
        assertThat(PigguRole.of("SUPERUSER")).isEqualTo(PigguRole.MEMBRO);
        assertThat(PigguRole.of("")).isEqualTo(PigguRole.MEMBRO);
        assertThat(PigguRole.of(null)).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("authority segue o prefixo que o Spring Security espera")
    void authorityComPrefixo() {
        assertThat(PigguRole.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
        assertThat(PigguRole.MEMBRO.authority()).isEqualTo("ROLE_MEMBRO");
    }
}
