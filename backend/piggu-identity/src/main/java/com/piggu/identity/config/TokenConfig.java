package com.piggu.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Monta o par de chaves RSA usado para assinar e conferir os tokens do Piggu.
 *
 * <p>A chave publica sai no endpoint JWKS; os outros servicos a baixam de la e validam
 * os tokens sozinhos, sem precisar chamar o identity a cada requisicao.</p>
 *
 * <p>Cada chave pode vir como PEM literal (variavel de ambiente em producao) ou como
 * caminho {@code classpath:} / {@code file:} — util no ambiente local.</p>
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, GoogleProperties.class})
public class TokenConfig {

    private final ResourceLoader carregador;

    public TokenConfig(ResourceLoader carregador) {
        this.carregador = carregador;
    }

    @Bean
    public RSAPublicKey chavePublica(JwtProperties propriedades) {
        try (InputStream fluxo = abrir(propriedades.publicKey(), "piggu.jwt.public-key")) {
            return RsaKeyConverters.x509().convert(fluxo);
        } catch (IOException erro) {
            throw new IllegalStateException("Nao foi possivel ler a chave publica RSA.", erro);
        }
    }

    @Bean
    public RSAPrivateKey chavePrivada(JwtProperties propriedades) {
        try (InputStream fluxo = abrir(propriedades.privateKey(), "piggu.jwt.private-key")) {
            return RsaKeyConverters.pkcs8().convert(fluxo);
        } catch (IOException erro) {
            throw new IllegalStateException("Nao foi possivel ler a chave privada RSA.", erro);
        }
    }

    @Bean
    public JWKSource<SecurityContext> fonteDeChaves(RSAPublicKey publica, RSAPrivateKey privada) {
        RSAKey chave = new RSAKey.Builder(publica)
                .privateKey(privada)
                .keyID("piggu-signing-key")
                .build();
        return new ImmutableJWKSet<>(new JWKSet(chave));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> fonteDeChaves) {
        return new NimbusJwtEncoder(fonteDeChaves);
    }

    /** Decodificador dos proprios tokens, usado para proteger os endpoints deste servico. */
    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publica) {
        return NimbusJwtDecoder.withPublicKey(publica).build();
    }

    private InputStream abrir(String valor, String propriedade) throws IOException {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta configurar " + propriedade + ".");
        }
        String limpo = valor.trim();
        if (limpo.startsWith("classpath:") || limpo.startsWith("file:")) {
            Resource recurso = carregador.getResource(limpo);
            if (!recurso.exists()) {
                throw new IllegalStateException("Arquivo de chave nao encontrado em " + limpo);
            }
            return recurso.getInputStream();
        }
        return new ByteArrayInputStream(limpo.getBytes(StandardCharsets.UTF_8));
    }
}
