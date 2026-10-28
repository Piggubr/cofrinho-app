package br.com.piggu.core.ui.formatos

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// cpf.spec.ts, email.spec.ts e nascimento.spec.ts.
class CadastroTest {

    @Test
    fun `CPF confere os digitos verificadores`() {
        assertTrue(cpfValido("529.982.247-25"))
        assertTrue(cpfValido("11144477735"))
        assertFalse(cpfValido("529.982.247-24"))
        assertFalse(cpfValido("111.111.111-11"))
        assertFalse(cpfValido("1234567890"))
    }

    @Test
    fun `CPF formata enquanto digita e corta no 11o digito`() {
        assertEquals("529", formatarCpf("529"))
        assertEquals("529.982.2", formatarCpf("5299822"))
        assertEquals("529.982.247-25", formatarCpf("529982247251234"))
        assertEquals("52998224725", digitosDoCpf("529.982.247-25"))
    }

    @Test
    fun `e-mail aceita o formato nome@dominio_algo`() {
        assertTrue(pareceEmail("ana@piggu.com.br"))
        assertTrue(pareceEmail("a@b.c"))
        assertTrue(pareceEmail("a@b.c."))
    }

    @Test
    fun `e-mail recusa sem arroba, com dois, com espaco ou sem ponto no meio do dominio`() {
        listOf(
            "ana.piggu.com", "@piggu.com", "ana@@piggu.com", "a@b@c.com", "ana @piggu.com",
            "ana@piggu", "ana@.com", "ana@piggu.",
        ).forEach { assertFalse(it, pareceEmail(it)) }
    }

    @Test(timeout = 1_000)
    fun `e-mail nao demora num texto comprido cheio de pontos`() {
        assertFalse(pareceEmail("a@" + ".".repeat(100_000) + " "))
    }

    private val hoje = LocalDate.of(2026, 10, 28)
    private fun anosAtras(anos: Long) = hoje.minusYears(anos).toString()

    @Test
    fun `nascimento aceita quem tem 13 anos ou mais`() {
        assertNull(problemaDoNascimento(anosAtras(13), hoje))
        assertNull(problemaDoNascimento("1990-05-20", hoje))
    }

    @Test
    fun `nascimento recusa menor de 13, data vazia e data no futuro`() {
        assertEquals(ProblemaDoNascimento.MenorDeIdade, problemaDoNascimento(anosAtras(12), hoje))
        assertEquals(ProblemaDoNascimento.MenorDeIdade, problemaDoNascimento(hoje.minusYears(13).plusDays(1).toString(), hoje))
        assertEquals(ProblemaDoNascimento.Vazia, problemaDoNascimento("", hoje))
        assertEquals(ProblemaDoNascimento.Invalida, problemaDoNascimento(anosAtras(-1), hoje))
        assertEquals(ProblemaDoNascimento.Invalida, problemaDoNascimento("1900-01-01", hoje))
        assertEquals(ProblemaDoNascimento.Invalida, problemaDoNascimento("2000-02-30", hoje))
    }
}
