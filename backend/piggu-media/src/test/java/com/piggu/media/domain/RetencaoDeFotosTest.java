package com.piggu.media.domain;

import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Foto do mural sem registro no mural sai no dia seguinte; as outras ficam. */
class RetencaoDeFotosTest extends PostgresIntegrationTest {

    @Autowired
    private RetencaoDeFotos retencao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("so a foto orfa e antiga do mural sai")
    void orfasAntigas() {
        UUID familia = UUID.randomUUID();
        UUID orfaAntiga = foto(familia, "FEED", "2 days");
        UUID orfaNova = foto(familia, "FEED", "1 hour");
        UUID noMural = foto(familia, "FEED", "2 days");
        UUID deLugar = foto(familia, "LUGAR", "2 days");
        jdbc.update("INSERT INTO feed_photos (month_key, asset_id, user_email, household_id) VALUES (?, ?, ?, ?)",
                "2026-09", noMural, "a@x.test", familia);

        retencao.aplicar();

        assertThat(existe(orfaAntiga)).isFalse();
        assertThat(existe(orfaNova)).isTrue();
        assertThat(existe(noMural)).isTrue();
        assertThat(existe(deLugar)).isTrue();
    }

    private UUID foto(UUID familia, String contexto, String idade) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assets (id, drive_file_id, content_type, size_bytes, context, owner_email, household_id,"
                        + " created_at) VALUES (?, ?, ?, 10, ?, ?, ?, now() - CAST(? AS interval))",
                id, "arquivo-que-nao-existe-" + id, "image/png", contexto, "a@x.test", familia, idade);
        return id;
    }

    private boolean existe(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM assets WHERE id = ?", Integer.class, id) == 1;
    }
}
