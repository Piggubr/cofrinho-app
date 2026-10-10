package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.UserAccount;

import com.piggu.identity.domain.ModulosDeEstiloDeVida;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perfil publico do usuario — o mesmo formato que {@code usuarioPublico_} devolvia,
 * acrescido do id.
 */
public record UserResponse(
        UUID id,
        String email,
        String nome,
        String primeiroNome,
        String apelido,
        String foto,
        PigguRole role,
        boolean ativo,
        Map<String, Object> permissoes,
        Preferencias preferencias,
        Plano plano,
        Instant premiumAte,
        UUID familia
) {

    /**
     * Moeda dos valores, moeda da cotacao, se a cotacao aparece no topo, fuso, idioma e os
     * modulos de estilo de vida ligados no menu.
     */
    public record Preferencias(String moeda, String moedaConversao, boolean mostrarCotacao, String fuso,
                               String idioma, List<String> modulos) {

        public Preferencias(String moeda, String moedaConversao, boolean mostrarCotacao) {
            this(moeda, moedaConversao, mostrarCotacao, null, null, ModulosDeEstiloDeVida.TODOS);
        }
    }

    /** O plano vem da familia: o titular assina e todos usam. */
    public static UserResponse de(UserAccount conta, Household familia) {
        return new UserResponse(
                conta.getId(),
                conta.getEmail(),
                conta.nomeExibicao(),
                conta.primeiroNomeExibicao(),
                conta.getNickname(),
                conta.getGooglePicture(),
                conta.getRole(),
                conta.isActive(),
                conta.getPermissions(),
                new Preferencias(conta.getCurrency(), conta.getConversionCurrency(), conta.isShowExchangeRate(),
                        conta.getTimezone(), conta.getLocale(), conta.getModulos()),
                familia.planoVigente(),
                familia.planoVigente() == Plano.PREMIUM ? familia.getPremiumUntil() : null,
                familia.getId()
        );
    }
}
