package com.piggu.finance.config;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.dados.DadosDaFamilia.Tabela;
import com.piggu.common.dados.DadosDaFamilia;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Onde o financeiro guarda dado de alguem, para exportar e apagar (LGPD art. 18).
 *
 * <p>Tabela nova com dado de pessoa entra aqui, senao a exclusao fica incompleta em
 * silencio. A ordem e a de exclusao: notas apontam para gastos.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                Tabela.compartilhada("notes", "user_email"),
                Tabela.compartilhada("expense_shares", "member_email"),
                Tabela.compartilhada("expenses", "user_email"),
                Tabela.compartilhada("incomes", "user_email"),
                Tabela.compartilhada("recurring_bills", "user_email"),
                Tabela.compartilhada("category_budgets", "user_email"),
                Tabela.compartilhada("category_rules", "user_email"),
                Tabela.compartilhada("payment_accounts", "user_email"),
                Tabela.compartilhada("piggy_deposits", "user_email"),
                Tabela.compartilhada("monthly_goals", "user_email"),
                Tabela.compartilhada("product_memory", "user_email"),
                Tabela.compartilhada("custom_categories", "created_by"),
                Tabela.compartilhada("eventos_de_auditoria", "autor_email"),
                Tabela.pessoal("consents", "user_email"),
                new Tabela("receipt_usage", null, false)
        ), extras.orderedStream().toList());
    }

    /** Quem mudou gastos, receitas, metas, orcamentos, contas fixas e o cofrinho. */
    @Bean
    TrilhaDeAuditoria trilhaDeAuditoria(JdbcTemplate jdbc,
                                        @Value("${piggu.auditoria.retencao-dias:400}") int retencaoEmDias) {
        return new TrilhaDeAuditoria(jdbc, Duration.ofDays(retencaoEmDias), Clock.systemUTC());
    }

    @Bean
    Consentimentos consentimentos(JdbcTemplate jdbc) {
        return new Consentimentos(jdbc);
    }
}
