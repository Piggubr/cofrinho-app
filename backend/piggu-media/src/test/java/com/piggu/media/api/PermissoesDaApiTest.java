package com.piggu.media.api;

import com.piggu.media.domain.Asset;
import com.piggu.media.domain.AssetService;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem alcanca fotos e o feed.
 *
 * <p>O feed sempre foi so de ADMIN e BEATRIZ, mas as fotos em si ficavam abertas a
 * qualquer conta logada: bastava o id. Estes testes travam as duas portas.</p>
 */
@AutoConfigureMockMvc
class PermissoesDaApiTest extends PostgresIntegrationTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetService assets;

    @Test
    @DisplayName("sem token nenhuma foto sai")
    void semTokenNaoPassa() throws Exception {
        Asset foto = assets.guardar(PNG, "image/png", "feed", null, TokensDeTeste.EMAIL_BEATRIZ);
        mockMvc.perform(get("/api/assets/" + foto.getId() + "/content")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("familiar nao le foto por id, nao envia arquivo e nao ve o feed")
    void familiarNaoAlcanca() throws Exception {
        Asset foto = assets.guardar(PNG, "image/png", "feed", null, TokensDeTeste.EMAIL_BEATRIZ);

        mockMvc.perform(get("/api/assets/" + foto.getId() + "/content").with(TokensDeTeste.familiar()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/assets").with(TokensDeTeste.familiar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageBase64\":\"" + PNG + "\",\"mimeType\":\"image/png\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed").with(TokensDeTeste.familiar())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("beatriz le a foto, com nosniff para o navegador nao reinterpretar o arquivo")
    void beatrizLe() throws Exception {
        Asset foto = assets.guardar(PNG, "image/png", "feed", null, TokensDeTeste.EMAIL_BEATRIZ);

        mockMvc.perform(get("/api/assets/" + foto.getId() + "/content").with(TokensDeTeste.beatriz()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
