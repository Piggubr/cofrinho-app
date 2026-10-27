package com.piggu.common.security;

import com.piggu.common.error.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.UUID;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return parametro.hasParameterAnnotation(AuthUser.class)
                && CurrentUser.class.isAssignableFrom(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro,
                                  ModelAndViewContainer container,
                                  NativeWebRequest pedido,
                                  WebDataBinderFactory binderFactory) {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof Jwt jwt)) {
            throw new UnauthorizedException("Faca login para continuar.");
        }
        return JwtClaims.toCurrentUser(jwt);
    }

    /** Leitura das claims do Piggu, em um lugar so. */
    public static final class JwtClaims {

        public static final String CLAIM_EMAIL = "email";
        public static final String CLAIM_ROLE = "role";
        public static final String CLAIM_PLANO = "plano";
        public static final String CLAIM_FAMILIA = "familia";
        /** Falso quando a conta ainda nao informou o CPF (ver {@link CpfObrigatorio}). */
        public static final String CLAIM_CPF = "cpf";
        /** Falso quando falta apelido, nascimento, CPF ou celular (ver {@link CpfObrigatorio}). */
        public static final String CLAIM_CADASTRO = "cadastro";
        /** So no token do titular e do parceiro: de quem mais da familia eles veem os dados. */

        public static final String CLAIM_TAMBEM_VE = "tambemVe";

        private JwtClaims() {
        }

        public static CurrentUser toCurrentUser(Jwt jwt) {
            String email = jwt.getClaimAsString(CLAIM_EMAIL);
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Sua sessao esta incompleta. Entre novamente.");
            }
            return new CurrentUser(
                    UUID.fromString(jwt.getSubject()),
                    email.toLowerCase(),
                    PigguRole.of(jwt.getClaimAsString(CLAIM_ROLE)),
                    Plano.de(jwt.getClaimAsString(CLAIM_PLANO)),
                    FamiliaAtual.de(jwt),
                    tambemVe(jwt)
            );
        }

        /** Ids da claim {@link #CLAIM_TAMBEM_VE}; ausente ou invalida vira lista vazia. */
        public static List<UUID> tambemVe(Jwt jwt) {
            List<String> ids = jwt.getClaimAsStringList(CLAIM_TAMBEM_VE);
            if (ids == null) {
                return List.of();
            }
            try {
                return ids.stream().map(UUID::fromString).toList();
            } catch (IllegalArgumentException invalido) {
                return List.of();
            }
        }

    }
}
