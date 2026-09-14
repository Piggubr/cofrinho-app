package com.piggu.lifestyle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.CatalogProduct;
import com.piggu.lifestyle.config.IntegracoesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca de produtos no catalogo aberto do OpenFoodFacts.
 *
 * <p>Porte de buscarProdutosMercado_. O resultado fica em cache por seis horas,
 * como no original: o catalogo muda pouco e a API pede moderacao no uso.</p>
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private static final int MAXIMO_DE_RESULTADOS = 8;

    private final RestClient cliente;
    private final IntegracoesProperties.Catalogo propriedades;

    public CatalogClient(RestClient.Builder builder, IntegracoesProperties propriedades) {
        this.propriedades = propriedades.catalogo();
        this.cliente = builder.baseUrl(this.propriedades.baseUrl()).build();
    }

    @Cacheable(value = "catalogo", key = "#termo.toLowerCase()")
    public List<CatalogProduct> buscar(String termo) {
        String busca = Texto.limitar(termo, 80);
        if (busca.length() < 2) {
            throw new BusinessException("Digite pelo menos duas letras.");
        }

        try {
            JsonNode dados = cliente.get()
                    .uri(builder -> builder.path("/cgi/search.pl")
                            .queryParam("search_terms", busca)
                            .queryParam("search_simple", "1")
                            .queryParam("action", "process")
                            .queryParam("json", "1")
                            .queryParam("page_size", MAXIMO_DE_RESULTADOS)
                            .queryParam("sort_by", "popularity")
                            .queryParam("fields",
                                    "code,product_name,generic_name,brands,quantity,image_small_url,image_url")
                            .build())
                    .header("User-Agent", propriedades.userAgent())
                    .header("Accept", "application/json")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new UpstreamException("A busca de produtos esta indisponivel agora.");
                    })
                    .body(JsonNode.class);

            return extrair(dados);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao consultar o catalogo de produtos", erro);
            throw new UpstreamException("A busca de produtos esta indisponivel agora.");
        }
    }

    private List<CatalogProduct> extrair(JsonNode dados) {
        List<CatalogProduct> produtos = new ArrayList<>();
        if (dados == null) {
            return produtos;
        }

        dados.path("products").forEach(produto -> {
            if (produtos.size() >= MAXIMO_DE_RESULTADOS) {
                return;
            }
            String nome = primeiroNaoVazio(
                    produto.path("product_name").asText(""),
                    produto.path("generic_name").asText(""));
            if (nome.isBlank()) {
                return;
            }
            produtos.add(new CatalogProduct(
                    produto.path("code").asText(""),
                    Texto.limitar(nome, 150),
                    primeiraMarca(produto.path("brands").asText("")),
                    Texto.limitar(produto.path("quantity").asText(""), 50),
                    primeiroNaoVazio(
                            produto.path("image_small_url").asText(""),
                            produto.path("image_url").asText(""))
            ));
        });
        return produtos;
    }

    /** O campo brands vem como lista separada por virgula; a primeira basta. */
    private String primeiraMarca(String marcas) {
        return Texto.limitar(marcas.split(",")[0], 100);
    }

    private String primeiroNaoVazio(String preferido, String alternativo) {
        String limpo = preferido == null ? "" : preferido.trim();
        return limpo.isEmpty() ? (alternativo == null ? "" : alternativo.trim()) : limpo;
    }
}
