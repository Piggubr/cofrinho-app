package com.piggu.identity.api.dto;

/**
 * Par de tokens devolvido no login e na renovacao.
 *
 * @param accessToken  JWT curto, enviado no cabecalho Authorization de cada chamada
 * @param refreshToken token opaco e longo, usado apenas para renovar o acesso
 * @param expiresIn    validade do access token, em segundos
 * @param usuario      perfil de quem entrou, para o front nao precisar de outra chamada
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserResponse usuario) {
}
