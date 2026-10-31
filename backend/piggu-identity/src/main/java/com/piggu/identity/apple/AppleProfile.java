package com.piggu.identity.apple;

/**
 * Dados extraidos do identity token da Apple, ja validados.
 *
 * @param subject identificador estavel da conta Apple neste app
 * @param email   e-mail normalizado em minusculas; pode ser o de retransmissao da Apple
 *                (privaterelay.appleid.com) quando a pessoa escolhe ocultar o e-mail
 * @param clienteId a audiencia do token (o Bundle ID), para trocar o codigo e revogar
 */
public record AppleProfile(String subject, String email, String clienteId) {
}
