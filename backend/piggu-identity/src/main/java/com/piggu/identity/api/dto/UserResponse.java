package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import com.piggu.identity.domain.UserAccount;

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
        Map<String, Object> permissoes
) {

    public static UserResponse de(UserAccount conta) {
        return new UserResponse(
                conta.getId(),
                conta.getEmail(),
                conta.nomeExibicao(),
                conta.primeiroNomeExibicao(),
                conta.getNickname(),
                conta.getGooglePicture(),
                conta.getRole(),
                conta.isActive(),
                conta.getPermissions()
        );
    }
}
