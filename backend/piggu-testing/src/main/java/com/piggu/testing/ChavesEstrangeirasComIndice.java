package com.piggu.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda chave estrangeira tem um indice que comeca pelas colunas dela.
 *
 * <p>Sem ele, apagar o registro apontado varre a tabela que aponta (DATA_MODEL, "Coisas que
 * achei", item 7). Cada servico com banco estende esta classe num teste vazio.</p>
 */
public abstract class ChavesEstrangeirasComIndice extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("toda chave estrangeira tem indice")
    void todaChaveEstrangeiraTemIndice() {
        List<String> semIndice = jdbc.queryForList("""
                SELECT c.conrelid::regclass || '.' || string_agg(a.attname, ',' ORDER BY k.n)
                FROM pg_constraint c
                CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, n)
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                JOIN pg_namespace s ON s.oid = c.connamespace
                WHERE c.contype = 'f' AND s.nspname = current_schema()
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_index i
                      WHERE i.indrelid = c.conrelid
                        AND (i.indkey::int2[])[0:cardinality(c.conkey) - 1] @> c.conkey
                        AND (i.indkey::int2[])[0:cardinality(c.conkey) - 1] <@ c.conkey)
                GROUP BY c.conrelid, c.conname
                """, String.class);

        assertThat(semIndice).isEmpty();
    }
}
