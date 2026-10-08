package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.UserAccount;

import java.time.Instant;
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

    /** Moeda dos valores, moeda da cotacao e se a cotacao aparece no topo. */
    public record Preferencias(String moeda, String moedaConversao, boolean mostrarCotacao) {
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
                new Preferencias(conta.getCurrency(), conta.getConversionCurrency(), conta.isShowExchangeRate()),
                familia.planoVigente(),
                familia.planoVigente() == Plano.PREMIUM ? familia.getPremiumUntil() : null,
                familia.getId()
        );
    }
}
