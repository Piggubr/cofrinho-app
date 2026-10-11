package com.piggu.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regra de quem pode mexer no que.
 *
 * <p>O Apps Script repetia a comparacao de dono em lugares, notas e depositos.
 * Agora ela vive aqui (pelo id da pessoa), entao vale testar a borda: dono, terceiro e administrador.</p>
 */
class CurrentUserTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID OUTRA = UUID.randomUUID();

    @Test
    @DisplayName("dono do registro pode gerenciar")
    void donoPodeGerenciar() {
        CurrentUser dona = new CurrentUser(ID, "titular@piggu.test", PigguRole.TITULAR);
        assertThat(dona.podeGerenciar(ID)).isTrue();
    }

    @Test
    @DisplayName("membro gerencia o que ele mesmo lancou, pelo id")
    void membroDono() {
        CurrentUser membro = new CurrentUser(ID, "membro@piggu.test", PigguRole.MEMBRO);
        assertThat(membro.podeGerenciar(ID)).isTrue();
    }

    @Test
    @DisplayName("titular gerencia o que qualquer pessoa da familia lancou")
    void titularGerencia() {
        CurrentUser titular = new CurrentUser(ID, "titular@piggu.test", PigguRole.TITULAR);
        assertThat(titular.podeGerenciar(OUTRA)).isTrue();
    }

    @Test
    @DisplayName("quem nao e dono nem titular nao pode")
    void terceiroNaoPode() {
        CurrentUser outra = new CurrentUser(ID, "membro@piggu.test", PigguRole.MEMBRO);
        assertThat(outra.podeGerenciar(OUTRA)).isFalse();
    }

    @Test
    @DisplayName("administrador gerencia registro de qualquer pessoa")
    void adminPodeTudo() {
        CurrentUser admin = new CurrentUser(ID, "admin@piggu.test", PigguRole.ADMIN);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.podeGerenciar(OUTRA)).isTrue();
    }

    @Test
    @DisplayName("registro sem dono so e gerenciavel por administrador")
    void registroSemDono() {
        CurrentUser comum = new CurrentUser(ID, "membro@piggu.test", PigguRole.MEMBRO);
        assertThat(comum.podeGerenciar(null)).isFalse();

        CurrentUser admin = new CurrentUser(ID, "admin@piggu.test", PigguRole.ADMIN);
        assertThat(admin.podeGerenciar(null)).isTrue();
    }

    @Test
    @DisplayName("Premium barra o gratuito com codigo proprio e deixa passar o pago e o admin")
    void exigirPremium() {
        CurrentUser gratuita = new CurrentUser(ID, "titular@piggu.test", PigguRole.TITULAR);
        assertThatThrownBy(() -> gratuita.exigirPremium("Conectar bancos"))
                .isInstanceOfSatisfying(com.piggu.common.error.BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo(Plano.CODIGO_PREMIUM));

        new CurrentUser(ID, "titular@piggu.test", PigguRole.TITULAR, Plano.PREMIUM).exigirPremium("Conectar bancos");
        new CurrentUser(ID, "admin@piggu.test", PigguRole.ADMIN).exigirPremium("Conectar bancos");
    }
}
