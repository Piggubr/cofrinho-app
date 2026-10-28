package br.com.piggu.app.navegacao

import java.math.BigDecimal
import java.net.URI
import java.net.URLDecoder

/** Para onde um link leva: uma tela, o lançar gasto ou um gasto de um mês. */
sealed interface Destino {
    data class Abrir(val tela: Tela) : Destino

    /** piggu://gasto?valor=12.50 (atalho, Siri do app antigo, widget); valor ruim abre em branco. */
    data class LancarGasto(val valor: BigDecimal?) : Destino

    /** piggu://gastos?id=...&mes=aaaa-mm; sem o mês, o atual (D-097 do iOS). */
    data class VerGasto(val id: String, val mes: String?) : Destino
}

private val DOMINIOS = setOf("piggu.com.br", "www.piggu.com.br")

/** O mesmo teto do backend (ExpenseItemRequest). */
private val VALOR_MAXIMO = BigDecimal(1_000_000)

private val MES = Regex("""\d{4}-(0[1-9]|1[0-2])""")

/** Só caminho interno do app: nada de endereço de fora vindo de um link (rotaInterna de atalhos.ts). */
fun rotaInterna(rota: String?): Boolean = rota != null && rota.startsWith("/") && !rota.startsWith("//")

/**
 * Lê um link do app: `piggu://...`, `https://piggu.com.br/...` ou um caminho interno (`/gastos`).
 * Aceita os links do app Capacitor que já estão em atalhos salvos (`piggu://gastos?rapido=1&valor=12.50`).
 * Endereço de fora devolve nulo; caminho do app que não existe vai para a 404, como no site.
 */
fun lerLink(link: String, lerValor: (String) -> BigDecimal?): Destino? {
    val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
    val pedacos = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }.toMutableList()
    when (uri.scheme?.lowercase()) {
        // piggu://gastos/x: o host é o primeiro pedaço do caminho.
        "piggu" -> uri.host?.let { pedacos.add(0, it) }
        "https" -> if (uri.host?.lowercase() !in DOMINIOS) return null
        null -> if (uri.host != null || !rotaInterna(link.trim())) return null
        else -> return null
    }
    val consulta = consultaDe(uri.rawQuery)
    fun valor() = consulta["valor"]?.let(lerValor)?.takeIf { it > BigDecimal.ZERO && it <= VALOR_MAXIMO }

    val primeiro = pedacos.firstOrNull() ?: return Destino.Abrir(Tela.Painel)
    return when {
        primeiro == "gasto" -> Destino.LancarGasto(valor())
        primeiro == "gastos" && !consulta["id"].isNullOrBlank() ->
            Destino.VerGasto(consulta.getValue("id"), consulta["mes"]?.takeIf { MES.matches(it) })
        primeiro == "gastos" && consulta["rapido"] == "1" -> Destino.LancarGasto(valor())
        primeiro == "confirmar-email" -> Destino.Abrir(Tela.ConfirmarEmail(consulta["token"].orEmpty()))
        primeiro == "redefinir-senha" -> Destino.Abrir(Tela.RedefinirSenha(consulta["token"].orEmpty()))
        else -> Destino.Abrir(TELAS_POR_CAMINHO[primeiro] ?: Tela.NaoEncontrada)
    }
}

private val TELAS_POR_CAMINHO: Map<String, Tela> = listOf(
    Tela.Entrar, Tela.Privacidade, Tela.Termos, Tela.EsqueciSenha, Tela.Aceite, Tela.SobreVoce, Tela.Cpf,
    Tela.Painel, Tela.Gastos, Tela.Receitas, Tela.Relatorios, Tela.Mais, Tela.Perfil, Tela.Familia, Tela.Plano,
    Tela.ContasECartoes, Tela.ContasFixas, Tela.Orcamentos, Tela.Calendario, Tela.Compras, Tela.Lugares,
    Tela.Filmes, Tela.Feed, Tela.Premios, Tela.Planejar, Tela.Metas, Tela.GirlsMath, Tela.Erro,
).associateBy { it.caminho.removePrefix("/") } + ("fotos" to Tela.Feed)

private fun consultaDe(bruta: String?): Map<String, String> =
    bruta.orEmpty().split('&').filter { it.isNotEmpty() }.associate { par ->
        val (nome, valor) = par.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        decodificar(nome) to decodificar(valor)
    }

private fun decodificar(texto: String) = runCatching { URLDecoder.decode(texto, Charsets.UTF_8) }.getOrDefault(texto)
