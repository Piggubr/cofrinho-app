package com.piggu.media.config;

import com.piggu.common.dados.DadosDaFamilia.Tabela;
import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.media.domain.Asset;
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
 * anonimizada. Quando a familia inteira sai, os arquivos saem do armazenamento. Foto
 * privada ({@link Asset#CONTEXTOS_PRIVADOS}, como a do Girls Math) sai com a pessoa mesmo
 * que a familia fique: era so dela.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    private static final Logger log = LoggerFactory.getLogger(DadosDoTitularConfig.class);

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                Tabela.compartilhada("feed_photos", "user_id"),
                Tabela.compartilhada("assets", "owner_id")
        ), extras.orderedStream().toList());
    }

    /**
     * Os bytes saem do armazenamento so depois que o banco confirmar: apagar antes e
     * ver a transacao desfeita deixaria registro apontando para arquivo que nao existe.
     */
    @Bean
    DadosDaFamilia.AoApagar arquivosDaFamilia(JdbcTemplate jdbc, StoragePort armazenamento) {
        return (familia, pessoa, escopo) -> {
            List<String> arquivos;
            if (escopo == EscopoDeExclusao.FAMILIA) {
                arquivos = jdbc.queryForList(
                        "SELECT drive_file_id FROM assets WHERE household_id = ?", String.class, familia);
            } else {
                // Antes da anonimizacao das tabelas, que tiraria o dono e deixaria a foto para sempre.
                String privadas = "household_id = ? AND owner_id = ? AND context IN ("
                        + String.join(", ", Asset.CONTEXTOS_PRIVADOS.stream().map(c -> "'" + c + "'").toList()) + ")";
                arquivos = jdbc.queryForList("SELECT drive_file_id FROM assets WHERE " + privadas, String.class,
                        familia, pessoa);
                jdbc.update("DELETE FROM assets WHERE " + privadas, familia, pessoa);
            }
            if (arquivos.isEmpty()) {
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String arquivo : arquivos) {
                        try {
                            armazenamento.apagar(arquivo);
                        } catch (RuntimeException erro) {
                            log.warn("Arquivo nao apagado na exclusao (familia {}, escopo {})", familia, escopo, erro);
                        }
                    }
                }
            });
        };
    }
}
