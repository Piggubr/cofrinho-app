package com.piggu.media.domain;

import com.piggu.media.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Foto do mural que nao pertence a mais nada sai no dia seguinte (LGPD art. 16).
 *
 * <p>Acontece quando o envio da foto deu certo e o registro do mural nao (queda no meio,
 * aba fechada). SQL direto, sem o filtro por familia: e manutencao da instalacao inteira.</p>
 *
 * <p>ponytail: foto de lugar orfa nao entra aqui, porque so o lifestyle sabe quais estao
 * em uso; o lifestyle ja apaga a foto ao trocar ou apagar o lugar. Cruzar os dois quando
 * aparecer foto de lugar perdida.</p>
 */
@Component
public class RetencaoDeFotos {

    private static final Logger log = LoggerFactory.getLogger(RetencaoDeFotos.class);

    private final JdbcTemplate jdbc;
    private final StoragePort armazenamento;

    public RetencaoDeFotos(JdbcTemplate jdbc, StoragePort armazenamento) {
        this.jdbc = jdbc;
        this.armazenamento = armazenamento;
    }

    @Scheduled(cron = "${piggu.retencao.cron:0 45 3 * * *}", zone = "America/Sao_Paulo")
    public void aplicar() {
        List<Map<String, Object>> orfas = jdbc.queryForList("""
                SELECT a.id, a.drive_file_id FROM assets a
                WHERE a.context = 'FEED' AND a.created_at < now() - interval '1 day'
                  AND NOT EXISTS (SELECT 1 FROM feed_photos f WHERE f.asset_id = a.id)
                """);
        int apagadas = 0;
        for (Map<String, Object> foto : orfas) {
            try {
                armazenamento.apagar((String) foto.get("drive_file_id"));
                jdbc.update("DELETE FROM assets WHERE id = ?", foto.get("id"));
                apagadas++;
            } catch (RuntimeException erro) {
                log.warn("Retencao: foto {} fica para a proxima rodada", foto.get("id"), erro);
            }
        }
        log.info("Retencao de fotos: orfasApagadas={}", apagadas);
    }
}
