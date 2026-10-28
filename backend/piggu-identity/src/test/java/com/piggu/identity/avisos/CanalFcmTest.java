package com.piggu.identity.avisos;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contrato com o FCM: troca do JWT da conta de servico pelo access token e o envio pela API v1. */
class CanalFcmTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Mensagem MENSAGEM = new Mensagem("Piggu: 1 aviso de hoje", List.of("Luz vence amanha"));

    @Test
    @DisplayName("troca o JWT pelo access token uma vez, manda para o aparelho e entende a resposta")
    @SuppressWarnings("unchecked")
    void enviaEEntendeAResposta() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        KeyPair par = gerador.generateKeyPair();
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> token = resposta(200, "{\"access_token\":\"ya29.teste\",\"expires_in\":3599}");
        HttpResponse<String> enviado = resposta(200, "{\"name\":\"projects/piggu-app/messages/1\"}");
        HttpResponse<String> naoExiste = resposta(404,
                "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}");
        HttpResponse<String> invalido = resposta(400, "{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\","
                + "\"message\":\"The registration token is not a valid FCM registration token\"}}");
        HttpResponse<String> projetoErrado = resposta(404,
                "{\"error\":{\"code\":404,\"message\":\"Requested entity was not found.\",\"status\":\"NOT_FOUND\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) token, enviado, naoExiste, invalido, projetoErrado);

        CanalFcm canal = new CanalFcm(new AvisosProperties(null, null, new AvisosProperties.Fcm(contaDeServico(par))),
                JSON, http, Clock.systemUTC());

        assertThat(canal.ligado()).isTrue();
        assertThat(canal.enviar("tokenA", MENSAGEM)).isEqualTo(CanalDePush.Resultado.ENVIADO);
        assertThat(canal.enviar("tokenB", MENSAGEM)).isEqualTo(CanalDePush.Resultado.TOKEN_INVALIDO);
        assertThat(canal.enviar("tokenC", MENSAGEM)).isEqualTo(CanalDePush.Resultado.TOKEN_INVALIDO);
        // 404 sem UNREGISTERED e configuracao errada, nao aparelho: nada e apagado.
        assertThat(canal.enviar("tokenD", MENSAGEM)).isEqualTo(CanalDePush.Resultado.FALHOU);

        ArgumentCaptor<HttpRequest> pedidos = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(5)).send(pedidos.capture(), any());
        HttpRequest troca = pedidos.getAllValues().get(0);
        assertThat(troca.uri().toString()).isEqualTo("https://oauth2.googleapis.com/token");
        String assercao = URLDecoder.decode(corpo(troca), StandardCharsets.UTF_8).split("assertion=")[1];
        SignedJWT jwt = SignedJWT.parse(assercao);
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) par.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("chave-1");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("push@piggu-app.iam.gserviceaccount.com");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://oauth2.googleapis.com/token");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("scope"))
                .isEqualTo("https://www.googleapis.com/auth/firebase.messaging");

        HttpRequest envio = pedidos.getAllValues().get(1);
        assertThat(envio.uri().toString())
                .isEqualTo("https://fcm.googleapis.com/v1/projects/piggu-app/messages:send");
        assertThat(envio.headers().firstValue("authorization")).contains("Bearer ya29.teste");
        Map<String, Map<String, Object>> mensagem = JSON.readValue(corpo(envio), Map.class);
        assertThat(mensagem.get("message")).containsEntry("token", "tokenA")
                .containsEntry("notification", Map.of("title", "Piggu: 1 aviso de hoje", "body", "Luz vence amanha"));
    }

    @Test
    @DisplayName("sem a conta de servico o canal fica desligado")
    void desligadoSemCredenciais() {
        assertThat(new CanalFcm(new AvisosProperties(null, null, null), JSON).ligado()).isFalse();
    }

    @Test
    @DisplayName("conta de servico que nao e o JSON do Google falha sem derrubar o lembrete")
    void contaInvalidaFalha() {
        CanalFcm canal = new CanalFcm(new AvisosProperties(null, null, new AvisosProperties.Fcm("{\"private_key\":\"x\"}")),
                JSON, mock(HttpClient.class), Clock.systemUTC());
        assertThat(canal.enviar("tokenA", MENSAGEM)).isEqualTo(CanalDePush.Resultado.FALHOU);
    }

    /** O JSON que o Google Cloud entrega, com uma chave gerada agora so para o teste. */
    private static String contaDeServico(KeyPair par) {
        // Cabecalho PEM montado aos pedacos para o gitleaks nao confundir com chave de verdade.
        String marca = "PRIVATE" + " KEY-----";
        String pem = "-----BEGIN " + marca + "\n"
                + Base64.getMimeEncoder().encodeToString(par.getPrivate().getEncoded()) + "\n-----END " + marca + "\n";
        return JSON.writeValueAsString(Map.of(
                "type", "service_account",
                "project_id", "piggu-app",
                "private_key_id", "chave-1",
                "private_key", pem,
                "client_email", "push@piggu-app.iam.gserviceaccount.com",
                "token_uri", "https://oauth2.googleapis.com/token"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> resposta(int status, String corpo) {
        HttpResponse<String> resposta = mock(HttpResponse.class);
        when(resposta.statusCode()).thenReturn(status);
        when(resposta.body()).thenReturn(corpo);
        return resposta;
    }

    /** Le o corpo do pedido, que o HttpClient so entrega como fluxo. */
    private static String corpo(HttpRequest pedido) {
        StringBuilder lido = new StringBuilder();
        pedido.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription assinatura) {
                assinatura.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer pedaco) {
                lido.append(StandardCharsets.UTF_8.decode(pedaco));
            }

            @Override
            public void onError(Throwable erro) {
                throw new IllegalStateException(erro);
            }

            @Override
            public void onComplete() {
                // nada: o texto ja esta em lido
            }
        });
        return lido.toString();
    }
}
