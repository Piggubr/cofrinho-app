package com.piggu.lifestyle.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param lista Compras ou Desejos; qualquer outro valor vira Compras
 */
public record ShoppingItemRequest(
        @NotBlank(message = "Digite o que deseja adicionar.")
        @Size(max = 150, message = "O nome do item e longo demais.")
        String item,

        @Size(max = 50)
        String quantidade,

        @Pattern(regexp = "Compras|Desejos", message = "Lista invalida.")
        String lista,

        @Size(max = 100)
        String marca,

        @Size(max = 1000)
        String imagem,

        @Size(max = 20)
        String codigo
) {
}
