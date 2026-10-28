package br.com.piggu.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeradorDePerfil {
    @get:Rule
    val regra = BaselineProfileRule()

    // Abre o app até a primeira tela. As etapas seguintes acrescentam os caminhos principais.
    @Test
    fun abrir() = regra.collect(
        // O plugin passa o id da variante (br.com.piggu.app ou br.com.piggu.app.demo).
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId")!!,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
