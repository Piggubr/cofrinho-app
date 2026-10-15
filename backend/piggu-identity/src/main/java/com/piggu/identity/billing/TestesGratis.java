package com.piggu.identity.billing;

import com.piggu.identity.domain.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o teste gratis precisa lembrar fora da conta: CPFs de contas excluidas que ja
 * testaram, as campanhas que liberam o teste de novo e quem usou cada campanha.
 */
@Component
public class TestesGratis {

    /** Mesmo prazo do CDC art. 27 para a reparacao de danos. */
    public static final Duration RETENCAO_DO_CPF = Duration.ofDays(5 * 365);

    /**
     * Campanha de teste gratis.
     *
     * @param limite            quantas contas podem usar; nulo e sem limite
     * @param paraQuemJaTestou  libera o teste tambem para quem ja usou o seu
     */
    public record Campanha(String codigo, int diasDeTeste, Instant inicio, Instant fim, Integer limite, int usos,
                           boolean paraQuemJaTestou) {

        boolean valeAgora() {
            Instant agora = Instant.now();
            return !agora.isBefore(inicio) && agora.isBefore(fim) && (limite == null || usos < limite);
        }
    }

    private final JdbcTemplate jdbc;

    public TestesGratis(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Codigo como a pessoa digitou ou veio no link: sem espacos e em maiusculas. */
    public static String normalizar(String codigo) {
        return codigo == null || codigo.isBlank() ? null : codigo.strip().toUpperCase(Locale.ROOT);
    }

    /** Um CPF de conta ja excluida usou o teste. */
    public boolean cpfDeContaExcluidaTestou(String cpfIndice) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trial_cpf_history WHERE cpf_indice = ?)", Boolean.class, cpfIndice));
    }

    /** Na exclusao da conta: guarda so o HMAC do CPF e quando o teste foi usado. */
    public void lembrarAoExcluir(UserAccount conta) {
        if (conta.temCpf() && conta.usouTesteGratis()) {
            jdbc.update("INSERT INTO trial_cpf_history (cpf_indice, used_at) VALUES (?, ?) "
                            + "ON CONFLICT (cpf_indice) DO UPDATE SET used_at = GREATEST(trial_cpf_history.used_at, EXCLUDED.used_at)",
                    conta.getCpfIndice(), Timestamp.from(conta.getTrialUsedAt()));
        }
    }

    /** @return quantos CPFs passaram do prazo e sairam */
    public int apagarVencidos(Instant agora) {
        return jdbc.update("DELETE FROM trial_cpf_history WHERE used_at < ?", Timestamp.from(agora.minus(RETENCAO_DO_CPF)));
    }

    public Optional<Campanha> campanha(String codigo) {
        return jdbc.query("SELECT code, trial_days, starts_at, ends_at, max_uses, uses, for_previous_trial_users "
                        + "FROM trial_campaigns WHERE code = ?",
                (linha, n) -> new Campanha(linha.getString(1), linha.getInt(2), linha.getTimestamp(3).toInstant(),
                        linha.getTimestamp(4).toInstant(), (Integer) linha.getObject(5), linha.getInt(6), linha.getBoolean(7)),
                codigo).stream().findFirst();
    }

    public boolean usouCampanha(UUID contaId, String codigo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trial_campaign_uses WHERE user_id = ? AND code = ?)",
                Boolean.class, contaId, codigo));
    }

    /**
     * O teste da campanha comecou. Idempotente: a Stripe repete o aviso e o uso conta uma vez.
     *
     * <p>ponytail: o limite e conferido no checkout e contado aqui, entao dois checkouts
     * simultaneos no ultimo lugar podem passar os dois. Contar no checkout com reserva se
     * campanha com limite apertado virar comum.</p>
     */
    public void registrarUso(UUID contaId, String codigo) {
        int novo = jdbc.update("INSERT INTO trial_campaign_uses (user_id, code) SELECT ?, code FROM trial_campaigns "
                + "WHERE code = ? ON CONFLICT DO NOTHING", contaId, codigo);
        if (novo > 0) {
            jdbc.update("UPDATE trial_campaigns SET uses = uses + 1 WHERE code = ?", codigo);
        }
    }

    /** Um aviso por chave: falso quando ja foi registrado antes (reenvio do provedor). */
    public boolean primeiraVez(String chave) {
        return jdbc.update("INSERT INTO billing_events (event_id, provider) VALUES (?, 'STRIPE') ON CONFLICT DO NOTHING",
                chave) > 0;
    }
}
