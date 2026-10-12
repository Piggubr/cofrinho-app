package com.piggu.identity.api.dto;

import com.piggu.common.security.Plano;
import com.piggu.identity.billing.AssinaturaService;
import com.piggu.identity.billing.Periodo;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.UserAccount;

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
 * @param planoFamilia         a assinatura vigente da casa e o plano familia
 * @param voceAssinou          foi esta conta que assinou (so ela gerencia e pede reembolso)
 * @param familiaDisponivel    o plano familia tem preco configurado
 * @param siteFamilia          precos do plano familia no site
 * @param appFamilia           precos do plano familia no app (+15%)
 * @param pessoasNoFamilia     quantas pessoas o plano familia cobre
 * @param casaTemAssinatura    ha assinatura vigente na casa (talvez de outra pessoa, no individual)
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
        List<String> premium,
        boolean planoFamilia,
        boolean voceAssinou,
        boolean familiaDisponivel,
        Precos siteFamilia,
        Precos appFamilia,
        int pessoasNoFamilia,
        boolean casaTemAssinatura
) {

    /** Em reais. */
    public record Precos(String mensal, String anual) {
    }

    public static final Precos PRECOS_SITE = new Precos("19,90", "199,00");
    public static final Precos PRECOS_APP = new Precos("22,89", "228,85");
    /** Plano familia, ate 5 pessoas (Eduardo, 2026-10-11). App: +15%, arredondado para baixo. */
    public static final Precos PRECOS_FAMILIA_SITE = new Precos("39,99", "399,90");
    public static final Precos PRECOS_FAMILIA_APP = new Precos("45,99", "459,89");

    private static final List<String> GRATUITO = List.of(
            "Gastos, receitas, categorias, meta do mês e calendário",
            "Leitura de nota fiscal pela foto: 10 por mês por família",
            "Relatório do mês, comparação com o mês anterior e projeção do fim do mês",
            "Cofrinho com depósitos da família e um cofrinho por objetivo",
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
            "Leitura de nota fiscal pela foto sem limite",
            "Mural de fotos",
            "Vários cofrinhos por objetivo, cada um com prazo e quanto guardar por mês",
            "Assistente de IA que responde sobre os seus gastos"
    );

    /** @param openFinance falso esconde o Open Finance da lista (recurso desligado por enquanto) */
    public static PlanoResponse de(UserAccount conta, Household familia, boolean assinaturaDisponivel,
                                   boolean familiaDisponivel, int diasDeTeste, boolean openFinance) {
        Plano vigente = familia.planoPara(conta.getId());
        boolean voceAssinou = familia.planoVigente() == Plano.PREMIUM && conta.getId().equals(familia.getSubscriberId());
        Instant reembolsoAte = voceAssinou && "WEB".equals(familia.getPlanSource())
                && familia.getPremiumSince() != null
                ? familia.getPremiumSince().plus(AssinaturaService.PRAZO_DE_ARREPENDIMENTO) : null;
        return new PlanoResponse(vigente, vigente == Plano.PREMIUM ? familia.getPremiumUntil() : null,
                familia.getPlanSource(),
                reembolsoAte != null && reembolsoAte.isAfter(Instant.now()) ? reembolsoAte : null,
                diasDeTeste, assinaturaDisponivel, PRECOS_SITE, PRECOS_APP, GRATUITO,
                openFinance ? PREMIUM : PREMIUM.stream().filter(item -> !item.contains("Open Finance")).toList(),
                familia.planoVigente() == Plano.PREMIUM && familia.isFamilia(), voceAssinou,
                familiaDisponivel, PRECOS_FAMILIA_SITE, PRECOS_FAMILIA_APP, Periodo.PESSOAS_NO_FAMILIA,
                familia.planoVigente() == Plano.PREMIUM);
    }
}
