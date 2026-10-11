package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contas fixas: aluguel, luz, internet. "Marcar como paga" lanca o gasto do mes, uma
 * vez so; com o lancamento automatico ligado, o job diario faz isso no vencimento.
 */
@Service
public class ContasFixasService {

    public enum Situacao { PAGA, PENDENTE, VENCIDA }

    /** A conta como ela esta num mes. */
    public record ContaDoMes(RecurringBill conta, LocalDate vencimento, Situacao situacao) {
    }

    private static final Logger log = LoggerFactory.getLogger(ContasFixasService.class);
    private static final String ORIGEM = "Conta fixa";

    private final RecurringBillRepository repositorio;
    private final ExpenseService gastos;
    private final CategoryService categorias;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final TrilhaDeAuditoria trilha;

    public ContasFixasService(RecurringBillRepository repositorio, ExpenseService gastos, CategoryService categorias,
                              JdbcTemplate jdbc, TransactionTemplate transacao, TrilhaDeAuditoria trilha) {
        this.repositorio = repositorio;
        this.gastos = gastos;
        this.categorias = categorias;
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.relogio = Clock.system(Meses.BRASILIA);
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public List<ContaDoMes> doMes(YearMonth mes) {
        LocalDate hoje = LocalDate.now(relogio);
        return repositorio.findByActiveTrueOrderByDueDayAsc().stream()
                .map(conta -> {
                    LocalDate vencimento = conta.vencimentoEm(mes);
                    Situacao situacao = conta.pagaEm(mes) ? Situacao.PAGA
                            : vencimento.isBefore(hoje) ? Situacao.VENCIDA : Situacao.PENDENTE;
                    return new ContaDoMes(conta, vencimento, situacao);
                })
                .toList();
    }

    @Transactional
    public RecurringBill criar(String descricao, String categoria, BigDecimal valor, int dia, boolean automatico,
                               UUID autor) {
        RecurringBill conta = repositorio.save(new RecurringBill(Texto.limitar(descricao, 200),
                categorias.normalizar(categoria), valor, dia, automatico, autor));
        trilha.criou("conta-fixa", conta.getId(), Resumos.contaFixa(conta));
        return conta;
    }

    @Transactional
    public RecurringBill atualizar(UUID id, String descricao, String categoria, BigDecimal valor, int dia,
                                   boolean automatico) {
        RecurringBill conta = buscar(id);
        String antes = Resumos.contaFixa(conta);
        conta.editar(Texto.limitar(descricao, 200), categorias.normalizar(categoria), valor, dia, automatico);
        trilha.editou("conta-fixa", id, antes, Resumos.contaFixa(conta));
        return conta;
    }

    @Transactional
    public void excluir(UUID id) {
        RecurringBill conta = buscar(id);
        repositorio.delete(conta);
        trilha.apagou("conta-fixa", id, Resumos.contaFixa(conta));
    }

    /** Lanca o gasto do mes no vencimento, uma vez so por mes. */
    @Transactional
    public ExpenseResponse pagar(UUID id, YearMonth mes, UUID autor) {
        RecurringBill conta = buscar(id);
        if (conta.pagaEm(mes)) {
            throw new BusinessException("Esta conta já foi lançada em " + mes + ".", HttpStatus.CONFLICT, "JA_PAGA");
        }
        ExpenseResponse gasto = gastos.salvar(new SaveExpensesRequest(conta.vencimentoEm(mes), "", null, ORIGEM,
                List.of(new ExpenseItemRequest(conta.getDescription(), conta.getCategory(), conta.getAmount(), "Fixo"))),
                autor).get(0);
        conta.marcarPaga(mes);
        trilha.registrar(TrilhaDeAuditoria.Acao.PAGOU, "conta-fixa", id, null,
                Resumos.contaFixa(conta) + " · " + mes);
        log.info("Conta fixa lancada: conta={} mes={}", id, mes);
        return gasto;
    }

    /**
     * Lancamento automatico, todo dia as 6h de Brasilia: contas com o automatico ligado
     * que venceram ate hoje e ainda nao foram lancadas no mes. Cada familia na propria
     * sessao; uma conta que falha nao trava as outras.
     */
    @Scheduled(cron = "${piggu.contas-fixas.cron:0 0 6 * * *}", zone = "America/Sao_Paulo")
    public void lancarAutomaticas() {
        lancarAutomaticas(LocalDate.now(relogio));
    }

    void lancarAutomaticas(LocalDate hoje) {
        YearMonth mes = YearMonth.from(hoje);
        int ateODia = hoje.getDayOfMonth() == mes.lengthOfMonth() ? 31 : hoje.getDayOfMonth();
        List<Map<String, Object>> devidas = jdbc.queryForList("""
                SELECT id, household_id, user_id FROM recurring_bills
                WHERE active AND auto_launch AND due_day <= ? AND (last_paid_month IS NULL OR last_paid_month < ?)
                """, ateODia, mes.toString());
        int lancadas = 0;
        for (Map<String, Object> devida : devidas) {
            try {
                FamiliaAtual.como((UUID) devida.get("household_id"), () -> transacao.executeWithoutResult(status ->
                        pagar((UUID) devida.get("id"), mes, (UUID) devida.get("user_id"))));
                lancadas++;
            } catch (RuntimeException erro) {
                log.warn("Conta fixa {} nao lancada automaticamente", devida.get("id"), erro);
            }
        }
        log.info("Contas fixas lancadas automaticamente: {} de {}", lancadas, devidas.size());
    }

    private RecurringBill buscar(UUID id) {
        return repositorio.findById(id).orElseThrow(() -> new NotFoundException("Conta fixa nao encontrada."));
    }
}
