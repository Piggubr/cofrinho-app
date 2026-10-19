package com.piggu.identity.config;

import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Pessoas ativas para o painel: consulta valida no banco real e no maximo uma por minuto. */
@AutoConfigureMockMvc
class MetricasDeUsoTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricasDeUso metricas;

    @Test
    @DisplayName("a consulta roda no banco de verdade e as series aparecem para o Prometheus")
    void noPrometheus() throws Exception {
        assertThat(metricas.valor("semana")).isNotNaN().isGreaterThanOrEqualTo(0);

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("piggu_uso_pessoas_ativas{janela=\"dia\"}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("piggu_uso_familias_premium")));
    }

    @Autowired
    private JdbcTemplate banco;

    @Test
    @DisplayName("as views do painel analitico (V31) rodam e so devolvem contagens")
    void viewsDoPainel() {
        for (String view : new String[] {"contas_novas_por_dia", "testes_gratis_por_dia", "assinaturas_por_dia",
                "resumo", "premium_por_origem", "familias_por_tamanho"}) {
            banco.queryForList("SELECT * FROM analitico." + view);
        }
        assertThat(banco.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'analitico' AND (column_name LIKE '%email%' OR column_name LIKE '%id')""")).isEmpty();
    }

    @Test
    @DisplayName("le o banco uma vez por minuto; banco fora do ar vira NaN, sem excecao")
    void cacheEFalha() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        given(jdbc.queryForMap(anyString())).willReturn(Map.of("dia", 3L, "contas", 10L));
        Clock[] relogio = {Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)};
        MetricasDeUso uso = new MetricasDeUso(jdbc, new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zona) { return this; }
            @Override public Instant instant() { return relogio[0].instant(); }
        });

        assertThat(uso.valor("dia")).isEqualTo(3.0);
        assertThat(uso.valor("contas")).isEqualTo(10.0);
        assertThat(uso.valor("mes")).isNaN();
        verify(jdbc, times(1)).queryForMap(anyString());

        given(jdbc.queryForMap(anyString())).willThrow(new IllegalStateException("sem banco"));
        relogio[0] = Clock.offset(relogio[0], java.time.Duration.ofSeconds(61));
        assertThat(uso.valor("dia")).isNaN();
    }
}
