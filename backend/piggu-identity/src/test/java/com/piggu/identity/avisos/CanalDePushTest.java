package com.piggu.identity.avisos;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

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
import static org.mockito.Mockito.when;

/** Contrato com o APNs: endereco, cabecalhos e o JWT ES256 assinado com a chave .p8. */
class CanalDePushTest {

    @Test
    @DisplayName("assina com a chave .p8, manda para o aparelho e entende 200 e 410")
    @SuppressWarnings("unchecked")
    void enviaEEntendeAResposta() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = gerador.generateKeyPair();
        String p8 = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(par.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200, 410);
        when(ok.body()).thenReturn("", "{\"reason\":\"Unregistered\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) ok);

        CanalDePush canal = new CanalDePush(
                new AvisosProperties(null, new AvisosProperties.Apns("TEAM123", "KEY123", p8, "app.piggu", true)),
                JsonMapper.builder().build(), http, Clock.systemUTC());
        Mensagem mensagem = new Mensagem("Piggu: 1 aviso de hoje", List.of("Luz vence amanha"));

        assertThat(canal.ligado()).isTrue();
        assertThat(canal.enviar("ab12", mensagem)).isEqualTo(CanalDePush.Resultado.ENVIADO);
        assertThat(canal.enviar("ab12", mensagem)).isEqualTo(CanalDePush.Resultado.TOKEN_INVALIDO);

        ArgumentCaptor<HttpRequest> pedido = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(http, org.mockito.Mockito.times(2)).send(pedido.capture(), any());
        HttpRequest primeiro = pedido.getAllValues().get(0);
        assertThat(primeiro.uri().toString()).isEqualTo("https://api.sandbox.push.apple.com/3/device/ab12");
        assertThat(primeiro.headers().firstValue("apns-topic")).contains("app.piggu");
        SignedJWT jwt = SignedJWT.parse(primeiro.headers().firstValue("authorization").orElseThrow()
                .substring("bearer ".length()));
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) par.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY123");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123");
    }

    @Test
    @DisplayName("sem as quatro credenciais o canal fica desligado")
    void desligadoSemCredenciais() {
        assertThat(new CanalDePush(new AvisosProperties(null, null), JsonMapper.builder().build()).ligado())
                .isFalse();
    }
}
