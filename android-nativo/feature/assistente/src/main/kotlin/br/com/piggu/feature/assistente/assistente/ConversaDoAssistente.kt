package br.com.piggu.feature.assistente.assistente

import br.com.piggu.core.datastore.ConversaGuardada
import br.com.piggu.core.model.JsonDoPiggu
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** Quanto tempo a conversa fica no aparelho. Igual ao que diz o aviso de privacidade. */
const val DIAS_GUARDADA = 30

/** Teto de falas guardadas, para o aparelho não crescer sem fim (FALAS_GUARDADAS do site). */
internal const val FALAS_GUARDADAS = 200

/** Uma fala da conversa; [pessoa] falso é a resposta, que pode sugerir uma [tela]. [em] em ISO. */
@Serializable
data class FalaGuardada(val pessoa: Boolean, val texto: String, val tela: String? = null, val em: String)

@Serializable
internal data class Conversa(val usuario: String, val falas: List<FalaGuardada>)

/**
 * A conversa com o assistente só neste aparelho, por [DIAS_GUARDADA] dias (conversa-do-assistente.ts).
 * O servidor não guarda nada. Uma por aparelho, da última pessoa que conversou; outra pessoa que
 * entre não vê. Sai inteira ao sair da conta ([ConversaGuardada.esquecerConversa]).
 */
class ConversaDoAssistente @Inject constructor(private val guardada: ConversaGuardada, private val relogio: Clock) {

    suspend fun ler(usuario: String): List<FalaGuardada> = podar()?.takeIf { it.usuario == usuario }?.falas.orEmpty()

    /** Tira as falas com mais de [DIAS_GUARDADA] dias, de quem quer que seja. */
    internal suspend fun podar(): Conversa? {
        val texto = guardada.ler() ?: return null
        return try {
            val conversa = JsonDoPiggu.decodeFromString(Conversa.serializer(), texto)
            val limite = relogio.instant() - Duration.ofDays(DIAS_GUARDADA.toLong())
            val falas = conversa.falas.filter { !Instant.parse(it.em).isBefore(limite) }
            if (falas.size != conversa.falas.size) guardar(conversa.usuario, falas)
            conversa.copy(falas = falas)
        } catch (_: IllegalArgumentException) {
            // JSON corrompido (SerializationException é um IllegalArgumentException): não quebra a tela.
            esquecer()
            null
        } catch (_: DateTimeParseException) {
            esquecer()
            null
        }
    }

    suspend fun guardar(usuario: String, falas: List<FalaGuardada>) {
        if (falas.isEmpty()) {
            esquecer()
        } else {
            guardada.guardar(JsonDoPiggu.encodeToString(Conversa.serializer(), Conversa(usuario, falas.takeLast(FALAS_GUARDADAS))))
        }
    }

    suspend fun esquecer() = guardada.esquecerConversa()

    fun agora(): String = relogio.instant().toString()
}
