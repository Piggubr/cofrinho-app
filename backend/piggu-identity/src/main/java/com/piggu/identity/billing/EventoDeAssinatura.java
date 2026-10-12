package com.piggu.identity.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * O que um provedor de pagamento disse sobre a assinatura de alguem, ja traduzido.
 *
 * @param usuarioId         dono da assinatura no Piggu
 * @param clienteNoProvedor id do cliente no provedor, para abrir o portal depois
 * @param premiumAte        ate quando vale; nulo ou no passado encerra o Premium
 * @param momento           quando o provedor gerou o evento, para ignorar os atrasados
 * @param inicio            quando a assinatura comecou, para o prazo de arrependimento; pode ser nulo
 * @param emTeste           a assinatura esta no teste gratis
 * @param familia           plano familia (a casa toda); falso e o individual (so quem assinou)
 */
public record EventoDeAssinatura(UUID usuarioId, String clienteNoProvedor, Instant premiumAte, Instant momento,
                                 Instant inicio, boolean emTeste, boolean familia) {

    public EventoDeAssinatura(UUID usuarioId, String clienteNoProvedor, Instant premiumAte, Instant momento,
                              Instant inicio, boolean emTeste) {
        this(usuarioId, clienteNoProvedor, premiumAte, momento, inicio, emTeste, false);
    }

    public EventoDeAssinatura(UUID usuarioId, String clienteNoProvedor, Instant premiumAte, Instant momento) {
        this(usuarioId, clienteNoProvedor, premiumAte, momento, null, false, false);
    }
}
