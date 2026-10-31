package com.piggu.banking.domain;

import com.piggu.banking.api.dto.BankAccountResponse;
import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registro e sincronizacao dos bancos conectados, com a Pluggy simulada e o
 * PostgreSQL de verdade (as migrations entram junto).
 */
class BankingServiceTest extends PostgresIntegrationTest {

    @MockitoBean
    private PluggyClient pluggy;

    @Autowired
    private BankingService servico;

    @Autowired
    private BankConnectionRepository conexoes;

    @Autowired
    private JdbcTemplate jdbc;

    private final CurrentUser titular = new CurrentUser(UUID.randomUUID(), "titular@piggu.test", PigguRole.TITULAR);
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@piggu.test", PigguRole.ADMIN);

    @BeforeEach
    void limpar() {
        conexoes.deleteAll();
    }

    @Test
    @DisplayName("registrar um item do proprio usuario grava as contas com saldo")
    void registraContas() {
        item("item-1", titular, "Nubank");
        when(pluggy.listarContas("item-1")).thenReturn(List.of(
                conta("c1", "Conta corrente", "1520.35"),
                conta("c2", "Cartao", "-300.00")));

        List<BankAccountResponse> contas = servico.registrar("item-1", titular);

        assertThat(contas).extracting(BankAccountResponse::nome).containsExactlyInAnyOrder("Conta corrente", "Cartao");
        assertThat(contas).extracting(BankAccountResponse::saldo)
                .containsExactlyInAnyOrder(new BigDecimal("1520.35"), new BigDecimal("-300.00"));
        assertThat(contas).allSatisfy(conta -> assertThat(conta.instituicao()).isEqualTo("Nubank"));
    }

    @Test
    @DisplayName("do numero da conta so ficam os 4 ultimos digitos, e a API ja entrega mascarado")
    void guardaSoOsUltimosDigitos() {
        item("item-6", titular, "Caixa");
        when(pluggy.listarContas("item-6")).thenReturn(List.of(
                new PluggyClient.Conta("c6", "Corrente", "CHECKING_ACCOUNT", "00012345-6", BigDecimal.TEN, "BRL"),
                new PluggyClient.Conta("c7", "Cartao", "CREDIT_CARD", "", BigDecimal.ONE, "BRL")));

        List<BankAccountResponse> contas = servico.registrar("item-6", titular);

        assertThat(contas).extracting(BankAccountResponse::numero).containsExactlyInAnyOrder("•••• 3456", "");
        assertThat(jdbc.queryForList("SELECT number FROM bank_accounts WHERE pluggy_account_id IN ('c6', 'c7')", String.class))
                .containsExactlyInAnyOrder("3456", "");
    }

    @Test
    @DisplayName("item conectado por outra conta e recusado: o id vem do navegador")
    void recusaItemAlheio() {
        item("item-2", admin, "Itau");

        assertThatThrownBy(() -> servico.registrar("item-2", titular)).isInstanceOf(ForbiddenException.class);
        assertThat(conexoes.count()).isZero();
    }

    @Test
    @DisplayName("registrar o mesmo item duas vezes nao duplica conexao nem conta")
    void registroIdempotente() {
        item("item-3", titular, "Inter");
        when(pluggy.listarContas("item-3")).thenReturn(List.of(conta("c3", "Conta", "10.00")));

        servico.registrar("item-3", titular);
        List<BankAccountResponse> contas = servico.registrar("item-3", titular);

        assertThat(conexoes.count()).isEqualTo(1);
        assertThat(contas).hasSize(1);
    }

    @Test
    @DisplayName("sincronizar atualiza o saldo e tira a conta que sumiu da Pluggy")
    void sincronizaSaldo() {
        item("item-4", titular, "Bradesco");
        when(pluggy.listarContas("item-4")).thenReturn(List.of(
                conta("c4", "Corrente", "100.00"), conta("c5", "Poupanca", "50.00")));
        servico.registrar("item-4", titular);

        when(pluggy.listarContas("item-4")).thenReturn(List.of(conta("c4", "Corrente", "250.00")));
        List<BankAccountResponse> contas = servico.sincronizarTudo(titular);

        assertThat(contas).singleElement().satisfies(conta -> {
            assertThat(conta.nome()).isEqualTo("Corrente");
            assertThat(conta.saldo()).isEqualByComparingTo("250.00");
        });
    }

    @Test
    @DisplayName("cada usuario ve so as contas dos proprios bancos")
    void listaSoDoUsuario() {
        item("item-5", titular, "Nubank");
        when(pluggy.listarContas("item-5")).thenReturn(List.of(conta("c6", "Dela", "1.00")));
        item("item-6", admin, "Itau");
        when(pluggy.listarContas("item-6")).thenReturn(List.of(conta("c7", "Dele", "2.00")));
        servico.registrar("item-5", titular);
        servico.registrar("item-6", admin);

        assertThat(servico.listar(titular)).extracting(BankAccountResponse::nome).containsExactly("Dela");
        assertThat(servico.listar(admin)).extracting(BankAccountResponse::nome).containsExactly("Dele");
    }

    @Test
    @DisplayName("o connect token sai com o id do usuario como clientUserId")
    void connectTokenComIdDoUsuario() {
        when(pluggy.criarConnectToken(titular.id().toString())).thenReturn("token-do-widget");

        assertThat(servico.gerarConnectToken(titular).accessToken()).isEqualTo("token-do-widget");
    }

    @Test
    @DisplayName("desconectar apaga o item na Pluggy e as contas guardadas")
    void desconecta() {
        item("item-7", titular, "Nubank");
        when(pluggy.listarContas("item-7")).thenReturn(List.of(conta("c8", "Conta", "5.00")));
        UUID conexao = servico.registrar("item-7", titular).get(0).conexaoId();

        assertThat(servico.desconectar(conexao, titular)).isEmpty();

        verify(pluggy).apagarItem("item-7");
        assertThat(conexoes.count()).isZero();
    }

    @Test
    @DisplayName("banco de outra pessoa nao e desconectado nem confirmado que existe")
    void naoDesconectaAlheio() {
        item("item-8", admin, "Itau");
        when(pluggy.listarContas("item-8")).thenReturn(List.of(conta("c9", "Dele", "1.00")));
        UUID conexao = servico.registrar("item-8", admin).get(0).conexaoId();

        assertThatThrownBy(() -> servico.desconectar(conexao, titular)).isInstanceOf(NotFoundException.class);

        verify(pluggy, never()).apagarItem(anyString());
        assertThat(conexoes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("item que a Pluggy ja apagou conta como desconectado; Pluggy fora do ar nao apaga nada aqui")
    void pluggyJaApagouOuForaDoAr() {
        item("item-9", titular, "Inter");
        when(pluggy.listarContas("item-9")).thenReturn(List.of(conta("c10", "Conta", "1.00")));
        UUID conexao = servico.registrar("item-9", titular).get(0).conexaoId();

        doThrow(new UpstreamException("fora")).when(pluggy).apagarItem("item-9");
        assertThatThrownBy(() -> servico.desconectar(conexao, titular)).isInstanceOf(UpstreamException.class);
        assertThat(conexoes.count()).isEqualTo(1);

        doThrow(new NotFoundException("sumiu")).when(pluggy).apagarItem("item-9");
        servico.desconectar(conexao, titular);
        assertThat(conexoes.count()).isZero();
    }

    @Test
    @DisplayName("gastos do banco: so debitos confirmados, de todas as contas da pessoa, do mais novo ao mais velho")
    void gastosDoBanco() {
        item("item-g", titular, "Nubank");
        when(pluggy.listarContas("item-g")).thenReturn(List.of(conta("cg1", "Conta", "10"), conta("cg2", "Cartao", "0")));
        servico.registrar("item-g", titular);
        LocalDate desde = LocalDate.now().minusDays(10);
        when(pluggy.listarTransacoes("cg1", desde)).thenReturn(List.of(
                transacao("a", desde.plusDays(1), "Padaria", "12.50", true, true),
                transacao("b", desde.plusDays(2), "Salario", "5000", false, true),
                transacao("c", desde.plusDays(3), "Pendente", "8", true, false)));
        when(pluggy.listarTransacoes("cg2", desde)).thenReturn(List.of(
                transacao("d", desde.plusDays(4), "Cinema", "60", true, true)));

        jdbc.update("UPDATE bank_connections SET synced_at = now() - interval '89 days' WHERE pluggy_item_id = 'item-g'");

        List<BankingService.GastoDoBanco> gastos = servico.gastosDesde(desde, titular);

        assertThat(gastos).extracting(BankingService.GastoDoBanco::idExterno).containsExactly("pluggy:d", "pluggy:a");
        // Quem importa toda semana usa o banco: a retencao de 90 dias nao o desconecta.
        assertThat(jdbc.queryForObject("SELECT synced_at > now() - interval '1 minute' FROM bank_connections"
                + " WHERE pluggy_item_id = 'item-g'", Boolean.class)).isTrue();
        assertThat(gastos.get(0).conta()).isEqualTo("Nubank · Cartao");
        assertThat(gastos.get(1).valor()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("gastos do banco: so ate 90 dias para tras e nunca no futuro; banco de outra pessoa nao entra")
    void gastosDoBancoNoPrazo() {
        item("item-h", admin, "Itau");
        when(pluggy.listarContas("item-h")).thenReturn(List.of(conta("ch1", "Conta", "10")));
        servico.registrar("item-h", admin);

        assertThatThrownBy(() -> servico.gastosDesde(LocalDate.now().minusDays(91), titular))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> servico.gastosDesde(LocalDate.now().plusDays(1), titular))
                .isInstanceOf(BusinessException.class);
        assertThat(servico.gastosDesde(LocalDate.now().minusDays(5), titular)).isEmpty();
        verify(pluggy, never()).listarTransacoes(anyString(), org.mockito.ArgumentMatchers.any());
    }

    private static PluggyClient.Transacao transacao(String id, LocalDate data, String descricao, String valor,
                                                    boolean debito, boolean confirmada) {
        return new PluggyClient.Transacao(id, data, descricao, new BigDecimal(valor), debito, confirmada, "BRL");
    }

    private void item(String id, CurrentUser dono, String instituicao) {
        when(pluggy.buscarItem(id)).thenReturn(new PluggyClient.Item(id, "UPDATED", dono.id().toString(), instituicao));
    }

    private static PluggyClient.Conta conta(String id, String nome, String saldo) {
        return new PluggyClient.Conta(id, nome, "CHECKING_ACCOUNT", "0001", new BigDecimal(saldo), "BRL");
    }
}
