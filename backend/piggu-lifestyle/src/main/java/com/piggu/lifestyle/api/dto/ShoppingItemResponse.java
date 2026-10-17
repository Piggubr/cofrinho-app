package com.piggu.lifestyle.api.dto;

import com.piggu.lifestyle.domain.ShoppingItem;

import java.util.UUID;

public record ShoppingItemResponse(
        UUID id,
        String item,
        String quantidade,
        String lista,
        boolean comprado,
        String marca,
        String imagem,
        String codigo,
        UUID usuario
) {

    public static ShoppingItemResponse de(ShoppingItem item) {
        return new ShoppingItemResponse(
                item.getId(),
                item.getItem(),
                item.getQuantity(),
                item.getListName(),
                item.isPurchased(),
                item.getBrand(),
                item.getImageUrl(),
                item.getBarcode(),
                item.getUserId()
        );
    }
}
