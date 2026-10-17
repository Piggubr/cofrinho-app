package com.piggu.finance.migracao;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
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

/**
 * A V13 troca o e-mail de quem lancou pelo id da conta, usando o de-para que o
 * {@code migrar-emails-para-ids.sh} copia do identity. Sem o de-para, nada perde o dono:
 * a migracao para com erro.
 */
class MigracaoDoEmailParaIdTest extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("com o de-para, o e-mail vira o id; conta-excluida vira nulo e sistema o id zero; o de-para sai")
    void trocaPeloDePara() {
        String esquema = "emails_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(esquema, "12").migrate();
        UUID ana = UUID.randomUUID();
        jdbc.execute("CREATE TABLE " + esquema + ".de_para_usuarios (id UUID PRIMARY KEY, email VARCHAR(320) NOT NULL)");
        jdbc.update("INSERT INTO " + esquema + ".de_para_usuarios VALUES (?, 'ana@legado.test')", ana);
        gasto(jdbc, esquema, "Pao", "Ana@Legado.test");
        gasto(jdbc, esquema, "Leite", "conta-excluida");
        jdbc.update("INSERT INTO " + esquema + ".eventos_de_auditoria (household_id, autor_email, acao, entidade, criado_em) "
                + "VALUES (gen_random_uuid(), 'sistema', 'PAGOU', 'conta-fixa', now())");

        flyway(esquema, null).migrate();

        assertThat(jdbc.queryForObject("SELECT user_id FROM " + esquema + ".expenses WHERE item = 'Pao'", UUID.class))
                .isEqualTo(ana);
        assertThat(jdbc.queryForObject("SELECT user_id FROM " + esquema + ".expenses WHERE item = 'Leite'", UUID.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT autor_id FROM " + esquema + ".eventos_de_auditoria", UUID.class))
                .isEqualTo(TrilhaDeAuditoria.SISTEMA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? "
                + "AND column_name IN ('user_email', 'member_email', 'created_by', 'autor_email')", Integer.class, esquema))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass(?)::text", String.class, esquema + ".de_para_usuarios"))
                .isNull();
    }

    @Test
    @DisplayName("sem o de-para, linha com e-mail para a migracao em vez de perder o dono")
    void semDeParaPara() {
        String esquema = "emails_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(esquema, "12").migrate();
        gasto(jdbc, esquema, "Pao", "ana@legado.test");

        assertThatThrownBy(() -> flyway(esquema, null).migrate())
                .hasMessageContaining("migrar-emails-para-ids.sh");
        assertThat(jdbc.queryForObject("SELECT user_email FROM " + esquema + ".expenses", String.class))
                .isEqualTo("ana@legado.test");
    }

    private static void gasto(JdbcTemplate jdbc, String esquema, String item, String email) {
        jdbc.update("INSERT INTO " + esquema + ".expenses (household_id, expense_date, receipt_id, item, category, amount, user_email) "
                + "VALUES (gen_random_uuid(), '2026-09-01', gen_random_uuid(), ?, 'Mercado', 5, ?)", item, email);
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
