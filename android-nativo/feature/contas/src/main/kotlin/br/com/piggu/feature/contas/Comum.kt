package br.com.piggu.feature.contas

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.ui.FalhaAoCarregar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Um texto do app com os argumentos, para o estado não depender do Context. */
data class Aviso(@param:StringRes val texto: Int, val argumentos: List<Any> = emptyList())

@Composable
internal fun Aviso.texto(): String = stringResource(texto, *argumentos.toTypedArray())

@Composable
internal fun MensagemDeErro.texto(): String = texto(LocalContext.current)

/** Por que a lista não carregou, para o ícone e o texto do [br.com.piggu.core.ui.EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}

/** "28 de out., 10:00": quando o guardado foi gravado, como o AvisoDeGuardado do iOS. */
internal fun quandoGuardado(milissegundos: Long, zona: ZoneId): String =
    DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(milissegundos).atZone(zona))
