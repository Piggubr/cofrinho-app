package com.piggu.banking.api;

import com.piggu.banking.domain.BankingService;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Com o Open Finance desligado (padrao), o card some e nenhuma conexao nova abre. */
class OpenFinanceDesligadoTest {

    private final BankingService servico = mock(BankingService.class);
    private final Consentimentos consentimentos = mock(Consentimentos.class);
    private final BankingController controller = new BankingController(servico, consentimentos, false);
    private final CurrentUser premium =
            new CurrentUser(UUID.randomUUID(), "t@piggu.test", PigguRole.TITULAR, Plano.PREMIUM, UUID.randomUUID());

    @Test
    @DisplayName("status diz desligado mesmo com a Pluggy configurada")
    void statusDesligado() {
        when(servico.habilitado()).thenReturn(true);

        assertThat(controller.status()).containsEntry("habilitado", false);
    }

    @Test
    @DisplayName("conectar banco e recusado antes de pedir consentimento ou falar com a Pluggy")
    void conectarRecusado() {
        assertThatThrownBy(() -> controller.connectToken(null, premium))
                .isInstanceOf(BusinessException.class).hasMessageContaining("desligado");
        verifyNoInteractions(consentimentos);
    }
}
