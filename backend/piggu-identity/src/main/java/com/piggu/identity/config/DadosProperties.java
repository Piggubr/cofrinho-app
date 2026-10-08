package com.piggu.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servicos que guardam dado de pessoas, para a exportacao e a exclusao em cascata.
 *
 * <p>Esta lista e a definicao de "tudo": servico novo com dado pessoal que nao entrar
 * aqui fica fora da exclusao, sem nenhum aviso. Endereco vazio (banking desligado)
 * fica de fora.</p>
 *
 * @param servicos nome do servico para o endereco interno dele
 */
@ConfigurationProperties(prefix = "piggu.dados")
public record DadosProperties(Map<String, String> servicos) {

    public DadosProperties {
        Map<String, String> ligados = new LinkedHashMap<>();
        if (servicos != null) {
            servicos.forEach((nome, url) -> {
                if (url != null && !url.isBlank()) {
                    ligados.put(nome, url);
                }
            });
        }
        servicos = ligados;
    }
}
