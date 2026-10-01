package com.piggu.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regra de quem pode mexer no que.
 *
 * <p>O Apps Script repetia a comparacao de e-mail em lugares, notas e depositos.
 * Agora ela vive aqui, entao vale testar a borda: dono, terceiro e administrador.</p>
 */
class CurrentUserTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    @DisplayName("dono do registro pode gerenciar")
    void donoPodeGerenciar() {
        CurrentUser dona = new CurrentUser(ID, "beatriz@piggu.test", PigguRole.BEATRIZ);
        assertThat(dona.podeGerenciar("beatriz@piggu.test")).isTrue();
    }

    @Test
    @DisplayName("comparacao de dono ignora maiusculas")
    void comparacaoIgnoraCaixa() {
        CurrentUser dona = new CurrentUser(ID, "beatriz@piggu.test", PigguRole.BEATRIZ);
        assertThat(dona.podeGerenciar("Beatriz@Piggu.TEST")).isTrue();
    }

    @Test
    @DisplayName("quem nao e dono nem admin nao pode")
    void terceiroNaoPode() {
        CurrentUser outra = new CurrentUser(ID, "familiar@piggu.test", PigguRole.FAMILIAR);
        assertThat(outra.podeGerenciar("beatriz@piggu.test")).isFalse();
    }

    @Test
    @DisplayName("administrador gerencia registro de qualquer pessoa")
    void adminPodeTudo() {
        CurrentUser admin = new CurrentUser(ID, "admin@piggu.test", PigguRole.ADMIN);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.podeGerenciar("beatriz@piggu.test")).isTrue();
    }

    @Test
    @DisplayName("registro sem dono so e gerenciavel por administrador")
    void registroSemDono() {
        CurrentUser comum = new CurrentUser(ID, "beatriz@piggu.test", PigguRole.BEATRIZ);
        assertThat(comum.podeGerenciar(null)).isFalse();

        CurrentUser admin = new CurrentUser(ID, "admin@piggu.test", PigguRole.ADMIN);
        assertThat(admin.podeGerenciar(null)).isTrue();
    }

    @Test
    @DisplayName("Premium barra o gratuito com codigo proprio e deixa passar o pago e o admin")
    void exigirPremium() {
        CurrentUser gratuita = new CurrentUser(ID, "beatriz@piggu.test", PigguRole.BEATRIZ);
        assertThatThrownBy(() -> gratuita.exigirPremium("Conectar bancos"))
                .isInstanceOfSatisfying(com.piggu.common.error.BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo(Plano.CODIGO_PREMIUM));

        new CurrentUser(ID, "beatriz@piggu.test", PigguRole.BEATRIZ, Plano.PREMIUM).exigirPremium("Conectar bancos");
        new CurrentUser(ID, "admin@piggu.test", PigguRole.ADMIN).exigirPremium("Conectar bancos");
    }
}
