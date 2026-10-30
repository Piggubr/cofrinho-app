package br.com.piggu.app.moldura

import br.com.piggu.app.navegacao.Tela
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuTest {
    private fun telas(grupos: List<Pair<GrupoDoMenu, List<ItemDoMenu>>>) = grupos.flatMap { it.second }.map { it.tela }

    @Test
    fun `titular ve o menu inteiro nos grupos e na ordem do site`() {
        val grupos = gruposVisiveis(ehTitular = true, modulos = null)
        assertEquals(GrupoDoMenu.entries, grupos.map { it.first })
        assertEquals(ITENS_DO_MENU.map { it.tela }, telas(grupos))
    }

    @Test
    fun `parceiro e membro veem tudo, menos o Premium`() {
        val telas = telas(gruposVisiveis(ehTitular = false, modulos = null))
        assertFalse(Tela.Plano in telas)
        assertEquals(ITENS_DO_MENU.size - 1, telas.size)
    }

    @Test
    fun `modulos desligados somem e sem a lista aparecem todos`() {
        val telas = telas(gruposVisiveis(ehTitular = true, modulos = listOf("filmes")))
        assertTrue(Tela.Filmes in telas)
        listOf(Tela.Compras, Tela.Lugares, Tela.Feed, Tela.Premios, Tela.GirlsMath).forEach { assertFalse(it in telas) }
        // Sem nenhum módulo, o grupo Estilo de vida some inteiro.
        assertFalse(GrupoDoMenu.Estilo in gruposVisiveis(ehTitular = true, modulos = emptyList()).map { it.first })
    }

    @Test
    fun `no Mais do celular as abas nao se repetem`() {
        val grupos = gruposVisiveis(ehTitular = true, modulos = null, semAsAbas = true)
        assertFalse(GrupoDoMenu.DiaADia in grupos.map { it.first })
        assertEquals(Tela.Planejar, grupos.first().second.first().tela)
    }

    @Test
    fun `as abas do celular sao Painel, Gastos, Receitas e Relatorios`() {
        assertEquals(listOf(Tela.Painel, Tela.Gastos, Tela.Receitas, Tela.Relatorios), ITENS_DAS_ABAS.map { it.tela })
    }
}
