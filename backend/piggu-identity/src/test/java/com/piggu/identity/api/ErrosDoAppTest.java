package com.piggu.identity.api;

import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Erro de JavaScript do app: chega sem token e vai para o log sem dado pessoal. */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ErrosDoAppTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("aceita sem token e registra no log, sem e-mail, CPF nem o que vinha na busca")
    void registraSemDadoPessoal(CapturedOutput saida) throws Exception {
        String corpo = """
                {"mensagem": "Falhou ao salvar fulana@exemplo.test com CPF 123.456.789-09",
                 "pilha": "TypeError: x is undefined\\n    at salvar (main-ABC123.js:1:2345)",
                 "tela": "/gastos/:id?busca=mercado", "versao": "1.4.0"}
                """;

        mockMvc.perform(post(ErrosDoAppController.ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNoContent());

        assertThat(saida.getAll())
                .contains("Erro no app: Falhou ao salvar [email] com CPF [cpf]")
                .doesNotContain("fulana@exemplo.test", "123.456.789-09", "mercado");
    }

    @Test
    @DisplayName("so POST: o resto da rota continua fechado")
    void soRegistrar() throws Exception {
        mockMvc.perform(post("/api/app/outra").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("limpeza: token, telefone, numero comprido, quebra de linha e tamanho")
    void limpeza() {
        assertThat(ErrosDoAppController.limpar("Bearer abc.def eyJhbGciOi.eyJzdWIiOiIx.c2ln", 200))
                .isEqualTo("Bearer [token] [token]");
        assertThat(ErrosDoAppController.limpar("ligue (11) 98765-4321 ou 4111111111111111", 200))
                .isEqualTo("ligue [telefone] ou [numero]");
        assertThat(ErrosDoAppController.limpar("linha 1\r\nlinha 2\u0007", 200)).isEqualTo("linha 1 | linha 2");
        assertThat(ErrosDoAppController.limpar("x".repeat(500), 300)).hasSize(300);
        assertThat(ErrosDoAppController.limpar(null, 10)).isEqualTo("-");
        assertThat(ErrosDoAppController.limpar("   ", 10)).isEqualTo("-");
        assertThat(ErrosDoAppController.limpar("Por que?", 50)).isEqualTo("Por que?");
    }
}
