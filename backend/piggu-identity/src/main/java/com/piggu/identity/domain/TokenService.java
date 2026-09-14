package com.piggu.identity.domain;

import com.piggu.identity.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Emissao dos tokens do Piggu.
 *
 * <p>O access token carrega o que os outros servicos precisam para decidir sozinhos:
 * o id do usuario em {@code sub}, o e-mail e o papel. Nenhum servico de dominio
 * precisa consultar o identity para autorizar uma chamada.</p>
 */
@Service
public class TokenService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JwtEncoder encoder;
    private final JwtProperties propriedades;

    public TokenService(JwtEncoder encoder, JwtProperties propriedades) {
        this.encoder = encoder;
        this.propriedades = propriedades;
    }

    public String gerarAccessToken(UserAccount conta) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propriedades.issuer())
                .issuedAt(agora)
                .expiresAt(agora.plus(propriedades.accessTtl()))
                .subject(conta.getId().toString())
                .claim("email", conta.getEmail())
                .claim("role", conta.getRole().name())
                .claim("nome", conta.nomeExibicao())
                .build();

        JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).keyId("piggu-signing-key").build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }

    /** Token opaco de 256 bits para a sessao longa. Nunca e' gravado em claro. */
    public String gerarRefreshToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM.", erro);
        }
    }

    public long segundosDeAcesso() {
        return propriedades.accessTtl().toSeconds();
    }

    public Instant expiracaoDaSessao() {
        return Instant.now().plus(propriedades.refreshTtl());
    }
}
