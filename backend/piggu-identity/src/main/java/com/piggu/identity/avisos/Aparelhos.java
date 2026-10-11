package com.piggu.identity.avisos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Aparelhos que recebem push. O mesmo token trocando de conta fica com a ultima. */
@Repository
public class Aparelhos {

    private final JdbcTemplate jdbc;

    public Aparelhos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(UUID usuario, String token, String plataforma) {
        jdbc.update("""
                INSERT INTO push_devices (token, user_id, platform) VALUES (?, ?, ?)
                ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform
                """, token, usuario, plataforma);
    }

    public void remover(UUID usuario, String token) {
        jdbc.update("DELETE FROM push_devices WHERE user_id = ? AND token = ?", usuario, token);
    }

    /** Token que a Apple recusou (app apagado, permissao tirada). */
    public void esquecer(String token) {
        jdbc.update("DELETE FROM push_devices WHERE token = ?", token);
    }

    public List<String> doUsuario(UUID usuario) {
        return jdbc.queryForList("SELECT token FROM push_devices WHERE user_id = ? ORDER BY created_at", String.class,
                usuario);
    }
}
