package com.piggu.identity.avisos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Canais dos avisos fora do app. Cada canal so liga com as proprias credenciais; sem
 * elas o lembrete diario roda, registra no log e nao marca nada como enviado.
 *
 * @param email e-mail por API HTTP (formato da Resend: POST com from, to, subject, text)
 * @param apns  push no iPhone pela Apple (chave .p8 de autenticacao por token)
 * @param fcm   push no Android pelo Firebase Cloud Messaging (conta de servico do Google)
 */
@ConfigurationProperties(prefix = "piggu.avisos")
public record AvisosProperties(Email email, Apns apns, Fcm fcm) {

    public AvisosProperties {
        email = email == null ? new Email(null, null, null) : email;
        apns = apns == null ? new Apns(null, null, null, null, false) : apns;
        fcm = fcm == null ? new Fcm(null) : fcm;
    }

    public record Email(String apiUrl, String apiKey, String remetente) {
        public boolean ligado() {
            return preenchido(apiUrl) && preenchido(apiKey) && preenchido(remetente);
        }
    }

    /**
     * @param chave   conteudo do arquivo AuthKey_XXXX.p8 (PEM PKCS#8)
     * @param sandbox true manda para o ambiente de desenvolvimento da Apple (app do Xcode)
     */
    public record Apns(String teamId, String keyId, String chave, String bundleId, boolean sandbox) {
        public boolean ligado() {
            return preenchido(teamId) && preenchido(keyId) && preenchido(chave) && preenchido(bundleId);
        }
    }

    /** @param contaDeServico conteudo do JSON da conta de servico do projeto do Firebase */
    public record Fcm(String contaDeServico) {
        public boolean ligado() {
            return preenchido(contaDeServico);
        }
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
