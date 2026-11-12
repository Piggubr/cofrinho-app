package com.piggu.identity.api;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.apple.AppleIdTokenVerifier;
import com.piggu.identity.apple.AppleProfile;
import com.piggu.identity.apple.RevogacaoDaApple;
import com.piggu.identity.avisos.Aparelhos;
import com.piggu.identity.domain.Senhas;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import com.piggu.testing.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O app nativo nao tem o cookie do site (D-058): pede o refresh no corpo e o devolve no corpo.
 * O site continua igual, e o refresh do cookie nunca aparece no corpo.
 */
@AutoConfigureMockMvc
class AppNativoTest extends PostgresIntegrationTest {

    private static final String TERMOS = "\"versaoDosTermos\":\"" + Consentimentos.VERSAO_DO_AVISO + "\"";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleIdTokenVerifier google;

    @MockitoBean
    private AppleIdTokenVerifier apple;

    @MockitoBean
    private RevogacaoDaApple revogacaoDaApple;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private Aparelhos aparelhos;

    @Autowired
    private Senhas senhas;

    @BeforeEach
    void preparar() {
        given(google.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-nativo", "nativo@exemplo.test", "Pessoa Nativa", "Pessoa", null));
    }

    @Test
    @DisplayName("com refreshNoCorpo o login devolve o refresh no corpo e nenhum cookie")
    void loginNoCorpo() throws Exception {
        MvcResult login = entrarNoApp();

        assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(refreshDe(login)).hasSizeGreaterThan(30);
        assertThat(login.getResponse().getContentAsString()).contains("accessToken").doesNotContain("lembrar");
    }

    @Test
    @DisplayName("renovar com o refresh no corpo troca por outro no corpo, sem cookie; o antigo para de valer")
    void renovarNoCorpo() throws Exception {
        String primeiro = refreshDe(entrarNoApp());

        MvcResult renovado = renovar(primeiro).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String segundo = refreshDe(renovado);

        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(renovado.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        renovar(primeiro).andExpect(status().isUnauthorized());
        renovar(segundo).andExpect(status().isOk());
    }

    @Test
    @DisplayName("o refresh do cookie nunca vai para o corpo, mesmo com corpo vazio ou pedindo refreshNoCorpo")
    void cookieNuncaVaiProCorpo() throws Exception {
        MvcResult site = mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"," + TERMOS + "}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(site.getResponse().getContentAsString()).doesNotContain("refreshToken");
        Cookie cookie = site.getResponse().getCookie(AuthController.COOKIE);

        MvcResult renovado = mockMvc.perform(post("/api/auth/refresh").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"\",\"refreshNoCorpo\":true}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(renovado.getResponse().getContentAsString()).doesNotContain("refreshToken");
        assertThat(renovado.getResponse().getCookie(AuthController.COOKIE).getValue()).isNotEqualTo(cookie.getValue());
    }

    @Test
    @DisplayName("sair com o refresh no corpo encerra a sessao do app")
    void sairNoCorpo() throws Exception {
        String refresh = refreshDe(entrarNoApp());

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        renovar(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("trocar a senha pelo app mantem a sessao do refresh do corpo e derruba as outras")
    void senhaMantemASessaoDoApp() throws Exception {
        MvcResult aqui = entrarNoApp();
        String outro = refreshDe(entrarNoApp());
        String id = JsonPath.read(aqui.getResponse().getContentAsString(), "$.usuario.id");
        String familia = JsonPath.read(aqui.getResponse().getContentAsString(), "$.usuario.familia");
        trocarSenha(id, familia, "{\"novaSenha\":\"cofrinho123\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CRIAR_SENHA_PELO_EMAIL"));
        // A primeira senha vem do link do e-mail (RedefinicaoDeSenhaTest); aqui so a troca.
        UserAccount conta = usuarios.findById(java.util.UUID.fromString(id)).orElseThrow();
        conta.definirSenha(senhas.hash("cofrinho123"));
        usuarios.save(conta);

        trocarSenha(id, familia, "{\"senhaAtual\":\"cofrinho123\",\"novaSenha\":\"cofrinho456\","
                + "\"refreshToken\":\"" + refreshDe(aqui) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temSenha").value(true));

        renovar(refreshDe(aqui)).andExpect(status().isOk());
        renovar(outro).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("entrar com a Apple cria a conta com o nome que o app manda e devolve o par")
    void entrarComApple() throws Exception {
        given(apple.verificar(eq("token-apple"), eq("nonce-1")))
                .willReturn(new AppleProfile("001.apple", "maca@privaterelay.appleid.com", "br.com.piggu.app"));
        given(revogacaoDaApple.ligada()).willReturn(true);
        given(revogacaoDaApple.trocarCodigo("codigo-1", "br.com.piggu.app")).willReturn("rt-apple");

        MvcResult primeira = mockMvc.perform(post("/api/auth/apple").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-apple\",\"nonce\":\"nonce-1\",\"nome\":\"  Maria   Maçã \","
                                + TERMOS + ",\"refreshNoCorpo\":true,\"codigo\":\"codigo-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value("maca@privaterelay.appleid.com"))
                .andExpect(jsonPath("$.usuario.apelido").value("Maria Maçã"))
                .andExpect(jsonPath("$.usuario.primeiroNome").value("Maria Maçã"))
                .andExpect(jsonPath("$.usuario.emailConfirmado").value(true))
                .andReturn();
        assertThat(refreshDe(primeira)).isNotBlank();
        // O token da Apple fica guardado para revogar quando a conta for excluida.
        assertThat(usuarios.findByEmail("maca@privaterelay.appleid.com").orElseThrow().getAppleRefreshToken())
                .isEqualTo("rt-apple");

        // Nas proximas vezes a Apple nao manda o nome, e os termos ja foram aceitos.
        mockMvc.perform(post("/api/auth/apple").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-apple\",\"nonce\":\"nonce-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value((String) JsonPath.read(
                        primeira.getResponse().getContentAsString(), "$.usuario.id")))
                .andExpect(jsonPath("$.usuario.apelido").value("Maria Maçã"));
    }

    @Test
    @DisplayName("Apple sem nonce e recusada antes do servico; Apple desligada responde 501")
    void appleRecusada() throws Exception {
        mockMvc.perform(post("/api/auth/apple").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-apple\"}"))
                .andExpect(status().isBadRequest());

        given(apple.verificar(anyString(), anyString())).willThrow(new BusinessException(
                "O login com a Apple ainda nao esta disponivel.", HttpStatus.NOT_IMPLEMENTED, "EM_BREVE"));
        mockMvc.perform(post("/api/auth/apple").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"t\",\"nonce\":\"n\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.codigo").value("EM_BREVE"));
    }

    @Test
    @DisplayName("o aparelho de push fica preso a sessao: passa pela renovacao e sai com o logout (D-188)")
    void pushPresoASessao() throws Exception {
        MvcResult login = entrarNoApp();
        String corpo = login.getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.usuario.id");
        String familia = JsonPath.read(corpo, "$.usuario.familia");
        String sessao = sessaoDe(JsonPath.read(corpo, "$.accessToken"));
        String aparelho = "dQw4w9WgXcQ:APA91bH" + "x-Y_z".repeat(28);
        mockMvc.perform(post("/api/auth/me/devices")
                        .with(jwt().jwt(token -> token.subject(id)
                                        .claim("email", "nativo@exemplo.test")
                                        .claim("role", PigguRole.TITULAR.name())
                                        .claim("familia", familia)
                                        .claim("sid", sessao))
                                .authorities(new SimpleGrantedAuthority(PigguRole.TITULAR.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + aparelho + "\",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().isNoContent());

        MvcResult renovado = renovar(refreshDe(login)).andExpect(status().isOk()).andReturn();
        String acessoNovo = JsonPath.read(renovado.getResponse().getContentAsString(), "$.accessToken");
        assertThat(sessaoDe(acessoNovo)).isEqualTo(sessao);
        assertThat(aparelhos.doUsuario(UUID.fromString(id), "ANDROID")).containsExactly(aparelho);

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshDe(renovado) + "\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(aparelhos.doUsuario(UUID.fromString(id), "ANDROID")).isEmpty();
    }

    private MvcResult entrarNoApp() throws Exception {
        return mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"," + TERMOS + ",\"refreshNoCorpo\":true}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private ResultActions trocarSenha(String id, String familia, String corpo) throws Exception {
        return mockMvc.perform(put("/api/auth/me/senha")
                .with(jwt().jwt(token -> token.subject(id)
                                .claim("email", "nativo@exemplo.test")
                                .claim("role", PigguRole.TITULAR.name())
                                .claim("familia", familia))
                        .authorities(new SimpleGrantedAuthority(PigguRole.TITULAR.authority())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions renovar(String refresh) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"));
    }

    private static String sessaoDe(String acesso) throws Exception {
        return SignedJWT.parse(acesso).getJWTClaimsSet().getStringClaim("sid");
    }

    private static String refreshDe(MvcResult resposta) throws Exception {
        return JsonPath.read(resposta.getResponse().getContentAsString(), "$.refreshToken");
    }
}
