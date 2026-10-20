package com.piggu.banking.api;

import com.piggu.banking.domain.BankAccount;
import com.piggu.banking.domain.BankAccountRepository;
import com.piggu.banking.domain.BankConnection;
import com.piggu.banking.domain.BankConnectionRepository;
import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Banco conectado e da pessoa: sai com ela, aqui e na Pluggy, mesmo que a familia fique.
 */
@AutoConfigureMockMvc
class MeusDadosTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluggyClient pluggy;

    @Autowired
    private BankConnectionRepository conexoes;

    @Autowired
    private BankAccountRepository contas;

    @Autowired
    private TransactionTemplate transacao;

    private final UUID familia = UUID.randomUUID();
    private final String quemSai = "sai-" + familia + "@piggu.test";
    private final String quemFica = "fica-" + familia + "@piggu.test";

    @Test
    @DisplayName("quem sai leva o banco que conectou; o banco de quem fica nao e tocado")
    void pessoaSaiLevaOBanco() throws Exception {
        conectar("item-sai-" + familia, TokensDeTeste.idDe(quemSai));
        conectar("item-fica-" + familia, TokensDeTeste.idDe(quemFica));

        mockMvc.perform(delete("/api/meus-dados")
                        .with(TokensDeTeste.exclusao(familia, quemSai, PigguRole.MEMBRO, "PESSOA")))
                .andExpect(status().isNoContent());

        verify(pluggy).apagarItem("item-sai-" + familia);
        verify(pluggy, never()).apagarItem("item-fica-" + familia);
        assertThat(conexoesDe(TokensDeTeste.idDe(quemSai))).isZero();
        assertThat(conexoesDe(TokensDeTeste.idDe(quemFica))).isEqualTo(1);
    }

    @Test
    @DisplayName("a exportacao do titular leva so as contas do banco que ele conectou, como no app")
    void exportacaoLevaSoAsProprias() throws Exception {
        String titular = "titular-" + familia + "@piggu.test";
        BankConnection doTitular = conectar("item-titular-" + familia, TokensDeTeste.idDe(titular));
        BankConnection doMembro = conectar("item-membro-" + familia, TokensDeTeste.idDe(quemFica));
        FamiliaAtual.como(familia, () -> transacao.executeWithoutResult(status -> {
            contas.save(conta(doTitular, "conta-titular-" + familia));
            contas.save(conta(doMembro, "conta-membro-" + familia));
        }));

        mockMvc.perform(get("/api/meus-dados").with(TokensDeTeste.da(familia, titular, PigguRole.TITULAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank_accounts.length()").value(1))
                .andExpect(jsonPath("$.bank_accounts[0].pluggy_account_id").value("conta-titular-" + familia));

        mockMvc.perform(get("/api/meus-dados").with(TokensDeTeste.da(familia, quemFica, PigguRole.MEMBRO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank_accounts.length()").value(1))
                .andExpect(jsonPath("$.bank_accounts[0].pluggy_account_id").value("conta-membro-" + familia));
    }

    @Test
    @DisplayName("se a Pluggy falha, nada e apagado aqui")
    void pluggyFalha() throws Exception {
        conectar("item-falha-" + familia, TokensDeTeste.idDe(quemSai));
        willThrow(new com.piggu.common.error.UpstreamException("fora")).given(pluggy).apagarItem(anyString());

        mockMvc.perform(delete("/api/meus-dados")
                        .with(TokensDeTeste.exclusao(familia, quemSai, PigguRole.TITULAR, "FAMILIA")))
                .andExpect(status().is5xxServerError());

        assertThat(conexoesDe(TokensDeTeste.idDe(quemSai))).isEqualTo(1);
    }

    private static BankAccount conta(BankConnection conexao, String id) {
        BankAccount conta = new BankAccount(conexao, id);
        conta.atualizar("Conta corrente", "BANK", "12345-6", java.math.BigDecimal.TEN, "BRL");
        return conta;
    }

    private BankConnection conectar(String item, UUID email) {
        return FamiliaAtual.como(familia, () -> transacao.execute(status ->
                conexoes.save(new BankConnection(item, email))));
    }

    private int conexoesDe(UUID email) {
        return FamiliaAtual.como(familia, () -> transacao.execute(status -> conexoes.findByUserId(email).size()));
    }
}
