package com.piggu.finance.api;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.domain.RelatorioService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

/** Resumos e relatorios da familia. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class ReportController {

    private final RelatorioService relatorios;

    public ReportController(RelatorioService relatorios) {
        this.relatorios = relatorios;
    }

    /** Mes: receitas, gastos, sobra, poupanca, comparacao com o anterior e projecao. Gratis. */
    @GetMapping("/month")
    public RelatorioService.ResumoDoMes mes(@RequestParam(required = false) String mes) {
        return relatorios.resumo(Meses.ouAtual(mes));
    }

    /** Ano mes a mes e por categoria. Premium. */
    @GetMapping("/year")
    public RelatorioService.RelatorioDoAno ano(@RequestParam(required = false) Integer ano, @AuthUser CurrentUser usuario) {
        if (ano != null && (ano < 2000 || ano > 2100)) {
            throw new BusinessException("Ano invalido.", HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO");
        }
        Year alvo = ano == null ? Year.now(Meses.BRASILIA) : Year.of(ano);
        return relatorios.doAno(alvo, usuario);
    }
}
