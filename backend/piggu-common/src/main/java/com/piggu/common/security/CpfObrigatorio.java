package com.piggu.common.security;

import com.piggu.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Conta sem CPF nao usa os dados: recusa (403, {@code CPF_PENDENTE}) o token cuja claim
 * {@code cpf} diz {@code false}, em qualquer rota do servico. Do mesmo jeito, recusa com
 * {@code CADASTRO_PENDENTE} o token com {@code cadastro=false}: falta apelido, nascimento,
 * CPF ou celular (D-088).
 *
 * <p>O identity desliga isto ({@code piggu.cpf.exigir=false}): e nele que a pessoa
 * informa o CPF, exporta ou apaga a conta. Token sem a claim passa: os internos (exclusao
 * da conta, lembrete diario) nao a levam, para apagar a conta funcionar sem CPF.</p>
 */
public class CpfObrigatorio implements HandlerInterceptor {

    public static final String CODIGO_CPF_PENDENTE = "CPF_PENDENTE";
    public static final String CODIGO_CADASTRO_PENDENTE = "CADASTRO_PENDENTE";

    @Override
    public boolean preHandle(HttpServletRequest pedido, HttpServletResponse resposta, Object handler) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt
                && Boolean.FALSE.equals(jwt.getClaimAsBoolean(CurrentUserArgumentResolver.JwtClaims.CLAIM_CPF))) {
            throw new BusinessException("Informe seu CPF para continuar.", HttpStatus.FORBIDDEN, CODIGO_CPF_PENDENTE);
        }
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt
                && Boolean.FALSE.equals(jwt.getClaimAsBoolean(CurrentUserArgumentResolver.JwtClaims.CLAIM_CADASTRO))) {
            throw new BusinessException("Complete seu cadastro para continuar.", HttpStatus.FORBIDDEN,
                    CODIGO_CADASTRO_PENDENTE);
        }
        return true;
    }
}
