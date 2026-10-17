package com.piggu.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Transforma a claim {@code role} do token em uma authority do Spring Security,
 * para que {@code @PreAuthorize("hasRole('ADMIN')")} funcione nos controllers.
 */
public class PigguJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        PigguRole role = PigguRole.of(jwt.getClaimAsString(CurrentUserArgumentResolver.JwtClaims.CLAIM_ROLE));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
