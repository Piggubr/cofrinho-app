package br.com.piggu.feature.receitas

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal

/** O formulário de receita (formulario-de-receita.ts e incomes-acoes.spec.ts), com a API falsa. */
@OptIn(ExperimentalCoroutinesApi::class)
class FormularioDeReceitaViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
        Dispatchers.resetMain()
    }

    /** Espera as categorias e a família chegarem, para o Turbine só ver o que o teste faz. */
    private suspend fun FormularioDeReceitaViewModel.pronto() = estado.esperar { it.categorias.isNotEmpty() && it.temCompanhia }

    @Test
    fun `lanca a receita com a categoria de salario, limpa o formulario e avisa as outras telas`() = runTest {
        val montagem = montar(pasta.root, loja)
        val formulario = montagem.formulario()
        val inicio = formulario.pronto()
        assertEquals("2026-10-28", inicio.data)
        assertEquals("Salário", inicio.categoria)
        assertEquals(listOf("Salário", "Extra", "Reembolso", "Investimentos", "Outros"), inicio.opcoesDeCategoria)

        formulario.mudarValor(BigDecimal("5000"))
        formulario.mudarDescricao(" Salário ")
        formulario.mudarCompartilhada(true)
        formulario.lancar()
        val depois = formulario.estado.esperar { it.lancada != null }

        assertEquals(Aviso(R.string.receita_lancada), depois.lancada)
        val pedido = montagem.pedidos("POST /api/incomes").single()
        assertTrue(pedido, pedido.contains(""""descricao":"Salário","categoria":"Salário","valor":5000,"compartilhado":true,"recorrente":false"""))
        assertFalse(pedido.contains("segundoDia"))
        assertEquals("", depois.descricao)
        assertNull(depois.valor)
        assertNull(depois.compartilhada)
        assertEquals("Salário", depois.categoria)
        assertEquals(1, montagem.lancamentos.versao.value)

        formulario.lancadaEntregue()
        assertNull(formulario.estado.value.lancada)
    }

    @Test
    fun `recorrente entra todo mes no dia da receita`() = runTest {
        val montagem = montar(pasta.root, loja)
        val formulario = montagem.formulario()
        formulario.pronto()
        formulario.mudarData("2026-10-05")
        formulario.mudarValor(BigDecimal("3000"))
        formulario.mudarDescricao("Aluguel da sala")
        formulario.mudarCategoria("Extra")
        formulario.mudarRepetir(true)
        formulario.lancar()

        assertEquals(Aviso(R.string.receita_lancada_recorrente, listOf(5)), formulario.estado.esperar { it.lancada != null }.lancada)
        val pedido = montagem.pedidos("POST /api/incomes").single()
        assertTrue(pedido, pedido.contains(""""data":"2026-10-05"""") && pedido.contains(""""categoria":"Extra"""") && pedido.contains(""""recorrente":true"""))
        assertFalse(formulario.estado.value.repetir)
    }

    @Test
    fun `salario em duas parcelas confere o dia e o valor da segunda (D-087)`() = runTest {
        val montagem = montar(pasta.root, loja)
        val formulario = montagem.formulario()
        formulario.pronto()
        formulario.mudarData("2026-10-20")
        formulario.mudarValor(BigDecimal("2000"))
        formulario.mudarDescricao("Salário")
        formulario.mudarRepetir(true)
        formulario.mudarEmDuasParcelas(true)

        formulario.estado.test {
            awaitItem()
            formulario.lancar()
            assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_segundo_dia), awaitItem().erro)
            formulario.mudarSegundoDia("32")
            awaitItem()
            formulario.lancar()
            expectNoEvents() // o mesmo erro: nada muda
            formulario.mudarSegundoDia("5a")
            assertEquals("5", awaitItem().segundoDia)
            formulario.lancar()
            assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_segundo_valor), awaitItem().erro)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(montagem.pedidos("POST /api/incomes").isEmpty())

        formulario.mudarSegundoValor(BigDecimal("3000"))
        formulario.lancar()
        assertEquals(
            Aviso(R.string.receita_lancada_duas_parcelas, listOf(20, 5)),
            formulario.estado.esperar { it.lancada != null }.lancada,
        )
        val pedido = montagem.pedidos("POST /api/incomes").single()
        assertTrue(pedido, pedido.contains(""""valor":2000""") && pedido.contains(""""recorrente":true,"segundoDia":5,"segundoValor":3000"""))
    }

    @Test
    fun `duas parcelas sem repetir nao manda a segunda`() = runTest {
        val montagem = montar(pasta.root, loja)
        val formulario = montagem.formulario()
        formulario.pronto()
        formulario.mudarRepetir(true)
        formulario.mudarEmDuasParcelas(true)
        formulario.mudarRepetir(false)
        formulario.mudarValor(BigDecimal("100"))
        formulario.mudarDescricao("Pix de volta")
        formulario.lancar()
        assertEquals(Aviso(R.string.receita_lancada), formulario.estado.esperar { it.lancada != null }.lancada)
        assertFalse(montagem.pedidos("POST /api/incomes").single().contains("segundo"))
    }

    @Test
    fun `validacoes na ordem do site e o erro do servidor`() = runTest {
        val fixas = RespostasFixas("POST /api/incomes" to RespostasFixas.erro(400, "VALIDACAO", "Valor alto demais."))
        val montagem = montar(pasta.root, loja, fixas)
        val formulario = montagem.formulario()
        formulario.pronto()
        formulario.estado.test {
            awaitItem()
            formulario.lancar()
            assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_descricao), awaitItem().erro)
            formulario.mudarDescricao("   ")
            awaitItem()
            formulario.lancar()
            expectNoEvents()
            formulario.mudarDescricao("Freela")
            awaitItem()
            formulario.mudarValor(BigDecimal.ZERO)
            awaitItem()
            formulario.lancar()
            assertEquals(MensagemDeErro.DoApp(R.string.receita_valor_invalido), awaitItem().erro)
            formulario.mudarValor(BigDecimal("999999"))
            awaitItem()
            formulario.lancar()
            assertTrue(awaitItem().salvando)
            val falhou = awaitItem()
            assertFalse(falhou.salvando)
            assertEquals(MensagemDeErro.DoServidor("Valor alto demais."), falhou.erro)
            assertNull(falhou.lancada)
            assertEquals("Freela", falhou.descricao)
        }
        assertEquals(0, montagem.lancamentos.versao.value)
    }

    @Test
    fun `sem mais ninguem na casa a receita nao vai compartilhada`() = runTest {
        val sozinha = """{"id":"familia-demo","nome":"Casa","plano":"GRATUITO","planoFamilia":false,""" +
            """"membros":[{"id":"u-ana","nome":"Ana Souza","papel":"TITULAR","premium":false}]}"""
        val fixas = RespostasFixas("GET /api/family" to br.com.piggu.core.testing.RespostaFixa(200, sozinha))
        val montagem = montar(pasta.root, loja, fixas)
        val formulario = montagem.formulario()
        formulario.estado.esperar { it.categorias.isNotEmpty() }
        assertFalse(formulario.estado.value.temCompanhia)
        formulario.mudarCompartilhada(true)
        formulario.mudarValor(BigDecimal("10"))
        formulario.mudarDescricao("Troco")
        formulario.lancar()
        formulario.estado.esperar { it.lancada != null }
        assertTrue(montagem.pedidos("POST /api/incomes").single().contains(""""compartilhado":false"""))
    }
}
