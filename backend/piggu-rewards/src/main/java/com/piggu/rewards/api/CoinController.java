package com.piggu.rewards.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.rewards.api.dto.CoinAdjustRequest;
import com.piggu.rewards.api.dto.CoinBalanceResponse;
import com.piggu.rewards.domain.RewardsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fofocoins. Substitui a acao adjustCoins e a parte de moedas do getData.
 *
 * <p>Consultar o saldo e aberto a quem esta logado; ajustar e so do administrador,
 * como exigirAdmin_ garantia no Apps Script.</p>
 */
@RestController
@RequestMapping("/api/coins")
public class CoinController {

    private final RewardsService servico;

    public CoinController(RewardsService servico) {
        this.servico = servico;
    }

    @GetMapping
    public CoinBalanceResponse saldo() {
        return servico.saldo();
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public CoinBalanceResponse ajustar(@Valid @RequestBody CoinAdjustRequest pedido,
                                       @AuthUser CurrentUser usuario) {
        return servico.ajustar(pedido, usuario.email());
    }
}
