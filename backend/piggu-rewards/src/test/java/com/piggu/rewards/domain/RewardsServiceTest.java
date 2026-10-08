package com.piggu.rewards.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.rewards.api.dto.CoinAdjustRequest;
import com.piggu.rewards.api.dto.CoinBalanceResponse;
import com.piggu.rewards.api.dto.PrizeRequest;
import com.piggu.rewards.api.dto.PrizeResponse;
import com.piggu.rewards.api.dto.RedemptionResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fofocoins, premios e resgates.
 *
 * <p>E a parte com dinheiro de brincadeira, mas com contabilidade de verdade: o
 * saldo e sempre a soma do livro-razao, e um resgate precisa debitar e registrar
 * junto ou nao acontecer.</p>
 *
 * <p>Sem transacao de teste em volta: os metodos de ajuste e resgate pedem
 * isolamento SERIALIZABLE, que nao pode ser imposto a uma transacao ja aberta.
 * A limpeza entre os testes e explicita.</p>
 */
class RewardsServiceTest extends PostgresIntegrationTest {

    private static final String ADMIN = "admin@piggu.test";
    private static final String TITULAR = "titular@piggu.test";

    @Autowired
    private RewardsService recompensas;

    @Autowired
    private CoinEntryRepository lancamentos;

    @Autowired
    private PrizeRepository premios;

    @Autowired
    private RedemptionRepository resgates;

    @BeforeEach
    void limpar() {
        resgates.deleteAll();
        premios.deleteAll();
        lancamentos.deleteAll();
    }

    @Test
    @DisplayName("saldo comeca zerado e e sempre a soma do livro-razao")
    void saldoInicial() {
        assertThat(recompensas.saldo().saldo()).isZero();
        assertThat(recompensas.saldo().historico()).isEmpty();
    }

    @Test
    @DisplayName("credito e debito somam no saldo")
    void creditoEDebito() {
        recompensas.ajustar(new CoinAdjustRequest(100, "Arrumou a cozinha"), ADMIN);
        CoinBalanceResponse depois = recompensas.ajustar(new CoinAdjustRequest(-30, "Trocou por doce"), ADMIN);

        assertThat(depois.saldo()).isEqualTo(70);
        assertThat(depois.historico()).hasSize(2);
    }

    @Test
    @DisplayName("saldo nunca fica negativo")
    void saldoNuncaNegativo() {
        recompensas.ajustar(new CoinAdjustRequest(10, "Credito"), ADMIN);

        assertThatThrownBy(() -> recompensas.ajustar(new CoinAdjustRequest(-11, "Debito grande"), ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O saldo nao pode ficar negativo.");

        assertThat(recompensas.saldo().saldo()).isEqualTo(10);
    }

    @Test
    @DisplayName("ajuste de zero e recusado")
    void ajusteDeZeroRecusado() {
        assertThatThrownBy(() -> recompensas.ajustar(new CoinAdjustRequest(0, "Nada"), ADMIN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("historico vem do mais novo para o mais antigo")
    void historicoMaisNovoPrimeiro() {
        recompensas.ajustar(new CoinAdjustRequest(10, "Primeiro"), ADMIN);
        recompensas.ajustar(new CoinAdjustRequest(20, "Segundo"), ADMIN);

        assertThat(recompensas.saldo().historico())
                .extracting(CoinBalanceResponse.Movimento::motivo)
                .containsExactly("Segundo", "Primeiro");
    }

    @Test
    @DisplayName("resgate debita o preco e registra o resgate juntos")
    void resgateDebitaERegistra() {
        recompensas.ajustar(new CoinAdjustRequest(100, "Credito"), ADMIN);
        PrizeResponse premio = recompensas.criarPremio(
                new PrizeRequest("Sessao de cinema", "Filme a escolha", 60, true), ADMIN);

        RedemptionResponse resgate = recompensas.resgatar(premio.id(), TITULAR);

        assertThat(resgate.preco()).isEqualTo(60);
        assertThat(resgate.saldo()).isEqualTo(40);
        assertThat(recompensas.saldo().saldo()).isEqualTo(40);
        assertThat(recompensas.listarResgates()).hasSize(1);
        assertThat(recompensas.saldo().historico().get(0).motivo()).isEqualTo("Resgate: Sessao de cinema");
    }

    @Test
    @DisplayName("resgate sem saldo suficiente nao debita nada")
    void resgateSemSaldo() {
        recompensas.ajustar(new CoinAdjustRequest(50, "Credito"), ADMIN);
        PrizeResponse premio = recompensas.criarPremio(new PrizeRequest("Caro", "", 60, true), ADMIN);

        assertThatThrownBy(() -> recompensas.resgatar(premio.id(), TITULAR))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Voce ainda nao possui Fofocoins suficientes.");

        assertThat(recompensas.saldo().saldo()).isEqualTo(50);
        assertThat(recompensas.listarResgates()).isEmpty();
    }

    @Test
    @DisplayName("premio desativado nao pode ser resgatado")
    void premioDesativado() {
        recompensas.ajustar(new CoinAdjustRequest(100, "Credito"), ADMIN);
        PrizeResponse premio = recompensas.criarPremio(new PrizeRequest("Fora do ar", "", 10, false), ADMIN);

        assertThatThrownBy(() -> recompensas.resgatar(premio.id(), TITULAR))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Este premio nao esta disponivel.");
    }

    @Test
    @DisplayName("premio inexistente da erro claro")
    void premioInexistente() {
        assertThatThrownBy(() -> recompensas.resgatar(UUID.randomUUID(), TITULAR))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("excluir premio desativa em vez de apagar, preservando o historico")
    void exclusaoDesativa() {
        recompensas.ajustar(new CoinAdjustRequest(100, "Credito"), ADMIN);
        PrizeResponse premio = recompensas.criarPremio(new PrizeRequest("Cinema", "", 10, true), ADMIN);
        recompensas.resgatar(premio.id(), TITULAR);

        recompensas.excluirPremio(premio.id());

        assertThat(recompensas.listarPremios(true)).isEmpty();
        assertThat(recompensas.listarPremios(false)).hasSize(1);
        assertThat(recompensas.listarResgates()).hasSize(1);
    }

    @Test
    @DisplayName("resgate guarda nome e preco do momento, mesmo se o premio mudar depois")
    void resgateGuardaValorHistorico() {
        recompensas.ajustar(new CoinAdjustRequest(100, "Credito"), ADMIN);
        PrizeResponse premio = recompensas.criarPremio(new PrizeRequest("Cinema", "", 20, true), ADMIN);
        recompensas.resgatar(premio.id(), TITULAR);

        recompensas.atualizarPremio(premio.id(), new PrizeRequest("Cinema IMAX", "", 90, true), ADMIN);

        RedemptionResponse resgate = recompensas.listarResgates().get(0);
        assertThat(resgate.premio()).isEqualTo("Cinema");
        assertThat(resgate.preco()).isEqualTo(20);
    }
}
