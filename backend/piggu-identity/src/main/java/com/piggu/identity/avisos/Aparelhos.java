package com.piggu.identity.avisos;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Aparelhos que recebem push. O mesmo token trocando de conta fica com a ultima. */
@Repository
public class Aparelhos {

    private final JdbcTemplate jdbc;

    public Aparelhos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Prende o aparelho a sessao do token (D-188): ela apagada, ele sai junto. Sessao que ja
     * saiu nao cadastra nada. Sem sessao (token de antes da D-188) fica solto ate a retencao.
     */
    public void registrar(UUID usuario, String token, String plataforma, UUID sessao) {
        try {
            jdbc.update("""
                    INSERT INTO push_devices (token, user_id, platform, session_id, seen_at)
                    SELECT ?, ?, ?, s.id, now()
                    FROM (SELECT 1) um LEFT JOIN refresh_sessions s ON s.id = ? AND s.user_id = ?
                    WHERE ?::uuid IS NULL OR s.id IS NOT NULL
                    ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform,
                        session_id = EXCLUDED.session_id, seen_at = EXCLUDED.seen_at
                    """, token, usuario, plataforma, sessao, usuario, sessao);
        } catch (DataIntegrityViolationException sessaoSaiuAgora) {
            // A sessao foi apagada entre a leitura e a gravacao: e um logout, nao ha o que cadastrar.
        }
    }

    /** Aparelho sem sessao que nao cadastrou de novo desde {@code limite}: o app nao abre mais nele. */
    public int esquecerSoltos(Instant limite) {
        return jdbc.update("DELETE FROM push_devices WHERE session_id IS NULL AND seen_at < ?",
                Timestamp.from(limite));
    }

    /** O token do iPhone foi guardado em minusculas e o do Android como veio: serve qualquer dos dois. */
    public void remover(UUID usuario, String token) {
        jdbc.update("DELETE FROM push_devices WHERE user_id = ? AND token IN (?, ?)", usuario, token,
                token.toLowerCase());
    }

    /** Token que a Apple recusou (app apagado, permissao tirada). */
    public void esquecer(String token) {
        jdbc.update("DELETE FROM push_devices WHERE token = ?", token);
    }

    public void esquecerDoUsuario(UUID usuario) {
        jdbc.update("DELETE FROM push_devices WHERE user_id = ?", usuario);
    }

    /** Aparelhos da pessoa numa plataforma: o APNs so entrega no iPhone (IOS). */
    public List<String> doUsuario(UUID usuario, String plataforma) {
        return jdbc.queryForList("SELECT token FROM push_devices WHERE user_id = ? AND platform = ? ORDER BY created_at",
                String.class, usuario, plataforma);
    }

    /** Para a exportacao dos dados: plataforma, quando entrou e o fim do token (o bastante para reconhecer). */
    public List<Map<String, Object>> paraExportar(UUID usuario) {
        return jdbc.query("SELECT token, platform, created_at FROM push_devices WHERE user_id = ? ORDER BY created_at",
                (linha, n) -> Map.of(
                        "plataforma", linha.getString("platform"),
                        "cadastradoEm", linha.getTimestamp("created_at").toInstant(),
                        "finalDoToken", fim(linha.getString("token"))),
                usuario);
    }

    private static String fim(String token) {
        return "..." + token.substring(Math.max(0, token.length() - 6));
    }
}
