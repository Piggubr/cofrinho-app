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
 * @param priceFamiliaMensal id do preco do plano familia, R$ 39,99/mes; vazio esconde o familia
 * @param priceFamiliaAnual  id do preco do plano familia, R$ 399,90/ano
 * @param priceCasalMensal   id do preco do plano casal, R$ 29,90/mes; vazio esconde o casal
 * @param priceCasalAnual    id do preco do plano casal, R$ 299/ano
 */
@ConfigurationProperties(prefix = "piggu.stripe")
public record StripeProperties(String baseUrl, String secretKey, String webhookSecret,
                               String priceMensal, String priceAnual, String siteUrl,
                               String priceFamiliaMensal, String priceFamiliaAnual,
                               String priceCasalMensal, String priceCasalAnual) {

    public StripeProperties {
        baseUrl = vazio(baseUrl) ? "https://api.stripe.com" : baseUrl;
        siteUrl = vazio(siteUrl) ? "http://localhost:4200" : siteUrl;
    }

    public boolean habilitado() {
        return !vazio(secretKey) && !vazio(webhookSecret) && !vazio(priceMensal) && !vazio(priceAnual);
    }

    public boolean familiaHabilitado() {
        return habilitado() && !vazio(priceFamiliaMensal) && !vazio(priceFamiliaAnual);
    }

    public boolean casalHabilitado() {
        return habilitado() && !vazio(priceCasalMensal) && !vazio(priceCasalAnual);
    }

    /** O preco que o checkout cobra para cada plano e periodo. */
    public String precoDe(com.piggu.identity.billing.Periodo periodo) {
        return switch (periodo) {
            case MENSAL -> priceMensal;
            case ANUAL -> priceAnual;
            case FAMILIA_MENSAL -> priceFamiliaMensal;
            case FAMILIA_ANUAL -> priceFamiliaAnual;
            case CASAL_MENSAL -> priceCasalMensal;
            case CASAL_ANUAL -> priceCasalAnual;
        };
    }

    /** Que plano o preco cobrado e; um preco desconhecido conta como individual. */
    public com.piggu.identity.billing.TipoDePlano tipoDoPreco(String preco) {
        if (ehPrecoDoFamilia(preco)) {
            return com.piggu.identity.billing.TipoDePlano.FAMILIA;
        }
        boolean casal = preco != null && !preco.isBlank()
                && (preco.equals(priceCasalMensal) || preco.equals(priceCasalAnual));
        return casal ? com.piggu.identity.billing.TipoDePlano.CASAL : com.piggu.identity.billing.TipoDePlano.INDIVIDUAL;
    }

    /** O preco veio de uma assinatura do plano familia. */
    public boolean ehPrecoDoFamilia(String preco) {
        return preco != null && !preco.isBlank()
                && (preco.equals(priceFamiliaMensal) || preco.equals(priceFamiliaAnual));
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
