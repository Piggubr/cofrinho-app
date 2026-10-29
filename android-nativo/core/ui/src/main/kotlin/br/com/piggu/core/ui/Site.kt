package br.com.piggu.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** O site do Piggu: as telas que ainda não são nativas abrem nele (D-090 do iOS). */
const val ENDERECO_DO_SITE = "https://piggu.com.br"

/**
 * Abre uma página do site num Custom Tab, com a barra na cor do tema. Sem navegador que
 * entenda Custom Tabs, abre no que houver.
 */
fun abrirNoSite(contexto: Context, caminho: String, corDaBarra: Int? = null) {
    val endereco = (ENDERECO_DO_SITE + caminho).toUri()
    val aba = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .apply { corDaBarra?.let { setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it).build()) } }
        .build()
    try {
        aba.launchUrl(contexto, endereco)
    } catch (_: ActivityNotFoundException) {
        runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, endereco).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** [abrirNoSite] com o contexto e a cor da tela atual. */
@Composable
fun lembrarAbrirNoSite(): (String) -> Unit {
    val contexto = LocalContext.current
    val cor = MaterialTheme.colorScheme.surface.toArgb()
    return remember(contexto, cor) { { caminho -> abrirNoSite(contexto, caminho, cor) } }
}
