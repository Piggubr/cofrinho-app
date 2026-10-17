package com.piggu.identity.domain;

import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.LocalDate;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emissao de tokens.
 *
 * <p>O access token e o unico canal por onde os outros servicos descobrem quem esta
 * chamando e com qual perfil. Se uma claim mudar de nome aqui, todos eles param de
 * autorizar corretamente, e nenhum teste dos outros modulos perceberia.</p>
 */
class TokenServiceTest extends PostgresIntegrationTest {

    private static final Household FAMILIA = new Household("Familia de teste");

    @Autowired
    private TokenService tokens;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdRepository casas;

    @Test
    @DisplayName("titular e parceiro levam no token um ao outro e os menores (13 a 17); o menor e o membro adulto, nada")
    void quemOsResponsaveisVeem() {
        Household casa = casas.save(new Household("Casa com menor"));
        UserAccount titular = usuarios.save(new UserAccount("titular.menores@piggu.test", PigguRole.TITULAR, casa.getId()));
        UserAccount parceiro = new UserAccount("parceiro.menores@piggu.test", PigguRole.PARCEIRO, casa.getId());
        parceiro.informarNascimento(LocalDate.now().minusYears(30));
        usuarios.save(parceiro);
        UserAccount menor = new UserAccount("menor.menores@piggu.test", PigguRole.MEMBRO, casa.getId());
        menor.informarNascimento(LocalDate.now().minusYears(15));
        usuarios.save(menor);
        UserAccount adulto = new UserAccount("adulto.menores@piggu.test", PigguRole.MEMBRO, casa.getId());
        adulto.informarNascimento(LocalDate.now().minusYears(18));
        usuarios.save(adulto);

        assertThat(decoder.decode(tokens.gerarAccessToken(titular, casa)).getClaimAsStringList("tambemVe"))
                .containsExactlyInAnyOrder(parceiro.getId().toString(), menor.getId().toString());
        assertThat(decoder.decode(tokens.gerarAccessToken(parceiro, casa)).getClaimAsStringList("tambemVe"))
                .containsExactlyInAnyOrder(titular.getId().toString(), menor.getId().toString());
        assertThat(decoder.decode(tokens.gerarAccessToken(menor, casa)).hasClaim("tambemVe")).isFalse();
        assertThat(decoder.decode(tokens.gerarAccessToken(adulto, casa)).hasClaim("tambemVe")).isFalse();
    }


    @Test
    @DisplayName("access token carrega id, e-mail, papel, plano e familia, e e verificavel pela chave publica")
    void accessTokenCarregaOEssencial() {
        UserAccount conta = new UserAccount("titular@piggu.test", PigguRole.TITULAR, FAMILIA.getId());

        Jwt token = decoder.decode(tokens.gerarAccessToken(conta, FAMILIA));

        assertThat(token.getSubject()).isEqualTo(conta.getId().toString());
        assertThat(token.getClaimAsString("email")).isEqualTo("titular@piggu.test");
        assertThat(token.getClaimAsString("role")).isEqualTo("TITULAR");
        assertThat(token.getClaimAsString("plano")).isEqualTo("GRATUITO");
        assertThat(token.getClaimAsString("familia")).isEqualTo(FAMILIA.getId().toString());
        assertThat(token.getIssuer()).hasToString("https://piggu.test");
    }

    @Test
    @DisplayName("access token traz o kid que o JWKS publica")
    void tokenTrazOKid() {
        UserAccount conta = new UserAccount("admin@piggu.test", PigguRole.ADMIN, FAMILIA.getId());

        Jwt token = decoder.decode(tokens.gerarAccessToken(conta, FAMILIA));

        assertThat(token.getHeaders()).containsEntry("kid", "piggu-signing-key");
        assertThat(token.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    @DisplayName("access token expira dentro do prazo configurado")
    void tokenExpira() {
        UserAccount conta = new UserAccount("titular@piggu.test", PigguRole.TITULAR, FAMILIA.getId());

        Jwt token = decoder.decode(tokens.gerarAccessToken(conta, FAMILIA));

        assertThat(token.getExpiresAt()).isNotNull().isAfter(Instant.now());
        assertThat(tokens.segundosDeAcesso()).isEqualTo(300);
    }

    @Test
    @DisplayName("documento e opcional: nenhum token leva a claim cpf")
    void semClaimDoCpf() {
        UserAccount conta = new UserAccount("titular@piggu.test", PigguRole.TITULAR, FAMILIA.getId());

        assertThat(decoder.decode(tokens.gerarAccessToken(conta, FAMILIA)).hasClaim("cpf")).isFalse();
        assertThat(decoder.decode(tokens.gerarTokenDeExclusao(conta, FAMILIA, EscopoDeExclusao.PESSOA))
                .hasClaim("cpf")).isFalse();
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
