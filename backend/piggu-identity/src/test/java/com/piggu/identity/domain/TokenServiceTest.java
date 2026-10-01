package com.piggu.identity.domain;

import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emissao de tokens.
 *
 * <p>O access token e o unico canal por onde os outros servicos descobrem quem esta
 * chamando e com qual perfil. Se uma claim mudar de nome aqui, todos eles param de
 * autorizar corretamente, e nenhum teste dos outros modulos perceberia.</p>
 */
class TokenServiceTest extends PostgresIntegrationTest {

    @Autowired
    private TokenService tokens;

    @Autowired
    private JwtDecoder decoder;

    @Test
    @DisplayName("access token carrega id, e-mail e perfil, e e verificavel pela chave publica")
    void accessTokenCarregaOEssencial() {
        UserAccount conta = new UserAccount("beatriz@piggu.test", PigguRole.BEATRIZ);

        Jwt token = decoder.decode(tokens.gerarAccessToken(conta));

        assertThat(token.getSubject()).isEqualTo(conta.getId().toString());
        assertThat(token.getClaimAsString("email")).isEqualTo("beatriz@piggu.test");
        assertThat(token.getClaimAsString("role")).isEqualTo("BEATRIZ");
        assertThat(token.getClaimAsString("plano")).isEqualTo("GRATUITO");
        assertThat(token.getIssuer()).hasToString("https://piggu.test");
    }

    @Test
    @DisplayName("access token traz o kid que o JWKS publica")
    void tokenTrazOKid() {
        UserAccount conta = new UserAccount("admin@piggu.test", PigguRole.ADMIN);

        Jwt token = decoder.decode(tokens.gerarAccessToken(conta));

        assertThat(token.getHeaders()).containsEntry("kid", "piggu-signing-key");
        assertThat(token.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    @DisplayName("access token expira dentro do prazo configurado")
    void tokenExpira() {
        UserAccount conta = new UserAccount("beatriz@piggu.test", PigguRole.BEATRIZ);

        Jwt token = decoder.decode(tokens.gerarAccessToken(conta));

        assertThat(token.getExpiresAt()).isNotNull().isAfter(Instant.now());
        assertThat(tokens.segundosDeAcesso()).isEqualTo(1800);
    }

    @Test
    @DisplayName("cada refresh token e diferente do anterior")
    void refreshSempreDiferente() {
        assertThat(tokens.gerarRefreshToken()).isNotEqualTo(tokens.gerarRefreshToken());
        assertThat(tokens.gerarRefreshToken()).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("hash e estavel para o mesmo token e diferente entre tokens")
    void hashEstavelEUnico() {
        String token = tokens.gerarRefreshToken();

        assertThat(tokens.hash(token)).isEqualTo(tokens.hash(token)).hasSize(64);
        assertThat(tokens.hash(token)).isNotEqualTo(tokens.hash(tokens.gerarRefreshToken()));
    }

    @Test
    @DisplayName("hash nao permite recuperar o token original")
    void hashNaoRevelaOToken() {
        String token = tokens.gerarRefreshToken();

        assertThat(tokens.hash(token)).doesNotContain(token);
    }

    @Test
    @DisplayName("sessao longa vale trinta dias")
    void sessaoLongaDeTrintaDias() {
        assertThat(tokens.expiracaoDaSessao())
                .isAfter(Instant.now().plusSeconds(29 * 24 * 3600))
                .isBefore(Instant.now().plusSeconds(31 * 24 * 3600));
    }
}
