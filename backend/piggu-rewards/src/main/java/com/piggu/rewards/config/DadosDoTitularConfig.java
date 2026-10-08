package com.piggu.rewards.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.DadosDaFamilia.Tabela;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Onde o rewards guarda dado de alguem, para exportar e apagar (LGPD art. 18).
 *
 * <p>Ordem de exclusao: resgates apontam para o livro-razao e para os premios.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                Tabela.compartilhada("redemptions", "user_email"),
                Tabela.compartilhada("coin_ledger", "actor_email"),
                Tabela.compartilhada("prizes", "updated_by")
        ), extras.orderedStream().toList());
    }
}
