package br.com.piggu.feature.receitas

import androidx.lifecycle.ViewModelStore
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.estadosDe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    }

    /** Espera as categorias e a família chegarem, para nenhuma carga mudar o formulário no meio do teste. */
    // "Salário" já vem escolhida antes da lista chegar: espera a lista inteira.
    private suspend fun FormularioDeReceitaViewModel.pronto() = estado.esperar { it.opcoesDeCategoria.size > 1 && it.temCompanhia }

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

        // A validação muda o estado na hora, sem ir à rede: lê o valor, sem contar emissões.
        formulario.lancar()
        assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_segundo_dia), formulario.estado.value.erro)
        formulario.mudarSegundoDia("32")
        formulario.lancar()
        assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_segundo_dia), formulario.estado.value.erro) // o mesmo erro
        formulario.mudarSegundoDia("5a")
        assertEquals("5", formulario.estado.value.segundoDia)
        formulario.lancar()
        assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_segundo_valor), formulario.estado.value.erro)
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
        // A validação muda o estado na hora, sem ir à rede: lê o valor, sem contar emissões.
        formulario.lancar()
        assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_descricao), formulario.estado.value.erro)
        formulario.mudarDescricao("   ")
        formulario.lancar()
        assertEquals(MensagemDeErro.DoApp(R.string.receita_falta_descricao), formulario.estado.value.erro)
        formulario.mudarDescricao("Freela")
        formulario.mudarValor(BigDecimal.ZERO)
        formulario.lancar()
        assertEquals(MensagemDeErro.DoApp(R.string.receita_valor_invalido), formulario.estado.value.erro)
        formulario.mudarValor(BigDecimal("999999"))
        val estados = estadosDe(formulario.estado)
        formulario.lancar()
        val falhou = formulario.estado.esperar { !it.salvando && it.erro != null }
        assertTrue(estados.any { it.salvando })
        assertEquals(MensagemDeErro.DoServidor("Valor alto demais."), falhou.erro)
        assertNull(falhou.lancada)
        assertEquals("Freela", falhou.descricao)
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
        // A família chega numa carga à parte: sem esperar por ela, o "false" seria só o valor inicial.
        formulario.estado.esperar { montagem.pedidos("GET /api/family").isNotEmpty() }
        assertFalse(formulario.estado.value.temCompanhia)
        formulario.mudarCompartilhada(true)
        formulario.mudarValor(BigDecimal("10"))
        formulario.mudarDescricao("Troco")
        formulario.lancar()
        formulario.estado.esperar { it.lancada != null }
        assertTrue(montagem.pedidos("POST /api/incomes").single().contains(""""compartilhado":false"""))
    }
}
