package com.piggu.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe, o provedor de pagamento do site.
 *
 * <p>Vem so de variaveis de ambiente. Sem a chave secreta e os dois precos o servico
 * sobe normalmente e a tela de planos mostra a assinatura como indisponivel.</p>
 *
 * @param secretKey     sk_live_... ou sk_test_...
 * @param webhookSecret whsec_..., confere que o aviso veio mesmo da Stripe
 * @param priceMensal   id do preco de R$ 19,90/mes criado no painel da Stripe
 * @param priceAnual    id do preco de R$ 199/ano
 * @param siteUrl       endereco do front, para onde a Stripe devolve a pessoa
 */
@ConfigurationProperties(prefix = "piggu.stripe")
public record StripeProperties(String baseUrl, String secretKey, String webhookSecret,
                               String priceMensal, String priceAnual, String siteUrl) {

    public StripeProperties {
        baseUrl = vazio(baseUrl) ? "https://api.stripe.com" : baseUrl;
        siteUrl = vazio(siteUrl) ? "http://localhost:4200" : siteUrl;
    }

    public boolean habilitado() {
        return !vazio(secretKey) && !vazio(webhookSecret) && !vazio(priceMensal) && !vazio(priceAnual);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
