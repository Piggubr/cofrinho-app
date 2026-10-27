package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ExpenseResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Contas e cartoes. No cartao, a fatura do mes M recebe as compras do fechamento de M-1
 * ate a vespera do fechamento de M: como nos bancos, a compra feita no dia do fechamento
 * ja vai para a fatura seguinte. Vence no dia de vencimento de M, ou de M+1 quando o dia
 * do vencimento nao passa do dia do fechamento.
 */
@Service
public class ContasECartoes {

    private final PaymentAccountRepository contas;
    private final ExpenseRepository gastos;
    private final JdbcTemplate jdbc;
    private final TrilhaDeAuditoria trilha;

    public ContasECartoes(PaymentAccountRepository contas, ExpenseRepository gastos, JdbcTemplate jdbc,
                          TrilhaDeAuditoria trilha) {
        this.contas = contas;
        this.gastos = gastos;
        this.jdbc = jdbc;
        this.trilha = trilha;
    }

    /** @param ultimaCompra vespera do fechamento: o ultimo dia que ainda entra nesta fatura */
    public record Periodo(String mes, LocalDate inicio, LocalDate ultimaCompra, LocalDate fechamento,
                          LocalDate vencimento) {
    }

    /** @param paga marcada como paga (ela ou uma fatura seguinte); so sai do "a pagar", os gastos nao mudam */
    public record Fatura(String mes, LocalDate inicio, LocalDate ultimaCompra, LocalDate fechamento,
                         LocalDate vencimento, BigDecimal total, boolean paga) {
    }

    public record FaturaComGastos(Fatura fatura, List<ExpenseResponse> gastos) {
    }

    /**
     * @param faturaAberta  a que ainda recebe compras (so cartao)
     * @param faturaAPagar  a ja fechada que ainda nao venceu (so cartao): e o lembrete
     * @param usuario       quem criou; so essa pessoa apaga ou desfaz o compartilhamento
     * @param compartilhado do casal (ou da familia): o que for lancado nela ja nasce compartilhado
     */
    public record Conta(UUID id, String nome, PaymentAccount.Tipo tipo, Short fechamento, Short vencimento,
                        Fatura faturaAberta, Fatura faturaAPagar, UUID usuario, boolean compartilhado) {

        Conta(PaymentAccount conta, Fatura faturaAberta, Fatura faturaAPagar) {
            this(conta.getId(), conta.getName(), conta.getKind(), conta.getClosingDay(), conta.getDueDay(),
                    faturaAberta, faturaAPagar, conta.getUserId(), conta.isShared());
        }
    }

    @Transactional(readOnly = true)
    public List<Conta> listar() {
        LocalDate hoje = LocalDate.now(Meses.BRASILIA);
        return contas.findAllByOrderByNameAsc().stream().map(conta -> {
            if (conta.getKind() != PaymentAccount.Tipo.CARTAO) {
                return new Conta(conta, null, null);
            }
            YearMonth aberta = mesDaFaturaAberta(conta.getClosingDay(), hoje);
            Periodo anterior = periodo(conta.getClosingDay(), conta.getDueDay(), aberta.minusMonths(1));
            Fatura aPagar = anterior.vencimento().isBefore(hoje) ? null : fatura(conta, anterior);
            return new Conta(conta, fatura(conta, periodo(conta.getClosingDay(), conta.getDueDay(), aberta)), aPagar);
        }).toList();
    }

    /** @param compartilhado conta ou cartao do casal: o que for lancado nela ja nasce compartilhado */
    @Transactional
    public Conta criar(String nomeBruto, PaymentAccount.Tipo tipo, Integer fechamento, Integer vencimento,
                       UUID pessoa, boolean compartilhado) {
        String nome = Texto.limitar(Texto.espacoUnico(nomeBruto), 60);
        if (nome.length() < 2) {
            throw new BusinessException("Digite o nome da conta ou do cartao.");
        }
        if (contas.existsByNameIgnoreCaseAndUserId(nome, pessoa)) {

            throw new BusinessException("Ja existe uma conta com esse nome.");
        }
        boolean cartao = tipo == PaymentAccount.Tipo.CARTAO;
        if (cartao && vencimento == null) {
            throw new BusinessException("Informe o dia de vencimento da fatura.");
        }
        Integer fecha = cartao && fechamento == null ? Integer.valueOf(fechamentoPadrao(vencimento)) : fechamento;
        PaymentAccount nova = new PaymentAccount(nome, tipo,
                cartao ? fecha.shortValue() : null, cartao ? vencimento.shortValue() : null, pessoa);
        nova.compartilhar(compartilhado);
        return new Conta(contas.save(nova), null, null);
    }

    /** Marca a conta como do casal (ou volta a ser so de quem criou). Os gastos ja lancados nao mudam. */
    @Transactional
    public void compartilhar(UUID id, boolean compartilhado, CurrentUser usuario) {
        PaymentAccount conta = buscar(id);
        if (Compartilhamento.muda(usuario, conta.getUserId(), conta.isShared(), compartilhado, "a conta")) {
            conta.compartilhar(compartilhado);
        }
    }

    /** Os gastos ficam; so perdem a conta (ON DELETE SET NULL). */
    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        PaymentAccount conta = buscar(id);
        Compartilhamento.exigirDono(usuario, conta.getUserId(), "a conta");
        contas.delete(conta);
    }

    @Transactional(readOnly = true)
    public FaturaComGastos fatura(UUID id, YearMonth mes) {
        PaymentAccount conta = buscarCartao(id);
        Periodo periodo = periodo(conta.getClosingDay(), conta.getDueDay(), mes);
        List<ExpenseResponse> doPeriodo = gastos.findByAccountIdAndExpenseDateBetweenOrderByExpenseDateAscCreatedAtAsc(
                id, periodo.inicio(), periodo.ultimaCompra()).stream().map(ExpenseResponse::de).toList();
        BigDecimal total = doPeriodo.stream().map(ExpenseResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FaturaComGastos(new Fatura(periodo.mes(), periodo.inicio(), periodo.ultimaCompra(),
                periodo.fechamento(), periodo.vencimento(), total, conta.faturaPagaEm(mes)), doPeriodo);
    }

    /**
     * Marca a fatura do mes como paga, e com ela as anteriores. Nao lanca gasto: as compras
     * ja contaram no dia em que foram feitas. Marcar de novo nao muda nada.
     */
    @Transactional
    public void pagarFatura(UUID id, YearMonth mes) {
        PaymentAccount conta = buscarCartao(id);
        if (mes.isAfter(mesDaFaturaAberta(conta.getClosingDay(), LocalDate.now(Meses.BRASILIA)))) {
            throw new BusinessException("Essa fatura ainda nao comecou.");
        }
        // Condicional no SQL: dois toques ao mesmo tempo nunca voltam a marca para um mes anterior.
        if (jdbc.update("""
                UPDATE payment_accounts SET last_paid_month = ?
                WHERE id = ? AND household_id = ? AND (last_paid_month IS NULL OR last_paid_month < ?)
                """, mes.toString(), id, FamiliaAtual.atual(), mes.toString()) > 0) {
            trilha.registrar(TrilhaDeAuditoria.Acao.PAGOU, "fatura", id, null, conta.getName() + " · " + mes);
        }
    }

    /** Desfaz o pagamento da fatura do mes; as seguintes, se estavam pagas, voltam a ficar em aberto. */
    @Transactional
    public void desfazerPagamento(UUID id, YearMonth mes) {
        PaymentAccount conta = buscarCartao(id);
        if (jdbc.update("""
                UPDATE payment_accounts SET last_paid_month = ?
                WHERE id = ? AND household_id = ? AND last_paid_month >= ?
                """, mes.minusMonths(1).toString(), id, FamiliaAtual.atual(), mes.toString()) > 0) {
            trilha.registrar(TrilhaDeAuditoria.Acao.EDITOU, "fatura", id, conta.getName() + " · " + mes + " paga",
                    conta.getName() + " · " + mes + " a pagar");
        }
    }

    private Fatura fatura(PaymentAccount conta, Periodo periodo) {
        BigDecimal total = gastos.somarDaConta(conta.getId(), periodo.inicio(), periodo.ultimaCompra());
        return new Fatura(periodo.mes(), periodo.inicio(), periodo.ultimaCompra(), periodo.fechamento(),
                periodo.vencimento(), total, conta.faturaPagaEm(YearMonth.parse(periodo.mes())));
    }

    private PaymentAccount buscar(UUID id) {
        return contas.findById(id).orElseThrow(() -> new NotFoundException("Conta ou cartao nao encontrado."));
    }

    private PaymentAccount buscarCartao(UUID id) {
        PaymentAccount conta = buscar(id);
        if (conta.getKind() != PaymentAccount.Tipo.CARTAO) {
            throw new BusinessException("Fatura so existe para cartao.");
        }
        return conta;
    }

    /** Fatura que fecha no mes; dia 31 vira o ultimo dia em meses curtos. */
    static Periodo periodo(int fechamento, int vencimento, YearMonth mes) {
        LocalDate fecha = dia(mes, fechamento);
        LocalDate inicio = dia(mes.minusMonths(1), fechamento);
        LocalDate vence = vencimento > fechamento ? dia(mes, vencimento) : dia(mes.plusMonths(1), vencimento);
        return new Periodo(mes.toString(), inicio, fecha.minusDays(1), fecha, vence);
    }

    /** Antes do dia do fechamento, a compra entra na fatura deste mes; dele em diante, na do proximo. */
    static YearMonth mesDaFaturaAberta(int fechamento, LocalDate hoje) {
        YearMonth mes = YearMonth.from(hoje);
        return hoje.isBefore(dia(mes, fechamento)) ? mes : mes.plusMonths(1);
    }

    /** Sem o dia do fechamento, ele fica 7 dias antes do vencimento, como na maioria dos bancos. */
    static int fechamentoPadrao(int vencimento) {
        return vencimento > 7 ? vencimento - 7 : vencimento + 24;
    }

    private static LocalDate dia(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }
}
