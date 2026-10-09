package com.piggu.finance.api;

import com.piggu.common.web.Meses;
import com.piggu.finance.domain.RelatorioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resumos e relatorios da familia. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class ReportController {

    private final RelatorioService relatorios;

    public ReportController(RelatorioService relatorios) {
        this.relatorios = relatorios;
    }

    /** Card do mes: receitas, gastos, sobra e taxa de poupanca. Gratis. */
    @GetMapping("/month")
    public RelatorioService.ResumoDoMes mes(@RequestParam(required = false) String mes) {
        return relatorios.resumo(Meses.ouAtual(mes));
    }
}
