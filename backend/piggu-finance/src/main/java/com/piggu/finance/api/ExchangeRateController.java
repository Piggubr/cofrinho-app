package com.piggu.finance.api;

import com.piggu.finance.api.dto.CurrencyResponse;
import com.piggu.finance.api.dto.ExchangeRateResponse;
import com.piggu.finance.integration.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cotacao entre duas moedas. Substitui a acao getExchangeRate, que so fazia EUR
 * para BRL; sem parametros continua sendo esse o par.
 * Aberto a todos os perfis, inclusive MEMBRO.
 */
@Tag(name = "Câmbio", description = "Cotação entre moedas.")
@RestController
@RequestMapping("/api/exchange-rate")
public class ExchangeRateController {

    private final ExchangeRateService servico;

    public ExchangeRateController(ExchangeRateService servico) {
        this.servico = servico;
    }

    @Operation(summary = "Cotação entre duas moedas")
    @GetMapping
    public ExchangeRateResponse consultar(@RequestParam(defaultValue = "EUR") String de,
                                          @RequestParam(defaultValue = "BRL") String para) {
        return servico.consultar(de, para);
    }

    @Operation(summary = "Moedas disponíveis")
    @GetMapping("/currencies")
    public List<CurrencyResponse> moedas() {
        return servico.moedas();
    }
}
