package com.piggu.common.security;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Diz ao Hibernate de qual familia e a sessao.
 *
 * <p>Toda entidade de dominio marca a coluna {@code household_id} com {@code @TenantId}:
 * o Hibernate acrescenta o filtro em toda consulta e preenche a coluna ao gravar. Uma
 * consulta nova nao vaza dado de outra familia, porque ninguem precisa lembrar de
 * filtrar.</p>
 *
 * <p>A familia vem da claim {@code familia} do token. Sem token (job agendado) a sessao
 * fica numa familia que nao existe e nao enxerga nada, a menos que o codigo diga
 * explicitamente para quem trabalha com {@link #como}. Consultas nativas (SQL puro)
 * nao passam pelo filtro: filtrar a mao.</p>
 */
public class FamiliaAtual implements CurrentTenantIdentifierResolver<UUID> {

    /** Familia que nao existe: sessao sem dono nao le nem grava em familia nenhuma. */
    public static final UUID NENHUMA = new UUID(0L, 0L);

    /** Todas as familias, so para manutencao (retencao). Nunca a partir de uma requisicao. */
    public static final UUID TODAS = new UUID(-1L, -1L);

    private static final ThreadLocal<UUID> FORCADA = new ThreadLocal<>();

    /** Roda {@code tarefa} como a familia informada; o Hibernate abre a sessao dentro dela. */
    public static <T> T como(UUID familia, Supplier<T> tarefa) {
        UUID anterior = FORCADA.get();
        FORCADA.set(familia);
        try {
            return tarefa.get();
        } finally {
            if (anterior == null) {
                FORCADA.remove();
            } else {
                FORCADA.set(anterior);
            }
        }
    }

    public static void como(UUID familia, Runnable tarefa) {
        como(familia, () -> {
            tarefa.run();
            return null;
        });
    }

    public static UUID atual() {
        UUID forcada = FORCADA.get();
        if (forcada != null) {
            return forcada;
        }
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            return de(jwt);
        }
        return NENHUMA;
    }

    /** Familia da claim do token; ausente ou invalida vira {@link #NENHUMA}. */
    public static UUID de(Jwt jwt) {
        String familia = jwt.getClaimAsString(CurrentUserArgumentResolver.JwtClaims.CLAIM_FAMILIA);
        try {
            return familia == null ? NENHUMA : UUID.fromString(familia);
        } catch (IllegalArgumentException invalida) {
            return NENHUMA;
        }
    }

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return atual();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID familia) {
        return TODAS.equals(familia);
    }
}
