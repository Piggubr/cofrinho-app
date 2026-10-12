package com.piggu.media.config;

import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.Plano;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Quem sai e deixa a familia leva as fotos privadas; as compartilhadas ficam sem dono. */
class ExclusaoDaPessoaTest extends PostgresIntegrationTest {

    @Autowired
    private DadosDaFamilia dados;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("foto do Girls Math sai com a pessoa; a do mural fica, anonimizada; a dos outros nao muda")
    void fotoPrivadaSaiComAPessoa() {
        UUID familia = UUID.randomUUID();
        UUID quemSai = UUID.randomUUID();
        UUID quemFica = UUID.randomUUID();
        UUID privada = foto(familia, quemSai, "GIRLS_MATH");
        UUID doMural = foto(familia, quemSai, "FEED");
        UUID privadaDeOutra = foto(familia, quemFica, "GIRLS_MATH");

        dados.apagar(new CurrentUser(quemSai, "sai@piggu.test", PigguRole.PARCEIRO, Plano.GRATUITO, familia),
                EscopoDeExclusao.PESSOA);

        assertThat(dono(privada)).isNull();
        assertThat(existe(privada)).isFalse();
        assertThat(existe(doMural)).isTrue();
        assertThat(dono(doMural)).isNull();
        assertThat(dono(privadaDeOutra)).isEqualTo(quemFica);
    }

    private UUID foto(UUID familia, UUID dono, String contexto) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assets (id, drive_file_id, content_type, size_bytes, context, owner_id, household_id)"
                        + " VALUES (?, ?, 'image/png', 10, ?, ?, ?)",
                id, "arquivo-que-nao-existe-" + id, contexto, dono, familia);
        return id;
    }

    private boolean existe(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM assets WHERE id = ?", Integer.class, id) == 1;
    }

    private UUID dono(UUID id) {
        return jdbc.query("SELECT owner_id FROM assets WHERE id = ?",
                linha -> linha.next() ? linha.getObject(1, UUID.class) : null, id);
    }
}
