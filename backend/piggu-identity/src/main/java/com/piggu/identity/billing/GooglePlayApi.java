package com.piggu.identity.billing;

import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;

import java.io.IOException;
import java.util.Optional;

/** O que o Piggu usa da Google Play Developer API. Os testes trocam por uma falsa. */
public interface GooglePlayApi {

    /** Sem a conta de servico configurada, a loja do Google fica desligada. */
    boolean configurada();

    /**
     * {@code purchases.subscriptionsv2.get} no pacote do app.
     *
     * @return vazio quando o Google nao conhece o token neste app (invalido ou de outro app)
     * @throws com.piggu.common.error.UpstreamException quando o Google nao respondeu
     */
    Optional<SubscriptionPurchaseV2> assinatura(String token);

    /** {@code purchases.subscriptions.acknowledge}: sem isto o Google estorna em 3 dias. */
    void confirmar(String produto, String token) throws IOException;
}
