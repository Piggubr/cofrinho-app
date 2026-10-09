package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.PigguRole;
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

    private static final String AUTORIZADA = "titular@exemplo.test";
    private static final String CONVIDADA = "convidada@exemplo.test";

    @MockitoBean
    private GoogleIdTokenVerifier verificador;

    @Autowired
    private AuthService auth;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private FamiliaService familias;

    @BeforeEach
    void limpar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("cadastro aberto: primeiro acesso cria a conta como titular de uma familia nova")
    void primeiroAcessoCriaConta() {
        responderGoogleCom(AUTORIZADA);

        TokenResponse acesso = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, "navegador-de-teste");

        assertThat(acesso.accessToken()).isNotBlank();
        assertThat(acesso.refreshToken()).isNotBlank();
        assertThat(acesso.usuario().email()).isEqualTo(AUTORIZADA);
        assertThat(acesso.usuario().role()).isEqualTo(PigguRole.TITULAR);
        assertThat(usuarios.findByEmail(AUTORIZADA)).isPresent();
        assertThat(acesso.usuario().familia()).isNotNull();
    }

    @Test
    @DisplayName("conta nova so nasce com o aceite dos termos na versao vigente; quem ja tem conta nao precisa")
    void termosNoCadastro() {
        responderGoogleCom(AUTORIZADA);
        assertThatThrownBy(() -> auth.entrarComGoogle("token-google", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("TERMOS_NECESSARIOS"));
        assertThatThrownBy(() -> auth.entrarComGoogle("token-google", "1999-01-01", null))
                .isInstanceOf(BusinessException.class);
        assertThat(usuarios.findByEmail(AUTORIZADA)).isEmpty();

        auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);
        UserAccount criada = usuarios.findByEmail(AUTORIZADA).orElseThrow();
        assertThat(criada.getTermsVersion()).isEqualTo(Consentimentos.VERSAO_DO_AVISO);
        assertThat(criada.getTermsAcceptedAt()).isNotNull();

        assertThat(auth.entrarComGoogle("token-google", null, null).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("e-mail da PIGGU_ADMIN_EMAILS vira ADMIN ao entrar; os outros nao")
    void adminPelaVariavel() {
        responderGoogleCom("outra.operadora@exemplo.test");
        assertThat(auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null).usuario().role())
                .isEqualTo(PigguRole.ADMIN);

        responderGoogleCom(AUTORIZADA);
        assertThat(auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null).usuario().role())
                .isEqualTo(PigguRole.TITULAR);
    }

    @Test
    @DisplayName("duas pessoas sem convite ficam em familias diferentes")
    void semConviteFamiliasSeparadas() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse primeira = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);
        responderGoogleCom(CONVIDADA);
        TokenResponse segunda = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        assertThat(segunda.usuario().familia()).isNotEqualTo(primeira.usuario().familia());
        assertThat(segunda.usuario().role()).isEqualTo(PigguRole.TITULAR);
    }

    @Test
    @DisplayName("quem foi convidado entra como membro da familia que convidou, e o convite e usado")
    void convidadoEntraComoMembro() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse titular = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);
        familias.convidar(comoUsuario(titular), "Convidada@Exemplo.test");

        responderGoogleCom(CONVIDADA);
        TokenResponse membro = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        assertThat(membro.usuario().familia()).isEqualTo(titular.usuario().familia());
        assertThat(membro.usuario().role()).isEqualTo(PigguRole.MEMBRO);
        assertThat(convites.count()).isZero();
    }

    @Test
    @DisplayName("convite vencido nao vale: a pessoa ganha familia propria")
    void conviteVencido() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse titular = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);
        convites.save(new HouseholdInvite(titular.usuario().familia(), CONVIDADA, titular.usuario().id(),
                java.time.Instant.now().minusSeconds(1)));

        responderGoogleCom(CONVIDADA);
        TokenResponse outra = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        assertThat(outra.usuario().familia()).isNotEqualTo(titular.usuario().familia());
    }

    @Test
    @DisplayName("segundo acesso reaproveita a conta e atualiza o perfil do Google")
    void segundoAcessoAtualizaPerfil() {
        responderGoogleCom(AUTORIZADA);
        auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        given(verificador.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-1", AUTORIZADA, "Titular Dias", "Titular", "https://foto.test/nova.jpg"));
        TokenResponse segundo = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(segundo.usuario().foto()).isEqualTo("https://foto.test/nova.jpg");
    }

    @Test
    @DisplayName("renovar entrega tokens novos e invalida o refresh usado")
    void renovarRotacionaOToken() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse primeiro = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

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
        TokenResponse acesso = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        auth.sair(acesso.refreshToken());

        assertThatThrownBy(() -> auth.renovar(acesso.refreshToken(), null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("conta desativada nao renova e perde as sessoes abertas")
    void contaDesativadaNaoRenova() {
        responderGoogleCom(AUTORIZADA);
        TokenResponse acesso = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

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
        TokenResponse acesso = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        assertThat(sessoes.findByTokenHash(acesso.refreshToken()))
                .as("procurar pelo token puro nao pode achar nada")
                .isEmpty();
        assertThat(sessoes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("conta nova comeca em reais, cotacao em dolar, horario de Brasilia e pt-BR")
    void preferenciasPadrao() {
        responderGoogleCom(AUTORIZADA);

        TokenResponse acesso = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null);

        assertThat(acesso.usuario().preferencias())
                .isEqualTo(new UserResponse.Preferencias("BRL", "USD", true, "America/Sao_Paulo", "pt-BR"));
    }

    @Test
    @DisplayName("preferencias de moeda sao gravadas e normalizadas em maiusculas")
    void salvaPreferencias() {
        responderGoogleCom(AUTORIZADA);
        java.util.UUID id = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null).usuario().id();

        auth.salvarPreferencias(id, new PreferencesRequest("usd", "jpy", false, "Europe/Lisbon"));

        assertThat(auth.perfil(id).preferencias())
                .isEqualTo(new UserResponse.Preferencias("USD", "JPY", false, "Europe/Lisbon", "pt-BR"));
        assertThatThrownBy(() -> auth.salvarPreferencias(id, new PreferencesRequest("BRL", "USD", true, "Marte/Base")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("moeda que nao existe na ISO 4217 e recusada sem gravar nada")
    void recusaMoedaInexistente() {
        responderGoogleCom(AUTORIZADA);
        java.util.UUID id = auth.entrarComGoogle("token-google", Consentimentos.VERSAO_DO_AVISO, null).usuario().id();

        assertThatThrownBy(() -> auth.salvarPreferencias(id, new PreferencesRequest("XYZ", "BRL", true)))
                .isInstanceOf(BusinessException.class);
        assertThat(auth.perfil(id).preferencias().moeda()).isEqualTo("BRL");
    }

    private static com.piggu.common.security.CurrentUser comoUsuario(TokenResponse acesso) {
        return new com.piggu.common.security.CurrentUser(acesso.usuario().id(), acesso.usuario().email(),
                acesso.usuario().role(), null, acesso.usuario().familia());
    }

    private void responderGoogleCom(String email) {
        given(verificador.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-1", email, "Pessoa de Teste", "Pessoa", "https://foto.test/p.jpg"));
    }
}
