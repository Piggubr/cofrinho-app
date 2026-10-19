package com.piggu.identity.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/**
 * Quantas pessoas usaram o app no ultimo dia, semana e mes, como metrica do Prometheus.
 *
 * <p>O banco so guarda o ultimo acesso de cada conta ({@code last_login_at}, que a renovacao
 * do token tambem atualiza), entao a historia de "pessoas ativas por dia" nao da para tirar
 * dele depois. O Prometheus le este numero a cada minuto e guarda a serie: e dela que sai o
 * grafico de pessoas ativas do painel "Uso do app".</p>
 *
 * <p>So contagens, de todas as familias juntas: nenhum id, e-mail ou familia vira rotulo.
 * A consulta roda no maximo uma vez por minuto, por mais que o Prometheus pergunte.</p>
 */
@Component
public class MetricasDeUso implements MeterBinder {

    static final String PESSOAS_ATIVAS = "piggu.uso.pessoas.ativas";
    static final String CONTAS = "piggu.uso.contas";
    static final String FAMILIAS_PREMIUM = "piggu.uso.familias.premium";
    private static final long VALIDADE_MS = 60_000;

    private static final String CONSULTA = """
            SELECT count(*) FILTER (WHERE last_login_at >= now() - interval '1 day')   AS dia,
                   count(*) FILTER (WHERE last_login_at >= now() - interval '7 days')  AS semana,
                   count(*) FILTER (WHERE last_login_at >= now() - interval '30 days') AS mes,
                   count(*)                                                            AS contas,
                   (SELECT count(*) FROM households WHERE premium_until > now())       AS premium
            FROM users
            """;

    private final JdbcTemplate jdbc;
    private final Clock relogio;
    private volatile Map<String, Object> ultima = Map.of();
    private volatile long lidaEm = Long.MIN_VALUE;

    @Autowired
    public MetricasDeUso(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    MetricasDeUso(JdbcTemplate jdbc, Clock relogio) {
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    @Override
    public void bindTo(MeterRegistry registro) {
        for (String janela : new String[] {"dia", "semana", "mes"}) {
            Gauge.builder(PESSOAS_ATIVAS, this, m -> m.valor(janela))
                    .tag("janela", janela)
                    .description("Pessoas que usaram o app na janela (ultimo dia, 7 ou 30 dias)")
                    .register(registro);
        }
        Gauge.builder(CONTAS, this, m -> m.valor("contas")).description("Contas existentes").register(registro);
        Gauge.builder(FAMILIAS_PREMIUM, this, m -> m.valor("premium"))
                .description("Familias com Premium ativo (inclui teste gratis)").register(registro);
    }

    double valor(String coluna) {
        long agora = relogio.millis();
        if (lidaEm == Long.MIN_VALUE || agora - lidaEm >= VALIDADE_MS) {
            synchronized (this) {
                if (lidaEm == Long.MIN_VALUE || agora - lidaEm >= VALIDADE_MS) {
                    try {
                        ultima = jdbc.queryForMap(CONSULTA);
                    } catch (RuntimeException banco) {
                        // Banco fora do ar: a metrica some (NaN) em vez de derrubar a leitura das outras.
                        ultima = Map.of();
                    }
                    lidaEm = agora;
                }
            }
        }
        Object valor = ultima.get(coluna);
        return valor instanceof Number numero ? numero.doubleValue() : Double.NaN;
    }
}
