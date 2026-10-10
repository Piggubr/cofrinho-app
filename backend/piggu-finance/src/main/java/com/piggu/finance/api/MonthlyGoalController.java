package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.MonthlyGoalRequest;
import com.piggu.finance.domain.MonthlyGoalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Limites de gasto por mes. Substitui a acao setMonthlyGoal. */
@RestController
@RequestMapping("/api/monthly-goals")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class MonthlyGoalController {

    private final MonthlyGoalService servico;

    public MonthlyGoalController(MonthlyGoalService servico) {
        this.servico = servico;
    }

    /** @return mapa de AAAA-MM para limite, o formato que o painel consome */
    @GetMapping
    public Map<String, BigDecimal> listar() {
        return servico.listar();
    }

    @PutMapping
    public Map<String, BigDecimal> definir(@Valid @RequestBody MonthlyGoalRequest pedido,
                                           @AuthUser CurrentUser usuario) {
        servico.definir(pedido.mes(), pedido.limite(), usuario.email());
        return servico.listar();
    }
}
