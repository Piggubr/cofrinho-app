package com.piggu.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * O que o login e a renovacao devolvem.
 *
 * <p>O refresh nunca vai no corpo: o controller o poe num cookie HttpOnly, que o
 * JavaScript da pagina nao le. Assim um XSS nao leva uma sessao de 30 dias.</p>
 *
 * @param accessToken  JWT curto, enviado no cabecalho Authorization de cada chamada; o
 *                     front guarda so em memoria
 * @param refreshToken token opaco e longo, so para o cookie
 * @param expiresIn    validade do access token, em segundos
 * @param usuario      perfil de quem entrou, para o front nao precisar de outra chamada
 * @param lembrar      cookie persistente (continuar conectado) ou so desta sessao do navegador
 */
public record TokenResponse(String accessToken,
                            @JsonIgnore String refreshToken,
                            long expiresIn,
                            UserResponse usuario,
                            @JsonIgnore boolean lembrar) {
}
