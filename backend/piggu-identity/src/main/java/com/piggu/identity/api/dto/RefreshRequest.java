package com.piggu.identity.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo opcional de renovar e sair. O site manda vazio e usa o cookie; o app nativo, que nao
 * tem o cookie (D-058), manda o refresh aqui.
 *
 * @param refreshToken o refresh que o app guardou no Keychain ou no Keystore
 */
public record RefreshRequest(@Size(max = 200) String refreshToken) {
}
