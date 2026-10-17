package com.piggu.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Em producao, senha padrao ou curta do banco derruba a subida. */
class ProtecaoDeProducaoTest {

    @Test
    @DisplayName("senha padrao, curta ou vazia e recusada; senha forte passa")
    void senhaDoBanco() {
        for (String fraca : new String[]{"piggu", "", "curta-demais", "POSTGRES"}) {
            assertThatThrownBy(() -> protecao(fraca).afterPropertiesSet())
                    .as(fraca).hasMessageContaining("DB_PASSWORD");
        }
        assertThatNoException().isThrownBy(() -> protecao("kq3vV9x2Lz8pR1tY7wB4nM6c").afterPropertiesSet());
    }

    @Test
    @DisplayName("servico sem banco (gateway) nao e conferido")
    void semBanco() {
        assertThatNoException().isThrownBy(() -> new ProtecaoDeProducao(producao()).afterPropertiesSet());
    }

    @Test
    @DisplayName("endereco do site ou origem do CORS em http derruba a subida em producao")
    void soHttps() {
        assertThatThrownBy(() -> new ProtecaoDeProducao(producao()
                .withProperty("CORS_ORIGINS", "https://piggu.app,http://piggu.app")).afterPropertiesSet())
                .hasMessageContaining("CORS_ORIGINS");
        assertThatThrownBy(() -> new ProtecaoDeProducao(producao()
                .withProperty("SITE_URL", "http://piggu.app")).afterPropertiesSet())
                .hasMessageContaining("SITE_URL");
        assertThatNoException().isThrownBy(() -> new ProtecaoDeProducao(producao()
                .withProperty("SITE_URL", "https://piggu.app")).afterPropertiesSet());
    }

    @Test
    @DisplayName("limite por IP contando so um proxy derruba a subida em producao")
    void proxiesNaFrente() {
        assertThatThrownBy(() -> new ProtecaoDeProducao(new MockEnvironment()).afterPropertiesSet())
                .hasMessageContaining("PROXIESNAFRENTE");
        assertThatThrownBy(() -> new ProtecaoDeProducao(new MockEnvironment()
                .withProperty("piggu.limite.proxies-na-frente", "1")).afterPropertiesSet())
                .hasMessageContaining("PROXIESNAFRENTE");
    }

    private static MockEnvironment producao() {
        return new MockEnvironment().withProperty("piggu.limite.proxies-na-frente", "2");
    }

    private static ProtecaoDeProducao protecao(String senha) {
        return new ProtecaoDeProducao(producao()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db/x")
                .withProperty("spring.datasource.password", senha));
    }
}
