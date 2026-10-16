package com.piggu.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Os parametros do filtro por dono saem do token de quem chama; sem token, nao corta nada. */
class DonoAtualTest {

    private static final UUID EU = UUID.randomUUID();
    private static final UUID MENOR = UUID.randomUUID();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sem token (job): todos, nenhum id visivel e ninguem fica com o sem dono")
    void semToken() {
        assertThat(new DonoAtual.Todos().get()).isTrue();
        assertThat(new DonoAtual.Visiveis().get()).isEqualTo("{}");
        assertThat(new DonoAtual.SemDono().get()).isFalse();
        assertThat(new DonoAtual.Eu().get()).isEqualTo(FamiliaAtual.NENHUMA.toString());
    }

    @Test
    @DisplayName("parceiro com tambemVe: ve o dele e o da claim, e fica com o sem dono")
    void parceiro() {
        autenticar(PigguRole.PARCEIRO, List.of(MENOR.toString()));

        assertThat(new DonoAtual.Todos().get()).isFalse();
        assertThat(new DonoAtual.Visiveis().get()).isEqualTo("{" + EU + "," + MENOR + "}");
        assertThat(new DonoAtual.SemDono().get()).isTrue();
        assertThat(new DonoAtual.Eu().get()).isEqualTo(EU.toString());
    }

    @Test
    @DisplayName("membro: so o dele; claim tambemVe invalida e ignorada")
    void membro() {
        autenticar(PigguRole.MEMBRO, List.of("nao-e-uuid"));

        assertThat(new DonoAtual.Visiveis().get()).isEqualTo("{" + EU + "}");
        assertThat(new DonoAtual.SemDono().get()).isFalse();
    }

    private static void autenticar(PigguRole papel, List<String> tambemVe) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .subject(EU.toString())
                .claim("email", "eu@piggu.test")
                .claim("role", papel.name())
                .claim(CurrentUserArgumentResolver.JwtClaims.CLAIM_TAMBEM_VE, tambemVe)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
