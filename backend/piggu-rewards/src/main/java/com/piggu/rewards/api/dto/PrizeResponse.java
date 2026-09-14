package com.piggu.rewards.api.dto;

import com.piggu.rewards.domain.Prize;

import java.util.UUID;

public record PrizeResponse(UUID id, String nome, String descricao, int preco, boolean ativo) {

    public static PrizeResponse de(Prize premio) {
        return new PrizeResponse(
                premio.getId(),
                premio.getName(),
                premio.getDescription(),
                premio.getPrice(),
                premio.isActive()
        );
    }
}
