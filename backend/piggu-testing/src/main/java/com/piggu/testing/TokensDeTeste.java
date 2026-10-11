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
 *
 * <p>Todos os atalhos sao da {@link #FAMILIA}. Para provar isolamento entre familias,
 * use {@link #da(UUID, String, PigguRole)} com {@link #OUTRA_FAMILIA}.</p>
 */
public final class TokensDeTeste {

    public static final String EMAIL_ADMIN = "admin@piggu.test";
    public static final String EMAIL_TITULAR = "titular@piggu.test";
    public static final String EMAIL_MEMBRO = "membro@piggu.test";
    public static final String EMAIL_PARCEIRO = "parceiro@piggu.test";

    public static final UUID FAMILIA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OUTRA_FAMILIA = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private TokensDeTeste() {
    }

    /** O id que o token de teste da a quem tem este e-mail: e ele que marca o autor nas tabelas. */
    public static UUID idDe(String email) {
        return UUID.nameUUIDFromBytes(email.getBytes());
    }

    public static RequestPostProcessor admin() {
        return como(EMAIL_ADMIN, PigguRole.ADMIN);
    }

    /** Titular com Premium: o caso comum dos testes de permissao. */
    public static RequestPostProcessor titular() {
        return como(EMAIL_TITULAR, PigguRole.TITULAR, idDe(EMAIL_TITULAR), "PREMIUM");
    }

    /** Titular no plano gratuito, para conferir o que o Premium barra. */
    public static RequestPostProcessor titularGratuita() {
        return como(EMAIL_TITULAR, PigguRole.TITULAR, idDe(EMAIL_TITULAR), "GRATUITO");
    }

    /** Parceiro da familia do titular, no Premium como ela. */
    public static RequestPostProcessor parceiro() {
        return como(EMAIL_PARCEIRO, PigguRole.PARCEIRO, idDe(EMAIL_PARCEIRO), "PREMIUM");
    }

    public static RequestPostProcessor membro() {
        return como(EMAIL_MEMBRO, PigguRole.MEMBRO);
    }

    /** Pessoa de outra familia, com Premium para nada ser barrado pelo plano. */
    public static RequestPostProcessor da(UUID familia, String email, PigguRole role) {
        return token(email, role, idDe(email), "PREMIUM", familia);
    }

    /** Token de exclusao que so o identity emite, com o escopo PESSOA ou FAMILIA. */
    public static RequestPostProcessor exclusao(UUID familia, String email, PigguRole role, String escopo) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token
                        .subject(idDe(email).toString())
                        .claim("email", email)
                        .claim("role", role.name())
                        .claim("familia", familia.toString())
                        .claim("exclusao", escopo))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role.authority()));
    }

    public static RequestPostProcessor como(String email, PigguRole role) {
        return como(email, role, idDe(email));
    }

    /** Permite fixar o id, para testes que comparam dono de registro. */
    public static RequestPostProcessor como(String email, PigguRole role, UUID id) {
        return como(email, role, id, "GRATUITO");
    }

    public static RequestPostProcessor como(String email, PigguRole role, UUID id, String plano) {
        return token(email, role, id, plano, FAMILIA);
    }

    private static RequestPostProcessor token(String email, PigguRole role, UUID id, String plano, UUID familia) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token
                        .subject(id.toString())
                        .claim("email", email)
                        .claim("role", role.name())
                        .claim("plano", plano)
                        .claim("familia", familia.toString())
                        .claim("nome", email.split("@")[0]))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role.authority()));
    }
}
