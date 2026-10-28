package br.com.piggu.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavegacaoTest {
    @Test
    fun `outra aba fica em cima do Painel`() {
        val pilha = mutableListOf<NavKey>(Painel)
        pilha.irPara(Gastos)
        pilha.irPara(Relatorios)
        assertEquals(listOf(Painel, Relatorios), pilha)
    }

    @Test
    fun `voltar ao Painel limpa a pilha`() {
        val pilha = mutableListOf<NavKey>(Painel, Mais)
        pilha.irPara(Painel)
        assertEquals(listOf<NavKey>(Painel), pilha)
    }

    @Test
    fun `tocar na aba aberta nao muda nada`() {
        val pilha = mutableListOf<NavKey>(Painel, Gastos)
        pilha.irPara(Gastos)
        assertEquals(listOf(Painel, Gastos), pilha)
    }
}
