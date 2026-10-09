package com.piggu.lifestyle.config;

import com.piggu.common.dados.DadosDaFamilia.Tabela;
import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.EscopoDeExclusao;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Onde o lifestyle guarda dado de alguem, para exportar e apagar (LGPD art. 18).
 *
 * <p>As fotos dos lugares moram no piggu-media, que apaga as dele. Tabela nova com
 * dado de pessoa entra aqui.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                Tabela.compartilhada("places", "user_email"),
                Tabela.compartilhada("custom_place_tags", "created_by"),
                Tabela.compartilhada("movies", "user_email"),
                Tabela.compartilhada("shopping_items", "user_email")
        ), extras.orderedStream().toList());
    }

    /** A nota de cada pessoa mora num JSON {"email": nota}; quem sai leva a dela. */
    @Bean
    DadosDaFamilia.AoApagar notasDeFilme(JdbcTemplate jdbc) {
        return (familia, email, escopo) -> {
            if (escopo == EscopoDeExclusao.PESSOA) {
                jdbc.update("UPDATE movies SET ratings = ratings - ? WHERE household_id = ?", email, familia);
            }
        };
    }
}
