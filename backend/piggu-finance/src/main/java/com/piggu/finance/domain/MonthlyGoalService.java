package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
    private final TrilhaDeAuditoria trilha;

    public MonthlyGoalService(MonthlyGoalRepository repositorio, TrilhaDeAuditoria trilha) {
        this.repositorio = repositorio;
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> listar() {
        Map<String, BigDecimal> metas = new LinkedHashMap<>();
        repositorio.findAll().forEach(meta -> metas.put(meta.getReferenceMonth(), meta.getLimitAmount()));
        return metas;
    }

    /** Cria ou substitui a meta do mes. */
    @Transactional
    public MonthlyGoal definir(String mes, BigDecimal limite, UUID usuarioId) {
        log.info("Meta do mes definida: mes={}", mes);
        return repositorio.findByReferenceMonth(mes)
                .map(existente -> {
                    String antes = Resumos.meta(mes, existente.getLimitAmount());
                    existente.atualizar(limite, usuarioId);
                    trilha.editou("meta", mes, antes, Resumos.meta(mes, limite));
                    return repositorio.save(existente);
                })
                .orElseGet(() -> {
                    trilha.criou("meta", mes, Resumos.meta(mes, limite));
                    return repositorio.save(new MonthlyGoal(mes, limite, usuarioId));
                });
    }
}
