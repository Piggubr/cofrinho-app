package com.piggu.identity.config;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;

/** Quem mudou papeis e quem entrou ou saiu da familia. */
@Configuration
public class AuditoriaConfig {

    @Bean
    TrilhaDeAuditoria trilhaDeAuditoria(JdbcTemplate jdbc,
                                        @Value("${piggu.auditoria.retencao-dias:400}") int retencaoEmDias) {
        return new TrilhaDeAuditoria(jdbc, Duration.ofDays(retencaoEmDias), Clock.systemUTC());
    }
}
