package com.piggu.identity.google;

import com.piggu.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

/** Login com Google no app: o codigo de volta so vale uma vez, por pouco tempo e com o verificador. */
class LoginPeloNavegadorTest {

    private static final String VERIFICADOR = "verificador-sorteado-pelo-app-com-43-letras-ou-mais";
    private static final String DESAFIO = LoginPeloNavegador.desafioDe(VERIFICADOR);

    private final GoogleIdTokenVerifier google = mock(GoogleIdTokenVerifier.class);
    private RelogioDeTeste relogio;
    private LoginPeloNavegador navegador;

    @BeforeEach
    void preparar() {
        relogio = new RelogioDeTeste();
        navegador = new LoginPeloNavegador(google, relogio);
        given(google.verificar(anyString(), anyString())).willReturn(
                new GoogleProfile("sub-1", "ana@gmail.com", "Ana", "Ana", null));
    }

    @Test
    @DisplayName("o desafio e o S256 do PKCE (exemplo da RFC 7636)")
    void desafioS256() {
        assertThat(LoginPeloNavegador.desafioDe("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    @DisplayName("com o verificador certo o app recebe o ID token, e o codigo nao vale de novo")
    void trocaUmaVez() {
        String codigo = navegador.guardar("id-token", DESAFIO);

        assertThat(navegador.trocar(codigo, VERIFICADOR)).isEqualTo("id-token");
        assertThatThrownBy(() -> navegador.trocar(codigo, VERIFICADOR)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("verificador errado queima o codigo: quem capturou o link nao entra")
    void verificadorErrado() {
        String codigo = navegador.guardar("id-token", DESAFIO);

        assertThatThrownBy(() -> navegador.trocar(codigo, "outro")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> navegador.trocar(codigo, VERIFICADOR)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("depois de 5 minutos o codigo venceu")
    void vence() {
        String codigo = navegador.guardar("id-token", DESAFIO);
        relogio.passar(LoginPeloNavegador.VALIDADE);

        assertThatThrownBy(() -> navegador.trocar(codigo, VERIFICADOR)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("codigo inventado ou vazio e recusado")
    void codigoInventado() {
        assertThatThrownBy(() -> navegador.trocar("inventado", VERIFICADOR)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> navegador.trocar(null, VERIFICADOR)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("token que o Google nao confirma (ou com outro nonce) nem fica guardado")
    void tokenRecusado() {
        willThrow(new UnauthorizedException("Login Google invalido para este site."))
                .given(google).verificar("falso", DESAFIO);

        assertThatThrownBy(() -> navegador.guardar("falso", DESAFIO)).isInstanceOf(UnauthorizedException.class);
    }

    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-10-24T12:00:00Z");

        void passar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }
}
