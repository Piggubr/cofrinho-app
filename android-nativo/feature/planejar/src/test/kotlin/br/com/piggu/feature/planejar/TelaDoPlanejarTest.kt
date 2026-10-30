package br.com.piggu.feature.planejar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasClickAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.designsystem.PigguTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O Planejar junta as telas que olham para a frente (plan-hub.spec.ts). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDoPlanejarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `leva ao Orcamento e as Contas do mes pelo caminho do site, e volta`() {
        val abertos = mutableListOf<String>()
        var voltou = false
        compose.setContent { PigguTheme { TelaDoPlanejar(voltar = { voltou = true }, abrirTela = { abertos += it }) } }

        assertEquals(listOf("/orcamentos", "/contas-fixas"), ATALHOS_DO_PLANEJAR.map { it.caminho })
        compose.onNodeWithText("80% e 100%", substring = true).assertExists()
        compose.onNodeWithText("Orçamento").assertHasClickAction().performClick()
        compose.onNodeWithText("Contas do mês").performClick()
        assertEquals(listOf("/orcamentos", "/contas-fixas"), abertos)

        compose.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(true, voltou)
    }
}
