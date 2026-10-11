package com.piggu.finance.domain;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orcamento por categoria (Premium): limite mensal e alerta em 80% e 100%.
 *
 * <p>Ver e apagar orcamentos que ja existem segue livre no gratuito; criar e mudar
 * limite e Premium.</p>
 */
@Service
public class OrcamentoService {

    public enum Alerta { OK, ATENCAO, ESTOUROU }

    static final BigDecimal LIMIAR_DE_ATENCAO = BigDecimal.valueOf(80);
    static final BigDecimal LIMIAR_ESTOURO = BigDecimal.valueOf(100);

    /** Uma categoria no mes: limite, quanto ja foi e o alerta. */
    public record Situacao(UUID id, String categoria, BigDecimal limite, BigDecimal gasto, BigDecimal percentual,
                           Alerta alerta) {
    }

    private final CategoryBudgetRepository orcamentos;
    private final ExpenseRepository gastos;
    private final CategoryService categorias;

    public OrcamentoService(CategoryBudgetRepository orcamentos, ExpenseRepository gastos, CategoryService categorias) {
        this.orcamentos = orcamentos;
        this.gastos = gastos;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<Situacao> doMes(YearMonth mes) {
        Map<String, BigDecimal> porCategoria = gastos.somarPorCategoria(mes.atDay(1), mes.atEndOfMonth());
        return orcamentos.findAllByOrderByCategoryAsc().stream()
                .map(orcamento -> {
                    BigDecimal gasto = porCategoria.getOrDefault(orcamento.getCategory(), BigDecimal.ZERO);
                    BigDecimal percentual = gasto.multiply(BigDecimal.valueOf(100))
                            .divide(orcamento.getLimitAmount(), 0, RoundingMode.HALF_UP);
                    Alerta alerta = percentual.compareTo(LIMIAR_ESTOURO) >= 0 ? Alerta.ESTOUROU
                            : percentual.compareTo(LIMIAR_DE_ATENCAO) >= 0 ? Alerta.ATENCAO : Alerta.OK;
                    return new Situacao(orcamento.getId(), orcamento.getCategory(), orcamento.getLimitAmount(), gasto,
                            percentual, alerta);
                })
                .toList();
    }

    /** Cria ou muda o limite da categoria. Premium. */
    @Transactional
    public void definir(String categoria, BigDecimal limite, CurrentUser usuario) {
        usuario.exigirPremium("Orçamento por categoria");
        String normalizada = categorias.normalizar(categoria);
        orcamentos.findByCategory(normalizada).ifPresentOrElse(
                orcamento -> orcamento.alterarLimite(limite, usuario.email()),
                () -> orcamentos.save(new CategoryBudget(normalizada, limite, usuario.email())));
    }

    /** Apagar e livre, inclusive com o Premium vencido. */
    @Transactional
    public void excluir(UUID id) {
        orcamentos.delete(orcamentos.findById(id).orElseThrow(() -> new NotFoundException("Orcamento nao encontrado.")));
    }
}
