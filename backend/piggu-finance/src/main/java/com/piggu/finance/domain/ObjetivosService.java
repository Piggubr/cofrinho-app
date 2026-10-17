package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.Plano;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cofrinhos por objetivo: quanto juntar, ate quando e quanto guardar por mes.
 *
 * <p>O gratuito tem um objetivo; o Premium, quantos quiser. Com o Premium vencido os
 * objetivos que ja existem seguem recebendo deposito, so nao nasce outro. O saldo do
 * objetivo e a soma dos depositos feitos para ele, sem descontar gasto: o desconto de
 * gastos vale so para o cofrinho geral.</p>
 */
@Service
public class ObjetivosService {

    /** Quantos objetivos a familia no gratuito pode ter. */
    static final int LIMITE_NO_GRATUITO = 1;

    /**
     * Um objetivo e onde ele esta.
     *
     * @param falta          o que ainda falta para o alvo, nunca negativo
     * @param mesesRestantes meses ate o prazo contando o atual; nulo sem prazo
     * @param porMes         quanto guardar por mes para chegar no prazo; nulo sem prazo
     * @param atrasado       o prazo passou e ainda falta dinheiro
     */
    public record Situacao(UUID id, String nome, BigDecimal alvo, LocalDate prazo, BigDecimal guardado,
                           BigDecimal falta, int percentual, Integer mesesRestantes, BigDecimal porMes,
                           boolean concluido, boolean atrasado) {
    }

    private final SavingsGoalRepository objetivos;
    private final PiggyDepositRepository depositos;
    private final TrilhaDeAuditoria trilha;

    public ObjetivosService(SavingsGoalRepository objetivos, PiggyDepositRepository depositos,
                            TrilhaDeAuditoria trilha) {
        this.objetivos = objetivos;
        this.depositos = depositos;
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public List<Situacao> listar() {
        Map<UUID, BigDecimal> guardados = new HashMap<>();
        for (Object[] linha : depositos.somarPorObjetivo()) {
            guardados.put((UUID) linha[0], (BigDecimal) linha[1]);
        }
        LocalDate hoje = LocalDate.now();
        return objetivos.findAllByOrderByCreatedAtAsc().stream()
                .map(objetivo -> situacao(objetivo, guardados.getOrDefault(objetivo.getId(), BigDecimal.ZERO), hoje))
                .toList();
    }

    @Transactional
    public Situacao criar(String nome, BigDecimal alvo, LocalDate prazo, CurrentUser usuario) {
        if (!usuario.isPremium() && objetivos.count() >= LIMITE_NO_GRATUITO) {
            throw new BusinessException("Mais de um cofrinho por objetivo faz parte do Piggu Premium.",
                    HttpStatus.UNPROCESSABLE_CONTENT, Plano.CODIGO_PREMIUM);
        }
        SavingsGoal objetivo = objetivos.save(new SavingsGoal(nome.strip(), alvo, prazo, usuario.id()));
        trilha.criou("objetivo", objetivo.getId(), Resumos.objetivo(objetivo));
        return situacao(objetivo, BigDecimal.ZERO, LocalDate.now());
    }

    /** Mudar nome, alvo ou prazo e livre, inclusive com o Premium vencido. */
    @Transactional
    public void alterar(UUID id, String nome, BigDecimal alvo, LocalDate prazo) {
        SavingsGoal objetivo = buscar(id);
        String antes = Resumos.objetivo(objetivo);
        objetivo.alterar(nome.strip(), alvo, prazo);
        trilha.editou("objetivo", id, antes, Resumos.objetivo(objetivo));
    }

    /** Os depositos do objetivo voltam para o cofrinho geral (ON DELETE SET NULL). */
    @Transactional
    public void excluir(UUID id) {
        SavingsGoal objetivo = buscar(id);
        objetivos.delete(objetivo);
        trilha.apagou("objetivo", id, Resumos.objetivo(objetivo));
    }

    private SavingsGoal buscar(UUID id) {
        return objetivos.findById(id).orElseThrow(() -> new NotFoundException("Objetivo nao encontrado."));
    }

    static Situacao situacao(SavingsGoal objetivo, BigDecimal guardado, LocalDate hoje) {
        BigDecimal alvo = objetivo.getTargetAmount();
        BigDecimal falta = alvo.subtract(guardado).max(BigDecimal.ZERO);
        boolean concluido = falta.signum() == 0;
        int percentual = guardado.multiply(BigDecimal.valueOf(100)).divide(alvo, 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100)).intValue();

        LocalDate prazo = objetivo.getDeadline();
        Integer meses = null;
        BigDecimal porMes = null;
        boolean atrasado = false;
        if (prazo != null) {
            meses = mesesAte(prazo, hoje);
            atrasado = !concluido && prazo.isBefore(hoje);
            // Prazo vencido: o que falta e para agora.
            porMes = falta.divide(BigDecimal.valueOf(Math.max(1, meses)), 2, RoundingMode.CEILING);
        }
        return new Situacao(objetivo.getId(), objetivo.getName(), alvo, prazo, guardado, falta, percentual, meses,
                porMes, concluido, atrasado);
    }

    /** Meses que ainda cabem deposito: o atual e o do prazo contam; prazo passado da zero. */
    static int mesesAte(LocalDate prazo, LocalDate hoje) {
        long meses = ChronoUnit.MONTHS.between(YearMonth.from(hoje), YearMonth.from(prazo)) + 1;
        return (int) Math.max(0, meses);
    }
}
