package com.piggu.banking.domain;

import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Banco que ninguem atualiza ha 90 dias e desconectado: saldo velho e dado financeiro
 * sem finalidade (LGPD art. 6, III, e 16), e o item vivo na Pluggy segue custando.
 *
 * <p>Apaga la e aqui, como o botao Desconectar. Falha na Pluggy deixa a conexao para
 * a proxima rodada, para nao sobrar item vivo la sem registro do lado de ca.</p>
 */
@Component
public class RetencaoDeBancos {

    private static final Logger log = LoggerFactory.getLogger(RetencaoDeBancos.class);

    private final JdbcTemplate jdbc;
    private final PluggyClient pluggy;
    private final Duration semAtualizacao;

    public RetencaoDeBancos(JdbcTemplate jdbc, PluggyClient pluggy,
                            @Value("${piggu.retencao.banco-sem-atualizacao:P90D}") Duration semAtualizacao) {
        this.jdbc = jdbc;
        this.pluggy = pluggy;
        this.semAtualizacao = semAtualizacao;
    }

    @Scheduled(cron = "${piggu.retencao.cron:0 50 3 * * *}", zone = "America/Sao_Paulo")
    public void aplicar() {
        List<Map<String, Object>> paradas = jdbc.queryForList(
                "SELECT id, pluggy_item_id FROM bank_connections WHERE COALESCE(synced_at, created_at) < ?",
                Timestamp.from(Instant.now().minus(semAtualizacao)));
        int desconectadas = 0;
        for (Map<String, Object> conexao : paradas) {
            try {
                try {
                    pluggy.apagarItem((String) conexao.get("pluggy_item_id"));
                } catch (NotFoundException jaApagado) {
                    // A Pluggy ja nao conhece: segue para apagar aqui.
                }
                jdbc.update("DELETE FROM bank_connections WHERE id = ?", conexao.get("id"));
                desconectadas++;
            } catch (RuntimeException erro) {
                log.warn("Retencao: conexao {} fica para a proxima rodada", conexao.get("id"), erro);
            }
        }
        log.info("Retencao de bancos: desconectados={}", desconectadas);
    }
}
