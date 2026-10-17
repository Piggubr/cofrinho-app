package com.piggu.banking.api.dto;

/**
 * @param accessToken token de 30 minutos que abre o widget Pluggy Connect
 * @param sandbox     se o widget deve listar os bancos de teste da Pluggy
 */
public record ConnectTokenResponse(String accessToken, boolean sandbox) {
}
