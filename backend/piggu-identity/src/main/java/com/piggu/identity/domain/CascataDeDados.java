package com.piggu.identity.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.piggu.common.error.UpstreamException;
import com.piggu.identity.config.DadosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fala com {@code /api/meus-dados} de cada servico de dominio.
 *
 * <p>Sem broker e sem credencial de servico: o identity ja emite os tokens, entao chama
 * cada servico com um token da propria pessoa (o da requisicao, para exportar; um token
 * de exclusao de cinco minutos, para apagar). O servico valida como valida qualquer
 * chamada. Qualquer falha aborta antes de a conta sair do identity, entao a pessoa pode
 * pedir de novo (apagar o que ja saiu nao faz mal) e nenhum dado fica sem dono.</p>
 */
@Component
public class CascataDeDados {

    private static final Logger log = LoggerFactory.getLogger(CascataDeDados.class);
    private static final String ROTA = "/api/meus-dados";

    private final RestClient cliente;
    private final DadosProperties propriedades;

    public CascataDeDados(RestClient.Builder builder, DadosProperties propriedades) {
        this.cliente = builder.build();
        this.propriedades = propriedades;
    }

    public Map<String, JsonNode> exportar(String token) {
        Map<String, JsonNode> dados = new LinkedHashMap<>();
        propriedades.servicos().forEach((nome, url) -> dados.put(nome, chamar(nome, () -> cliente.get()
                .uri(url + ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class))));
        return dados;
    }

    public void apagar(String tokenDeExclusao) {
        propriedades.servicos().forEach((nome, url) -> chamar(nome, () -> cliente.delete()
                .uri(url + ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeExclusao)
                .retrieve()
                .toBodilessEntity()));
    }

    private <T> T chamar(String servico, Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (RuntimeException erro) {
            log.error("Servico {} nao respondeu a {}", servico, ROTA, erro);
            throw new UpstreamException("Nao consegui falar com todos os servicos agora. Sua conta continua; tente de novo.");
        }
    }
}
