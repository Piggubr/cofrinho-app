package com.piggu.finance.domain;

import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migrations do Flyway contra o mapeamento das entidades.
 *
 * <p>Este teste existe por causa de um defeito real: tres colunas foram escritas
 * como CHAR no SQL enquanto as entidades esperavam VARCHAR, e o servico so quebrava
 * ao subir, com o Hibernate recusando o schema. Como o perfil de teste mantem
 * ddl-auto em validate, qualquer divergencia nova derruba este teste em vez de
 * derrubar o servico em producao.</p>
 *
 * <p>O simples fato de o contexto carregar ja e a metade da verificacao: significa
 * que o Flyway aplicou tudo e o Hibernate validou cada coluna.</p>
 */
class MigrationsTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("todas as tabelas do dominio financeiro foram criadas")
    void tabelasCriadas() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tabelas).contains(
                "expenses", "monthly_goals", "piggy_deposits",
                "notes", "product_memory", "custom_categories", "app_settings");
    }

    @Test
    @DisplayName("colunas de texto usam VARCHAR, nunca CHAR com preenchimento")
    void semColunasChar() {
        List<String> colunasChar = jdbc.queryForList(
                """
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND data_type = 'character'
                """,
                String.class);

        // CHAR preenche com espacos ate o tamanho fixo, entao um mes gravado como
        // 2026-09 voltaria diferente do que foi gravado em uma coluna maior.
        assertThat(colunasChar).isEmpty();
    }

    @Test
    @DisplayName("a restricao de valor do gasto impede numero negativo")
    void restricaoDeValor() {
        List<String> restricoes = jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'expenses'::regclass",
                String.class);

        assertThat(restricoes).contains("expenses_amount_check");
    }

    @Test
    @DisplayName("o mes da meta so aceita o formato AAAA-MM")
    void formatoDoMesDaMeta() {
        assertThat(jdbc.queryForObject(
                // Filtra pela tabela: o MigracaoParaFamiliasTest roda as migrations num schema
                // "legado", que cria uma restricao com o mesmo nome em outra tabela.
                """
                SELECT count(*) FROM pg_constraint
                WHERE conname = 'monthly_goals_month_check'
                  AND conrelid = 'monthly_goals'::regclass
                """,
                Integer.class)).isEqualTo(1);
    }
}
