package br.com.piggu.core.ui.formatos

import java.text.Collator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// regras-da-senha.spec.ts, documentos.spec.ts e o celular de login.spec.ts.
class EntradaTest {

    @Test
    fun `senha aceita 8 ou mais caracteres com letra e numero`() {
        assertNull(problemaDaSenha("cofrinho1"))
        assertNull(problemaDaSenha("ção12345"))
    }

    @Test
    fun `senha recusa curta, sem letra, sem numero, longa demais ou igual ao e-mail`() {
        assertEquals(ProblemaDaSenha.Curta, problemaDaSenha("abc123"))
        assertEquals(ProblemaDaSenha.SemLetraOuNumero, problemaDaSenha("12345678"))
        assertEquals(ProblemaDaSenha.SemLetraOuNumero, problemaDaSenha("abcdefgh"))
        assertEquals(ProblemaDaSenha.Longa, problemaDaSenha("a1" + "x".repeat(71)))
        assertEquals(ProblemaDaSenha.IgualAoEmail, problemaDaSenha("Ana1@piggu.test", "ana1@piggu.test"))
    }

    @Test
    fun `todo pais da lista tem DDI`() {
        assertEquals(emptyList<Pais>(), paises.filterNot { Regex("[1-9]\\d{0,2}").matches(ddiDoPais(it.codigo)) })
        assertEquals("55", ddiDoPais("BR"))
        assertEquals("351", ddiDoPais("PT"))
    }

    @Test
    fun `lista os paises em portugues, com o Brasil primeiro e o resto em ordem`() {
        assertEquals(Pais(BRASIL, "Brasil"), paises[0])
        assertTrue(paises.size > 240)
        assertEquals("Portugal", nomeDoPais("PT"))
        val nomes = paises.drop(1).map { it.nome }
        assertEquals(nomes.sortedWith(Collator.getInstance(PtBr)), nomes)
        assertFalse(paises.any { it.codigo == "AQ" })
    }

    @Test
    fun `celular pede 6 digitos e vai com o DDI`() {
        assertFalse(celularValido("(11) 9"))
        assertTrue(celularValido("(11) 99999-8888"))
        assertEquals("+55 (11) 99999-8888", celularCompleto(BRASIL, " (11) 99999-8888 "))
        assertEquals("+351 912 345 678", celularCompleto("PT", "912 345 678"))
    }
}
