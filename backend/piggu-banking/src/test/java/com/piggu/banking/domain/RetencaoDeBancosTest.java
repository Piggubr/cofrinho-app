package com.piggu.banking.domain;

import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.error.UpstreamException;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Banco parado ha 90 dias sai daqui e da Pluggy; falha na Pluggy fica para depois. */
class RetencaoDeBancosTest extends PostgresIntegrationTest {

    @MockitoBean
    private PluggyClient pluggy;

    @Autowired
    private RetencaoDeBancos retencao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("parado ha mais de 90 dias sai; atualizado ontem fica; Pluggy fora do ar adia")
    void bancosParados() {
        String parado = conexao("91 days");
        String recente = conexao("1 day");
        String pluggyFalha = conexao("120 days");
        willThrow(new UpstreamException("fora")).given(pluggy).apagarItem(pluggyFalha);

        retencao.aplicar();

        verify(pluggy).apagarItem(parado);
        verify(pluggy, never()).apagarItem(recente);
        assertThat(existe(parado)).isFalse();
        assertThat(existe(recente)).isTrue();
        assertThat(existe(pluggyFalha)).isTrue();
    }

    private String conexao(String desdeQuando) {
        String item = "item-" + UUID.randomUUID();
        jdbc.update("INSERT INTO bank_connections (pluggy_item_id, user_id, household_id, synced_at)"
                + " VALUES (?, ?, ?, now() - CAST(? AS interval))", item, UUID.randomUUID(), UUID.randomUUID(), desdeQuando);
        return item;
    }

    private boolean existe(String item) {
        return jdbc.queryForObject("SELECT count(*) FROM bank_connections WHERE pluggy_item_id = ?",
                Integer.class, item) == 1;
    }
}
