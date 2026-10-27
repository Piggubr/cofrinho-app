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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

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
    private static final LocalDate ADULTO = LocalDate.of(1990, 5, 20);


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

    @Autowired
    private com.piggu.identity.avisos.Aparelhos aparelhos;

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
        assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, "BR", CPF, CELULAR, null, ADULTO))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("TERMOS_NECESSARIOS"));
        for (String fraca : new String[] {"curta1", "somenteletras", "1234567890", EMAIL, "a1" + "x".repeat(71)}) {
            assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, fraca, "BR", CPF, CELULAR,
                    Consentimentos.VERSAO_DO_AVISO, ADULTO))
                    .as(fraca)
                    .isInstanceOfSatisfying(BusinessException.class,
                            erro -> assertThat(erro.getCodigo()).isEqualTo("SENHA_FRACA"));
        }
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("e-mail que ja tem conta responde como cadastro novo, sem criar outra nem mexer na senha")
    void emailDuplicado() {
        responderGoogleCom(EMAIL);
        auth.entrarComGoogle("token", Consentimentos.VERSAO_DO_AVISO, null);

        auth.cadastrar("Outra", EMAIL.toUpperCase(), SENHA, "BR", CPF, CELULAR, Consentimentos.VERSAO_DO_AVISO, ADULTO);

        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().temSenha()).isFalse();
    }

    @Test
    @DisplayName("entra com a senha certa; senha errada e e-mail sem conta dao a mesma resposta")
    void entrar() {
        cadastrar(EMAIL);

        assertThat(auth.entrarComSenha("SENHA@exemplo.test", SENHA, false, "navegador").accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, "errada123", true, null))
                .isInstanceOf(UnauthorizedException.class).hasMessageStartingWith("E-mail ou senha incorretos.");
        assertThatThrownBy(() -> auth.entrarComSenha("ninguem@exemplo.test", SENHA, true, null))
                .isInstanceOf(UnauthorizedException.class).hasMessageStartingWith("E-mail ou senha incorretos.");
    }

    @Test
    @DisplayName("renovar a sessao conta como acesso: a retencao nao apaga quem so renova")
    void renovarRegistraAcesso() throws InterruptedException {
        TokenResponse acesso = cadastrar(EMAIL);
        java.time.Instant antes = usuarios.findByEmail(EMAIL).orElseThrow().getLastLoginAt();
        Thread.sleep(20);

        auth.renovar(acesso.refreshToken(), null);

        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().getLastLoginAt()).isAfter(antes);
    }

    @Test
    @DisplayName("cinco senhas erradas de um IP travam a senha para esse IP; a dona entra de outro")
    void travaPorTentativas() {
        cadastrar(EMAIL);
        for (int i = 0; i < AuthService.TENTATIVAS_DE_SENHA; i++) {
            assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, "errada123", true, null, "203.0.113.9"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null, "203.0.113.9"))
                .as("travada responde como senha errada")
                .isInstanceOf(UnauthorizedException.class).hasMessageStartingWith("E-mail ou senha incorretos.");
        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().senhaTravada(java.time.Instant.now())).isFalse();
        assertThat(auth.entrarComSenha(EMAIL, SENHA, true, null, "198.51.100.7").accessToken())
                .as("quem errou de um lugar nao tranca a dona")
                .isNotBlank();
    }

    @Test
    @DisplayName("rajada de senhas erradas ao mesmo tempo nao desfaz a trava do IP")
    void rajadaNaoDesfazATrava() throws Exception {
        cadastrar(EMAIL);
        int tentativas = AuthService.TENTATIVAS_DE_SENHA + 2;
        java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(tentativas)) {
            java.util.List<java.util.concurrent.Future<?>> rajada = new java.util.ArrayList<>();
            for (int i = 0; i < tentativas; i++) {
                rajada.add(threads.submit(() -> {
                    largada.await();
                    return assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, "errada123", true, null, "203.0.113.9"))
                            .isInstanceOf(UnauthorizedException.class);
                }));
            }
            largada.countDown();
            for (var tentativa : rajada) {
                tentativa.get();
            }
        }

        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null, "203.0.113.9"))
                .as("o IP continua travado")
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("cinquenta senhas erradas, de IPs diferentes, travam a senha da conta para todos")
    void travaDaContaComMuitosIps() {
        cadastrar(EMAIL);
        for (int i = 0; i < AuthService.TENTATIVAS_DA_CONTA; i++) {
            String ip = "203.0.113." + i;
            assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, "errada123", true, null, ip))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null, "198.51.100.7"))
                .isInstanceOf(UnauthorizedException.class);
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
                .as("conta so do Google responde como senha errada")
                .isInstanceOf(UnauthorizedException.class).hasMessageStartingWith("E-mail ou senha incorretos.");

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
                .as("conta so do Google responde como senha errada")
                .isInstanceOf(UnauthorizedException.class).hasMessageStartingWith("E-mail ou senha incorretos.");
    }

    @Test
    @DisplayName("sem e-mail confirmado nao da acesso a ninguem: convite, parceria, mostrar tudo e push")
    void semEmailConfirmadoNaoArmaAConta() {
        TokenResponse intruso = cadastrar(EMAIL);
        CurrentUser eu = comoUsuario(intruso);
        UserAccount membro = usuarios.save(new UserAccount("membro@exemplo.test", PigguRole.MEMBRO,
                intruso.usuario().familia()));

        for (org.assertj.core.api.ThrowableAssert.ThrowingCallable acao : java.util.List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                () -> familias.convidar(eu, "comparsa@exemplo.test"),
                () -> familias.mudarPapel(eu, membro.getId(), PigguRole.PARCEIRO),
                () -> auth.escolherCompartilhamento(intruso.usuario().id(), true, false),
                () -> auth.exigirEmailConfirmado(intruso.usuario().id()))) {
            assertThatThrownBy(acao).isInstanceOfSatisfying(BusinessException.class,
                    erro -> assertThat(erro.getCodigo()).isEqualTo("EMAIL_NAO_CONFIRMADO"));
        }
    }

    @Test
    @DisplayName("o dono que entra pelo Google tira o que o intruso deixou: dados presos, aparelhos e a casa dele")
    void donoDoEmailLimpaOQueOIntrusoDeixou() {
        TokenResponse intruso = cadastrar(EMAIL);
        UserAccount armada = usuarios.findByEmail(EMAIL).orElseThrow();
        // Como uma conta armada antes desta correcao: comparsa na casa, vendo tudo, e um aparelho.
        UserAccount comparsa = new UserAccount("comparsa@exemplo.test", PigguRole.PARCEIRO, armada.getHouseholdId());
        comparsa.confirmarEmail();
        comparsa = usuarios.save(comparsa);
        armada.mostrarTudoPara(comparsa.getId());
        usuarios.save(armada);
        aparelhos.registrar(armada.getId(), "ab".repeat(32), "IOS");

        responderGoogleCom(EMAIL);
        TokenResponse dono = auth.entrarComGoogle("token", null, null);

        UserAccount conta = usuarios.findById(intruso.usuario().id()).orElseThrow();
        assertThat(dono.usuario().familia()).isNotEqualTo(intruso.usuario().familia());
        assertThat(conta.getRole()).isEqualTo(PigguRole.TITULAR);
        assertThat(conta.getShowsAllTo()).isNull();
        assertThat(conta.getBirthDate()).isNull();
        assertThat(conta.temCpf()).isFalse();
        assertThat(conta.getPhone()).isNull();
        assertThat(conta.getNickname()).isNull();
        assertThat(aparelhos.doUsuario(conta.getId(), "IOS")).isEmpty();
        assertThat(aparelhos.doUsuario(conta.getId(), "ANDROID")).isEmpty();
        assertThat(usuarios.findById(comparsa.getId()).orElseThrow().getHouseholdId())
                .isEqualTo(intruso.usuario().familia());
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
        auth.cadastrar("Operadora", "outra.operadora@exemplo.test", SENHA, "BR",
                CPF, CELULAR, Consentimentos.VERSAO_DO_AVISO, ADULTO);
        TokenResponse acesso = auth.entrarComSenha("outra.operadora@exemplo.test", SENHA, true, null);
        assertThat(acesso.usuario().role()).isEqualTo(PigguRole.TITULAR);
        assertThat(auth.entrarComSenha("outra.operadora@exemplo.test", SENHA, true, null).usuario().role())
                .isEqualTo(PigguRole.TITULAR);
    }

    @Test
    @DisplayName("o CPF e obrigatorio e valido, aqui e fora. O celular fica em E.164")
    void cpfNoCadastro() {
        assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, "BR", " ", CELULAR,
                Consentimentos.VERSAO_DO_AVISO, ADULTO))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("CPF_NECESSARIO"));
        assertThatThrownBy(() -> auth.cadastrar("Duda", EMAIL, SENHA, "BR", "529.982.247-26", CELULAR,
                Consentimentos.VERSAO_DO_AVISO, ADULTO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("CPF");
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();

        assertThatThrownBy(() -> auth.cadastrar("Ana", "ana@exemplo.test", SENHA, "PT", " ", "+351 912 345 678",
                Consentimentos.VERSAO_DO_AVISO, ADULTO))
                .as("fora do Brasil tambem pede CPF")
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("CPF_NECESSARIO"));

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
                    Consentimentos.VERSAO_DO_AVISO, ADULTO))
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
        assertThatThrownBy(() -> confirmacao.reenviar(acesso.usuario().id()))
                .as("um link por minuto")
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("LINK_RECENTE"));

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
        confirmacao.confirmar("link-2");
        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isTrue();
        assertThatThrownBy(() -> confirmacao.confirmar("link-2")).isInstanceOf(BusinessException.class);

        conta = usuarios.findByEmail(EMAIL).orElseThrow();
        conta.pedirConfirmacaoDeEmail(tokens.hash("vencido"), java.time.Instant.now().minusSeconds(1));
        usuarios.save(conta);
        assertThatThrownBy(() -> confirmacao.confirmar("vencido")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CPF que ja esta em outra conta barra o cadastro e nao sobra conta pela metade")
    void cpfRepetido() {
        cadastrar(EMAIL);

        assertThatThrownBy(() -> auth.cadastrar("Outra", "outra@exemplo.test", SENHA, "BR", "52998224725",
                CELULAR, Consentimentos.VERSAO_DO_AVISO, ADULTO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("já está em outra conta");
        assertThat(usuarios.findByEmail("outra@exemplo.test")).isEmpty();
    }

    @Test
    @DisplayName("quem mora fora cadastra com CPF e celular de la")
    void foraDoBrasilComCpf() {
        auth.cadastrar("Ana", EMAIL, SENHA, "pt", CPF, "+351 912 345 678", Consentimentos.VERSAO_DO_AVISO, ADULTO);
        TokenResponse acesso = auth.entrarComSenha(EMAIL, SENHA, true, null);

        assertThat(acesso.usuario().pais()).isEqualTo("PT");
        assertThat(acesso.usuario().cpfInformado()).isTrue();
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(conta.temCpf()).isTrue();
        assertThat(conta.temDocumento()).as("nao guarda documento de fora").isFalse();
        assertThat(conta.getPhone()).isEqualTo("+351912345678");

        assertThatThrownBy(() -> auth.cadastrar("Outra", "outra2@exemplo.test", SENHA, "XX", CPF, CELULAR,
                Consentimentos.VERSAO_DO_AVISO, ADULTO))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("PAIS_INVALIDO"));
    }

    @Test
    @DisplayName("cadastro de quem tem menos de 13 anos e recusado, sem criar conta")
    void menorDe13NaoCria() {
        LocalDate dozeAnos = LocalDate.now().minusYears(12);
        assertThatThrownBy(() -> auth.cadastrar("Kid", EMAIL, SENHA, "BR", CPF, CELULAR,
                Consentimentos.VERSAO_DO_AVISO, dozeAnos))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("IDADE_MINIMA"));
        assertThat(usuarios.findByEmail(EMAIL)).isEmpty();
    }

    private TokenResponse cadastrar(String email) {

        auth.cadastrar("  Duda   Souza ", email, SENHA, "BR", CPF, CELULAR, Consentimentos.VERSAO_DO_AVISO, ADULTO);
        return auth.entrarComSenha(email, SENHA, true, "navegador");
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
