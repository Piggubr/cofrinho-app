package com.piggu.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Cada pessoa ve so o que lancou; o titular ve tambem o dos menores (13 a 17 anos).
 *
 * <p>Como o {@link FamiliaAtual}, fica no Hibernate e nao em cada consulta: as entidades
 * com dono declaram o filtro {@link #FILTRO} (ligado sozinho, inclusive no
 * {@code findById}), e uma consulta nova nao vaza o dado de outra pessoa porque ninguem
 * precisa lembrar de filtrar. Consultas nativas (SQL puro) nao passam por ele.</p>
 *
 * <p>Sem token (job agendado) o filtro deixa passar tudo: quem limita a sessao e a
 * familia, que ja nao enxerga nada sem ser escolhida explicitamente.</p>
 *
 * <p>Registro sem dono (de antes de cada um ter os seus, ou de quem excluiu a conta) fica
 * com o titular.</p>
 */
public final class DonoAtual {

    public static final String FILTRO = "dono";
    public static final String TODOS = "todos";
    public static final String VISIVEIS = "visiveis";
    public static final String SEM_DONO = "semDono";
    public static final String EU = "eu";

    /** Condicao para a coluna {@code user_id}; {@code {alias}} e a tabela na consulta. */
    public static final String POR_USER_ID = "(:todos OR {alias}.user_id = ANY(CAST(:visiveis AS uuid[]))"
            + " OR ({alias}.user_id IS NULL AND :semDono))";

    /**
     * So o da propria pessoa, nem o titular ve o dos menores: preferencias que valem para
     * quem lanca (regras de categoria, memoria de precos, metas e orcamentos). Com uma
     * linha por pessoa, as buscas por chave (categoria, termo, mes) continuam achando uma so.
     * O que ficou sem dono aqui nao aparece para ninguem.
     */
    public static final String SO_DA_PESSOA = "(:todos OR {alias}.user_id = CAST(:eu AS uuid))";


    private DonoAtual() {
    }

    /** Sem pessoa autenticada (job): todos. */
    public static final class Todos implements Supplier<Boolean> {
        @Override
        public Boolean get() {
            return jwt() == null;
        }
    }

    /** Ids visiveis no formato de array do Postgres: {@code {id1,id2}}. */
    public static final class Visiveis implements Supplier<String> {
        @Override
        public String get() {
            Jwt jwt = jwt();
            if (jwt == null) {
                return "{}";
            }
            List<UUID> ids = CurrentUserArgumentResolver.JwtClaims.toCurrentUser(jwt).visiveis();
            return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
        }
    }

    /** Id de quem esta autenticado; sem pessoa, um id que nao existe. */
    public static final class Eu implements Supplier<String> {
        @Override
        public String get() {
            Jwt jwt = jwt();
            return jwt == null ? FamiliaAtual.NENHUMA.toString() : jwt.getSubject();
        }
    }

    /** O titular fica com o que nao tem dono. */

    public static final class SemDono implements Supplier<Boolean> {
        @Override
        public Boolean get() {
            Jwt jwt = jwt();
            return jwt != null && CurrentUserArgumentResolver.JwtClaims.toCurrentUser(jwt).isTitular();

        }
    }

    private static Jwt jwt() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }
}
