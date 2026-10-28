package br.com.piggu.feature.painel

import androidx.annotation.StringRes
import br.com.piggu.core.model.JsonDoPiggu
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cartão do Painel, com o id do site (core/ui/painel.ts), para a ordem salva valer igual. Na ordem
 * padrão; [padrao] diz se aparece de início (o que só vive no Painel) ou se a pessoa liga.
 */
enum class IdDoBloco(val id: String, val padrao: Boolean, @param:StringRes val nome: Int) {
    Resumo("resumo", true, R.string.bloco_resumo),
    Avisos("avisos", true, R.string.bloco_avisos),
    Mes("mes", true, R.string.bloco_mes),
    Cofrinho("cofrinho", true, R.string.bloco_cofrinho),
    Objetivos("objetivos", true, R.string.bloco_objetivos),
    Metas("metas", false, R.string.bloco_metas),
    Categorias("categorias", false, R.string.bloco_categorias),
    Notas("notas", false, R.string.bloco_notas),
    GirlsMath("girls-math", false, R.string.bloco_girls_math),
    ;

    companion object {
        fun deId(id: String?): IdDoBloco? = entries.firstOrNull { it.id == id }
    }
}

/** Um cartão e se aparece: `{"id":"resumo","visivel":true}`, como o site grava. */
@Serializable
data class BlocoDoPainel(val id: String, val visivel: Boolean) {
    val bloco: IdDoBloco get() = IdDoBloco.deId(id) ?: error("bloco desconhecido: $id")
}

/**
 * Ordem e visibilidade dos cartões (PainelOrganizado do site e do iOS), sem estado: quem guarda é
 * o [br.com.piggu.core.datastore.PainelDoAparelho], na mesma chave e no mesmo JSON do site.
 */
object PainelOrganizado {
    val padrao: List<BlocoDoPainel> = IdDoBloco.entries.map { BlocoDoPainel(it.id, it.padrao) }

    private val serializador = ListSerializer(BlocoDoPainel.serializer())

    /**
     * Junta o que estava salvo com a lista atual: bloco que deixou de existir sai, repetido conta
     * uma vez, sem "visivel" aparece, e bloco novo entra no fim como no padrão. Lixo vira o padrão.
     */
    fun ordemValida(json: String?): List<BlocoDoPainel> {
        val itens = json?.let { runCatching { JsonDoPiggu.parseToJsonElement(it) as? JsonArray }.getOrNull() } ?: return padrao
        val vistos = mutableSetOf<String>()
        val blocos = itens.mapNotNull { item ->
            val campos = item as? JsonObject ?: return@mapNotNull null
            val id = campos["id"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            if (IdDoBloco.deId(id) == null || !vistos.add(id!!)) return@mapNotNull null
            val visivel = campos["visivel"]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
            BlocoDoPainel(id, visivel != false)
        }
        return blocos + padrao.filter { it.id !in vistos }
    }

    /** O JSON.stringify do site: `[{"id":"resumo","visivel":true},...]`. */
    fun paraJson(blocos: List<BlocoDoPainel>): String = JsonDoPiggu.encodeToString(serializador, blocos)

    /** Sobe (-1) ou desce (1) um lugar; o primeiro não sobe e o último não desce. */
    fun mover(blocos: List<BlocoDoPainel>, bloco: IdDoBloco, passo: Int): List<BlocoDoPainel> {
        val de = blocos.indexOfFirst { it.id == bloco.id }
        return if (de < 0 || de + passo !in blocos.indices) blocos else moverPara(blocos, bloco, de + passo)
    }

    /** Leva o bloco para a posição, empurrando os outros: o soltar do arrastar. */
    fun moverPara(blocos: List<BlocoDoPainel>, bloco: IdDoBloco, indice: Int): List<BlocoDoPainel> {
        val de = blocos.indexOfFirst { it.id == bloco.id }
        val para = indice.coerceIn(0, blocos.lastIndex)
        if (de < 0 || de == para) return blocos
        return blocos.toMutableList().apply { add(para, removeAt(de)) }
    }

    fun alternar(blocos: List<BlocoDoPainel>, bloco: IdDoBloco): List<BlocoDoPainel> =
        blocos.map { if (it.id == bloco.id) it.copy(visivel = !it.visivel) else it }
}

/** O guia de primeiros passos neste aparelho (`piggu_guia_<id>`): `{"moeda":true,"fechado":true}`. */
@Serializable
data class EstadoDoGuia(val moeda: Boolean? = null, val fechado: Boolean? = null) {
    fun paraJson(): String = JsonDoPiggu.encodeToString(serializer(), this)

    companion object {
        fun deJson(json: String?): EstadoDoGuia =
            json?.let { runCatching { JsonDoPiggu.decodeFromString(serializer(), it) }.getOrNull() } ?: EstadoDoGuia()
    }
}
