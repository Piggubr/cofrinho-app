package br.com.piggu.app.navegacao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class LinksTest {
    private fun ler(link: String) = lerLink(link) { it.toBigDecimalOrNull() }

    @Test
    fun `atalho do app antigo e o curto lancam o gasto com o valor`() {
        assertEquals(Destino.LancarGasto(BigDecimal("12.50")), ler("piggu://gastos?rapido=1&valor=12.50"))
        assertEquals(Destino.LancarGasto(BigDecimal("12.50")), ler("piggu://gasto?valor=12.50"))
    }

    @Test
    fun `valor ilegivel, zero ou acima do teto abre o lancamento em branco`() {
        assertEquals(Destino.LancarGasto(null), ler("piggu://gasto?valor=abc"))
        assertEquals(Destino.LancarGasto(null), ler("piggu://gasto?valor=0"))
        assertEquals(Destino.LancarGasto(null), ler("piggu://gasto?valor=1000000.01"))
        assertEquals(Destino.LancarGasto(null), ler("piggu://gasto"))
    }

    @Test
    fun `gasto de um mes, e o mes invalido vira o atual`() {
        assertEquals(Destino.VerGasto("g1", "2026-10"), ler("piggu://gastos?id=g1&mes=2026-10"))
        assertEquals(Destino.VerGasto("g1", null), ler("piggu://gastos?id=g1&mes=2026-13"))
        assertEquals(Destino.VerGasto("g1", null), ler("piggu://gastos?id=g1"))
    }

    @Test
    fun `rotas do site pelo esquema do app, pelo dominio e pelo caminho interno`() {
        assertEquals(Destino.Abrir(Tela.Orcamentos), ler("piggu://orcamentos"))
        assertEquals(Destino.Abrir(Tela.Receitas), ler("https://piggu.com.br/receitas"))
        assertEquals(Destino.Abrir(Tela.Metas), ler("https://www.piggu.com.br/metas"))
        assertEquals(Destino.Abrir(Tela.GirlsMath), ler("/girls-math"))
        assertEquals(Destino.Abrir(Tela.Feed), ler("piggu://fotos"))
        assertEquals(Destino.Abrir(Tela.Painel), ler("https://piggu.com.br/"))
    }

    @Test
    fun `links dos e-mails levam o token`() {
        assertEquals(Destino.Abrir(Tela.ConfirmarEmail("abc")), ler("https://piggu.com.br/confirmar-email?token=abc"))
        assertEquals(Destino.Abrir(Tela.RedefinirSenha("x y")), ler("https://piggu.com.br/redefinir-senha?token=x%20y"))
    }

    @Test
    fun `caminho do app que nao existe vai para a 404`() {
        assertEquals(Destino.Abrir(Tela.NaoEncontrada), ler("piggu://nao-existe"))
        assertEquals(Destino.Abrir(Tela.NaoEncontrada), ler("/qualquer/coisa"))
    }

    @Test
    fun `endereco de fora nao abre nada`() {
        assertNull(ler("https://exemplo.com/painel"))
        assertNull(ler("http://piggu.com.br/painel"))
        assertNull(ler("//exemplo.com/painel"))
        assertNull(ler("javascript:alert(1)"))
        assertNull(ler("painel"))
    }
}
