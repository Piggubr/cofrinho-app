package com.piggu.finance.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Situacao do cofrinho.
 *
 * @param depositos      depositos visiveis para quem pediu
 * @param totalDepositos soma dos depositos
 * @param totalGastos    gastos registrados a partir do primeiro deposito
 * @param saldo          totalDepositos menos totalGastos
 */
public record PiggyBankResponse(
        List<DepositResponse> depositos,
        BigDecimal totalDepositos,
        BigDecimal totalGastos,
        BigDecimal saldo
) {
}
