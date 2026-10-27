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
 *
 * <p>Cada um ve o que lancou ({@code DonoAtual}), entao quase tudo e da pessoa: sai com
 * ela quando exclui a conta e vai junto quando muda de familia. Da familia ficam so as
 * categorias criadas e o historico, sem o nome de quem saiu.</p>
 */
@Configuration
public class DadosDoTitularConfig {

    @Bean
    DadosDaFamilia dadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<DadosDaFamilia.AoApagar> extras) {
        return new DadosDaFamilia(jdbc, json, List.of(
                Tabela.pessoal("notes", "user_id"),
                Tabela.pessoal("expense_shares", "member_id"),
                Tabela.pessoal("expenses", "user_id"),
                Tabela.pessoal("expense_submissions", "user_id"),
                Tabela.pessoal("receivables", "user_id"),
                Tabela.pessoal("incomes", "user_id"),
                Tabela.pessoal("recurring_bills", "user_id"),
                // So da pessoa tambem no app (DonoAtual.SO_DA_PESSOA): nem o titular ve a dos outros.
                Tabela.privada("category_budgets", "user_id"),
                Tabela.privada("category_rules", "user_id"),
                Tabela.pessoal("payment_accounts", "user_id"),
                Tabela.pessoal("piggy_deposits", "user_id"),
                Tabela.pessoal("savings_goals", "user_id"),
                Tabela.privada("monthly_goals", "user_id"),
                Tabela.privada("product_memory", "user_id"),
                Tabela.compartilhada("custom_categories", "created_by_id"),
                Tabela.compartilhada("eventos_de_auditoria", "autor_id"),
                Tabela.pessoal("consents", "user_id"),
                Tabela.pessoal("assistant_usage", "user_id"),
                new Tabela("receipt_usage", null, false)
        ), extras.orderedStream().toList(), List.of(semReferenciaEntreFamilias(jdbc)));
    }

    /**
     * Depois da mudanca, o que liga as duas familias se desfaz: a divisao de um gasto que
     * ficou de um lado e a parte do outro, a conta de outra pessoa num gasto, o objetivo de
     * outra pessoa num deposito do cofrinho, o gasto de outra pessoa numa nota.
     */
    static DadosDaFamilia.AoMover semReferenciaEntreFamilias(JdbcTemplate jdbc) {
        return (origem, pessoa, destino) -> {
            jdbc.update("""
                    DELETE FROM expense_shares s WHERE s.household_id IN (?, ?) AND NOT EXISTS
                        (SELECT 1 FROM expenses e WHERE e.id = s.expense_id AND e.household_id = s.household_id)
                    """, origem, destino);
            jdbc.update("""
                    UPDATE expenses e SET account_id = NULL WHERE e.household_id IN (?, ?) AND e.account_id IS NOT NULL
                        AND NOT EXISTS (SELECT 1 FROM payment_accounts a WHERE a.id = e.account_id AND a.household_id = e.household_id)
                    """, origem, destino);
            jdbc.update("""
                    UPDATE recurring_bills r SET account_id = NULL WHERE r.household_id IN (?, ?) AND r.account_id IS NOT NULL
                        AND NOT EXISTS (SELECT 1 FROM payment_accounts a WHERE a.id = r.account_id AND a.household_id = r.household_id)
                    """, origem, destino);
            jdbc.update("""
                    UPDATE piggy_deposits d SET goal_id = NULL WHERE d.household_id IN (?, ?) AND d.goal_id IS NOT NULL
                        AND NOT EXISTS (SELECT 1 FROM savings_goals g WHERE g.id = d.goal_id AND g.household_id = d.household_id)
                    """, origem, destino);
            jdbc.update("""
                    UPDATE notes n SET expense_id = NULL WHERE n.household_id IN (?, ?) AND n.expense_id IS NOT NULL
                        AND NOT EXISTS (SELECT 1 FROM expenses e WHERE e.id = n.expense_id AND e.household_id = n.household_id)
                    """, origem, destino);
        };
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
