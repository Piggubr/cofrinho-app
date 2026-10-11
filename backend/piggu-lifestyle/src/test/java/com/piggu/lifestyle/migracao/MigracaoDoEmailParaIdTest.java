package com.piggu.lifestyle.migracao;

import com.piggu.testing.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A V3 troca o e-mail pelo id de quem lancou e tambem na chave da nota de cada pessoa no filme. */
class MigracaoDoEmailParaIdTest extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("com o de-para, dono e notas do filme passam para o id")
    void trocaDonoENotas() {
        String esquema = esquemaNovo();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(esquema, "2").migrate();
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        jdbc.execute("CREATE TABLE " + esquema + ".de_para_usuarios (id UUID PRIMARY KEY, email VARCHAR(320) NOT NULL)");
        jdbc.update("INSERT INTO " + esquema + ".de_para_usuarios VALUES (?, 'ana@legado.test'), (?, 'beto@legado.test')",
                ana, beto);
        filme(jdbc, esquema, "{\"ana@legado.test\": 5, \"Beto@Legado.test\": 3}");

        flyway(esquema, null).migrate();

        assertThat(jdbc.queryForObject("SELECT user_id FROM " + esquema + ".movies", UUID.class)).isEqualTo(ana);
        assertThat(jdbc.queryForObject("SELECT ratings ->> ? FROM " + esquema + ".movies", String.class, beto.toString()))
                .isEqualTo("3");
        assertThat(jdbc.queryForObject("SELECT ratings ->> ? FROM " + esquema + ".movies", String.class, ana.toString()))
                .isEqualTo("5");
    }

    @Test
    @DisplayName("sem o de-para, filme com nota por e-mail para a migracao")
    void semDeParaPara() {
        String esquema = esquemaNovo();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(esquema, "2").migrate();
        filme(jdbc, esquema, "{\"ana@legado.test\": 5}");

        assertThatThrownBy(() -> flyway(esquema, null).migrate()).hasMessageContaining("migrar-emails-para-ids.sh");
    }

    private static String esquemaNovo() {
        return "emails_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void filme(JdbcTemplate jdbc, String esquema, String notas) {
        jdbc.update("INSERT INTO " + esquema + ".movies (household_id, title, ratings, user_email) "
                + "VALUES (gen_random_uuid(), 'Matrix', ?::jsonb, 'ana@legado.test')", notas);
    }

    private Flyway flyway(String esquema, String alvo) {
        var config = Flyway.configure().dataSource(dataSource).schemas(esquema).createSchemas(true)
                .locations("classpath:db/migration");
        if (alvo != null) {
            config.target(alvo);
        }
        return config.load();
    }
}
