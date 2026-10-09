package com.piggu.identity.api.dto;

import com.piggu.common.security.Plano;
import com.piggu.identity.billing.AssinaturaService;
import com.piggu.identity.domain.Household;

import java.time.Instant;
import java.util.List;

/**
 * O plano da conta e o que cada plano inclui, para a tela de planos.
 *
 * @param plano                o que vale agora (um Premium vencido ja aparece GRATUITO)
 * @param premiumAte           ate quando o Premium vale; nulo no gratuito
 * @param origem               onde foi assinado: WEB, APP_STORE ou PLAY_STORE
 * @param assinaturaDisponivel falso enquanto o provedor de pagamento do site nao estiver configurado
 * @param site                 precos no site (Stripe)
 * @param app                  precos dentro do app iOS/Android: +15% pela taxa das lojas
 * @param reembolsoAte         ate quando da para desistir com dinheiro de volta (7 dias, CDC
 *                             art. 49); nulo fora do prazo ou quando nao foi assinado pelo site
 * @param diasDeTeste          dias gratis que esta conta ainda tem ao assinar; 0 se ja usou
 */
public record PlanoResponse(
        Plano plano,
        Instant premiumAte,
        String origem,
        Instant reembolsoAte,
        int diasDeTeste,
        boolean assinaturaDisponivel,
        Precos site,
        Precos app,
        List<String> gratuito,
        List<String> premium
) {

    /** Em reais. */
    public record Precos(String mensal, String anual) {
    }

    public static final Precos PRECOS_SITE = new Precos("19,90", "199,00");
    public static final Precos PRECOS_APP = new Precos("22,89", "228,85");

    private static final List<String> GRATUITO = List.of(
            "Gastos, receitas, categorias, meta do mês e calendário",
            "Relatório do mês, comparação com o mês anterior e projeção do fim do mês",
            "Cofrinho com depósitos da família",
            "Lista de compras, lugares e filmes",
            "Prêmios e Fofocoins",
            "Moeda e cotação à sua escolha",
            "Baixar e apagar seus dados quando quiser",
            "Tudo o que você guardou continua visível se o Premium acabar"
    );

    private static final List<String> PREMIUM = List.of(
            "Tudo do gratuito",
            "Orçamento por categoria, com alerta em 80% e 100%",
            "Relatório do ano com gráficos",
            "Contas bancárias pelo Open Finance, com saldo atualizado",
            "Leitura da nota fiscal pela foto",
            "Mural de fotos"
    );

    public static PlanoResponse de(Household familia, boolean assinaturaDisponivel, int diasDeTeste) {
        Plano vigente = familia.planoVigente();
        Instant reembolsoAte = vigente == Plano.PREMIUM && "WEB".equals(familia.getPlanSource())
                && familia.getPremiumSince() != null
                ? familia.getPremiumSince().plus(AssinaturaService.PRAZO_DE_ARREPENDIMENTO) : null;
        return new PlanoResponse(vigente, vigente == Plano.PREMIUM ? familia.getPremiumUntil() : null,
                familia.getPlanSource(),
                reembolsoAte != null && reembolsoAte.isAfter(Instant.now()) ? reembolsoAte : null,
                diasDeTeste, assinaturaDisponivel, PRECOS_SITE, PRECOS_APP, GRATUITO, PREMIUM);
    }
}
