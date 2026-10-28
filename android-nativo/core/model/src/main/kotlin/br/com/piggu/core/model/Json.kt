package br.com.piggu.core.model

import java.math.BigDecimal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/**
 * O Json da API para o app inteiro (rede, cache e demonstração).
 *
 * A API só acrescenta campos (D-059): o app ignora o que não conhece. Nulo não vai no pedido,
 * e o padrão vai, para o servidor receber `refreshNoCorpo` e companhia.
 */
val JsonDoPiggu: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/** Número decimal da API (dinheiro, porcentagem, câmbio): BigDecimal, para não perder centavo. */
typealias Decimal = @Serializable(with = SerializadorDeDecimal::class) BigDecimal

/** Lê o número (ou o texto) do JSON como veio e escreve número, sem passar por Double. */
object SerializadorDeDecimal : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("br.com.piggu.Decimal", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val texto = if (decoder is JsonDecoder) {
            decoder.decodeJsonElement().jsonPrimitive.content
        } else {
            decoder.decodeString()
        }
        return texto.toBigDecimalOrNull() ?: throw SerializationException("Número inválido: $texto")
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }
}

/**
 * Enum da API que não quebra com valor novo (API_SPEC, Compatibilidade): o que o app não
 * conhece vira [desconhecido], também dentro de listas.
 */
open class SerializadorDeEnum<T : Enum<T>>(
    private val valores: List<T>,
    private val desconhecido: T,
    private val noJson: (T) -> String = { it.name },
) : KSerializer<T> {
    override val descriptor =
        PrimitiveSerialDescriptor(desconhecido.declaringJavaClass.name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(noJson(value))

    override fun deserialize(decoder: Decoder): T {
        val texto = decoder.decodeString()
        return valores.firstOrNull { it != desconhecido && noJson(it) == texto } ?: desconhecido
    }
}
