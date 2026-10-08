package com.piggu.finance.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MonthlyGoalService.class);

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
        log.info("Meta do mes definida: mes={}", mes);
        return repositorio.findByReferenceMonth(mes)
                .map(existente -> {
                    existente.atualizar(limite, emailUsuario);
                    return repositorio.save(existente);
                })
                .orElseGet(() -> repositorio.save(new MonthlyGoal(mes, limite, emailUsuario)));
    }
}
