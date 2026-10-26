package com.piggu.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O que o login e a renovacao devolvem.
 *
 * <p>No site o refresh nunca vai no corpo: o controller o poe num cookie HttpOnly, que o
 * JavaScript da pagina nao le, e tira do corpo com {@link #semRefresh()}. Assim um XSS nao leva
 * uma sessao de 30 dias. So o app nativo, que pede (D-058), recebe o refresh no corpo.</p>
 *
 * @param accessToken  JWT curto, enviado no cabecalho Authorization de cada chamada; o
 *                     front guarda so em memoria
 * @param refreshToken token opaco e longo; no corpo so para o app nativo, que o guarda no
 *                     Keychain ou no Keystore
 * @param expiresIn    validade do access token, em segundos
 * @param usuario      perfil de quem entrou, para o front nao precisar de outra chamada
 * @param lembrar      cookie persistente (continuar conectado) ou so desta sessao do navegador
 */
public record TokenResponse(String accessToken,
                            @Schema(description = "Só para o app nativo, quando pede com refreshNoCorpo ou renova "
                                    + "mandando o refresh no corpo. No site fica de fora: o refresh vai no cookie.")
                            @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken,
                            long expiresIn,
                            UserResponse usuario,
                            @JsonIgnore boolean lembrar) {

    /** O mesmo par sem o refresh, para o corpo da resposta do site. */
    public TokenResponse semRefresh() {
        return new TokenResponse(accessToken, null, expiresIn, usuario, lembrar);
    }
}
