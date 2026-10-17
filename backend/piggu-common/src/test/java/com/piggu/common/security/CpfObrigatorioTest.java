package com.piggu.common.security;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfObrigatorioTest {

    private final CpfObrigatorio interceptor = new CpfObrigatorio();

    @AfterEach
    void limpa() {
        SecurityContextHolder.clearContext();
    }

    private boolean passa() {
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    private void autentica(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private static Jwt token(Boolean cpf) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("1");
        if (cpf != null) {
            builder.claim(CurrentUserArgumentResolver.JwtClaims.CLAIM_CPF, cpf);
        }
        return builder.build();
    }

    @Test
    @DisplayName("recusa com CPF_PENDENTE o token que diz cpf=false")
    void recusaSemCpf() {
        autentica(token(false));
        assertThatThrownBy(this::passa)
                .isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo(CpfObrigatorio.CODIGO_CPF_PENDENTE);
    }

    @Test
    @DisplayName("deixa passar cpf=true, token sem a claim, outro principal e pedido sem login")
    void deixaPassar() {
        autentica(token(true));
        assertThat(passa()).isTrue();
        autentica(token(null));
        assertThat(passa()).isTrue();
        autentica("alguem");
        assertThat(passa()).isTrue();
        SecurityContextHolder.clearContext();
        assertThat(passa()).isTrue();
    }
}
