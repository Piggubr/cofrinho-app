package com.piggu.rewards.api.dto;

import com.piggu.rewards.domain.Redemption;

import java.time.Instant;
import java.util.UUID;

/**
 * @param saldo saldo restante depois do resgate, para a tela nao precisar recarregar
 */
public record RedemptionResponse(
        UUID id,
        String premio,
        int preco,
        UUID usuario,
        String status,
        Instant data,
        int saldo
) {

    public static RedemptionResponse de(Redemption resgate, int saldo) {
        return new RedemptionResponse(
                resgate.getId(),
                resgate.getPrizeName(),
                resgate.getPrice(),
                resgate.getUserId(),
                resgate.getStatus(),
                resgate.getCreatedAt(),
                saldo
        );
    }
}
