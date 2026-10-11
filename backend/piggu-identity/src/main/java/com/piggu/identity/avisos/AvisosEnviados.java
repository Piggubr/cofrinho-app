package com.piggu.identity.avisos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Chaves de aviso ja mandadas a cada pessoa: o mesmo aviso nunca chega duas vezes. */
@Repository
public class AvisosEnviados {

    /** Mais que um mes cobre toda chave que ainda pode voltar (as do mes trazem o mes). */
    static final Duration GUARDA = Duration.ofDays(120);

    private final JdbcTemplate jdbc;

    public AvisosEnviados(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> jaMandados(UUID usuario) {
        return new HashSet<>(jdbc.queryForList("SELECT alert_key FROM alerts_sent WHERE user_id = ?", String.class,
                usuario));
    }

    public void marcar(UUID usuario, Collection<String> chaves) {
        chaves.forEach(chave -> jdbc.update(
                "INSERT INTO alerts_sent (user_id, alert_key) VALUES (?, ?) ON CONFLICT DO NOTHING", usuario, chave));
    }

    public int limparAntigos() {
        return jdbc.update("DELETE FROM alerts_sent WHERE sent_at < ?",
                java.sql.Timestamp.from(Instant.now().minus(GUARDA)));
    }
}
