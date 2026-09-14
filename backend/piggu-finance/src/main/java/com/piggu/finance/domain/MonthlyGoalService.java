package com.piggu.finance.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limites de gasto por mes.
 *
 * <p>Porte de carregarMetas_ e salvarMetaMensal_. O formato de saida continua sendo
 * um mapa mes para limite, que e como o painel consome.</p>
 */
@Service
public class MonthlyGoalService {

    private final MonthlyGoalRepository repositorio;

    public MonthlyGoalService(MonthlyGoalRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> listar() {
        Map<String, BigDecimal> metas = new LinkedHashMap<>();
        repositorio.findAll().forEach(meta -> metas.put(meta.getReferenceMonth(), meta.getLimitAmount()));
        return metas;
    }

    /** Cria ou substitui a meta do mes. */
    @Transactional
    public MonthlyGoal definir(String mes, BigDecimal limite, String emailUsuario) {
        return repositorio.findById(mes)
                .map(existente -> {
                    existente.atualizar(limite, emailUsuario);
                    return repositorio.save(existente);
                })
                .orElseGet(() -> repositorio.save(new MonthlyGoal(mes, limite, emailUsuario)));
    }
}
