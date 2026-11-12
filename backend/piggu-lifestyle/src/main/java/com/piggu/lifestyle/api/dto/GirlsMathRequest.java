package com.piggu.lifestyle.api.dto;

import com.piggu.common.web.Valores;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Algo que a pessoa decidiu nao comprar.
 *
 * @param valor       quanto custaria; e o que ela deixou de gastar
 * @param data        quando decidiu; nula vira hoje (Brasilia)
 * @param imageBase64 foto opcional do produto
 */
public record GirlsMathRequest(
        @NotBlank(message = "Digite o produto.")
        @Size(max = 120, message = "O nome do produto é longo demais.")
        String produto,

        @Size(max = 120, message = "O nome da loja é longo demais.")
        String loja,

        @NotNull(message = "Digite o valor.")
        @DecimalMin(value = Valores.MINIMO, message = Valores.ABAIXO_DO_MINIMO)
        @DecimalMax(value = Valores.MAXIMO, message = Valores.ACIMA_DO_MAXIMO)
        BigDecimal valor,

        LocalDate data,

        @Size(max = 7_000_000, message = "A foto é grande demais. O limite é de 5 MB.")
        String imageBase64,

        @Pattern(regexp = "image/[a-z0-9.+-]{1,30}", message = "Formato de imagem não aceito.")
        String mimeType
) {
}
