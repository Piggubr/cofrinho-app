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
