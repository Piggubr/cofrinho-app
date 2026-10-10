package com.piggu.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.converter.RsaKeyConverters;

import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Subida sem chave propria.
 *
 * <p>A chave de desenvolvimento ja esteve no repositorio e e publica. Se algum dia o
 * application.yml voltar a ter um valor padrao para as chaves, um deploy sem
 * JWT_PRIVATE_KEY subiria saudavel assinando tokens que qualquer pessoa consegue forjar.
 * Este teste e o que quebra nesse caso.</p>
 */
class TokenConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TokenConfig.class);

    @Test
    @DisplayName("sem perfil e sem JWT_PRIVATE_KEY o servico recusa subir")
    void recusaSubirSemChave() {
        contexto.run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("JWT_PRIVATE_KEY"));
    }

    @Test
    @DisplayName("perfil dev sem as chaves geradas tambem recusa subir")
    void devSemChavesGeradasRecusa() {
        contexto.withPropertyValues("spring.profiles.active=dev")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("gerar-chaves-dev.sh"));
    }

    @Test
    @DisplayName("com as chaves configuradas o servico sobe")
    void sobeComChaves() {
        contexto.withPropertyValues(
                        "piggu.jwt.private-key=classpath:keys/piggu-test-private.pem",
                        "piggu.jwt.public-key=classpath:keys/piggu-test-public.pem")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(JwtEncoder.class));
    }

    @Test
    @DisplayName("a chave que ja esteve no Git e recusada em qualquer ambiente")
    void chaveVazadaRecusada() throws Exception {
        RSAPublicKey vazada = ler("/keys/chave-vazada-publica.pem");
        assertThatThrownBy(() -> TokenConfig.conferirChave(vazada, "env", "dev"))
                .hasMessageContaining("esteve no Git");
    }

    @Test
    @DisplayName("em producao a chave de desenvolvimento nao serve; fora dela, serve")
    void producaoSemChaveDeDev() throws Exception {
        RSAPublicKey qualquer = ler("/keys/piggu-test-public.pem");
        String deDev = "file:/app/keys/piggu-dev-private.pem";

        assertThatThrownBy(() -> TokenConfig.conferirChave(qualquer, deDev, "producao"))
                .hasMessageContaining("JWT_PRIVATE_KEY");
        TokenConfig.conferirChave(qualquer, deDev, "dev");
    }

    private RSAPublicKey ler(String caminho) throws Exception {
        try (var fluxo = getClass().getResourceAsStream(caminho)) {
            return RsaKeyConverters.x509().convert(fluxo);
        }
    }
}
