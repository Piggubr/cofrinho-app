package br.com.piggu.feature.premios

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.Premio
import br.com.piggu.core.model.SaldoDeMoedas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A tela sem estado: só quem cuida dos prêmios vê ajustar, novo, editar e remover (rewards.spec.ts). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDosPremiosTest {
    @get:Rule val compose = createComposeRule()

    private fun abrir(cuida: Boolean) {
        val dados = DadosDosPremios(SaldoDeMoedas(100), listOf(Premio("p1", "Pizza", "Sexta", 50, true), Premio("p2", "Cinema", null, 200, true)), emptyList())
        compose.setContent { PigguTheme { TelaDosPremios(EstadoDosPremios(cuidaDosPremios = cuida, carga = Carga.Pronto(dados)), EventosDosPremios()) } }
    }

    @Test
    fun `quem cuida ve ajustar, novo, editar e remover`() {
        abrir(cuida = true)
        compose.onNodeWithText("Ajustar").assertExists()
        compose.onNodeWithContentDescription("Novo prêmio").assertExists()
        compose.onNodeWithContentDescription("Editar Pizza").assertExists()
        compose.onNodeWithContentDescription("Remover Pizza").assertExists()
    }

    @Test
    fun `o membro so resgata, e so o que o saldo paga`() {
        abrir(cuida = false)
        compose.onNodeWithText("Ajustar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Novo prêmio").assertDoesNotExist()
        compose.onNodeWithContentDescription("Editar Pizza").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remover Pizza").assertDoesNotExist()
        compose.onNodeWithContentDescription("Resgatar Pizza").assertIsEnabled()
        compose.onNodeWithContentDescription("Resgatar Cinema").assertIsNotEnabled()
    }
}
