package com.piggu.common.contrato;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContratoDaApiConfigTest {

    private final ContratoDaApiConfig config = new ContratoDaApiConfig();

    /** Controller de mentira, so para os nomes e os tipos de retorno. */
    static class ExpenseController {

        public ResponseEntity<Void> excluir() {
            return ResponseEntity.noContent().build();
        }

        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202")
        public ResponseEntity<Void> aceitar() {
            return ResponseEntity.accepted().build();
        }

        public ResponseEntity<String> exportar() {
            return ResponseEntity.ok("");
        }
    }

    private Operation operacao(String metodo) throws NoSuchMethodException {
        HandlerMethod handler = new HandlerMethod(new ExpenseController(), ExpenseController.class.getMethod(metodo));
        Operation operacao = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        return config.nomeDaOperacao().customize(operacao, handler);
    }

    @Test
    @DisplayName("o nome da operacao junta o recurso e o metodo, sem sufixo _1")
    void nomeEstavel() throws Exception {
        assertThat(operacao("excluir").getOperationId()).isEqualTo("expenseExcluir");
    }

    @Test
    @DisplayName("ResponseEntity<Void> sem @ApiResponse vira 204; com @ApiResponse ou com corpo fica como esta")
    void semCorpo() throws Exception {
        assertThat(operacao("excluir").getResponses()).containsOnlyKeys("204");
        assertThat(operacao("aceitar").getResponses()).containsOnlyKeys("200");
        assertThat(operacao("exportar").getResponses()).containsOnlyKeys("200");
    }

    @Test
    @DisplayName("toda operacao ganha o ApiError como resposta de erro e as tags saem em ordem")
    void erroPadrao() {
        OpenAPI api = config.contratoDaApi("piggu-teste");
        Operation operacao = new Operation().responses(new ApiResponses());
        api.paths(new Paths().addPathItem("/api/x", new PathItem().get(operacao)));
        api.tags(new ArrayList<>(List.of(new Tag().name("Gastos"), new Tag().name("Cofrinho"))));

        config.erroPadrao().customise(api);

        assertThat(operacao.getResponses().get("default").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/ApiError");
        assertThat(api.getComponents().getSchemas()).containsKeys("ApiError", "CampoInvalido");
        assertThat(api.getComponents().getSecuritySchemes()).containsKey(ContratoDaApiConfig.JWT);
        assertThat(api.getTags()).extracting(Tag::getName).containsExactly("Cofrinho", "Gastos");
    }
}
