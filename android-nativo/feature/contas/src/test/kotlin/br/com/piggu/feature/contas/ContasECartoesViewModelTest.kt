package br.com.piggu.feature.contas

import androidx.lifecycle.ViewModelStore
import br.com.piggu.core.data.Carga
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.TipoDeConta
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.ui.FalhaAoCarregar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

private val EstadoDasContasECartoes.lista: List<ContaOuCartao>? get() = (contas as? Carga.Pronto)?.valor
private val EstadoDasContasECartoes.faturaPronta get() = (fatura?.carga as? Carga.Pronto)?.valor

private val OPEN_FINANCE_DESLIGADO = "GET /api/banking/status" to RespostaFixa(200, """{"habilitado":false}""")

/** Cartões e bancos (accounts.spec.ts e accounts-acoes.spec.ts), com a API falsa da demonstração. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContasECartoesViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
    }

    @Test
    fun `lista as contas e cartoes, com a fatura aberta e de quem e`() = runTest {
        val montagem = montar(pasta.root, loja)
        val estado = montagem.contas().estado.esperar { it.lista != null && it.temCompanhia }
        assertEquals(listOf("Conta Itaú", "Nubank"), estado.lista!!.map { it.nome })
        val nubank = estado.lista!!.first { it.nome == "Nubank" }
        assertEquals(5, nubank.fechamento)
        assertEquals(12, nubank.vencimento)
        // 28/10 já passou do fechamento (dia 5): a aberta é a de novembro.
        assertEquals("2026-11", nubank.faturaAberta!!.mes)
        assertTrue(estado.minha(nubank))
        assertTrue(estado.podeCompartilhar(nubank))
        assertNull(estado.deQuem(nubank))
        val doBeto = nubank.copy(usuario = "u-beto")
        assertFalse(estado.minha(doBeto))
        assertEquals(Aviso(R.string.contas_de, listOf("Beto Souza")), estado.deQuem(doBeto))
        assertEquals(Aviso(R.string.contas_de_ex_membro), estado.deQuem(nubank.copy(usuario = "u-antigo")))
        assertNull(estado.guardadoEm)
        assertTrue(montagem.guardado.respostas.containsKey("contas"))
    }

    @Test
    fun `cartao novo pede nome e dias, o fechamento acompanha o vencimento e vai compartilhado`() = runTest {
        val montagem = montar(pasta.root, loja)
        val contas = montagem.contas()
        // Tudo carregado antes: nenhuma carga muda o formulário no meio.
        contas.estado.esperar { it.lista != null && it.temCompanhia && it.openFinanceLigado && it.bancos.isNotEmpty() }

        // A validação muda o estado na hora, sem ir à rede: lê o valor, sem contar emissões.
        contas.salvar()
        contas.estado.value.let {
            assertEquals(MensagemDeErro.DoApp(R.string.nova_conta_falta_nome), it.erro)
            assertEquals(CampoDaConta.Nome, it.faltou)
        }
        contas.mudarNome(" Visa ")
        contas.salvar()
        contas.estado.value.let {
            assertEquals(MensagemDeErro.DoApp(R.string.nova_conta_falta_vencimento), it.erro)
            assertEquals(CampoDaConta.Vencimento, it.faltou)
        }
        contas.mudarVencimento("12")
        assertEquals("5", contas.estado.value.fechamento)
        contas.mudarVencimento("20")
        assertEquals("13", contas.estado.value.fechamento)
        contas.mudarFechamento("40")
        contas.salvar()
        assertEquals(MensagemDeErro.DoApp(R.string.nova_conta_falta_fechamento), contas.estado.value.erro)
        contas.mudarVencimento("25")
        assertEquals("40", contas.estado.value.fechamento) // mudado à mão: não acompanha mais
        assertTrue(montagem.pedidos("POST /api/accounts").isEmpty())

        contas.mudarFechamento("18")
        contas.mudarCompartilhada(true)
        contas.salvar()
        val salvo = contas.estado.esperar { it.lista?.any { c -> c.nome == "Visa" } == true && !it.salvando }
        assertEquals(
            listOf("""POST /api/accounts {"nome":"Visa","tipo":"CARTAO","fechamento":18,"vencimento":25,"compartilhado":true}"""),
            montagem.pedidos("POST /api/accounts"),
        )
        val visa = salvo.lista!!.first { it.nome == "Visa" }
        assertEquals(true, visa.compartilhado)
        assertEquals("", salvo.nome)
        assertEquals("", salvo.vencimento)
        assertFalse(salvo.compartilhada)
        assertNull(salvo.erro)
        assertEquals(1, montagem.lancamentos.versao.value)
    }

    @Test
    fun `conta bancaria vai sem os dias, e o erro do servidor aparece`() = runTest {
        val montagem = montar(pasta.root, loja, RespostasFixas("POST /api/accounts" to RespostasFixas.erro(409, "NOME_REPETIDO", "Nome repetido.")))
        val contas = montagem.contas()
        contas.estado.esperar { it.lista != null }
        contas.mudarNome("Itaú")
        contas.mudarTipo(TipoDeConta.CONTA)
        contas.salvar()
        val falhou = contas.estado.esperar { it.erro != null && !it.salvando }
        assertEquals(MensagemDeErro.DoServidor("Nome repetido."), falhou.erro)
        assertEquals("Itaú", falhou.nome)
        val pedido = montagem.pedidos("POST /api/accounts").single()
        assertTrue(pedido, pedido.contains(""""tipo":"CONTA"""") && pedido.contains(""""compartilhado":false"""))
        assertFalse(pedido, Regex(""""(fechamento|vencimento)":\d""").containsMatchIn(pedido))
    }

    @Test
    fun `compartilha com o casal e deixa so meu`() = runTest {
        val montagem = montar(pasta.root, loja)
        val contas = montagem.contas()
        val nubank = contas.estado.esperar { it.lista != null }.lista!!.first { it.nome == "Nubank" }

        contas.alternarCompartilhamento(nubank)
        val doCasal = contas.estado.esperar { it.lista?.first { c -> c.id == nubank.id }?.compartilhado == true }.lista!!.first { it.id == nubank.id }
        assertEquals(listOf("""PUT /api/accounts/conta-nubank/sharing {"compartilhado":true}"""), montagem.pedidos("PUT /api/accounts"))

        contas.alternarCompartilhamento(doCasal)
        contas.estado.esperar { it.lista?.first { c -> c.id == nubank.id }?.compartilhado == false }
        assertEquals("""PUT /api/accounts/conta-nubank/sharing {"compartilhado":false}""", montagem.pedidos("PUT /api/accounts").last())
    }

    @Test
    fun `apaga so depois de confirmar e fecha a fatura aberta`() = runTest {
        val montagem = montar(pasta.root, loja)
        val contas = montagem.contas()
        val nubank = contas.estado.esperar { it.lista != null }.lista!!.first { it.nome == "Nubank" }

        contas.pedirParaApagar(nubank)
        assertEquals(Confirmacao.ApagarConta(nubank), contas.estado.value.confirmacao)
        contas.desistir()
        assertNull(contas.estado.value.confirmacao)
        assertTrue(montagem.pedidos("DELETE").isEmpty())

        contas.verFatura(nubank)
        contas.estado.esperar { it.faturaPronta != null }
        contas.pedirParaApagar(nubank)
        contas.confirmar()
        val depois = contas.estado.esperar { it.lista?.none { c -> c.id == nubank.id } == true }
        assertNull(depois.fatura)
        assertNull(depois.confirmacao)
        assertEquals(listOf("DELETE /api/accounts/conta-nubank"), montagem.pedidos("DELETE"))
    }

    @Test
    fun `abre a fatura do cartao com os gastos e anda para a anterior e a seguinte`() = runTest {
        val montagem = montar(pasta.root, loja)
        val contas = montagem.contas()
        val nubank = contas.estado.esperar { it.lista != null }.lista!!.first { it.nome == "Nubank" }

        contas.verFatura(nubank)
        val novembro = contas.estado.esperar { it.faturaPronta != null }
        assertEquals("2026-11", novembro.fatura!!.mes)
        val fatura = novembro.faturaPronta!!
        assertEquals("2026-10-05", fatura.fatura.inicio)
        assertEquals("2026-11-04", fatura.fatura.ultimaCompra)
        assertEquals("2026-11-12", fatura.fatura.vencimento)
        assertEquals(listOf("Uber Centro", "Jantar de sexta", "Combustível", "Cinema"), fatura.gastos.map { it.item }.filter { it != "Fone de ouvido" })
        assertEquals(0, fatura.fatura.total.compareTo(fatura.gastos.fold(java.math.BigDecimal.ZERO) { s, g -> s + g.valor }))

        contas.faturaAnterior()
        val outubro = contas.estado.esperar { it.fatura?.mes == "2026-10" && it.faturaPronta != null }
        assertEquals("2026-09-05", outubro.faturaPronta!!.fatura.inicio)
        contas.proximaFatura()
        contas.proximaFatura()
        assertEquals("2026-12", contas.estado.esperar { it.fatura?.mes == "2026-12" && it.faturaPronta != null }.faturaPronta!!.fatura.mes)

        contas.fecharFatura()
        assertNull(contas.estado.value.fatura)
    }

    @Test
    fun `marca a fatura como paga e desfaz, sem lancar gasto`() = runTest {
        val montagem = montar(pasta.root, loja)
        val contas = montagem.contas()
        val nubank = contas.estado.esperar { it.lista != null }.lista!!.first { it.nome == "Nubank" }
        contas.verFatura(nubank)
        assertEquals(false, contas.estado.esperar { it.faturaPronta != null }.faturaPronta!!.fatura.paga)

        contas.alternarPagamento()
        val paga = contas.estado.esperar { it.faturaPronta?.fatura?.paga == true && !it.pagando }
        assertEquals(listOf("PUT /api/accounts/conta-nubank/statement/paid"), montagem.pedidos("PUT /api/accounts"))
        assertEquals(true, paga.lista!!.first { it.id == nubank.id }.faturaAberta!!.paga)
        assertEquals(1, montagem.lancamentos.versao.value)

        contas.alternarPagamento()
        contas.estado.esperar { it.faturaPronta?.fatura?.paga == false && !it.pagando }
        assertEquals(listOf("DELETE /api/accounts/conta-nubank/statement/paid"), montagem.pedidos("DELETE"))
        assertTrue(montagem.pedidos("POST /api/expenses").isEmpty())
    }

    @Test
    fun `erro ao marcar como paga aparece e a fatura fica como estava`() = runTest {
        val montagem = montar(
            pasta.root,
            loja,
            RespostasFixas("PUT /api/accounts/conta-nubank/statement/paid" to RespostasFixas.erro(422, "FATURA_FUTURA", "Essa fatura ainda não começou.")),
        )
        val contas = montagem.contas()
        val nubank = contas.estado.esperar { it.lista != null }.lista!!.first { it.nome == "Nubank" }
        contas.verFatura(nubank)
        contas.estado.esperar { it.faturaPronta != null }
        contas.alternarPagamento()
        val falhou = contas.estado.esperar { it.erro != null && !it.pagando }
        assertEquals(MensagemDeErro.DoServidor("Essa fatura ainda não começou."), falhou.erro)
        assertEquals(false, falhou.faturaPronta!!.fatura.paga)
        assertEquals(0, montagem.lancamentos.versao.value)
    }

    @Test
    fun `open finance ligado mostra conectar banco, atualizar e desconectar`() = runTest {
        val montagem = montar(pasta.root, loja)
        val contas = montagem.contas()
        val estado = contas.estado.esperar { it.openFinanceLigado && it.bancos.isNotEmpty() }
        assertTrue(estado.mostrarBancos)
        assertEquals(listOf("conexao-demo" to "Banco Exemplo"), estado.conexoes)
    }

    @Test
    fun `open finance desligado e sem banco conectado esconde os bancos`() = runTest {
        val montagem = montar(pasta.root, loja, RespostasFixas(OPEN_FINANCE_DESLIGADO, "GET /api/banking/accounts" to RespostaFixa(200, "[]")))
        val contas = montagem.contas()
        val estado = contas.estado.esperar { it.lista != null && montagem.pedidos("GET /api/banking/accounts").isNotEmpty() }
        assertFalse(estado.openFinanceLigado)
        assertTrue(estado.bancos.isEmpty())
        assertFalse(estado.mostrarBancos)
    }

    @Test
    fun `open finance desligado com banco conectado ainda atualiza e desconecta`() = runTest {
        val montagem = montar(pasta.root, loja, RespostasFixas(OPEN_FINANCE_DESLIGADO))
        val contas = montagem.contas()
        val estado = contas.estado.esperar { it.bancos.isNotEmpty() && montagem.pedidos("GET /api/banking/status").isNotEmpty() }
        assertFalse(estado.openFinanceLigado)
        assertTrue(estado.mostrarBancos)

        contas.atualizarBancos()
        contas.estado.esperar { !it.mexendoNosBancos && montagem.pedidos("POST /api/banking/sync").isNotEmpty() }

        contas.pedirParaDesconectar("conexao-demo", "Banco Exemplo")
        assertEquals(Confirmacao.Desconectar("conexao-demo", "Banco Exemplo"), contas.estado.value.confirmacao)
        contas.confirmar()
        val desconectado = contas.estado.esperar { it.bancos.isEmpty() && !it.mexendoNosBancos }
        assertEquals(listOf("DELETE /api/banking/connections/conexao-demo"), montagem.pedidos("DELETE"))
        assertFalse(desconectado.mostrarBancos)
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        val montagem = montar(pasta.root, loja)
        montagem.contas().estado.esperar { it.lista != null }
        val semRede = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun contas(): List<ContaOuCartao> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        montagem.conectado.value = false
        val guardado = montagem.contas(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(2, guardado.lista!!.size)
        assertFalse(guardado.conectado)
        assertNotNull(guardado.guardadoEm)

        montagem.guardado.respostas.clear()
        val vazio = montagem.contas(semRede).estado.esperar { it.contas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.contas as Carga.Falhou).erro))
    }

    @Test
    fun `erro do servidor na lista pede tentar de novo e recarrega`() = runTest {
        var falhar = true
        val montagem = montar(pasta.root, loja)
        val instavel = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun contas(): List<ContaOuCartao> =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else montagem.rede.financas.contas()
        }
        val contas = montagem.contas(instavel)
        val falhou = contas.estado.esperar { it.contas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((falhou.contas as Carga.Falhou).erro))
        falhar = false
        contas.tentarDeNovo()
        assertEquals(2, contas.estado.esperar { it.lista != null }.lista!!.size)
    }
}
