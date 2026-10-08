package com.piggu.identity.migracao;

import com.piggu.testing.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V4 move as contas que ja existiam para a familia inicial sem perder nada.
 *
 * <p>Roda as migrations num schema proprio ate a V3, grava contas no formato antigo
 * (papeis BEATRIZ e FAMILIAR, Premium na conta) e so entao aplica o resto.</p>
 */
class MigracaoParaFamiliasTest extends PostgresIntegrationTest {

    private static final String FAMILIA_INICIAL = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("contas antigas viram titular e membro da familia inicial, que herda o Premium")
    void migraContasExistentes() {
        Flyway ate = flyway("3");
        ate.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO legado.users (email, role) VALUES ('a@legado.test', 'BEATRIZ'), ('b@legado.test', 'FAMILIAR'), ('c@legado.test', 'ADMIN')");
        jdbc.update("UPDATE legado.users SET premium_until = now() + interval '10 days', plan_source = 'WEB' WHERE email = 'a@legado.test'");

        flyway(null).migrate();

        assertThat(jdbc.queryForList("SELECT role FROM legado.users ORDER BY email", String.class))
                .containsExactly("TITULAR", "MEMBRO", "ADMIN");
        assertThat(jdbc.queryForList("SELECT DISTINCT household_id::text FROM legado.users", String.class))
                .containsExactly(FAMILIA_INICIAL);
        assertThat(jdbc.queryForObject("SELECT plan_source FROM legado.households WHERE id = '" + FAMILIA_INICIAL + "'", String.class))
                .isEqualTo("WEB");
        assertThat(jdbc.queryForObject("SELECT premium_until > now() FROM legado.households WHERE id = '" + FAMILIA_INICIAL + "'", Boolean.class))
                .isTrue();
    }

    private Flyway flyway(String alvo) {
        var config = Flyway.configure().dataSource(dataSource).schemas("legado").createSchemas(true)
                .locations("classpath:db/migration");
        if (alvo != null) {
            config.target(alvo);
        }
        return config.load();
    }
}
