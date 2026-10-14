package com.piggu.common;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Em producao ({@code PIGGU_AMBIENTE=producao}), recusa subir com a senha padrao do
 * banco ou com uma senha curta.
 *
 * <p>Protecao que depende de alguem lembrar de trocar uma configuracao falha aberta:
 * a senha "piggu" do Compose funcionaria em producao sem nenhum aviso. Aqui a falha e
 * na subida, com a mensagem do que fazer.</p>
 */
public class ProtecaoDeProducao implements InitializingBean {

    static final int TAMANHO_MINIMO = 16;
    private static final Set<String> SENHAS_CONHECIDAS = Set.of("piggu", "postgres", "password", "senha", "admin", "123456");

    private final Environment ambiente;

    public ProtecaoDeProducao(Environment ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public void afterPropertiesSet() {
        // Endereco do site (volta da Stripe) e origens do CORS: em producao, so HTTPS.
        for (String variavel : List.of("SITE_URL", "CORS_ORIGINS")) {
            String valor = ambiente.getProperty(variavel, "");
            if (valor.contains("http://")) {
                throw new IllegalStateException("Em producao " + variavel + " precisa usar https:// (veio: " + valor + ").");
            }
        }
        // Atras do Caddy e do gateway: com menos de 2, o limite por IP conta o IP do Caddy
        // e vira um balde so para todo mundo (20 logins por minuto no site inteiro).
        int proxies = Binder.get(ambiente).bind("piggu.limite.proxies-na-frente", Integer.class).orElse(1);
        if (proxies < 2) {
            throw new IllegalStateException("Em producao PIGGU_LIMITE_PROXIESNAFRENTE precisa ser 2 (Caddy + gateway),"
                    + " senao o limite por IP vale para todos juntos (veio: " + proxies + ").");
        }
        if (ambiente.getProperty("spring.datasource.url") == null) {
            return;
        }
        String senha = ambiente.getProperty("spring.datasource.password", "");
        if (senha.length() < TAMANHO_MINIMO || SENHAS_CONHECIDAS.contains(senha.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Em producao a senha do banco (DB_PASSWORD) precisa ter pelo menos "
                    + TAMANHO_MINIMO + " caracteres e nao pode ser a padrao. Gere uma com: openssl rand -base64 24");
        }
    }
}
