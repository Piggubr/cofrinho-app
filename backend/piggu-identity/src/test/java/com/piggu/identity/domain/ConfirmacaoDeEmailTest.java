package com.piggu.identity.domain;

import com.piggu.common.security.PigguRole;
import com.piggu.identity.avisos.CanalDeEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ConfirmacaoDeEmailTest {

    private final CanalDeEmail email = mock(CanalDeEmail.class);
    private final ConfirmacaoDeEmail confirmacao = new ConfirmacaoDeEmail(mock(UserAccountRepository.class),
            mock(TokenService.class), email, "https://piggu.test", "producao");

    @Test
    @DisplayName("cadastro repetido em loop manda um aviso so para o dono do e-mail")
    void avisoDeCadastroRepetidoUmaVez() {
        given(email.ligado()).willReturn(true);
        UserAccount dona = new UserAccount("dona@exemplo.test", PigguRole.TITULAR, UUID.randomUUID());
        UserAccount outra = new UserAccount("outra@exemplo.test", PigguRole.TITULAR, UUID.randomUUID());

        for (int i = 0; i < 20; i++) {
            confirmacao.avisarCadastroRepetido(dona);
        }
        confirmacao.avisarCadastroRepetido(outra);

        verify(email, timeout(5000).times(1)).enviar(eq("dona@exemplo.test"), anyString(), anyString());
        verify(email, timeout(5000).times(1)).enviar(eq("outra@exemplo.test"), anyString(), anyString());
    }
}
