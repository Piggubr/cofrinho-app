package com.piggu.identity.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.PigguRole;
import com.piggu.common.error.BusinessException;
import com.piggu.identity.api.dto.PreferencesRequest;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Entrada, renovacao e saida da conta.
 *
 * <p>O verificador do Google e substituido por um dublê: o que esta sob teste e o
 * que acontece depois que o Google confirma quem e a pessoa.</p>
 */
class AuthServiceTest extends PostgresIntegrationTest {

    private static final String AUTORIZADA = "beatrizvieirasouzadias@gmail.com";
    private static final String DE_FORA = "estranho@exemplo.test";

    @MockitoBean
    private GoogleIdTokenVerifier verificador;

    @Autowired
    private AuthService auth;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private RefreshSessionRepository sessoes;

    @BeforeEach
    void limpar() {
        sessoes.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("primeiro acesso de e-mail liberado cria a conta com o perfil da lista")
    void primeiroAcessoCriaConta() {
        responderGoogleCom(AUTORIZADA);

        TokenResponse acesso = auth.entrarComGoogle("token-google", "navegador-de-teste");

        assertThat(acesso.accessToken()).isNotBlank();
        assertThat(acesso.refreshToken()).isNotBlank();
        assertThat(acesso.usuario().email()).isEqualTo(AUTORIZADA);
        assertThat(acesso.usuario().role()).isEqualTo(PigguRole.BEATRIZ);
        assertThat(usuarios.findByEmail(AUTORIZADA)).isPresent();
    }

    @Test
    @DisplayName("e-mail fora da lista nao entra e nao cria conta")
    void emailDeForaNaoEntra() {
        responderGoogleCom(DE_FORA);

        assertThatThrownBy(() -> auth.entrarComGoogle("token-google", null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Este e-mail nao esta autorizado.");

        assertThat(usuarios.findByEmail(DE_FORA)).isEmpty();
    }

    @Test
    @DisplayName("segundo acesso reaproveita a conta e atualiza o perfil do Google")
    void segundoAcessoAtualizaPerfil() {
        responderGoogleCom(AUTORIZADA);
        auth.entrarComGoogle("token-google", null);

        given(verificador.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-1", AUTORIZADA, "Beatriz Dias", "Beatriz", "https://foto.test/nova.jpg"));
        TokenResponse segundo = auth.entrarComGoogle("token-google", null);

        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(segundo.usuario().foto()).isEqualTo("https://foto.test/nova.jpg");
    }

    @Test
    @DisplayName("renovar entrega tokens novos e invalida o refresh usado")
    void renovarRotacionaOToken() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse primeiro = auth.entrarComGoogle("token-google", null);

        TokenResponse renovado = auth.renovar(primeiro.refreshToken(), null);

        assertThat(renovado.refreshToken()).isNotEqualTo(primeiro.refreshToken());
        assertThatThrownBy(() -> auth.renovar(primeiro.refreshToken(), null))
                .as("o refresh antigo nao pode mais valer")
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("refresh desconhecido e recusado")
    void refreshDesconhecido() {
        assertThatThrownBy(() -> auth.renovar("token-que-nunca-existiu", null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("sessao venceu");
    }

    @Test
    @DisplayName("sair encerra a sessao daquele refresh")
    void sairEncerraSessao() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse acesso = auth.entrarComGoogle("token-google", null);

        auth.sair(acesso.refreshToken());

        assertThatThrownBy(() -> auth.renovar(acesso.refreshToken(), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("conta desativada nao renova e perde as sessoes abertas")
    void contaDesativadaNaoRenova() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse acesso = auth.entrarComGoogle("token-google", null);

        UserAccount conta = usuarios.findByEmail(AUTORIZADA).orElseThrow();
        conta.setActive(false);
        usuarios.save(conta);

        assertThatThrownBy(() -> auth.renovar(acesso.refreshToken(), null))
                .isInstanceOf(ForbiddenException.class);
        assertThat(sessoes.count()).isZero();
    }

    @Test
    @DisplayName("o refresh token nunca e gravado em claro")
    void refreshNaoEGravadoEmClaro() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse acesso = auth.entrarComGoogle("token-google", null);

        assertThat(sessoes.findByTokenHash(acesso.refreshToken()))
                .as("procurar pelo token puro nao pode achar nada")
                .isEmpty();
        assertThat(sessoes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("conta nova comeca em euro, convertendo para real, com a cotacao visivel")
    void preferenciasPadrao() {
        responderGoogleCom(AUTORIZADA);

        TokenResponse acesso = auth.entrarComGoogle("token-google", null);

        assertThat(acesso.usuario().preferencias())
                .isEqualTo(new UserResponse.Preferencias("EUR", "BRL", true));
    }

    @Test
    @DisplayName("preferencias de moeda sao gravadas e normalizadas em maiusculas")
    void salvaPreferencias() {
        responderGoogleCom(AUTORIZADA);
        java.util.UUID id = auth.entrarComGoogle("token-google", null).usuario().id();

        auth.salvarPreferencias(id, new PreferencesRequest("usd", "jpy", false));

        assertThat(auth.perfil(id).preferencias())
                .isEqualTo(new UserResponse.Preferencias("USD", "JPY", false));
    }

    @Test
    @DisplayName("moeda que nao existe na ISO 4217 e recusada sem gravar nada")
    void recusaMoedaInexistente() {
        responderGoogleCom(AUTORIZADA);
        java.util.UUID id = auth.entrarComGoogle("token-google", null).usuario().id();

        assertThatThrownBy(() -> auth.salvarPreferencias(id, new PreferencesRequest("XYZ", "BRL", true)))
                .isInstanceOf(BusinessException.class);
        assertThat(auth.perfil(id).preferencias().moeda()).isEqualTo("EUR");
    }

    private void responderGoogleCom(String email) {
        given(verificador.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-1", email, "Pessoa de Teste", "Pessoa", "https://foto.test/p.jpg"));
    }
}
