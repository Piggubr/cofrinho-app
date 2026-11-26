package br.com.piggu.app

import br.com.piggu.app.moldura.ABAS_COM_ANUNCIO
import br.com.piggu.app.navegacao.Tela
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.Usuario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O banner (D-180) segue o estado da sessão: assinar ou trocar de conta muda a moldura na hora. */
class AnunciosNaMolduraTest {
    private fun conta(id: String, plano: Plano) = Usuario(
        id = id, email = "$id@example.com", nome = "Ana Souza", primeiroNome = "Ana",
        role = PigguRole.TITULAR, ativo = true, plano = plano, familia = "f-$id", nascimento = "1990-01-01",
    )

    @Test
    fun `assinar o Premium tira o banner`() {
        val gratuita = conta("u1", Plano.GRATUITO)
        assertTrue(gratuita.paraAMoldura(cotacao = null, demo = false).mostraAnuncios)
        assertFalse(gratuita.copy(plano = Plano.PREMIUM).paraAMoldura(cotacao = null, demo = false).mostraAnuncios)
    }

    @Test
    fun `trocar de conta segue a conta nova`() {
        val molduras = listOf(conta("u1", Plano.PREMIUM), conta("u2", Plano.GRATUITO), conta("u3", Plano.PREMIUM))
            .map { it.paraAMoldura(cotacao = null, demo = false).mostraAnuncios }
        assertEquals(listOf(false, true, false), molduras)
    }

    @Test
    fun `a demonstracao nunca mostra`() =
        assertFalse(conta("u1", Plano.GRATUITO).paraAMoldura(cotacao = null, demo = true).mostraAnuncios)

    @Test
    fun `so o Painel e os Lancamentos`() =
        assertEquals(setOf(Tela.Painel, Tela.Gastos, Tela.Receitas), ABAS_COM_ANUNCIO)
}
