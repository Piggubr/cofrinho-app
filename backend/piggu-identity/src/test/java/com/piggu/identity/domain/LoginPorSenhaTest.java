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
    private static final String CPF = "529.982.247-25";
    private static final String CELULAR = "+55 (11) 99999-8888";

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

    @Autowired
    private ConfirmacaoDeEmail confirmacao;

    @Autowired
    private TokenService tokens;

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
        assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, "BR", CPF, CELULAR, null, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("TERMOS_NECESSARIOS"));
        for (String fraca : new String[] {"curta1", "somenteletras", "1234567890", EMAIL, "a1" + "x".repeat(71)}) {
            assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, fraca, "BR", CPF, CELULAR,
                    Consentimentos.VERSAO_DO_AVISO, true, null))
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
        TokenResponse acesso = auth.cadastrar("Operadora", "outra.operadora@exemplo.test", SENHA, "BR",
                CPF, CELULAR, Consentimentos.VERSAO_DO_AVISO, true, null);
        assertThat(acesso.usuario().role()).isEqualTo(PigguRole.TITULAR);
        assertThat(auth.entrarComSenha("outra.operadora@exemplo.test", SENHA, true, null).usuario().role())
                .isEqualTo(PigguRole.TITULAR);
    }

    @Test
    @DisplayName("documento e opcional; se vier, precisa ser valido. O celular fica em E.164")
    void cpfNoCadastro() {
        assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, "BR", "529.982.247-26", CELULAR,
                Consentimentos.VERSAO_DO_AVISO, true, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CPF");
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();

        TokenResponse semCpf = auth.cadastrar("Ana", "ana@exemplo.test", SENHA, "PT", " ", "+351 912 345 678",
                Consentimentos.VERSAO_DO_AVISO, true, null);
        assertThat(semCpf.usuario().cpfInformado()).isFalse();
        assertThat(usuarios.findByEmail("ana@exemplo.test").orElseThrow().getPhone()).isEqualTo("+351912345678");

        TokenResponse acesso = cadastrar(EMAIL);

        assertThat(acesso.usuario().cpfInformado()).isTrue();
        assertThat(acesso.usuario().pais()).isEqualTo("BR");
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(conta.temCpf()).isTrue();
        assertThat(conta.getCpfCifrado()).doesNotContain("52998224725");
        assertThat(conta.getPhone()).isEqualTo("+5511999998888");
    }

    @Test
    @DisplayName("celular sem codigo fora do Brasil, ou curto demais, nao cadastra")
    void celularInvalido() {
        for (String[] caso : new String[][] {{"US", "2025550123"}, {"BR", "1234"}, {"BR", ""}}) {
            assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, caso[0], null, caso[1],
                    Consentimentos.VERSAO_DO_AVISO, true, null))
                    .as(caso[0] + " " + caso[1])
                    .isInstanceOfSatisfying(BusinessException.class,
                            erro -> assertThat(erro.getCodigo()).isEqualTo("TELEFONE_INVALIDO"));
        }
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("o link do e-mail confirma uma vez; reenviar invalida o anterior")
    void confirmacaoPorLink() {
        TokenResponse acesso = cadastrar(EMAIL);
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(conta.isEmailVerified()).isFalse();

        // O token em claro so existe no e-mail; aqui pedimos um com hash conhecido.
        conta.pedirConfirmacaoDeEmail(tokens.hash("link-1"), java.time.Instant.now().plusSeconds(60));
        usuarios.save(conta);
        confirmacao.reenviar(acesso.usuario().id());
        assertThatThrownBy(() -> confirmacao.confirmar("link-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("LINK_INVALIDO"));

        conta = usuarios.findByEmail(EMAIL).orElseThrow();
        conta.pedirConfirmacaoDeEmail(tokens.hash("link-2"), java.time.Instant.now().plusSeconds(60));
        usuarios.save(conta);
        assertThat(confirmacao.confirmar("link-2").emailConfirmado()).isTrue();
        assertThatThrownBy(() -> confirmacao.confirmar("link-2")).isInstanceOf(BusinessException.class);

        conta = usuarios.findByEmail(EMAIL).orElseThrow();
        conta.pedirConfirmacaoDeEmail(tokens.hash("vencido"), java.time.Instant.now().minusSeconds(1));
        usuarios.save(conta);
        assertThatThrownBy(() -> confirmacao.confirmar("vencido")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CPF que ja esta em outra conta barra o cadastro: uma conta por pessoa")
    void cpfRepetido() {
        cadastrar(EMAIL);

        assertThatThrownBy(() -> auth.cadastrar("Outra", "outra@exemplo.test", SENHA, "BR", "52998224725", CELULAR,
                Consentimentos.VERSAO_DO_AVISO, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo(MinhaContaService.CODIGO_CPF_EM_USO));
        assertThat(usuarios.findByEmail("outra@exemplo.test")).isEmpty();
    }

    @Test
    @DisplayName("quem mora num pais sem documento no catalogo cadastra sem documento e o app nao pede")
    void foraDoBrasilSemCpf() {
        TokenResponse acesso = auth.cadastrar("Ana", EMAIL, SENHA, "US", null, CELULAR, Consentimentos.VERSAO_DO_AVISO, true, null);

        assertThat(acesso.usuario().pais()).isEqualTo("US");
        assertThat(acesso.usuario().cpfInformado()).as("nada pendente de CPF").isTrue();
        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().temCpf()).isFalse();
    }

    @Test
    @DisplayName("em Portugal o NIF informado precisa ser valido, guarda cifrado e nao deixa repetir")
    void documentoDePortugal() {
        assertThatThrownBy(() -> auth.cadastrar("Rita", EMAIL, SENHA, "PT", "123456780", CELULAR, Consentimentos.VERSAO_DO_AVISO, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("DOCUMENTO_INVALIDO"));
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();

        TokenResponse acesso = auth.cadastrar("Rita", EMAIL, SENHA, "pt", "123 456 789", CELULAR, Consentimentos.VERSAO_DO_AVISO,
                true, null);

        assertThat(acesso.usuario().pais()).isEqualTo("PT");
        assertThat(acesso.usuario().cpfInformado()).as("documento em dia").isTrue();
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(conta.temDocumento()).isTrue();
        assertThat(conta.getTipoDoDocumento()).isEqualTo("PT_NIF");
        assertThat(conta.getDocumentoCifrado()).doesNotContain("123456789");

        assertThatThrownBy(() -> auth.cadastrar("Outra", "outra@exemplo.test", SENHA, "PT", "123456789", CELULAR,
                Consentimentos.VERSAO_DO_AVISO, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("DOCUMENTO_EM_USO"));
        assertThatThrownBy(() -> auth.cadastrar("Outra", "outra@exemplo.test", SENHA, "XX", null, CELULAR,
                Consentimentos.VERSAO_DO_AVISO, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("PAIS_INVALIDO"));
    }

    private TokenResponse cadastrar(String email) {
        return auth.cadastrar("  Duda   Souza ", email, SENHA, "BR", CPF, CELULAR, Consentimentos.VERSAO_DO_AVISO, true,
                "navegador");
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
