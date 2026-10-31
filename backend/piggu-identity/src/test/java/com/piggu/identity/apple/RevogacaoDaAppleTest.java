package com.piggu.identity.apple;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.piggu.identity.config.AppleProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contrato com a Apple: troca do codigo, revogacao e o client secret ES256. */
class RevogacaoDaAppleTest {

    private static final String APP = "br.com.piggu.app";

    @Test
    @DisplayName("troca o codigo pelo refresh token, revoga e assina o client secret com a chave .p8")
    @SuppressWarnings("unchecked")
    void trocaERevoga() throws Exception {
        KeyPair par = parDeChaves();
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resposta = mock(HttpResponse.class);
        when(resposta.statusCode()).thenReturn(200, 200, 400);
        when(resposta.body()).thenReturn("{\"refresh_token\":\"rt-1\",\"id_token\":\"x\"}", "",
                "{\"error\":\"invalid_grant\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) resposta);

        RevogacaoDaApple apple = apple(http, par);

        assertThat(apple.ligada()).isTrue();
        assertThat(apple.trocarCodigo("codigo-1", APP)).isEqualTo("rt-1");
        assertThat(apple.revogar("rt-1", APP)).isTrue();
        assertThat(apple.trocarCodigo("codigo-usado", APP)).isNull();

        ArgumentCaptor<HttpRequest> pedidos = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(pedidos.capture(), any());
        assertThat(pedidos.getAllValues()).extracting(pedido -> pedido.uri().toString())
                .containsExactly("https://appleid.apple.com/auth/token", "https://appleid.apple.com/auth/revoke",
                        "https://appleid.apple.com/auth/token");

        SignedJWT segredo = SignedJWT.parse(apple.clientSecret(APP));
        assertThat(segredo.verify(new ECDSAVerifier((ECPublicKey) par.getPublic()))).isTrue();
        assertThat(segredo.getHeader().getKeyID()).isEqualTo("KEY123");
        assertThat(segredo.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123");
        assertThat(segredo.getJWTClaimsSet().getSubject()).isEqualTo(APP);
        assertThat(segredo.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(segredo.getJWTClaimsSet().getExpirationTime()).isAfter(segredo.getJWTClaimsSet().getIssueTime());
    }

    @Test
    @DisplayName("Apple recusando, fora do ar ou respondendo lixo vira nulo ou falso, sem excecao")
    @SuppressWarnings("unchecked")
    void falhasDaApple() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resposta = mock(HttpResponse.class);
        when(resposta.statusCode()).thenReturn(200, 200, 400);
        when(resposta.body()).thenReturn("{}", "nao e json", "{\"error\":\"invalid_token\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) resposta, (HttpResponse) resposta, (HttpResponse) resposta)
                .thenThrow(new IOException("sem rede"), new IOException("sem rede"), new InterruptedException());
        RevogacaoDaApple apple = apple(http, parDeChaves());

        assertThat(apple.trocarCodigo("sem-token", APP)).isNull();
        assertThat(apple.trocarCodigo("corpo-ilegivel", APP)).isNull();
        assertThat(apple.revogar("rt-vencido", APP)).isFalse();
        assertThat(apple.trocarCodigo("fora-do-ar", APP)).isNull();
        assertThat(apple.revogar("fora-do-ar", APP)).isFalse();
        assertThat(apple.trocarCodigo("interrompido", APP)).isNull();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("sem a chave .p8 a revogacao fica desligada")
    void desligadaSemChave() {
        assertThat(new RevogacaoDaApple(new AppleProperties(List.of(APP), null, null, "TEAM123", null, null),
                JsonMapper.builder().build()).ligada()).isFalse();
    }

    private static RevogacaoDaApple apple(HttpClient http, KeyPair par) {
        // Chave gerada agora, so para o teste (cabecalho montado para o gitleaks nao confundir).
        String marca = "PRIVATE" + " KEY-----";
        String p8 = "-----BEGIN " + marca + "\n"
                + Base64.getMimeEncoder().encodeToString(par.getPrivate().getEncoded())
                + "\n-----END " + marca;
        return new RevogacaoDaApple(new AppleProperties(List.of(APP), null, null, "TEAM123", "KEY123", p8),
                JsonMapper.builder().build(), http, Clock.systemUTC());
    }

    private static KeyPair parDeChaves() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        return gerador.generateKeyPair();
    }
}
