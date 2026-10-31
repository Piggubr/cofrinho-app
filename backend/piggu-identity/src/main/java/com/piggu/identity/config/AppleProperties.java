package com.piggu.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracao do Entrar com Apple.
 *
 * @param clientIds audiencias aceitas: o Bundle ID do app ({@code br.com.piggu.app}) e, se um
 *                  dia o site tiver o botao, o Services ID. Vazio desliga a rota (501).
 * @param jwkSetUri chaves publicas da Apple usadas para conferir a assinatura do token
 * @param issuer    emissor aceito
 * @param teamId    Team ID da conta de desenvolvedor, para assinar o client secret
 * @param keyId     ID da chave .p8 com o Entrar com Apple ligado
 * @param chave     conteudo do AuthKey_XXXX.p8 (PEM). Sem as tres, o servico nao guarda o token
 *                  da Apple nem revoga ao excluir a conta.
 */
@ConfigurationProperties(prefix = "piggu.apple")
public record AppleProperties(
        List<String> clientIds,
        String jwkSetUri,
        String issuer,
        String teamId,
        String keyId,
        String chave
) {

    public AppleProperties {
        clientIds = clientIds == null ? List.of() : clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        jwkSetUri = (jwkSetUri == null || jwkSetUri.isBlank()) ? "https://appleid.apple.com/auth/keys" : jwkSetUri;
        issuer = (issuer == null || issuer.isBlank()) ? "https://appleid.apple.com" : issuer;
    }

    public boolean ligado() {
        return !clientIds.isEmpty();
    }

    public boolean revogacaoLigada() {
        return ligado() && teamId != null && !teamId.isBlank() && keyId != null && !keyId.isBlank()
                && chave != null && !chave.isBlank();
    }
}
