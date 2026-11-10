package com.piggu.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CodigoDoAdminTest {

    // "12345678901234567890" em base32: o segredo SHA-1 da RFC 6238.
    private static final String SEGREDO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("gera os codigos da RFC 6238 (os 6 ultimos digitos)")
    void vetoresDaRfc() {
        assertThat(CodigoDoAdmin.base32(SEGREDO)).isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertThat(CodigoDoAdmin.gerar(SEGREDO, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(CodigoDoAdmin.gerar(SEGREDO, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(CodigoDoAdmin.gerar(SEGREDO, Instant.ofEpochSecond(2000000000))).isEqualTo("279037");
    }

    @Test
    @DisplayName("aceita o passo vizinho, mas nunca o mesmo codigo duas vezes")
    void semRepeticao() {
        CodigoDoAdmin codigo = new CodigoDoAdmin(SEGREDO);
        Instant agora = Instant.ofEpochSecond(1111111109);
        String anterior = CodigoDoAdmin.gerar(SEGREDO, agora.minusSeconds(30));

        assertThat(codigo.confere(anterior, agora)).isTrue();
        assertThat(codigo.confere(anterior, agora)).isFalse();
        assertThat(codigo.confere(CodigoDoAdmin.gerar(SEGREDO, agora), agora)).isTrue();
        assertThat(codigo.confere(CodigoDoAdmin.gerar(SEGREDO, agora.minusSeconds(90)), agora)).isFalse();
    }

    @Test
    @DisplayName("sem segredo recusa sempre; 5 erros bloqueiam por 15 minutos")
    void recusaEBloqueia() {
        Instant agora = Instant.ofEpochSecond(2000000000);
        assertThat(new CodigoDoAdmin("").confere(CodigoDoAdmin.gerar(SEGREDO, agora), agora)).isFalse();

        CodigoDoAdmin codigo = new CodigoDoAdmin(SEGREDO);
        for (int i = 0; i < 5; i++) {
            assertThat(codigo.confere("000000", agora)).isFalse();
        }
        assertThat(codigo.confere(CodigoDoAdmin.gerar(SEGREDO, agora), agora)).isFalse();
        Instant depois = agora.plusSeconds(15 * 60);
        assertThat(codigo.confere(CodigoDoAdmin.gerar(SEGREDO, depois), depois)).isTrue();
    }
}
