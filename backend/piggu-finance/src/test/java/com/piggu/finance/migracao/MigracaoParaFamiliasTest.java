package com.piggu.finance.migracao;

import com.piggu.testing.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V2 entrega os dados que ja existiam a familia inicial e troca as chaves que eram
 * unicas na instalacao (mes da meta, produto) por chaves unicas na familia.
 */
class MigracaoParaFamiliasTest extends PostgresIntegrationTest {

    private static final String FAMILIA_INICIAL = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("gastos, meta e memoria de precos antigos vao para a familia inicial")
    void migraDadosExistentes() {
        flyway("1").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO legado.expenses (expense_date, receipt_id, item, category, amount, user_email) "
                + "VALUES ('2026-09-01', gen_random_uuid(), 'Pao', 'Mercado', 5, 'a@legado.test')");
        jdbc.update("INSERT INTO legado.monthly_goals (reference_month, limit_amount, user_email) VALUES ('2026-09', 800, 'a@legado.test')");
        jdbc.update("INSERT INTO legado.product_memory (product_key, name, user_email) VALUES ('pao', 'Pao', 'a@legado.test')");

        flyway(null).migrate();

        for (String tabela : new String[]{"expenses", "monthly_goals", "product_memory"}) {
            assertThat(jdbc.queryForList("SELECT DISTINCT household_id::text FROM legado." + tabela, String.class))
                    .as(tabela).containsExactly(FAMILIA_INICIAL);
        }
        // Outra familia pode ter meta para o mesmo mes.
        jdbc.update("INSERT INTO legado.monthly_goals (household_id, reference_month, limit_amount, user_email) "
                + "VALUES (gen_random_uuid(), '2026-09', 500, 'b@outra.test')");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legado.monthly_goals", Integer.class)).isEqualTo(2);
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
