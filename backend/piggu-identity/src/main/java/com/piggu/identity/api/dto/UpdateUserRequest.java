package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import jakarta.validation.constraints.Size;

/**
 * Alteracoes que um administrador pode fazer em uma conta.
 * Campos nulos ficam como estao.
 */
public record UpdateUserRequest(
        @Size(max = 120, message = "O apelido e' longo demais.") String apelido,
        PigguRole role,
        Boolean ativo
) {
}
