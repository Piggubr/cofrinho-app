package com.piggu.common.security;

import com.piggu.common.error.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Token sem e-mail ou chamada sem token: 401 com texto acentuado, nunca 500. */
class SessaoIncompletaTest {

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("token sem e-mail pede para entrar de novo")
    void semEmail() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(UUID.randomUUID().toString()).build();

        assertThatThrownBy(() -> CurrentUserArgumentResolver.JwtClaims.toCurrentUser(jwt))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Sua sessão está incompleta. Entre novamente.");
    }

    @Test
    @DisplayName("sem token nenhum, pede login")
    void semToken() {
        assertThatThrownBy(() -> new CurrentUserArgumentResolver()
                .resolveArgument((MethodParameter) null, null, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Faça login para continuar.");
    }
}
