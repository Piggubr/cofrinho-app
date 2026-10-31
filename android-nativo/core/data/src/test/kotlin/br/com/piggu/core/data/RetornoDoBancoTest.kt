package br.com.piggu.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A volta da página /conectar-banco (OpenFinanceTests do iOS). */
class RetornoDoBancoTest {
    @Test
    fun `le o item, o erro ou o fechou`() {
        assertEquals(ResultadoDoBanco.Conectado("item 1"), lerVoltaDoBanco("piggu://banco-conectado?item=item%201"))
        assertEquals(ResultadoDoBanco.Falhou("Senha errada."), lerVoltaDoBanco("piggu://banco-conectado?erro=Senha%20errada."))
        assertEquals(ResultadoDoBanco.Fechou, lerVoltaDoBanco("piggu://banco-conectado?fechou=1"))
        assertEquals(ResultadoDoBanco.Fechou, lerVoltaDoBanco("piggu://banco-conectado?item="))
    }

    @Test
    fun `outros links nao sao a volta do banco`() {
        assertNull(lerVoltaDoBanco("piggu://gastos"))
        assertNull(lerVoltaDoBanco("https://banco-conectado/?item=x"))
        assertNull(lerVoltaDoBanco("https://piggu.com.br/conectar-banco"))
    }

    @Test
    fun `guarda ate alguem usar`() {
        val retorno = RetornoDoBanco()
        assertFalse(retorno.receber("piggu://painel"))
        assertNull(retorno.resultado.value)
        assertTrue(retorno.receber("piggu://banco-conectado?item=i1"))
        assertEquals(ResultadoDoBanco.Conectado("i1"), retorno.resultado.value)
        retorno.usar()
        assertNull(retorno.resultado.value)
    }
}
