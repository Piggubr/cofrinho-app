package br.com.piggu.app

import androidx.navigation3.runtime.NavKey
import br.com.piggu.app.navegacao.Tela
import org.junit.Assert.assertEquals
import org.junit.Test

class NavegacaoTest {
    @Test
    fun `outra aba fica em cima do Painel`() {
        val pilha = mutableListOf<NavKey>(Tela.Painel)
        pilha.irPara(Tela.Gastos)
        pilha.irPara(Tela.Relatorios)
        assertEquals(listOf(Tela.Painel, Tela.Relatorios), pilha)
    }

    @Test
    fun `voltar ao Painel limpa a pilha`() {
        val pilha = mutableListOf<NavKey>(Tela.Painel, Tela.Mais)
        pilha.irPara(Tela.Painel)
        assertEquals(listOf<NavKey>(Tela.Painel), pilha)
    }

    @Test
    fun `tocar na aba aberta nao muda nada`() {
        val pilha = mutableListOf<NavKey>(Tela.Painel, Tela.Gastos)
        pilha.irPara(Tela.Gastos)
        assertEquals(listOf(Tela.Painel, Tela.Gastos), pilha)
    }
}

class OrdemDeCargaTest {
    /** Regressão de 28/10/2026: o Painel carregado primeiro deixava a lista das abas com nulo. */
    @Test
    fun `as abas existem mesmo com uma tela carregada antes`() {
        val painel = Tela.Painel
        assertEquals(true, painel in Tela.abas)
        assertEquals(false, Tela.abas.any { it == null })
    }
}
