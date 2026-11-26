package br.com.piggu.core.data

import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.Usuario
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Quem vê o banner do plano gratuito (D-180). */
class AnunciosTest {
    private val hoje = LocalDate.of(2026, 11, 8)

    private fun usuario(plano: Plano, role: PigguRole, nascimento: String? = "1990-01-01") = Usuario(
        id = "u1", email = "ana@example.com", nome = "Ana Souza", primeiroNome = "Ana",
        role = role, ativo = true, plano = plano, familia = "f1", nascimento = nascimento,
    )

    @Test fun `gratuito logado e adulto ve`() =
        assertTrue(usuario(Plano.GRATUITO, PigguRole.TITULAR).deveMostrarAnuncios(demo = false, hoje = hoje))

    @Test fun `premium nao ve`() =
        assertFalse(usuario(Plano.PREMIUM, PigguRole.TITULAR).deveMostrarAnuncios(demo = false, hoje = hoje))

    @Test fun `admin nao ve, mesmo no gratuito`() =
        assertFalse(usuario(Plano.GRATUITO, PigguRole.ADMIN).deveMostrarAnuncios(demo = false, hoje = hoje))

    @Test fun `demonstracao nao ve`() =
        assertFalse(usuario(Plano.GRATUITO, PigguRole.MEMBRO).deveMostrarAnuncios(demo = true, hoje = hoje))

    @Test fun `plano desconhecido nao ve`() =
        assertFalse(usuario(Plano.DESCONHECIDO, PigguRole.MEMBRO).deveMostrarAnuncios(demo = false, hoje = hoje))

    @Test fun `sem usuario nao ve`() = assertFalse((null as Usuario?).deveMostrarAnuncios(demo = false, hoje = hoje))

    @Test fun `fez 18 hoje ve`() =
        assertTrue(usuario(Plano.GRATUITO, PigguRole.TITULAR, "2008-11-08").deveMostrarAnuncios(false, hoje))

    @Test fun `faz 18 amanha nao ve`() =
        assertFalse(usuario(Plano.GRATUITO, PigguRole.MEMBRO, "2008-11-09").deveMostrarAnuncios(false, hoje))

    @Test fun `sem nascimento nao ve`() =
        assertFalse(usuario(Plano.GRATUITO, PigguRole.TITULAR, null).deveMostrarAnuncios(false, hoje))

    @Test fun `nascimento ilegivel nao ve`() =
        assertFalse(usuario(Plano.GRATUITO, PigguRole.TITULAR, "xx").deveMostrarAnuncios(false, hoje))

    @Test fun `o padrao de hoje e o dia em Brasilia`() =
        assertTrue(usuario(Plano.GRATUITO, PigguRole.MEMBRO).deveMostrarAnuncios(demo = false))
}
