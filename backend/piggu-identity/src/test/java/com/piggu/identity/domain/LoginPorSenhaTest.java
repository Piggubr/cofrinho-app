package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Cadastro e entrada por e-mail e senha, com o Google como outra porta da mesma conta.
 */
class LoginPorSenhaTest extends PostgresIntegrationTest {

    private static final String EMAIL = "senha@exemplo.test";
    private static final String SENHA = "cofrinho123";

    @MockitoBean
    private GoogleIdTokenVerifier verificador;

    @Autowired
    private AuthService auth;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private HouseholdInviteRepository convites;

    @BeforeEach
    void limpar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("cadastro cria conta titular com o nome informado, senha em BCrypt e e-mail ainda sem prova")
    void cadastroCriaConta() {
        TokenResponse acesso = cadastrar(" Senha@Exemplo.TEST ");

        assertThat(acesso.accessToken()).isNotBlank();
        assertThat(acesso.usuario().email()).isEqualTo(EMAIL);
        assertThat(acesso.usuario().nome()).isEqualTo("Duda Souza");
        assertThat(acesso.usuario().role()).isEqualTo(PigguRole.TITULAR);
        assertThat(acesso.usuario().temSenha()).isTrue();
        assertThat(acesso.usuario().emailConfirmado()).isFalse();
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(conta.getPasswordHash()).startsWith("$2").doesNotContain(SENHA);
        assertThat(conta.getTermsVersion()).isEqualTo(Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("cadastro pede o aceite dos termos e recusa senha fraca sem criar conta")
    void cadastroValida() {
        assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, null, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("TERMOS_NECESSARIOS"));
        for (String fraca : new String[] {"curta1", "somenteletras", "1234567890", EMAIL, "a1" + "x".repeat(71)}) {
            assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, fraca, Consentimentos.VERSAO_DO_AVISO, true, null))
                    .as(fraca)
                    .isInstanceOfSatisfying(BusinessException.class,
                            erro -> assertThat(erro.getCodigo()).isEqualTo("SENHA_FRACA"));
        }
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("e-mail que ja tem conta (Google ou senha) nao cadastra de novo")
    void emailDuplicado() {
        responderGoogleCom(EMAIL);
        auth.entrarComGoogle("token", Consentimentos.VERSAO_DO_AVISO, null);

        assertThatThrownBy(() -> cadastrar(EMAIL.toUpperCase()))
                .isInstanceOfSatisfying(BusinessException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("EMAIL_JA_CADASTRADO");
                    assertThat(erro.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("entra com a senha certa; senha errada e e-mail sem conta dao a mesma resposta")
    void entrar() {
        cadastrar(EMAIL);

        assertThat(auth.entrarComSenha("SENHA@exemplo.test", SENHA, false, "navegador").accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, "errada123", true, null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("E-mail ou senha incorretos.");
        assertThatThrownBy(() -> auth.entrarComSenha("ninguem@exemplo.test", SENHA, true, null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("E-mail ou senha incorretos.");
    }

    @Test
    @DisplayName("cinco senhas erradas travam a entrada por senha, mesmo com a certa depois")
    void travaPorTentativas() {
        cadastrar(EMAIL);
        for (int i = 0; i < AuthService.TENTATIVAS_DE_SENHA; i++) {
            assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, "errada123", true, null))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().senhaTravada(java.time.Instant.now())).isTrue();
    }

    @Test
    @DisplayName("conta do Google sem senha avisa para entrar pelo Google; depois de criar a senha, entra pelos dois")
    void googleCriaSenhaDepois() {
        responderGoogleCom(EMAIL);
        TokenResponse google = auth.entrarComGoogle("token", Consentimentos.VERSAO_DO_AVISO, null);
        assertThat(google.usuario().temSenha()).isFalse();
        assertThat(google.usuario().emailConfirmado()).isTrue();

        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("CONTA_SEM_SENHA"));

        assertThat(auth.definirSenha(google.usuario().id(), null, SENHA, null).temSenha()).isTrue();
        assertThat(auth.entrarComSenha(EMAIL, SENHA, true, null).usuario().id()).isEqualTo(google.usuario().id());
        assertThat(auth.entrarComGoogle("token", null, null).usuario().temSenha()).isTrue();
    }

    @Test
    @DisplayName("trocar a senha pede a atual e derruba as outras sessoes, menos a deste aparelho")
    void trocarSenha() {
        TokenResponse aqui = cadastrar(EMAIL);
        auth.entrarComSenha(EMAIL, SENHA, true, "outro aparelho");
        java.util.UUID id = aqui.usuario().id();
        assertThat(sessoes.findByUserId(id)).hasSize(2);

        assertThatThrownBy(() -> auth.definirSenha(id, "errada123", "novaSenha456", aqui.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("SENHA_ATUAL_ERRADA"));

        auth.definirSenha(id, SENHA, "novaSenha456", aqui.refreshToken());

        assertThat(sessoes.findByUserId(id)).hasSize(1);
        assertThat(auth.renovar(aqui.refreshToken(), null).accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null)).isInstanceOf(UnauthorizedException.class);
        assertThat(auth.entrarComSenha(EMAIL, "novaSenha456", true, null).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("quem cadastrou o e-mail de outra pessoa perde a senha quando o dono entra pelo Google")
    void donoDoEmailRetomaAConta() {
        TokenResponse intruso = cadastrar(EMAIL);

        responderGoogleCom(EMAIL);
        TokenResponse dono = auth.entrarComGoogle("token", null, null);

        assertThat(dono.usuario().id()).isEqualTo(intruso.usuario().id());
        assertThat(dono.usuario().temSenha()).isFalse();
        assertThat(dono.usuario().emailConfirmado()).isTrue();
        assertThatThrownBy(() -> auth.renovar(intruso.refreshToken(), null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("CONTA_SEM_SENHA"));
    }

    @Test
    @DisplayName("cadastro por senha nao usa convite de familia nem ve convites enquanto o e-mail nao tem prova")
    void conviteSoComEmailConfirmado() {
        responderGoogleCom("titular@familia.test");
        TokenResponse titular = auth.entrarComGoogle("token", Consentimentos.VERSAO_DO_AVISO, null);
        familias.convidar(comoUsuario(titular), EMAIL);

        TokenResponse cadastro = cadastrar(EMAIL);

        assertThat(cadastro.usuario().familia()).isNotEqualTo(titular.usuario().familia());
        assertThat(cadastro.usuario().role()).isEqualTo(PigguRole.TITULAR);
        assertThat(familias.convitesParaMim(comoUsuario(cadastro))).isEmpty();
        java.util.UUID convite = convites.findByEmailOrderByCreatedAtDesc(EMAIL).getFirst().getId();
        assertThatThrownBy(() -> familias.aceitarConvite(comoUsuario(cadastro), convite))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("EMAIL_NAO_CONFIRMADO"));
    }

    @Test
    @DisplayName("e-mail da PIGGU_ADMIN_EMAILS cadastrado por senha nao vira ADMIN")
    void adminSoComEmailConfirmado() {
        TokenResponse acesso = auth.cadastrar("Operadora", "outra.operadora@exemplo.test", SENHA,
                Consentimentos.VERSAO_DO_AVISO, true, null);
        assertThat(acesso.usuario().role()).isEqualTo(PigguRole.TITULAR);
        assertThat(auth.entrarComSenha("outra.operadora@exemplo.test", SENHA, true, null).usuario().role())
                .isEqualTo(PigguRole.TITULAR);
    }

    private TokenResponse cadastrar(String email) {
        return auth.cadastrar("  Duda   Souza ", email, SENHA, Consentimentos.VERSAO_DO_AVISO, true, "navegador");
    }

    private static CurrentUser comoUsuario(TokenResponse acesso) {
        return new CurrentUser(acesso.usuario().id(), acesso.usuario().email(),
                acesso.usuario().role(), null, acesso.usuario().familia());
    }

    private void responderGoogleCom(String email) {
        given(verificador.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-1", email, "Pessoa de Teste", "Pessoa", "https://foto.test/p.jpg"));
    }
}
