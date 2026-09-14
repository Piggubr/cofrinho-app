package com.piggu.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leitura de perfil vinda do banco ou do token.
 *
 * <p>A regra importante e a de seguranca: valor desconhecido cai em FAMILIAR, que
 * e o perfil mais restrito. Cair em ADMIN por engano seria uma escalada de acesso.</p>
 */
class PigguRoleTest {

    @Test
    @DisplayName("reconhece os tres perfis, em qualquer caixa")
    void reconhecePerfis() {
        assertThat(PigguRole.of("ADMIN")).isEqualTo(PigguRole.ADMIN);
        assertThat(PigguRole.of("beatriz")).isEqualTo(PigguRole.BEATRIZ);
        assertThat(PigguRole.of("  Familiar  ")).isEqualTo(PigguRole.FAMILIAR);
    }

    @Test
    @DisplayName("valor desconhecido cai no perfil mais restrito")
    void desconhecidoCaiEmFamiliar() {
        assertThat(PigguRole.of("SUPERUSER")).isEqualTo(PigguRole.FAMILIAR);
        assertThat(PigguRole.of("")).isEqualTo(PigguRole.FAMILIAR);
        assertThat(PigguRole.of(null)).isEqualTo(PigguRole.FAMILIAR);
    }

    @Test
    @DisplayName("authority segue o prefixo que o Spring Security espera")
    void authorityComPrefixo() {
        assertThat(PigguRole.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
        assertThat(PigguRole.FAMILIAR.authority()).isEqualTo("ROLE_FAMILIAR");
    }
}
