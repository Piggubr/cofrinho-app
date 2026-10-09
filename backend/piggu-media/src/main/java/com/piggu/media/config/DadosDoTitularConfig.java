package com.piggu.media.config;

import com.piggu.common.dados.DadosDaFamilia.Tabela;
import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.media.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Onde o media guarda dado de alguem, para exportar e apagar (LGPD art. 18).
 *
 * <p>Foto do mural e da familia: se a pessoa sai e a familia fica, a foto fica,
 * anonimizada. Quando a familia inteira sai, os arquivos saem do armazenamento.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    private static final Logger log = LoggerFactory.getLogger(DadosDoTitularConfig.class);

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                Tabela.compartilhada("feed_photos", "user_email"),
                Tabela.compartilhada("assets", "owner_email")
        ), extras.orderedStream().toList());
    }

    /**
     * Os bytes saem do armazenamento so depois que o banco confirmar: apagar antes e
     * ver a transacao desfeita deixaria registro apontando para arquivo que nao existe.
     */
    @Bean
    DadosDaFamilia.AoApagar arquivosDaFamilia(JdbcTemplate jdbc, StoragePort armazenamento) {
        return (familia, email, escopo) -> {
            if (escopo != EscopoDeExclusao.FAMILIA) {
                return;
            }
            List<String> arquivos = jdbc.queryForList(
                    "SELECT drive_file_id FROM assets WHERE household_id = ?", String.class, familia);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String arquivo : arquivos) {
                        try {
                            armazenamento.apagar(arquivo);
                        } catch (RuntimeException erro) {
                            // A retencao varre arquivo orfao depois.
                            log.warn("Arquivo nao apagado na exclusao da familia {}", familia, erro);
                        }
                    }
                }
            });
        };
    }
}
