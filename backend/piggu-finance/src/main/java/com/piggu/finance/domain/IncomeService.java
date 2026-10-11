package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Receitas da familia. */
@Service
public class IncomeService {

    /** Categorias de receita; texto livre cai em Outros, como nos gastos. */
    public static final List<String> CATEGORIAS = List.of("Salário", "Extra", "Reembolso", "Investimentos", "Outros");

    private static final Logger log = LoggerFactory.getLogger(IncomeService.class);

    private final IncomeRepository repositorio;
    private final TrilhaDeAuditoria trilha;

    public IncomeService(IncomeRepository repositorio, TrilhaDeAuditoria trilha) {
        this.repositorio = repositorio;
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public List<Income> doMes(YearMonth mes) {
        return repositorio.findByIncomeDateBetweenOrderByIncomeDateDesc(mes.atDay(1), mes.atEndOfMonth());
    }

    @Transactional
    public Income criar(LocalDate data, String descricao, String categoria, BigDecimal valor, UUID usuarioId) {
        Income receita = repositorio.save(new Income(data, Texto.limitar(descricao, 200), categoria(categoria), valor, usuarioId));
        trilha.criou("receita", receita.getId(), Resumos.receita(receita));
        log.info("Receita lancada: id={}", receita.getId());
        return receita;
    }

    @Transactional
    public Income atualizar(UUID id, LocalDate data, String descricao, String categoria, BigDecimal valor) {
        Income receita = buscar(id);
        String antes = Resumos.receita(receita);
        receita.editar(data, Texto.limitar(descricao, 200), categoria(categoria), valor);
        trilha.editou("receita", id, antes, Resumos.receita(receita));
        return receita;
    }

    @Transactional
    public void excluir(UUID id) {
        Income receita = buscar(id);
        repositorio.delete(receita);
        trilha.apagou("receita", id, Resumos.receita(receita));
        log.info("Receita apagada: id={}", id);
    }

    private Income buscar(UUID id) {
        return repositorio.findById(id).orElseThrow(() -> new NotFoundException("Receita nao encontrada."));
    }

    private static String categoria(String informada) {
        return CATEGORIAS.stream().filter(c -> c.equalsIgnoreCase(Texto.limitar(informada, 50).trim()))
                .findFirst().orElse("Outros");
    }
}
