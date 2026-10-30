package br.com.piggu.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ESPERA = 10_000L

/**
 * O caminho de quem abre o app todo dia, na variante demo (a API falsa responde sem backend):
 * entrar, o Painel e as abas. O perfil vale para as duas variantes (mergeIntoMain no app).
 */
@RunWith(AndroidJUnit4::class)
class GeradorDePerfil {
    @get:Rule
    val regra = BaselineProfileRule()

    // O app medido (br.com.piggu.app ou br.com.piggu.app.demo), conforme a variante (build.gradle.kts).
    private val pacote get() = InstrumentationRegistry.getArguments().getString("targetAppId")!!

    @Test
    fun abrir() = regra.collect(packageName = pacote) {
        pressHome()
        startActivityAndWait()
        // Só a demo entra: na produção, a conta de exemplo iria para a API de verdade.
        if (!pacote.endsWith(".demo")) return@collect
        entrarSePrecisar()
        device.wait(Until.hasObject(By.textStartsWith("Olá")), ESPERA)
        // O tour do primeiro acesso.
        device.findObject(By.text("Pular"))?.click()
        for (aba in listOf("Gastos", "Receitas", "Relatórios", "Painel")) {
            device.findObject(By.text(aba))?.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.entrarSePrecisar() {
        if (!device.wait(Until.hasObject(By.text("Esqueci minha senha")), ESPERA)) return
        val campos = device.findObjects(By.clazz("android.widget.EditText"))
        campos[0].text = "ana@demo.piggu.app"
        campos[1].text = "senha123"
        // O "Entrar" de baixo é o botão; o de cima é a aba.
        device.findObjects(By.text("Entrar")).maxBy { it.visibleBounds.top }.click()
    }
}
