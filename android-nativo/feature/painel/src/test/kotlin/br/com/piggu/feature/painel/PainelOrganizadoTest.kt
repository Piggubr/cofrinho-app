package br.com.piggu.feature.painel

import org.junit.Assert.assertEquals
import org.junit.Test

/** A ordem do Painel: os mesmos casos de painel.spec.ts e PainelOrganizadoTests.swift. */
class PainelOrganizadoTest {
    private val ids get() = IdDoBloco.entries.map { it.id }

    @Test
    fun `sem nada salvo e o padrao do site`() {
        val padrao = PainelOrganizado.ordemValida(null)
        assertEquals(listOf("resumo", "avisos", "mes", "cofrinho", "objetivos", "metas", "categorias", "notas", "girls-math"), padrao.map { it.id })
        assertEquals(listOf("resumo", "avisos", "mes", "cofrinho", "objetivos"), padrao.filter { it.visivel }.map { it.id })
    }

    @Test
    fun `junta o salvo pelo site com os blocos de hoje`() {
        // Bloco que não existe mais sai, repetido conta uma vez, sem "visivel" aparece,
        // e o que faltar entra no fim como no padrão.
        val salvo = """[{"id":"girls-math","visivel":true},{"id":"velho"},{"id":"mes","visivel":false},
            {"id":"girls-math","visivel":false},{"id":"cofrinho"},"lixo",{"id":7}]"""
        val blocos = PainelOrganizado.ordemValida(salvo)
        assertEquals(
            listOf(BlocoDoPainel("girls-math", true), BlocoDoPainel("mes", false), BlocoDoPainel("cofrinho", true)),
            blocos.take(3),
        )
        assertEquals(listOf("resumo", "avisos", "objetivos", "metas", "categorias", "notas"), blocos.drop(3).map { it.id })
        assertEquals(BlocoDoPainel("notas", false), blocos.last())
        assertEquals(PainelOrganizado.padrao, PainelOrganizado.ordemValida("não é json"))
        assertEquals(PainelOrganizado.padrao, PainelOrganizado.ordemValida("""{"id":"resumo"}"""))
    }

    @Test
    fun `o JSON e o mesmo do site`() {
        val blocos = PainelOrganizado.alternar(PainelOrganizado.padrao, IdDoBloco.Notas).take(2)
        assertEquals("""[{"id":"resumo","visivel":true},{"id":"avisos","visivel":true}]""", PainelOrganizado.paraJson(blocos))
        // O que o app grava volta igual, e o site lê o mesmo texto.
        val todos = PainelOrganizado.moverPara(PainelOrganizado.padrao, IdDoBloco.GirlsMath, 0)
        assertEquals(todos, PainelOrganizado.ordemValida(PainelOrganizado.paraJson(todos)))
    }

    @Test
    fun `subir, descer e arrastar`() {
        val padrao = PainelOrganizado.padrao
        // O primeiro não sobe e o último não desce.
        assertEquals(padrao, PainelOrganizado.mover(padrao, IdDoBloco.Resumo, -1))
        assertEquals(padrao, PainelOrganizado.mover(padrao, IdDoBloco.GirlsMath, 1))

        val subiu = PainelOrganizado.mover(padrao, IdDoBloco.GirlsMath, -1).map { it.id }
        assertEquals(subiu.indexOf("notas") - 1, subiu.indexOf("girls-math"))

        val arrastado = PainelOrganizado.moverPara(padrao, IdDoBloco.GirlsMath, 0).map { it.id }
        assertEquals(listOf("girls-math", "resumo", "avisos"), arrastado.take(3))
        assertEquals("resumo", PainelOrganizado.moverPara(padrao, IdDoBloco.Resumo, 99).last().id)
        assertEquals(ids.size, arrastado.toSet().size)
    }

    @Test
    fun `o guia guarda so o que mudou, como o site`() {
        assertEquals("""{"fechado":true}""", EstadoDoGuia(fechado = true).paraJson())
        assertEquals(EstadoDoGuia(moeda = true), EstadoDoGuia.deJson("""{"moeda":true}"""))
        assertEquals(EstadoDoGuia(), EstadoDoGuia.deJson("lixo"))
    }
}
