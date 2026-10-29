package br.com.piggu.feature.receitas

import androidx.lifecycle.ViewModelStore
import br.com.piggu.core.data.Carga
import br.com.piggu.core.model.Receita
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
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
import java.math.BigDecimal

private val EstadoDeReceitas.lista: List<Receita>? get() = (receitas as? Carga.Pronto)?.valor

/** A aba Receitas (incomes.ts e incomes-acoes.spec.ts), com a API falsa da demonstração. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceitasViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
    }

    @Test
    fun `mostra as receitas do mes com o total e de quem e, e troca de mes`() = runTest {
        val montagem = montar(pasta.root, loja)
        val receitas = montagem.receitas()
        val estado = receitas.estado.esperar { it.lista != null && it.temCompanhia }
        assertEquals("2026-10", estado.mes)
        assertEquals(listOf("Salário Ana", "Salário Beto"), estado.lista!!.map { it.descricao }.sorted())
        assertEquals(BigDecimal("10700"), estado.total.stripTrailingZeros().let { BigDecimal(it.toPlainString()) })
        val beto = estado.lista!!.first { it.descricao == "Salário Beto" }
        val ana = estado.lista!!.first { it.descricao == "Salário Ana" }
        assertEquals(Aviso(R.string.receitas_de, listOf("Beto Souza")), estado.deQuem(beto))
        assertNull(estado.deQuem(ana))
        assertTrue(estado.podeApagar(beto)) // não compartilhada: aparece para quem lançou e pode apagar
        assertFalse(estado.podeApagar(beto.copy(compartilhado = true)))
        assertEquals(Aviso(R.string.receitas_de_ex_membro), estado.deQuem(beto.copy(usuario = "u-antigo")))
        assertEquals(Aviso(R.string.receitas_de_piggu), estado.deQuem(beto.copy(usuario = SISTEMA)))
        assertNull(estado.guardadoEm)

        receitas.mudarMes("2026-09")
        val setembro = receitas.estado.esperar { it.mes == "2026-09" && it.lista?.any { r -> r.descricao == "Freela de design" } == true }
        assertEquals(0, setembro.total.compareTo(BigDecimal("11500")))
        assertTrue(montagem.pedidos("GET /api/bills").isNotEmpty())
    }

    @Test
    fun `apaga a receita so depois de confirmar e recarrega`() = runTest {
        val montagem = montar(pasta.root, loja)
        val receitas = montagem.receitas()
        val beto = receitas.estado.esperar { it.lista != null }.lista!!.first { it.descricao == "Salário Beto" }

        receitas.pedirParaApagar(beto)
        assertEquals(Confirmacao.ApagarReceita(beto), receitas.estado.value.confirmacao)
        receitas.desistir()
        assertNull(receitas.estado.value.confirmacao)
        assertTrue(montagem.pedidos("DELETE").isEmpty())

        receitas.pedirParaApagar(beto)
        receitas.confirmar()
        val depois = receitas.estado.esperar { it.lista?.none { r -> r.id == beto.id } == true }
        assertNull(depois.confirmacao)
        assertEquals(listOf("DELETE /api/incomes/${beto.id}"), montagem.pedidos("DELETE"))
        assertEquals(1, montagem.lancamentos.versao.value)
    }

    @Test
    fun `erro ao apagar aparece e nada muda`() = runTest {
        val montagem = montar(pasta.root, loja)
        val recusa = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun excluirReceita(id: String) =
                throw ErroDaApi.DoServidor(403, "PROIBIDO", "Receita de outra pessoa.", emptyList(), null)
        }
        val receitas = montagem.receitas(recusa)
        val receita = receitas.estado.esperar { it.lista != null }.lista!!.first()
        receitas.pedirParaApagar(receita)
        receitas.confirmar()
        // Espera o estado final: no CI, mais lento, a ordem das emissões no meio do caminho varia.
        val depois = receitas.estado.esperar { it.erro != null }
        assertNull(depois.confirmacao)
        assertEquals(MensagemDeErro.DoServidor("Receita de outra pessoa."), depois.erro)
        assertTrue(depois.lista!!.any { it.id == receita.id })
        assertEquals(0, montagem.lancamentos.versao.value)
    }

    @Test
    fun `recarrega quando algo e lancado pelo +`() = runTest {
        val montagem = montar(pasta.root, loja)
        val receitas = montagem.receitas()
        receitas.estado.esperar { it.lista?.size == 2 }
        val formulario = montagem.formulario()
        formulario.mudarValor(BigDecimal("800"))
        formulario.mudarDescricao("Freela")
        formulario.lancar()
        val depois = receitas.estado.esperar { it.lista?.any { r -> r.descricao == "Freela" } == true }
        assertEquals(0, depois.total.compareTo(BigDecimal("11500")))
    }

    @Test
    fun `entram todo mes mostra a recorrente com as duas parcelas e para de repetir`() = runTest {
        val montagem = montar(pasta.root, loja)
        val formulario = montagem.formulario()
        formulario.mudarData("2026-10-20")
        formulario.mudarValor(BigDecimal("2000"))
        formulario.mudarDescricao("Salário")
        formulario.mudarRepetir(true)
        formulario.mudarEmDuasParcelas(true)
        formulario.mudarSegundoDia("5")
        formulario.mudarSegundoValor(BigDecimal("3000"))
        formulario.lancar()
        formulario.estado.esperar { it.lancada != null }

        val receitas = montagem.receitas()
        val fixa = receitas.estado.esperar { it.recorrentes.isNotEmpty() }.recorrentes.single()
        assertEquals("Salário", fixa.descricao)
        assertEquals(20, fixa.dia)
        assertEquals(5, fixa.segundoDia)
        assertEquals(0, fixa.segundoValor!!.compareTo(BigDecimal("3000")))
        assertTrue(montagem.pedidos("GET /api/bills").all { it.startsWith("GET /api/bills") })

        receitas.pedirParaPararDeRepetir(fixa)
        assertEquals(Confirmacao.PararDeRepetir(fixa), receitas.estado.value.confirmacao)
        receitas.confirmar()
        receitas.estado.esperar { it.recorrentes.isEmpty() }
        assertEquals(listOf("DELETE /api/bills/${fixa.id}"), montagem.pedidos("DELETE"))
    }

    @Test
    fun `a receber pede quem, valor e data antes de guardar`() = runTest {
        val montagem = montar(pasta.root, loja)
        val receitas = montagem.receitas()
        receitas.estado.esperar { it.lista != null }
        // Sem turbine: a carga do mês ainda pode emitir no meio (CI lento); espera cada falta.
        receitas.guardarAReceber()
        receitas.estado.esperar { it.faltouNoAReceber == CampoDoAReceber.Quem }.let {
            assertEquals(MensagemDeErro.DoApp(R.string.a_receber_falta_quem), it.erro)
        }
        receitas.mudarQuem("João")
        receitas.guardarAReceber()
        receitas.estado.esperar { it.faltouNoAReceber == CampoDoAReceber.Valor }.let {
            assertEquals(MensagemDeErro.DoApp(R.string.a_receber_falta_valor), it.erro)
        }
        receitas.mudarValorAReceber(BigDecimal("25"))
        receitas.mudarDataAReceber("")
        receitas.guardarAReceber()
        receitas.estado.esperar { it.erro == MensagemDeErro.DoApp(R.string.a_receber_falta_data) }
        assertTrue(montagem.pedidos("POST /api/incomes/receivables").isEmpty())
    }

    @Test
    fun `guarda o a receber, recebe e vira receita de hoje, e apaga outro`() = runTest {
        val montagem = montar(pasta.root, loja)
        val receitas = montagem.receitas()
        receitas.estado.esperar { it.lista != null }
        receitas.mudarMes("2026-08")
        receitas.mudarQuem(" João ")
        receitas.mudarMotivo("Metade da pizza")
        receitas.mudarValorAReceber(BigDecimal("25"))
        receitas.mudarDataAReceber("2020-01-10")
        receitas.guardarAReceber()
        val guardado = receitas.estado.esperar { it.aReceber.isNotEmpty() && !it.guardando }
        assertEquals(
            listOf("""POST /api/incomes/receivables {"quem":"João","descricao":"Metade da pizza","valor":25,"data":"2020-01-10"}"""),
            montagem.pedidos("POST /api/incomes/receivables"),
        )
        assertEquals(Aviso(R.string.a_receber_guardado), guardado.aviso)
        assertEquals("", guardado.quem)
        assertNull(guardado.valorAReceber)
        val pizza = guardado.aReceber.single()
        assertTrue(guardado.atrasada(pizza))
        assertFalse(guardado.atrasada(pizza.copy(data = "2026-10-28")))

        receitas.receber(pizza)
        val recebido = receitas.estado.esperar { it.aReceber.isEmpty() && it.lista?.any { r -> r.descricao == "João · Metade da pizza" } == true }
        assertEquals(Aviso(R.string.a_receber_pagou, listOf("João")), recebido.aviso)
        assertEquals("2026-10", recebido.mes)
        assertTrue(montagem.pedidos("POST /api/incomes/receivables/${pizza.id}/receive").isNotEmpty())

        receitas.mudarQuem("Bia")
        receitas.mudarValorAReceber(BigDecimal("40"))
        receitas.mudarDataAReceber("2026-11-10")
        receitas.guardarAReceber()
        val bia = receitas.estado.esperar { it.aReceber.isNotEmpty() }.aReceber.single()
        receitas.pedirParaApagarAReceber(bia)
        assertEquals(Confirmacao.ApagarAReceber(bia), receitas.estado.value.confirmacao)
        receitas.confirmar()
        receitas.estado.esperar { it.aReceber.isEmpty() }
        assertEquals(listOf("DELETE /api/incomes/receivables/${bia.id}"), montagem.pedidos("DELETE"))
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        val montagem = montar(pasta.root, loja)
        montagem.receitas().estado.esperar { it.lista != null && it.aReceber.isEmpty() }
        montagem.guardado.respostas.getValue("receitas/2026-10") // guardou para depois

        val semRede = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun listarReceitas(mes: String): List<Receita> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        montagem.conectado.value = false
        val offline = montagem.receitas(semRede)
        val guardado = offline.estado.esperar { it.guardadoEm != null }
        assertEquals(2, guardado.lista!!.size)
        assertFalse(guardado.conectado)
        assertNotNull(guardado.guardadoEm)

        offline.mudarMes("2026-07")
        val vazio = offline.estado.esperar { it.mes == "2026-07" && it.receitas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.receitas as Carga.Falhou).erro))
        assertNull(vazio.guardadoEm)
    }

    @Test
    fun `erro do servidor na lista pede tentar de novo`() = runTest {
        var falhar = true
        val montagem = montar(pasta.root, loja)
        val instavel = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun listarReceitas(mes: String): List<Receita> =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else montagem.rede.financas.listarReceitas(mes)
        }
        val receitas = montagem.receitas(instavel)
        val falhou = receitas.estado.esperar { it.receitas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((falhou.receitas as Carga.Falhou).erro))
        falhar = false
        receitas.tentarDeNovo()
        assertEquals(2, receitas.estado.esperar { it.lista != null }.lista!!.size)
    }
}
