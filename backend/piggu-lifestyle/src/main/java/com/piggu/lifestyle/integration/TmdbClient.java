package com.piggu.lifestyle.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.TmdbMovie;
import com.piggu.lifestyle.config.IntegracoesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Catalogo de filmes do TMDB.
 *
 * <p>Porte de consultarTmdb_, buscarFilmes_, sortearFilme_ e normalizarFilmeTmdb_.</p>
 *
 * <p>So o token longo da v4, no cabecalho Authorization. A chave curta da v3 iria na
 * URL, e a URL aparece no log em erro de rede: com ela a busca fica desligada.</p>
 */
@Component
public class TmdbClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbClient.class);
    private static final String BASE_POSTER = "https://image.tmdb.org/t/p/w500";
    private static final int PAGINAS_PARA_SORTEIO = 20;
    private static final int RESULTADOS_NA_BUSCA = 8;

    private final RestClient cliente;
    private final IntegracoesProperties.Tmdb propriedades;

    public TmdbClient(RestClient.Builder builder, IntegracoesProperties propriedades) {
        this.propriedades = propriedades.tmdb();
        this.cliente = builder.baseUrl(this.propriedades.baseUrl()).build();
        String token = this.propriedades.token();
        if (token != null && !token.isBlank() && !this.propriedades.habilitado()) {
            log.warn("TMDB_READ_TOKEN nao e um token v4 (eyJ...): busca de filmes desligada."
                    + " A chave curta v3 iria na URL e acabaria no log.");
        }
    }

    public List<TmdbMovie> buscar(String termo) {
        String busca = Texto.limitar(termo, 100);
        if (busca.length() < 2) {
            throw new BusinessException("Digite pelo menos duas letras.");
        }

        JsonNode dados = chamar("/search/movie", Map.of(
                "query", busca,
                "language", propriedades.idioma(),
                "region", propriedades.regiao(),
                "include_adult", "false",
                "page", "1"
        ));

        List<TmdbMovie> filmes = new ArrayList<>();
        dados.path("results").forEach(filme -> {
            if (filmes.size() < RESULTADOS_NA_BUSCA && !filme.path("title").asString("").isBlank()) {
                filmes.add(normalizar(filme));
            }
        });
        return filmes;
    }

    /**
     * Sorteia um filme popular, opcionalmente de um genero.
     *
     * <p>A pagina e escolhida ao acaso entre as 20 primeiras: sem isso o sorteio
     * devolveria sempre os mesmos vinte filmes mais populares.</p>
     */
    public TmdbMovie sortear(String genero) {
        Map<String, String> parametros = new HashMap<>(Map.of(
                "language", propriedades.idioma(),
                "region", propriedades.regiao(),
                "include_adult", "false",
                "include_video", "false",
                "sort_by", "popularity.desc",
                "vote_count.gte", "100",
                "page", String.valueOf(ThreadLocalRandom.current().nextInt(1, PAGINAS_PARA_SORTEIO + 1))
        ));
        if (genero != null && genero.matches("\\d+")) {
            parametros.put("with_genres", genero);
        }

        JsonNode dados = chamar("/discover/movie", parametros);
        List<JsonNode> resultados = new ArrayList<>();
        dados.path("results").forEach(filme -> {
            if (!filme.path("title").asString("").isBlank()) {
                resultados.add(filme);
            }
        });

        if (resultados.isEmpty()) {
            throw new BusinessException("Nenhum filme encontrado com esse filtro.");
        }
        return normalizar(resultados.get(ThreadLocalRandom.current().nextInt(resultados.size())));
    }

    private JsonNode chamar(String caminho, Map<String, String> parametros) {
        if (!propriedades.habilitado()) {
            throw new BusinessException("A busca de filmes nao esta configurada.");
        }

        Function<UriBuilder, URI> uri = builder -> {
            builder.path(caminho);
            parametros.forEach(builder::queryParam);
            return builder.build();
        };

        try {
            return cliente.get().uri(uri)
                    .header("accept", "application/json")
                    .header("Authorization", "Bearer " + propriedades.token())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw traduzirErro(res.getStatusCode().value());
                    })
                    .body(JsonNode.class);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao consultar o TMDB", erro);
            throw new UpstreamException("O catalogo de filmes esta indisponivel agora.");
        }
    }

    private TmdbMovie normalizar(JsonNode filme) {
        String poster = filme.path("poster_path").asString("");
        BigDecimal nota = BigDecimal.valueOf(filme.path("vote_average").asDouble(0))
                .setScale(1, RoundingMode.HALF_UP);

        return new TmdbMovie(
                filme.path("id").asString(""),
                Texto.limitar(filme.path("title").asString(""), 200),
                Texto.limitar(filme.path("release_date").asString(""), 4),
                poster.isBlank() ? "" : BASE_POSTER + poster,
                nota,
                Texto.limitar(filme.path("overview").asString(""), 1000)
        );
    }

    private RuntimeException traduzirErro(int codigo) {
        if (codigo == 401) {
            return new UpstreamException("A credencial do TMDB foi recusada. Confira a configuracao.");
        }
        return new UpstreamException("O TMDB nao respondeu corretamente (erro " + codigo + ").");
    }
}
