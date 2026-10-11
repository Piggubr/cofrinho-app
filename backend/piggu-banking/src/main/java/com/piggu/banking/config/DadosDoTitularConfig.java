package com.piggu.banking.config;

import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.dados.DadosDaFamilia.Tabela;
import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.common.error.NotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Onde o banking guarda dado de alguem, para exportar e apagar (LGPD art. 18).
 *
 * <p>Banco conectado e da pessoa, nao da familia: sai mesmo quando a familia fica, e a
 * conexao e apagada tambem na Pluggy (revogar o consentimento, Res. Conjunta 1/2020
 * art. 15). As contas saem junto pela chave estrangeira.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                new Tabela("bank_accounts", null, true),
                Tabela.pessoal("bank_connections", "user_id"),
                Tabela.pessoal("consents", "user_id")
        ), extras.orderedStream().toList());
    }

    @Bean
    Consentimentos consentimentos(JdbcTemplate jdbc) {
        return new Consentimentos(jdbc);
    }

    /** Falha na Pluggy aborta a exclusao: item vivo la sem registro aqui ninguem mais apaga. */
    @Bean
    DadosDaFamilia.AoApagar conexoesNaPluggy(JdbcTemplate jdbc, PluggyClient pluggy) {
        return (familia, pessoa, escopo) -> {
            List<String> itens = escopo == EscopoDeExclusao.FAMILIA
                    ? jdbc.queryForList("SELECT pluggy_item_id FROM bank_connections WHERE household_id = ?",
                            String.class, familia)
                    : jdbc.queryForList("SELECT pluggy_item_id FROM bank_connections WHERE household_id = ? AND user_id = ?",
                            String.class, familia, pessoa);
            for (String item : itens) {
                try {
                    pluggy.apagarItem(item);
                } catch (NotFoundException jaApagado) {
                    // Item que a Pluggy nao conhece mais conta como apagado.
                }
            }
        };
    }
}
