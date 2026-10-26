package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
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
 * A receita recorrente (o salario) usa o mesmo caminho e lanca uma receita.
 */
@Service
public class ContasFixasService {

    public enum Situacao { PAGA, PENDENTE, VENCIDA }

    /** A conta como ela esta num mes. */
    public record ContaDoMes(RecurringBill conta, LocalDate vencimento, Situacao situacao) {
    }

    private static final Logger log = LoggerFactory.getLogger(ContasFixasService.class);
    static final String ORIGEM = "Conta fixa";

    private final RecurringBillRepository repositorio;
    private final ExpenseService gastos;
    private final IncomeService receitas;
    private final CategoryService categorias;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final TrilhaDeAuditoria trilha;

    public ContasFixasService(RecurringBillRepository repositorio, ExpenseService gastos, IncomeService receitas,
                              CategoryService categorias, JdbcTemplate jdbc, TransactionTemplate transacao,
                              TrilhaDeAuditoria trilha) {
        this.repositorio = repositorio;
        this.gastos = gastos;
        this.receitas = receitas;
        this.categorias = categorias;
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.relogio = Clock.system(Meses.BRASILIA);
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public List<ContaDoMes> doMes(YearMonth mes) {
        return doMes(mes, RecurringBill.Tipo.GASTO);
    }

    @Transactional(readOnly = true)
    public List<ContaDoMes> doMes(YearMonth mes, RecurringBill.Tipo tipo) {
        LocalDate hoje = LocalDate.now(relogio);
        return repositorio.findByActiveTrueAndKindOrderByDueDayAsc(tipo).stream()
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
        return criar(descricao, categoria, valor, dia, automatico, autor, false);
    }

    /** @param compartilhado a conta e o gasto que ela lanca aparecem para todos da familia */
    @Transactional
    public RecurringBill criar(String descricao, String categoria, BigDecimal valor, int dia, boolean automatico,
                               UUID autor, boolean compartilhado) {
        RecurringBill nova = new RecurringBill(Texto.limitar(descricao, 200),
                categoriaDo(RecurringBill.Tipo.GASTO, categoria), valor, dia, automatico, autor);
        nova.compartilhar(compartilhado);
        RecurringBill conta = repositorio.save(nova);
        trilha.criou("conta-fixa", conta.getId(), Resumos.contaFixa(conta));
        return conta;
    }

    /** @param compartilhado nulo mantem como esta; so quem criou muda */
    @Transactional
    public RecurringBill atualizar(UUID id, String descricao, String categoria, BigDecimal valor, int dia,
                                   boolean automatico, Boolean compartilhado, CurrentUser usuario) {
        RecurringBill conta = buscar(id);
        Compartilhamento.exigirEdicao(usuario, conta.getUserId(), conta.isShared(), "a conta fixa");
        if (Compartilhamento.muda(usuario, conta.getUserId(), conta.isShared(), compartilhado, "a conta fixa")) {
            conta.compartilhar(compartilhado);
        }
        String antes = Resumos.contaFixa(conta);
        conta.editar(Texto.limitar(descricao, 200), categoriaDo(conta.getKind(), categoria), valor, dia, automatico);
        trilha.editou("conta-fixa", id, antes, Resumos.contaFixa(conta));
        return conta;
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        RecurringBill conta = buscar(id);
        Compartilhamento.exigirDono(usuario, conta.getUserId(), "a conta fixa");
        repositorio.delete(conta);
        trilha.apagou("conta-fixa", id, Resumos.contaFixa(conta));
    }

    /**
     * Gasto recorrente lancado na tela de gastos: entra agora, como qualquer gasto, e cada
     * item vira uma conta fixa automatica no mesmo dia, ja lancada no mes da data.
     */
    @Transactional
    public List<ExpenseResponse> lancarGastoRecorrente(SaveExpensesRequest pedido, UUID autor) {
        if (pedido.parcelas() != null && pedido.parcelas() > 1) {
            throw new BusinessException("Gasto recorrente não pode ser parcelado.");
        }
        List<ExpenseResponse> salvos = gastos.salvar(pedido, autor);
        YearMonth mes = YearMonth.from(pedido.data());
        for (ExpenseResponse gasto : salvos) {
            RecurringBill conta = new RecurringBill(RecurringBill.Tipo.GASTO, gasto.item(), gasto.categoria(),
                    gasto.valor(), pedido.data().getDayOfMonth(), true, pedido.contaId(), autor);
            conta.compartilhar(gasto.compartilhado());
            conta.marcarPaga(mes);
            trilha.criou("conta-fixa", repositorio.save(conta).getId(), Resumos.contaFixa(conta));
        }
        return salvos;
    }

    /** Receita que entra todo mes no mesmo dia: lanca a deste mes e liga o automatico para os proximos. */
    @Transactional
    public Income lancarReceitaRecorrente(LocalDate data, String descricao, String categoria, BigDecimal valor,
                                          UUID autor) {
        return lancarReceitaRecorrente(data, descricao, categoria, valor, autor, false);
    }

    @Transactional
    public Income lancarReceitaRecorrente(LocalDate data, String descricao, String categoria, BigDecimal valor,
                                          UUID autor, boolean compartilhado) {
        Income receita = receitas.criar(data, descricao, categoria, valor, autor, compartilhado);
        RecurringBill conta = new RecurringBill(RecurringBill.Tipo.RECEITA, receita.getDescription(),
                receita.getCategory(), valor, data.getDayOfMonth(), true, null, autor);
        conta.compartilhar(compartilhado);
        conta.marcarPaga(YearMonth.from(data));
        trilha.criou("receita-recorrente", repositorio.save(conta).getId(), Resumos.contaFixa(conta));
        return receita;
    }

    /**
     * Lanca o gasto (ou a receita) do mes no vencimento, uma vez so por mes.
     *
     * @return o gasto lancado; nulo quando e uma receita
     */
    @Transactional
    public ExpenseResponse pagar(UUID id, YearMonth mes, UUID autor) {
        RecurringBill conta = buscar(id);
        if (conta.pagaEm(mes)) {
            throw new BusinessException("Esta conta já foi lançada em " + mes + ".", HttpStatus.CONFLICT, "JA_PAGA");
        }
        ExpenseResponse gasto = null;
        if (conta.getKind() == RecurringBill.Tipo.RECEITA) {
            receitas.criar(conta.vencimentoEm(mes), conta.getDescription(), conta.getCategory(), conta.getAmount(),
                    autor, conta.isShared());
        } else {
            // A conta fixa do casal lanca o gasto compartilhado, em nome de quem pagou.
            gasto = gastos.salvar(new SaveExpensesRequest(conta.vencimentoEm(mes), "", null, ORIGEM,
                    List.of(new ExpenseItemRequest(conta.getDescription(), conta.getCategory(), conta.getAmount(),
                            "Fixo")), conta.getAccountId(), null, null, null, conta.isShared()), autor).get(0);
        }
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
            UUID dono = (UUID) devida.get("user_id");
            try {
                // No historico, em nome do dono: o resto da familia nao ve a conta fixa dele.
                FamiliaAtual.como((UUID) devida.get("household_id"), () -> TrilhaDeAuditoria.emNomeDe(dono, () ->
                        transacao.executeWithoutResult(status -> pagar((UUID) devida.get("id"), mes, dono))));
                lancadas++;
            } catch (RuntimeException erro) {
                log.warn("Conta fixa {} nao lancada automaticamente", devida.get("id"), erro);
            }
        }
        log.info("Contas fixas lancadas automaticamente: {} de {}", lancadas, devidas.size());
    }

    private String categoriaDo(RecurringBill.Tipo tipo, String categoria) {
        return tipo == RecurringBill.Tipo.RECEITA ? IncomeService.categoria(categoria) : categorias.normalizar(categoria);
    }

    private RecurringBill buscar(UUID id) {
        return repositorio.findById(id).orElseThrow(() -> new NotFoundException("Conta fixa nao encontrada."));
    }
}
