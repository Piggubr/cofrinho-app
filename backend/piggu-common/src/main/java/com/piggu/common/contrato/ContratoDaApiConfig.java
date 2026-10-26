package com.piggu.common.contrato;

import com.piggu.common.error.ApiError;
import com.piggu.common.security.AuthUser;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * O que todo contrato da API (docs/api) tem em comum: quem e o servico, os enderecos, o
 * login por JWT e o corpo de erro {@link ApiError}.
 *
 * <p>So existe com o springdoc no classpath e {@code springdoc.api-docs.enabled=true}, o que
 * acontece no perfil dev e nos testes. Em producao nada daqui e carregado.</p>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ContratoDaApiConfig {

    /** Nome do esquema de seguranca que as operacoes referenciam. */
    public static final String JWT = "bearer";

    private static final String ERRO = "ApiError";

    static {
        // Quem chama vem do token, nao da requisicao: sem isto o usuario apareceria como parametro.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthUser.class);
    }

    @Bean
    OpenAPI contratoDaApi(@Value("${spring.application.name}") String servico) {
        return new OpenAPI()
                .info(new Info()
                        .title(servico)
                        .version("1")
                        .description("Contrato da API do Piggu. Regras de uso e de compatibilidade em docs/API_SPEC.md."))
                .servers(List.of(
                        new Server().url("https://piggu.com.br").description("Producao"),
                        new Server().url("http://localhost:8080").description("Local, pelo gateway")))
                .components(new Components().addSecuritySchemes(JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token do login (POST /api/auth/entrar, /api/auth/google, /api/auth/apple ou /api/auth/refresh).")))
                .addSecurityItem(new SecurityRequirement().addList(JWT));
    }

    /**
     * Nome estavel de cada operacao, que vira o nome do metodo no cliente gerado (Swift e
     * Kotlin): recurso do controller mais o metodo, como {@code expenseListar}. Sem isto o
     * springdoc repetiria {@code listar} com sufixos _1, _2 que mudam conforme a ordem.
     */
    @Bean
    OperationCustomizer nomeDaOperacao() {
        return (operacao, metodo) -> {
            String recurso = metodo.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            String nome = metodo.getMethod().getName();
            semCorpoE204(operacao.getResponses(), metodo);
            return operacao.operationId(Character.toLowerCase(recurso.charAt(0)) + recurso.substring(1)
                    + Character.toUpperCase(nome.charAt(0)) + nome.substring(1));
        };
    }

    /**
     * {@code ResponseEntity<Void>} quase sempre e {@code noContent()}, mas o springdoc so ve o
     * tipo e escreveria 200. Os poucos que respondem outra coisa (202 do cadastro, 201 da conta
     * fixa) declaram {@code @ApiResponse} no proprio metodo e ficam como estao.
     */
    private static void semCorpoE204(ApiResponses respostas, HandlerMethod metodo) {
        boolean declarou = metodo.hasMethodAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse.class)
                || metodo.hasMethodAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class);
        ResolvableType tipo = ResolvableType.forMethodReturnType(metodo.getMethod());
        boolean semCorpo = ResponseEntity.class.equals(tipo.resolve())
                && Void.class.equals(tipo.getGeneric(0).resolve());
        if (declarou || !semCorpo || respostas == null || !respostas.containsKey("200")) {
            return;
        }
        respostas.remove("200");
        respostas.addApiResponse("204", new ApiResponse().description("No Content"));
    }

    /**
     * Toda operacao responde {@link ApiError} quando da errado. Em vez de repetir cada status
     * em cada rota, fica uma resposta {@code default}; os status usados estao no API_SPEC.md.
     */
    @Bean
    OpenApiCustomizer erroPadrao() {
        return api -> {
            Map<String, Schema> esquemas = ModelConverters.getInstance(true).readAll(ApiError.class);
            esquemas.forEach(api.getComponents()::addSchemas);
            ApiResponse erro = new ApiResponse()
                    .description("Erro: corpo ApiError, com a mensagem pronta para a tela e um código estável.")
                    .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERRO))));
            if (api.getTags() != null) {
                api.getTags().sort(Comparator.comparing(io.swagger.v3.oas.models.tags.Tag::getName));
            }
            if (api.getPaths() == null) {
                return;
            }
            api.getPaths().values().forEach(caminho -> caminho.readOperations()
                    .forEach(operacao -> operacao.getResponses().putIfAbsent("default", erro)));
        };
    }
}
