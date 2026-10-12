package com.piggu.finance.api;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.Plano;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.integration.LeitorDeRecibos;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Retirar a autorizacao da foto da nota (LGPD art. 8, par. 5): vale so para quem pediu. */
@AutoConfigureMockMvc
class ConsentimentoRetiradoTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Consentimentos consentimentos;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a pessoa retira a dela; a de outra pessoa da familia continua valendo")
    void retiraSoADaPessoa() throws Exception {
        UUID familia = UUID.randomUUID();
        String email = "retira-" + familia + "@piggu.test";
        String outroEmail = "fica-" + familia + "@piggu.test";
        CurrentUser quemRetira = pessoa(familia, email);
        CurrentUser quemFica = pessoa(familia, outroEmail);
        autorizar(quemRetira);
        autorizar(quemFica);

        mockMvc.perform(delete("/api/receipts/consent").with(TokensDeTeste.da(familia, email, PigguRole.PARCEIRO)))
                .andExpect(status().isNoContent());

        assertThat(consentimentos.jaAutorizou(quemRetira, LeitorDeRecibos.FINALIDADE)).isFalse();
        assertThat(consentimentos.jaAutorizou(quemFica, LeitorDeRecibos.FINALIDADE)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consents WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, quemRetira.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("membro nao usa a leitura de nota, entao nao tem o que retirar")
    void membroNaoEntra() throws Exception {
        mockMvc.perform(delete("/api/receipts/consent").with(TokensDeTeste.membro())).andExpect(status().isForbidden());
    }

    private static CurrentUser pessoa(UUID familia, String email) {
        return new CurrentUser(TokensDeTeste.idDe(email), email, PigguRole.PARCEIRO, Plano.PREMIUM, familia);
    }

    private void autorizar(CurrentUser quem) {
        consentimentos.exigir(quem, LeitorDeRecibos.FINALIDADE, "teste", true, Consentimentos.VERSAO_DO_AVISO);
    }
}
