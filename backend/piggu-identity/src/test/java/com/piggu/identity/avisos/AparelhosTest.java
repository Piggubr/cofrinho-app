package com.piggu.identity.avisos;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.domain.FamiliaService;
import com.piggu.identity.domain.HouseholdInviteRepository;
import com.piggu.identity.domain.RefreshSessionRepository;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.piggu.identity.avisos.LembretesDiariosTest.TOKEN_DO_ANDROID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro de aparelho para push, no iPhone (APNs) e no Android (FCM), pela porta da API. */
@AutoConfigureMockMvc
class AparelhosTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Aparelhos aparelhos;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private JdbcTemplate jdbc;

    private UserAccount ana;

    @BeforeEach
    void preparar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
        ana = familias.criarConta("ana@familia.test", "Ana", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("Android guarda o token do FCM como veio; o do iPhone vira minusculas; os dois saem pelo DELETE")
    void androidEIphone() throws Exception {
        registrar(TOKEN_DO_ANDROID, "ANDROID").andExpect(status().isNoContent());
        registrar("AB".repeat(32), "IOS").andExpect(status().isNoContent());

        assertThat(aparelhos.doUsuario(ana.getId(), "ANDROID")).containsExactly(TOKEN_DO_ANDROID);
        assertThat(aparelhos.doUsuario(ana.getId(), "IOS")).containsExactly("ab".repeat(32));

        mockMvc.perform(delete("/api/auth/me/devices/" + TOKEN_DO_ANDROID).with(daAna()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/auth/me/devices/" + "AB".repeat(32)).with(daAna()))
                .andExpect(status().isNoContent());

        assertThat(aparelhos.doUsuario(ana.getId(), "ANDROID")).isEmpty();
        assertThat(aparelhos.doUsuario(ana.getId(), "IOS")).isEmpty();
    }

    @Test
    @DisplayName("token com caractere fora do APNs e do FCM, ou grande demais, e 400")
    void tokenInvalido() throws Exception {
        registrar("abc/def".repeat(8), "ANDROID").andExpect(status().isBadRequest());
        registrar("a".repeat(513), "ANDROID").andExpect(status().isBadRequest());
        registrar("curto", "ANDROID").andExpect(status().isBadRequest());

        assertThat(aparelhos.doUsuario(ana.getId(), "ANDROID")).isEmpty();
    }

    @Test
    @DisplayName("aparelho sem sessao sai depois de 30 dias sem cadastrar de novo; o recente fica (D-188)")
    void soltosSaemNaRetencao() {
        aparelhos.registrar(ana.getId(), "aa".repeat(32), "IOS", null);
        aparelhos.registrar(ana.getId(), "bb".repeat(32), "IOS", null);
        jdbc.update("UPDATE push_devices SET seen_at = now() - interval '31 days' WHERE token = ?", "aa".repeat(32));

        assertThat(aparelhos.esquecerSoltos(Instant.now().minus(Duration.ofDays(30)))).isEqualTo(1);
        assertThat(aparelhos.doUsuario(ana.getId(), "IOS")).containsExactly("bb".repeat(32));
    }

    @Test
    @DisplayName("token de sessao que ja saiu nao cadastra o aparelho (D-188)")
    void sessaoQueSaiuNaoCadastra() {
        aparelhos.registrar(ana.getId(), "cc".repeat(32), "IOS", UUID.randomUUID());

        assertThat(aparelhos.doUsuario(ana.getId(), "IOS")).isEmpty();
    }

    private ResultActions registrar(String token, String plataforma)
            throws Exception {
        return mockMvc.perform(post("/api/auth/me/devices").with(daAna())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"plataforma\":\"" + plataforma + "\"}"));
    }

    private RequestPostProcessor daAna() {
        return TokensDeTeste.como(ana.getEmail(), PigguRole.TITULAR, ana.getId());
    }
}
