package com.piggu.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Endpoints e credenciais dos servicos externos usados pelo financeiro.
 *
 * <p>No Apps Script esses valores ficavam nas Propriedades do script; aqui vem por
 * variavel de ambiente, e nenhum deles tem valor padrao com segredo embutido.</p>
 */
@ConfigurationProperties(prefix = "piggu.integracoes")
public record IntegracoesProperties(Gemini gemini, Cambio cambio) {

    /**
     * @param apiKey  chave da API do Gemini; vazia desliga a leitura de recibos
     * @param modelo  modelo usado na leitura
     * @param timeout tempo maximo de espera por uma leitura
     */
    public record Gemini(String baseUrl, String apiKey, String modelo, Duration timeout) {

        public Gemini {
            baseUrl = vazio(baseUrl) ? "https://generativelanguage.googleapis.com" : baseUrl;
            modelo = vazio(modelo) ? "gemini-3.6-flash" : modelo;
            timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
        }

        public boolean habilitado() {
            return !vazio(apiKey);
        }
    }

    /**
     * @param url     endpoint da cotacao de referencia EUR para BRL
     * @param padrao  valor usado enquanto nao houver nenhuma cotacao conhecida
     */
    public record Cambio(String url, Duration timeout, String padrao) {

        public Cambio {
            url = vazio(url) ? "https://api.frankfurter.dev/v2/rate/EUR/BRL" : url;
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
            padrao = vazio(padrao) ? "6.15" : padrao;
        }
    }

    public IntegracoesProperties {
        gemini = gemini == null ? new Gemini(null, null, null, null) : gemini;
        cambio = cambio == null ? new Cambio(null, null, null) : cambio;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
