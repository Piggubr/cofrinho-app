package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.avisos.CanalDeEmail;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** "Esqueci minha senha": o link no e-mail, a senha nova e o que acontece com as sessoes. */
class RedefinicaoDeSenhaTest extends PostgresIntegrationTest {

    private static final String EMAIL = "esqueci@exemplo.test";
    private static final String SENHA = "cofrinho123";
    private static final String NOVA = "porquinho456";
    private static final Pattern LINK = Pattern.compile("/redefinir-senha\\?token=(\\S+)");

    @MockitoBean
    private CanalDeEmail email;

    @Autowired
    private RedefinicaoDeSenha redefinicao;

    @Autowired
    private AuthService auth;

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
        given(email.ligado()).willReturn(true);
        given(email.enviar(anyString(), anyString(), anyString())).willReturn(true);
    }

    @Test
    @DisplayName("o link do e-mail troca a senha, confirma o e-mail e derruba todas as sessoes")
    void redefinePeloLink() {
        TokenResponse sessao = cadastrarEEntrar();
        clearInvocations(email);

        redefinicao.pedir(" Esqueci@Exemplo.TEST ");
        redefinicao.redefinir(tokenDoEmail(), NOVA);

        assertThat(auth.entrarComSenha(EMAIL, NOVA, true, null).accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.entrarComSenha(EMAIL, SENHA, true, null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.renovar(sessao.refreshToken(), null))
                .as("a sessao aberta antes da troca caiu")
                .isInstanceOf(UnauthorizedException.class);
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        assertThat(conta.isEmailVerified()).isTrue();
        assertThat(conta.getPasswordHash()).doesNotContain(NOVA);
    }

    @Test
    @DisplayName("o link vale uma vez so, e token inventado nao serve")
    void linkUsadoOuTrocado() {
        cadastrarEEntrar();
        clearInvocations(email);
        redefinicao.pedir(EMAIL);
        String primeiro = tokenDoEmail();
        redefinicao.redefinir(primeiro, NOVA);

        assertThatThrownBy(() -> redefinicao.redefinir(primeiro, "outra12345"))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("LINK_INVALIDO"));
        assertThatThrownBy(() -> redefinicao.redefinir("inventado", NOVA))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("LINK_INVALIDO"));
    }

    @Test
    @DisplayName("senha fraca e recusada e o link continua valendo para tentar de novo")
    void senhaFraca() {
        cadastrarEEntrar();
        clearInvocations(email);
        redefinicao.pedir(EMAIL);
        String token = tokenDoEmail();

        assertThatThrownBy(() -> redefinicao.redefinir(token, "curta1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("SENHA_FRACA"));
        redefinicao.redefinir(token, NOVA);
        assertThat(auth.entrarComSenha(EMAIL, NOVA, true, null).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("e-mail sem conta responde igual e nao manda nada; pedir de novo no mesmo minuto nao manda outro")
    void semContaENaoRepete() {
        redefinicao.pedir("ninguem@exemplo.test");
        verify(email, never()).enviar(anyString(), anyString(), anyString());

        cadastrarEEntrar();
        clearInvocations(email);
        redefinicao.pedir(EMAIL);
        redefinicao.pedir(EMAIL);
        verify(email, times(1)).enviar(eq(EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("conta so com Google cria a primeira senha pelo link")
    void contaDoGoogleCriaSenha() {
        cadastrarEEntrar();
        UserAccount conta = usuarios.findByEmail(EMAIL).orElseThrow();
        conta.apagarSenha();
        usuarios.save(conta);
        clearInvocations(email);

        redefinicao.pedir(EMAIL);
        redefinicao.redefinir(tokenDoEmail(), NOVA);

        assertThat(usuarios.findByEmail(EMAIL).orElseThrow().temSenha()).isTrue();
    }

    private TokenResponse cadastrarEEntrar() {
        auth.cadastrar("Duda", EMAIL, SENHA, "BR", "529.982.247-25", "+55 11 99999-8888",
                Consentimentos.VERSAO_DO_AVISO, LocalDate.of(1990, 5, 20));
        return auth.entrarComSenha(EMAIL, SENHA, true, "navegador");
    }

    private String tokenDoEmail() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq(EMAIL), eq("Piggu: crie uma senha nova"), texto.capture());
        Matcher link = LINK.matcher(texto.getValue());
        assertThat(link.find()).as("o e-mail traz o link").isTrue();
        return link.group(1);
    }
}
