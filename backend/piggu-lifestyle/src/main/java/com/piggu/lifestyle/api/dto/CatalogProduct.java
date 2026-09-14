package com.piggu.lifestyle.api.dto;

/** Produto encontrado no catalogo aberto do OpenFoodFacts. */
public record CatalogProduct(
        String codigo,
        String nome,
        String marca,
        String quantidade,
        String imagem
) {
}
