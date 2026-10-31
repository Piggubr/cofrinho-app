package br.com.piggu.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/** O que a página /conectar-banco do site devolveu ao app (OpenFinance.Resultado do iOS). */
sealed interface ResultadoDoBanco {
    data class Conectado(val item: String) : ResultadoDoBanco
    data object Fechou : ResultadoDoBanco
    data class Falhou(val mensagem: String) : ResultadoDoBanco
}

/**
 * A volta de conectar banco: o site abre a Pluggy num Custom Tab e responde em
 * `piggu://banco-conectado?item=...` (ou `?fechou=1`, `?erro=...`). A MainActivity entrega o link
 * aqui e Cartões e bancos registra o item. Fica guardado até alguém [usar], então vale também
 * quando a tela volta depois da activity.
 */
@Singleton
class RetornoDoBanco @Inject constructor() {
    private val ultimo = MutableStateFlow<ResultadoDoBanco?>(null)
    val resultado: StateFlow<ResultadoDoBanco?> = ultimo.asStateFlow()

    /** Falso se o link não é a volta de conectar banco. */
    fun receber(link: String): Boolean {
        val resultado = lerVoltaDoBanco(link) ?: return false
        ultimo.value = resultado
        return true
    }

    fun usar() {
        ultimo.value = null
    }
}

/** `piggu://banco-conectado?...`; nulo para qualquer outro link. */
fun lerVoltaDoBanco(link: String): ResultadoDoBanco? {
    val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "piggu" || uri.host != "banco-conectado") return null
    val consulta = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { par ->
        val (nome, valor) = par.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        nome to runCatching { URLDecoder.decode(valor, "UTF-8") }.getOrDefault(valor)
    }
    val item = consulta["item"]
    val erro = consulta["erro"]
    return when {
        !item.isNullOrBlank() -> ResultadoDoBanco.Conectado(item)
        erro != null -> ResultadoDoBanco.Falhou(erro)
        else -> ResultadoDoBanco.Fechou
    }
}
