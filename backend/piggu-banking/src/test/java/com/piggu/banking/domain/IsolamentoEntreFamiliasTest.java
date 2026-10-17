package com.piggu.banking.domain;

import com.piggu.common.security.FamiliaAtual;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Banco conectado por alguem de uma familia nao aparece para outra, nem por id.
 *
 * <p>Direto nos repositorios: as rotas dependem da Pluggy, e o que importa provar e o
 * filtro por familia que toda consulta recebe.</p>
 */
class IsolamentoEntreFamiliasTest extends PostgresIntegrationTest {

    @Autowired
    private BankConnectionRepository conexoes;

    @Autowired
    private BankAccountRepository contas;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    @DisplayName("conexao e contas so existem dentro da familia que conectou")
    void bancosPorFamilia() {
        UUID casaA = UUID.randomUUID();
        UUID casaB = UUID.randomUUID();
        UUID email = UUID.nameUUIDFromBytes(("mesma-pessoa-" + casaA + "@piggu.test").getBytes());

        UUID conexaoId = FamiliaAtual.como(casaA, () -> transacao.execute(status -> {
            BankConnection conexao = conexoes.save(new BankConnection("item-" + casaA, email));
            BankAccount conta = new BankAccount(conexao, "conta-" + casaA);
            conta.atualizar("Corrente", "BANK", "123", new BigDecimal("10.00"), "BRL");
            contas.save(conta);
            return conexao.getId();
        }));

        FamiliaAtual.como(casaB, () -> transacao.executeWithoutResult(status -> {
            assertThat(conexoes.findById(conexaoId)).isEmpty();
            assertThat(conexoes.findByUserId(email)).isEmpty();
            assertThat(contas.listarDoUsuario(email)).isEmpty();
        }));
        FamiliaAtual.como(casaA, () -> transacao.executeWithoutResult(status ->
                assertThat(contas.listarDoUsuario(email)).hasSize(1)));
    }

    @Test
    @DisplayName("sem familia (sem token) nada aparece")
    void semFamiliaNadaAparece() {
        UUID casa = UUID.randomUUID();
        UUID email = UUID.nameUUIDFromBytes(("sem-familia-" + casa + "@piggu.test").getBytes());
        FamiliaAtual.como(casa, () -> transacao.executeWithoutResult(status ->
                conexoes.save(new BankConnection("item-" + casa, email))));

        transacao.executeWithoutResult(status -> assertThat(conexoes.findByUserId(email)).isEmpty());
    }
}
