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
 */
public record EventoDeAssinatura(UUID usuarioId, String clienteNoProvedor, Instant premiumAte, Instant momento) {
}
