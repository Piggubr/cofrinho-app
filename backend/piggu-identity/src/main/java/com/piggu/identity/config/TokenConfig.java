package com.piggu.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HexFormat;

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
@EnableConfigurationProperties({JwtProperties.class, GoogleProperties.class, StripeProperties.class, DadosProperties.class})
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

    /**
     * Impressao digital do modulo da chave publica que ja esteve no Git (removida em
     * c5f54e1). Qualquer um com o historico assina tokens com o par dela.
     */
    static final String CHAVE_VAZADA = "892cff2a20cce332f53932951588736209d218c2e83c9e80851d53daec047936";

    /**
     * A chave que vazou nunca assina token, em ambiente nenhum; em producao, a chave de
     * desenvolvimento gerada na maquina tambem nao.
     */
    static void conferirChave(RSAPublicKey publica, String origem, String ambiente) {
        if (CHAVE_VAZADA.equals(impressao(publica))) {
            throw new IllegalStateException("Esta chave JWT esteve no Git e e publica. Gere um par novo com"
                    + " scripts/gerar-chaves-dev.sh (local) ou fora do repositorio (producao).");
        }
        if ("producao".equals(ambiente) && origem != null && origem.contains("piggu-dev-")) {
            throw new IllegalStateException("Em producao defina JWT_PRIVATE_KEY e JWT_PUBLIC_KEY com um par proprio;"
                    + " a chave de desenvolvimento nao serve.");
        }
    }

    static String impressao(RSAPublicKey publica) {
        try {
            byte[] modulo = publica.getModulus().toString(16).getBytes(StandardCharsets.US_ASCII);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(modulo));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM.", erro);
        }
    }

    @Bean
    public JWKSource<SecurityContext> fonteDeChaves(RSAPublicKey publica, RSAPrivateKey privada, JwtProperties propriedades,
                                                   @Value("${piggu.ambiente:dev}") String ambiente) {
        conferirChave(publica, propriedades.privateKey(), ambiente);
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
            throw new IllegalStateException("Falta configurar " + propriedade
                    + ". Defina JWT_PRIVATE_KEY e JWT_PUBLIC_KEY, ou suba com o perfil dev"
                    + " depois de rodar scripts/gerar-chaves-dev.sh.");
        }
        String limpo = valor.trim();
        if (limpo.startsWith("classpath:") || limpo.startsWith("file:")) {
            Resource recurso = carregador.getResource(limpo);
            if (!recurso.exists()) {
                throw new IllegalStateException("Arquivo de chave nao encontrado em " + limpo
                        + ". No ambiente local, gere com scripts/gerar-chaves-dev.sh.");
            }
            return recurso.getInputStream();
        }
        return new ByteArrayInputStream(limpo.getBytes(StandardCharsets.UTF_8));
    }
}
