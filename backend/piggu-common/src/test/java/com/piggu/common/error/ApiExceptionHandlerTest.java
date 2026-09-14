package com.piggu.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tradutor de excecoes em respostas HTTP.
 *
 * <p>Inclui a regressao do defeito encontrado nos testes manuais: um PATCH em um
 * endereco que so aceita GET voltava 500, porque o catch-all de Exception engolia
 * as excecoes de protocolo do proprio Spring MVC. Isso escondia do front que o erro
 * estava na chamada, e nao no servidor.</p>
 */
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void prepararServidor() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDeTeste())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("metodo nao suportado responde 405, nao 500")
    void metodoNaoSuportadoDevolve405() throws Exception {
        mockMvc.perform(patch("/teste/somente-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_INVALIDO"))
                .andExpect(jsonPath("$.erro").value("Esta operacao nao existe neste endereco."));
    }

    @Test
    @DisplayName("erro de negocio mantem a mensagem escrita para o usuario")
    void erroDeNegocioMantemMensagem() throws Exception {
        mockMvc.perform(get("/teste/negocio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("O saldo nao pode ficar negativo."))
                .andExpect(jsonPath("$.codigo").value("BusinessException"));
    }

    @Test
    @DisplayName("nao encontrado responde 404 com a mensagem do dominio")
    void naoEncontradoDevolve404() throws Exception {
        mockMvc.perform(get("/teste/ausente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Gasto nao encontrado."));
    }

    @Test
    @DisplayName("falha inesperada vira 500 generico, sem vazar detalhe tecnico")
    void falhaInesperadaNaoVazaDetalhe() throws Exception {
        mockMvc.perform(get("/teste/explode"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Ocorreu um erro. Tente novamente."))
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));
    }

    @Test
    @DisplayName("validacao lista o campo invalido e resume a primeira mensagem")
    void validacaoDetalhaCampo() throws Exception {
        mockMvc.perform(post("/teste/validado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Pedido(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("nome"))
                .andExpect(jsonPath("$.erro").value("Digite o nome."));
    }

    @Test
    @DisplayName("corpo ilegivel responde 400, nao 500")
    void corpoIlegivelDevolve400() throws Exception {
        mockMvc.perform(post("/teste/validado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{isso nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_INVALIDO"));
    }

    record Pedido(@NotBlank(message = "Digite o nome.") String nome) {
    }

    @RestController
    @RequestMapping("/teste")
    static class ControladorDeTeste {

        @GetMapping("/somente-get")
        String somenteGet() {
            return "ok";
        }

        @GetMapping("/negocio")
        String negocio() {
            throw new BusinessException("O saldo nao pode ficar negativo.");
        }

        @GetMapping("/ausente")
        String ausente() {
            throw new NotFoundException("Gasto nao encontrado.");
        }

        @GetMapping("/explode")
        String explode() {
            throw new IllegalStateException("detalhe interno que nao pode vazar");
        }

        @PostMapping("/validado")
        String validado(@Valid @RequestBody Pedido pedido) {
            return pedido.nome();
        }
    }
}
