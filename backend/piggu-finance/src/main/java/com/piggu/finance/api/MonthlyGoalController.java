package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.MonthlyGoalRequest;
import com.piggu.finance.domain.MonthlyGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Limites de gasto por mes. Substitui a acao setMonthlyGoal. */
@Tag(name = "Metas do mês", description = "Limite de gasto de cada mês.")
@RestController
@RequestMapping("/api/monthly-goals")
public class MonthlyGoalController {

    private final MonthlyGoalService servico;

    public MonthlyGoalController(MonthlyGoalService servico) {
        this.servico = servico;
    }

    /** @return mapa de AAAA-MM para limite, o formato que o painel consome */
    @Operation(summary = "Limites de gasto por mês")
    @GetMapping
    public Map<String, BigDecimal> listar() {
        return servico.listar();
    }

    @Operation(summary = "Definir o limite de gasto do mês")
    @PutMapping
    public Map<String, BigDecimal> definir(@Valid @RequestBody MonthlyGoalRequest pedido,
                                           @AuthUser CurrentUser usuario) {
        servico.definir(pedido.mes(), pedido.limite(), usuario.id());
        return servico.listar();
    }
}
