package com.piggu.lifestyle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Servicos externos usados pelo lifestyle.
 *
 * @param tmdb     catalogo de filmes
 * @param catalogo catalogo aberto de produtos de mercado (OpenFoodFacts)
 */
@ConfigurationProperties(prefix = "piggu.integracoes")
public record IntegracoesProperties(Tmdb tmdb, Catalogo catalogo) {

    /**
     * @param token credencial do TMDB; aceita tanto a chave curta v3 quanto o token
     *              longo v4, porque o Apps Script ja lidava com as duas formas
     */
    public record Tmdb(String baseUrl, String token, String idioma, String regiao, Duration timeout) {

        public Tmdb {
            baseUrl = vazio(baseUrl) ? "https://api.themoviedb.org/3" : baseUrl;
            idioma = vazio(idioma) ? "pt-BR" : idioma;
            regiao = vazio(regiao) ? "PT" : regiao;
            timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        }

        public boolean habilitado() {
            return !vazio(token);
        }
    }

    /**
     * @param userAgent o OpenFoodFacts exige identificacao da aplicacao nas chamadas
     */
    public record Catalogo(String baseUrl, String userAgent, Duration timeout) {

        public Catalogo {
            baseUrl = vazio(baseUrl) ? "https://world.openfoodfacts.org" : baseUrl;
            userAgent = vazio(userAgent) ? "Piggu/1.0 (contato@piggu.app)" : userAgent;
            timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        }
    }

    public IntegracoesProperties {
        tmdb = tmdb == null ? new Tmdb(null, null, null, null, null) : tmdb;
        catalogo = catalogo == null ? new Catalogo(null, null, null) : catalogo;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
