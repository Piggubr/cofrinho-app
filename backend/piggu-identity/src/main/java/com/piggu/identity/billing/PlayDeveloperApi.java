package com.piggu.identity.billing;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.piggu.common.error.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Google Play Developer API de verdade, com a conta de servico do Play Console
 * (google-api-services-androidpublisher e google-auth-library).
 */
@Component
public class PlayDeveloperApi implements GooglePlayApi {

    private static final Logger log = LoggerFactory.getLogger(PlayDeveloperApi.class);

    /** O Google responde assim ao token que nao existe, venceu ha muito ou e de outro app. */
    private static final Set<Integer> TOKEN_DESCONHECIDO = Set.of(400, 404, 410);

    private final String pacote;
    private final AndroidPublisher publisher;

    /**
     * @param credenciais o JSON da conta de servico, inteiro ou num caminho ({@code file:/app/keys/google-play.json});
     *                    vazio deixa a loja desligada
     */
    public PlayDeveloperApi(@Value("${piggu.google-play.pacote:br.com.piggu.app}") String pacote,
                            @Value("${piggu.google-play.credenciais:}") String credenciais,
                            ResourceLoader carregador) {
        this.pacote = pacote;
        this.publisher = credenciais == null || credenciais.isBlank() ? null : publisher(credenciais.trim(), carregador);
    }

    @Override
    public boolean configurada() {
        return publisher != null;
    }

    @Override
    public Optional<SubscriptionPurchaseV2> assinatura(String token) {
        try {
            return Optional.of(publisher.purchases().subscriptionsv2().get(pacote, token).execute());
        } catch (GoogleJsonResponseException recusa) {
            if (TOKEN_DESCONHECIDO.contains(recusa.getStatusCode())) {
                return Optional.empty();
            }
            // 401/403: a conta de servico nao tem permissao no Play Console.
            log.error("Google Play recusou a consulta da assinatura: status={} {}", recusa.getStatusCode(), recusa.getStatusMessage());
            throw new UpstreamException("Não deu para falar com o Google Play agora. Tente de novo em instantes.");
        } catch (IOException falha) {
            log.warn("Google Play fora do ar na consulta da assinatura", falha);
            throw new UpstreamException("Não deu para falar com o Google Play agora. Tente de novo em instantes.");
        }
    }

    @Override
    public void confirmar(String produto, String token) throws IOException {
        publisher.purchases().subscriptions()
                .acknowledge(pacote, produto, token, new SubscriptionPurchasesAcknowledgeRequest())
                .execute();
    }

    /** Credencial ruim nao derruba o identity (o login fica de pe): a loja so fica desligada. */
    private static AndroidPublisher publisher(String credenciais, ResourceLoader carregador) {
        try (InputStream json = credenciais.startsWith("{")
                ? new ByteArrayInputStream(credenciais.getBytes(StandardCharsets.UTF_8))
                : carregador.getResource(credenciais).getInputStream()) {
            GoogleCredentials conta = GoogleCredentials.fromStream(json).createScoped(List.of(AndroidPublisherScopes.ANDROIDPUBLISHER));
            return new AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(conta))
                    .setApplicationName("Piggu")
                    .build();
        } catch (IOException | GeneralSecurityException | RuntimeException falha) {
            log.error("Credenciais do Google Play ilegíveis (GOOGLE_PLAY_CREDENCIAIS): a loja do Google fica desligada", falha);
            return null;
        }
    }
}
