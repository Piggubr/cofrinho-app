package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ExpenseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Contas e cartoes. No cartao, a fatura do mes M vai do dia seguinte ao fechamento de M-1
 * ate o fechamento de M; vence no dia de vencimento de M, ou de M+1 quando o dia do
 * vencimento nao passa do dia do fechamento.
 */
@Service
public class ContasECartoes {

    private final PaymentAccountRepository contas;
    private final ExpenseRepository gastos;

    public ContasECartoes(PaymentAccountRepository contas, ExpenseRepository gastos) {
        this.contas = contas;
        this.gastos = gastos;
    }

    public record Periodo(String mes, LocalDate inicio, LocalDate fechamento, LocalDate vencimento) {
    }

    public record Fatura(String mes, LocalDate inicio, LocalDate fechamento, LocalDate vencimento, BigDecimal total) {
    }

    public record FaturaComGastos(Fatura fatura, List<ExpenseResponse> gastos) {
    }

    /**
     * @param faturaAberta a que ainda recebe compras (so cartao)
     * @param faturaAPagar a ja fechada que ainda nao venceu (so cartao): e o lembrete
     */
    public record Conta(UUID id, String nome, PaymentAccount.Tipo tipo, Short fechamento, Short vencimento,
                        Fatura faturaAberta, Fatura faturaAPagar) {
    }

    @Transactional(readOnly = true)
    public List<Conta> listar() {
        LocalDate hoje = LocalDate.now(Meses.BRASILIA);
        return contas.findAllByOrderByNameAsc().stream().map(conta -> {
            if (conta.getKind() != PaymentAccount.Tipo.CARTAO) {
                return new Conta(conta.getId(), conta.getName(), conta.getKind(), null, null, null, null);
            }
            YearMonth aberta = mesDaFaturaAberta(conta.getClosingDay(), hoje);
            Periodo anterior = periodo(conta.getClosingDay(), conta.getDueDay(), aberta.minusMonths(1));
            Fatura aPagar = anterior.vencimento().isBefore(hoje) ? null : fatura(conta, anterior);
            return new Conta(conta.getId(), conta.getName(), conta.getKind(), conta.getClosingDay(), conta.getDueDay(),
                    fatura(conta, periodo(conta.getClosingDay(), conta.getDueDay(), aberta)), aPagar);
        }).toList();
    }

    @Transactional
    public Conta criar(String nomeBruto, PaymentAccount.Tipo tipo, Integer fechamento, Integer vencimento,
                       UUID pessoa) {
        String nome = Texto.limitar(Texto.espacoUnico(nomeBruto), 60);
        if (nome.length() < 2) {
            throw new BusinessException("Digite o nome da conta ou do cartao.");
        }
        if (contas.existsByNameIgnoreCase(nome)) {
            throw new BusinessException("Ja existe uma conta com esse nome.");
        }
        boolean cartao = tipo == PaymentAccount.Tipo.CARTAO;
        if (cartao && (fechamento == null || vencimento == null)) {
            throw new BusinessException("Informe o dia de fechamento e o de vencimento do cartao.");
        }
        PaymentAccount conta = contas.save(new PaymentAccount(nome, tipo,
                cartao ? fechamento.shortValue() : null, cartao ? vencimento.shortValue() : null, pessoa));
        return new Conta(conta.getId(), conta.getName(), conta.getKind(), conta.getClosingDay(), conta.getDueDay(),
                null, null);
    }

    /** Os gastos ficam; so perdem a conta (ON DELETE SET NULL). */
    @Transactional
    public void excluir(UUID id) {
        contas.delete(buscar(id));
    }

    @Transactional(readOnly = true)
    public FaturaComGastos fatura(UUID id, YearMonth mes) {
        PaymentAccount conta = buscar(id);
        if (conta.getKind() != PaymentAccount.Tipo.CARTAO) {
            throw new BusinessException("Fatura so existe para cartao.");
        }
        Periodo periodo = periodo(conta.getClosingDay(), conta.getDueDay(), mes);
        List<ExpenseResponse> doPeriodo = gastos.findByAccountIdAndExpenseDateBetweenOrderByExpenseDateAscCreatedAtAsc(
                id, periodo.inicio(), periodo.fechamento()).stream().map(ExpenseResponse::de).toList();
        BigDecimal total = doPeriodo.stream().map(ExpenseResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FaturaComGastos(new Fatura(periodo.mes(), periodo.inicio(), periodo.fechamento(),
                periodo.vencimento(), total), doPeriodo);
    }

    private Fatura fatura(PaymentAccount conta, Periodo periodo) {
        BigDecimal total = gastos.somarDaConta(conta.getId(), periodo.inicio(), periodo.fechamento());
        return new Fatura(periodo.mes(), periodo.inicio(), periodo.fechamento(), periodo.vencimento(), total);
    }

    private PaymentAccount buscar(UUID id) {
        return contas.findById(id).orElseThrow(() -> new NotFoundException("Conta ou cartao nao encontrado."));
    }

    /** Fatura que fecha no mes; dia 31 vira o ultimo dia em meses curtos. */
    static Periodo periodo(int fechamento, int vencimento, YearMonth mes) {
        LocalDate fecha = dia(mes, fechamento);
        LocalDate inicio = dia(mes.minusMonths(1), fechamento).plusDays(1);
        LocalDate vence = vencimento > fechamento ? dia(mes, vencimento) : dia(mes.plusMonths(1), vencimento);
        return new Periodo(mes.toString(), inicio, fecha, vence);
    }

    /** Ate o dia do fechamento, a compra entra na fatura deste mes; depois, na do proximo. */
    static YearMonth mesDaFaturaAberta(int fechamento, LocalDate hoje) {
        YearMonth mes = YearMonth.from(hoje);
        return hoje.isAfter(dia(mes, fechamento)) ? mes.plusMonths(1) : mes;
    }

    private static LocalDate dia(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }
}
