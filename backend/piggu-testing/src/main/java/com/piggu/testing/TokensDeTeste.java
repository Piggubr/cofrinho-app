package com.piggu.testing;

import com.piggu.common.security.PigguRole;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/**
 * Tokens falsos para os testes de API.
 *
 * <p>Monta direto o objeto Jwt que o resolvedor de usuario espera, em vez de assinar
 * um token com a chave real. Assim os testes de dominio nao dependem do servico de
 * identidade nem de chaves no disco.</p>
 */
public final class TokensDeTeste {

    public static final String EMAIL_ADMIN = "admin@piggu.test";
    public static final String EMAIL_BEATRIZ = "beatriz@piggu.test";
    public static final String EMAIL_FAMILIAR = "familiar@piggu.test";

    private TokensDeTeste() {
    }

    public static RequestPostProcessor admin() {
        return como(EMAIL_ADMIN, PigguRole.ADMIN);
    }

    public static RequestPostProcessor beatriz() {
        return como(EMAIL_BEATRIZ, PigguRole.BEATRIZ);
    }

    public static RequestPostProcessor familiar() {
        return como(EMAIL_FAMILIAR, PigguRole.FAMILIAR);
    }

    public static RequestPostProcessor como(String email, PigguRole role) {
        return como(email, role, UUID.nameUUIDFromBytes(email.getBytes()));
    }

    /** Permite fixar o id, para testes que comparam dono de registro. */
    public static RequestPostProcessor como(String email, PigguRole role, UUID id) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token
                        .subject(id.toString())
                        .claim("email", email)
                        .claim("role", role.name())
                        .claim("nome", email.split("@")[0]))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role.authority()));
    }
}
