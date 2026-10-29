package br.com.piggu.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLDecoder
import java.text.Collator
import java.text.NumberFormat
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Currency
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration

/** A compra do Google Play com este token responde 409 OUTRA_CONTA, como a de outra conta do Piggu. */
const val TOKEN_DE_OUTRA_CONTA = "token-de-outra-conta"

/**
 * API falsa do modo demonstração: responde em memória todas as rotas /api, como a
 * frontend/src/app/demo/api-da-demo.ts do site, sem ir à rede.
 *
 * Escritas mudam o estado de verdade (lançar, apagar, pagar, resgatar...). Uma instância nova
 * volta ao estado inicial. O que depende de serviço de fora (Pluggy, Stripe, apagar conta)
 * responde que não está disponível. Vai por último no OkHttpClient: não chama `chain.proceed`
 * para /api.
 *
 * @param relogio dá o "hoje" dos dados e dos ids novos; fixe-o nos testes e nas capturas.
 * @param atraso espera antes de cada resposta, para parecer rede.
 */
class ApiDaDemo(
    private val relogio: Clock = Clock.system(ZoneId.of("America/Sao_Paulo")),
    private val atraso: Duration = Duration.ZERO,
) : Interceptor {
    private var sequencia = 0
    private val estado = EstadoDaDemo(LocalDate.now(relogio), relogio.zone, ::novoId)
    private val sorteio = Random(relogio.millis())
    private val rotas = mutableListOf<Rota>()

    /** A Ana assinou o família pelo site; a compra do Google Play troca para o individual do Play. */
    private var assinadoNoGooglePlay = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        val caminho = pedido.url.encodedPath
        val inicio = caminho.indexOf("/api/")
        // Como o site: só /api é da demo.
        if (inicio < 0) return chain.proceed(pedido)
        if (atraso.isPositive()) Thread.sleep(atraso.inWholeMilliseconds)
        return synchronized(this) { responder(pedido, caminho.substring(inicio + 4)) }
    }

    private fun responder(pedido: Request, caminho: String): Response {
        val corpo = lerCorpo(pedido)
        for (rota in rotas) {
            if (rota.metodo != pedido.method) continue
            val achou = rota.padrao.matchEntire(caminho) ?: continue
            return try {
                when (val resposta = rota.tratar(Pedido(pedido.url, achou.groupValues.drop(1), corpo))) {
                    Unit -> montar(pedido, 204, "".toResponseBody())
                    is Arquivo -> montar(pedido, 200, resposta.bytes.toResponseBody(resposta.tipo.toMediaType()))
                    else -> montar(pedido, if (pedido.method == "POST") 201 else 200, json(resposta).toString().toResponseBody(JSON))
                }
            } catch (erro: ErroDaDemo) {
                falhar(pedido, erro)
            } catch (_: RuntimeException) {
                falhar(pedido, ErroDaDemo(500, "Ocorreu um erro na demonstração."))
            }
        }
        return falhar(pedido, ErroDaDemo(404, INDISPONIVEL))
    }

    private fun lerCorpo(pedido: Request): JsonObject {
        val texto = pedido.body?.let { corpo -> Buffer().also(corpo::writeTo).readUtf8() } ?: return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(texto) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    private fun montar(pedido: Request, status: Int, corpo: okhttp3.ResponseBody) = Response.Builder()
        .request(pedido)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("Demo")
        .body(corpo)
        .build()

    /** Erro no mesmo formato do backend (ApiError): o app mostra a mensagem como mostraria a real. */
    private fun falhar(pedido: Request, erro: ErroDaDemo): Response {
        val corpo = registro("erro" to erro.message, "codigo" to erro.codigo, "campos" to emptyList<Any>(), "momento" to agora())
        return montar(pedido, erro.status, json(corpo).toString().toResponseBody(JSON))
    }

    private class ErroDaDemo(val status: Int, mensagem: String, val codigo: String = "DEMO") : RuntimeException(mensagem)

    private class Arquivo(val bytes: ByteArray, val tipo: String)

    private class Pedido(val url: HttpUrl, val partes: List<String>, val corpo: JsonObject) {
        val id: String get() = partes[0]
        fun param(nome: String): String? = url.queryParameter(nome)?.takeIf { it.isNotEmpty() }
    }

    private class Rota(val metodo: String, val padrao: Regex, val tratar: (Pedido) -> Any?)

    private fun rota(metodo: String, padrao: String, tratar: (Pedido) -> Any?) {
        rotas += Rota(metodo, Regex(padrao.replace("{id}", "([^/]+)")), tratar)
    }

    // ---------- Ajudantes ----------

    private fun novoId(prefixo: String): String = "$prefixo-${relogio.millis().toString(36)}-${++sequencia}"

    private fun hoje() = LocalDate.now(relogio)
    private fun hojeIso() = hoje().toString()
    private fun mesAtual() = YearMonth.from(hoje()).toString()
    private fun agora() = Instant.now(relogio).toString()
    private fun Pedido.mes() = param("mes") ?: mesAtual()
    private fun titularId() = estado.titular.texto("id")!!

    private fun somarMes(mes: String, passo: Long) = YearMonth.parse(mes).plusMonths(passo).toString()
    private fun somar(valores: Iterable<BigDecimal>) = valores.fold(BigDecimal.ZERO, BigDecimal::add)
    private fun semAcento(texto: String?) =
        Normalizer.normalize(texto.orEmpty(), Normalizer.Form.NFD).replace(ACENTOS, "").lowercase().trim()

    private fun Map<String, JsonElement>.data() = texto("data").orEmpty()
    private fun Map<String, JsonElement>.logico(chave: String) = when (val valor = this[chave]) {
        null, JsonNull -> false
        is JsonPrimitive -> valor.booleanOrNull ?: (valor.content.isNotEmpty() && valor.content != "0")
        else -> true
    }
    private fun Map<String, JsonElement>.textos(chave: String) =
        (this[chave] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun Map<String, JsonElement>.objetos(chave: String) = (this[chave] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun <T> exigir(valor: T?, mensagem: String = "Não encontrado."): T =
        valor ?: throw ErroDaDemo(404, mensagem, "NAO_ENCONTRADO")

    private fun MutableList<Registro>.achar(id: String) = find { it.texto("id") == id }

    private fun remover(lista: MutableList<Registro>, id: String) {
        val indice = lista.indexOfFirst { it.texto("id") == id }
        if (indice < 0) throw ErroDaDemo(404, "Não encontrado.", "NAO_ENCONTRADO")
        lista.removeAt(indice)
    }

    /** Arredonda como o Math.round do site (para cima no meio), para os percentuais. */
    private fun arredondar(valor: Double, casas: Int = 0): Double {
        val fator = Math.pow(10.0, casas.toDouble())
        return Math.round(valor * fator) / fator
    }

    private fun doMes(mes: String) = estado.gastos.filter { it.data().startsWith(mes) }
    private fun categorias() = CATEGORIAS_BASE + estado.categoriasExtras
    private fun categoriaValida(nome: String?) = categorias().find { semAcento(it) == semAcento(nome) } ?: "Outros"

    /** Categoria automática: regra pelo item/estabelecimento (termo mais longo) e depois a última compra. */
    private fun categoriaAutomatica(item: String?, estabelecimento: String?): String {
        val texto = " ${semAcento(item)} ${semAcento(estabelecimento)} "
        estado.regras.filter { texto.contains(it.texto("termo")!!) }.maxByOrNull { it.texto("termo")!!.length }
            ?.let { return it.texto("categoria")!! }
        return estado.gastos.lastOrNull { semAcento(it.texto("item")) == semAcento(item) }?.texto("categoria") ?: "Outros"
    }

    // ---------- Cálculos ----------

    private fun porCategoria(gastos: List<Registro>): Map<String, BigDecimal> = LinkedHashMap<String, BigDecimal>().apply {
        gastos.forEach { merge(it.texto("categoria")!!, it.dinheiro("valor"), BigDecimal::add) }
    }

    private fun resumo(mes: String): Registro {
        val agora = porCategoria(doMes(mes))
        val antes = porCategoria(doMes(somarMes(mes, -1)))
        val receitas = somar(estado.receitas.filter { it.data().startsWith(mes) }.map { it.dinheiro("valor") })
        val gastos = somar(agora.values)
        val gastosAntes = somar(antes.values)
        val sobra = receitas - gastos
        val hoje = hoje()
        val projecao = if (mes == mesAtual()) {
            gastos.multiply(BigDecimal(hoje.lengthOfMonth())).divide(BigDecimal(hoje.dayOfMonth), 2, RoundingMode.HALF_UP)
        } else {
            null
        }
        return registro(
            "mes" to mes,
            "receitas" to receitas,
            "gastos" to gastos,
            "sobra" to sobra,
            "taxaDePoupanca" to if (receitas.signum() > 0) arredondar(sobra.toDouble() / receitas.toDouble() * 100, 1) else null,
            "gastosMesAnterior" to gastosAntes,
            "variacao" to if (gastosAntes.signum() > 0) arredondar((gastos - gastosAntes).toDouble() / gastosAntes.toDouble() * 100, 1) else null,
            "projecaoDeGastos" to projecao,
            "porCategoria" to agora.entries.sortedByDescending { it.value }
                .map { (categoria, total) -> mapOf("categoria" to categoria, "total" to total, "anterior" to (antes[categoria] ?: BigDecimal.ZERO)) },
        )
    }

    private fun relatorioDoAno(ano: Int): Registro {
        val meses = (1..12).map { m ->
            val r = resumo(YearMonth.of(ano, m).toString())
            registro("mes" to r["mes"], "receitas" to r["receitas"], "gastos" to r["gastos"], "sobra" to r["sobra"])
        }
        val receitas = somar(meses.map { it.dinheiro("receitas") })
        val gastos = somar(meses.map { it.dinheiro("gastos") })
        return registro(
            "ano" to ano,
            "receitas" to receitas,
            "gastos" to gastos,
            "sobra" to receitas - gastos,
            "meses" to meses,
            "porCategoria" to porCategoria(estado.gastos.filter { it.data().startsWith("$ano") }).entries
                .sortedByDescending { it.value }.map { (categoria, total) -> mapOf("categoria" to categoria, "total" to total) },
        )
    }

    private fun orcamentos(mes: String): List<Registro> = estado.orcamentos.map { o ->
        val categoria = o.texto("categoria")!!
        val gasto = somar(doMes(mes).filter { it.texto("categoria") == categoria }.map { it.dinheiro("valor") })
        val percentual = arredondar(gasto.toDouble() / o.dinheiro("limite").toDouble() * 100).toLong()
        val alerta = if (percentual >= 100) "ESTOUROU" else if (percentual >= 80) "ATENCAO" else "OK"
        registro("id" to o["id"], "categoria" to categoria, "limite" to o["limite"], "gasto" to gasto, "percentual" to percentual, "alerta" to alerta)
    }.sortedWith { a, b -> COLLATOR.compare(a.texto("categoria"), b.texto("categoria")) }

    private fun contasFixas(mes: String, tipo: String): List<Registro> = estado.contasFixas
        .filter { (it.texto("tipo") ?: "GASTO") == tipo }
        .map { c ->
            val vencimento = diaNoMes(mes, c.inteiro("dia"))
            val paga = "${c.texto("id")}|$mes" in estado.contasPagas
            LinkedHashMap(c).apply {
                this["vencimento"] = vencimento
                this["situacao"] = if (paga) "PAGA" else if (vencimento < hojeIso()) "VENCIDA" else "PENDENTE"
            }
        }
        .sortedBy { it.inteiro("dia") }

    private fun novaContaFixa(descricao: String?, categoria: String?, valor: BigDecimal, dia: Int, automatico: Boolean, vararg extras: Pair<String, Any?>): Registro {
        val conta = registro(
            "id" to novoId("fixa"), "descricao" to descricao, "categoria" to categoria, "valor" to valor, "dia" to dia,
            "automatico" to automatico, "tipo" to "GASTO", "contaId" to null, "segundoDia" to null, "segundoValor" to null,
            "compartilhado" to false, "usuario" to titularId(),
        )
        extras.forEach { (chave, v) -> conta[chave] = v }
        estado.contasFixas += conta
        return conta
    }

    private fun gastosDaFatura(conta: Registro, mes: String): Pair<Registro, List<Registro>> {
        val fechamento = conta.inteiro("fechamento")
        val vencimento = conta.inteiro("vencimento")
        val diaDoFechamento = diaNoMes(mes, fechamento)
        val inicio = diaNoMes(somarMes(mes, -1), fechamento)
        val ultimaCompra = LocalDate.parse(diaDoFechamento).minusDays(1).toString()
        val gastos = estado.gastos
            .filter { it.texto("contaId") == conta.texto("id") && it.data() >= inicio && it.data() <= ultimaCompra }
            .sortedBy { it.data() }
        val paga = conta.texto("ultimaFaturaPaga")?.let { it >= mes } ?: false
        val fatura = registro(
            "mes" to mes,
            "inicio" to inicio,
            "ultimaCompra" to ultimaCompra,
            "fechamento" to diaDoFechamento,
            "vencimento" to if (vencimento > fechamento) diaNoMes(mes, vencimento) else diaNoMes(somarMes(mes, 1), vencimento),
            "total" to somar(gastos.map { it.dinheiro("valor") }),
            "paga" to paga,
        )
        return fatura to gastos
    }

    private fun contaComFaturas(conta: Registro): Registro {
        val saida = LinkedHashMap(conta).apply { remove("ultimaFaturaPaga") }
        if (conta.texto("tipo") != "CARTAO") {
            return saida.apply {
                this["faturaAberta"] = null
                this["faturaAPagar"] = null
            }
        }
        val hoje = hojeIso()
        val aberta = if (hoje >= diaNoMes(mesAtual(), conta.inteiro("fechamento"))) somarMes(mesAtual(), 1) else mesAtual()
        val anterior = gastosDaFatura(conta, somarMes(aberta, -1)).first
        return saida.apply {
            this["faturaAberta"] = gastosDaFatura(conta, aberta).first
            this["faturaAPagar"] = if (anterior.texto("vencimento")!! < hoje) null else anterior
        }
    }

    private class Acerto(val pessoa: String, val pagou: BigDecimal, val parte: BigDecimal) {
        val saldo: BigDecimal = pagou - parte
        var falta: BigDecimal = saldo.abs()
    }

    private fun acerto(mes: String): List<Acerto> {
        val pagou = LinkedHashMap<String, BigDecimal>()
        val parte = LinkedHashMap<String, BigDecimal>()
        for (gasto in doMes(mes)) {
            val partes = estado.partes[gasto.texto("id")] ?: continue
            for ((pessoa, valor) in partes) {
                parte.merge(pessoa, valor, BigDecimal::add)
                pagou.merge(gasto.texto("usuario").orEmpty(), valor, BigDecimal::add)
            }
        }
        return (pagou.keys + parte.keys)
            .map { Acerto(it, pagou[it] ?: BigDecimal.ZERO, parte[it] ?: BigDecimal.ZERO) }
            .sortedByDescending { it.saldo }
    }

    /** Quem deve a quem, com o menor número de transferências (como DivisaoDeGastos). */
    private fun transferencias(mes: String): List<Registro> {
        val todos = acerto(mes)
        val devem = ArrayDeque(todos.filter { it.saldo.signum() < 0 })
        val recebem = ArrayDeque(todos.filter { it.saldo.signum() > 0 })
        val lista = mutableListOf<Registro>()
        while (devem.isNotEmpty() && recebem.isNotEmpty()) {
            val de = devem.first()
            val para = recebem.first()
            val valor = de.falta.min(para.falta)
            lista += registro("de" to de.pessoa, "para" to para.pessoa, "valor" to valor)
            de.falta -= valor
            para.falta -= valor
            if (de.falta.signum() <= 0) devem.removeFirst()
            if (para.falta.signum() <= 0) recebem.removeFirst()
        }
        return lista
    }

    private fun produtos(): List<Registro> = estado.gastos
        .filter { it.numero("parcelas") == null }
        .groupBy { semAcento(it.texto("item")) }
        .map { (chave, lista) ->
            val precos = lista.map { it.dinheiro("valor") }
            val ultimo = lista.last()
            val anterior = lista.getOrNull(lista.size - 2)
            registro(
                "chave" to chave,
                "nome" to ultimo["item"],
                "categoria" to ultimo["categoria"],
                "ultimo" to ultimo.dinheiro("valor"),
                "menor" to precos.min(),
                "maior" to precos.max(),
                "media" to somar(precos).divide(BigDecimal(precos.size), 2, RoundingMode.HALF_UP),
                "compras" to lista.size,
                "data" to ultimo["data"],
                "variacao" to (anterior?.let { ultimo.dinheiro("valor") - it.dinheiro("valor") } ?: BigDecimal.ZERO),
            )
        }
        .sortedByDescending { it.inteiro("compras") }

    private fun cofrinho(): Registro {
        val depositos = estado.depositos.sortedByDescending { it.data() }
        val primeiro = depositos.lastOrNull()?.data()
        val totalDepositos = somar(depositos.map { it.dinheiro("valor") })
        val totalGastos = if (primeiro == null) BigDecimal.ZERO else somar(estado.gastos.filter { it.data() >= primeiro }.map { it.dinheiro("valor") })
        return registro("depositos" to depositos, "totalDepositos" to totalDepositos, "totalGastos" to totalGastos, "saldo" to totalDepositos - totalGastos)
    }

    /** Espelha o ObjetivosService do backend: meses contam o atual e o do prazo; por mês arredonda para cima. */
    private fun situacaoDoObjetivo(objetivo: Registro): Registro {
        val alvo = objetivo.dinheiro("alvo")
        val guardado = somar(estado.depositos.filter { it.texto("objetivo") == objetivo.texto("id") }.map { it.dinheiro("valor") })
        val falta = (alvo - guardado).max(BigDecimal.ZERO)
        val concluido = falta.signum() == 0
        val prazo = objetivo.texto("prazo")
        val mesesRestantes = prazo?.let {
            (ChronoUnit.MONTHS.between(YearMonth.from(hoje()), YearMonth.parse(it.take(7))) + 1).coerceAtLeast(0)
        }
        return LinkedHashMap(objetivo).apply {
            this["guardado"] = guardado
            this["falta"] = falta
            this["percentual"] = guardado.multiply(BigDecimal(100)).divide(alvo, 0, RoundingMode.FLOOR).toInt().coerceAtMost(100)
            this["mesesRestantes"] = mesesRestantes
            this["porMes"] = mesesRestantes?.let { falta.divide(BigDecimal(it.coerceAtLeast(1)), 2, RoundingMode.CEILING) }
            this["concluido"] = concluido
            this["atrasado"] = prazo != null && !concluido && prazo < hojeIso()
        }
    }

    private fun situacaoDoAssistente() = registro(
        "habilitado" to true,
        "autorizado" to estado.assistenteAutorizado,
        "limite" to LIMITE_DO_ASSISTENTE,
        "restantes" to LIMITE_DO_ASSISTENTE - estado.perguntasAoAssistente,
    )

    /** Na demo não há IA: três respostas de exemplo, escolhidas pelo assunto da pergunta. */
    private fun respostaDoAssistente(pergunta: String): Registro {
        val moeda = NumberFormat.getCurrencyInstance(PT_BR)
        fun real(valor: BigDecimal) = moeda.format(valor)
        val assunto = semAcento(pergunta)
        val mes = mesAtual()
        if (Regex("objetivo|cofrinho|guardar|viagem").containsMatchIn(assunto)) {
            val linhas = estado.objetivos.map(::situacaoDoObjetivo).map { o ->
                val nome = o.texto("nome")
                if (o.logico("concluido")) {
                    "$nome: concluído!"
                } else {
                    "$nome: faltam ${real(o.dinheiro("falta"))}" + (o.numero("porMes")?.let { ", guardando ${real(it)} por mês." } ?: ".")
                }
            }
            return registro("texto" to linhas.joinToString("\n").ifEmpty { "Você ainda não tem cofrinho por objetivo." }, "tela" to "painel")
        }
        if (Regex("orcamento|estour|limite").containsMatchIn(assunto)) {
            val atencao = orcamentos(mes).filter { it.texto("alerta") != "OK" }
            val texto = if (atencao.isEmpty()) {
                "Neste mês nenhuma categoria passou de 80% do limite."
            } else {
                atencao.joinToString("\n") {
                    "${it.texto("categoria")}: ${it.texto("percentual")}% do limite (${real(it.dinheiro("gasto"))} de ${real(it.dinheiro("limite"))})."
                }
            }
            return registro("texto" to texto, "tela" to "orcamentos")
        }
        val r = resumo(mes)
        val maior = (r["porCategoria"] as JsonArray).firstOrNull() as JsonObject?
        return registro(
            "texto" to "Neste mês você gastou ${real(r.dinheiro("gastos"))} e recebeu ${real(r.dinheiro("receitas"))}." +
                (maior?.let { " A maior categoria é ${it.texto("categoria")}, com ${real(it.dinheiro("total"))}." } ?: "") +
                " (Na demonstração as respostas são de exemplo; no app, a IA responde a sua pergunta.)",
            "tela" to "relatorios",
        )
    }

    private fun familia(): Registro {
        fun membro(u: Registro) = registro(
            "id" to u["id"], "nome" to u["nome"], "email" to u["email"], "foto" to u["foto"], "papel" to u["role"],
            "premium" to true, "parceriaPendente" to false,
        )
        return registro(
            "id" to "familia-demo",
            "nome" to estado.nomeDaFamilia,
            "plano" to "PREMIUM",
            "planoFamilia" to true,
            "planoCasal" to false,
            "membros" to listOf(membro(estado.titular), membro(estado.membro)),
            "convites" to estado.convites.map { LinkedHashMap(it).apply { this["familia"] = estado.nomeDaFamilia } },
        )
    }

    private fun plano() = registro(
        "plano" to "PREMIUM",
        "premiumAte" to estado.titular["premiumAte"],
        "origem" to if (assinadoNoGooglePlay) "PLAY_STORE" else "WEB",
        "reembolsoAte" to null,
        "diasDeTeste" to 0,
        "assinaturaDisponivel" to false,
        "site" to precos("19,90", "199,00"),
        "app" to precos("22,89", "228,85"),
        "gratuito" to listOf("Gastos, receitas e contas fixas", "Relatório do mês", "10 leituras de nota por mês"),
        "premium" to listOf(
            "Open Finance", "Notas sem limite", "Orçamento por categoria", "Relatório anual", "Mural de fotos",
            "Vários cofrinhos por objetivo", "Assistente de IA",
        ),
        "planoFamilia" to !assinadoNoGooglePlay,
        "voceAssinou" to true,
        "familiaDisponivel" to false,
        "siteFamilia" to precos("39,99", "399,90"),
        "appFamilia" to precos("45,99", "459,89"),
        "planoCasal" to false,
        "casalDisponivel" to false,
        "siteCasal" to precos("29,90", "299,00"),
        "appCasal" to precos("34,39", "343,85"),
        "pessoasNoFamilia" to 5,
        "casaTemAssinatura" to true,
    )

    private fun precos(mensal: String, anual: String) = mapOf("mensal" to mensal, "anual" to anual)

    /** Com refreshNoCorpo (app nativo, D-072) o refresh volta no corpo, como no backend. */
    private fun tokens(refreshNoCorpo: Boolean) = registro(
        "accessToken" to "token-da-demonstracao",
        "expiresIn" to 1800,
        "usuario" to estado.titular,
    ).apply { if (refreshNoCorpo) this["refreshToken"] = "refresh-da-demonstracao" }

    /** Alguns débitos recentes do banco de exemplo, sempre os mesmos ids para a reimportação pular. */
    private fun gastosDoBanco(): List<Registro> {
        val conta = "Banco Exemplo · Conta corrente"
        fun debito(atras: Long, descricao: String, valor: String, id: Int) = registro(
            "data" to hoje().minusDays(atras).toString(), "descricao" to descricao, "valor" to BigDecimal(valor),
            "idExterno" to "pluggy:demo-$id", "conta" to conta, "moeda" to "BRL",
        )
        return listOf(debito(1, "Padaria Pão Quente", "18.9", 1), debito(3, "Uber", "27.5", 2), debito(6, "Farmácia", "64.3", 3))
    }

    /** Lê o CSV (data, descrição, valor; débito negativo) ou o OFX da importação do jeito mais simples. */
    private fun previaDoExtrato(conteudo: String): List<Registro> {
        if (Regex("<OFX>", RegexOption.IGNORE_CASE).containsMatchIn(conteudo)) {
            return conteudo.split(Regex("<STMTTRN>", RegexOption.IGNORE_CASE)).drop(1).mapNotNull { bloco ->
                fun campo(nome: String) = Regex("<$nome>([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(bloco)?.groupValues?.get(1)?.trim().orEmpty()
                val valor = campo("TRNAMT").replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() < 0 } ?: return@mapNotNull null
                val d = campo("DTPOSTED")
                val descricao = campo("MEMO").ifEmpty { campo("NAME") }.ifEmpty { "Sem descrição" }
                linha("${d.take(4)}-${d.drop(4).take(2)}-${d.drop(6).take(2)}", descricao, valor.negate(), "ofx:" + campo("FITID"))
            }
        }
        val linhas = conteudo.removePrefix(BOM).split(Regex("\r?\n")).filter { it.isNotBlank() }
        val separador = if (linhas.firstOrNull()?.contains(';') == true) ";" else ","
        val lidas = linhas.drop(1).mapIndexed { i, l ->
            val colunas = l.split(separador).map { it.trim().removePrefix("\"").removeSuffix("\"") }
            val data = colunas[0]
            val iso = if ('/' in data) data.split('/').reversed().joinToString("-") else data.take(10)
            val limpo = colunas.getOrElse(2) { "" }.replace(Regex("[^0-9,.-]"), "")
            val numero = if (limpo.lastIndexOf(',') > limpo.lastIndexOf('.')) limpo.replace(".", "").replaceFirst(',', '.') else limpo.replace(",", "")
            Lida(iso, colunas.getOrElse(1) { "" }, if (numero.isEmpty()) BigDecimal.ZERO else numero.toBigDecimalOrNull(), i)
        }
        val temSinal = lidas.any { (it.valor?.signum() ?: 0) < 0 }
        return lidas.filter { it.valor != null && (!temSinal || it.valor.signum() < 0) }.map {
            val valor = it.valor!!.abs()
            linha(it.iso, it.descricao, valor, "csv:${it.iso}|${semAcento(it.descricao)}|${valor.stripTrailingZeros().toPlainString()}|${it.indice}")
        }
    }

    private class Lida(val iso: String, val descricao: String, val valor: BigDecimal?, val indice: Int)

    private fun linha(data: String?, descricao: String?, valor: BigDecimal, idExterno: String?) = registro(
        "data" to data,
        "descricao" to descricao,
        "valor" to valor,
        "idExterno" to idExterno,
        "categoria" to categoriaAutomatica(descricao, ""),
        "jaImportada" to estado.gastos.any { it.texto("idExterno") == idExterno },
    )

    private fun csvDoMes(mes: String): Arquivo {
        fun celula(v: String?) = "\"" + v.orEmpty().let { if (Regex("^[=+\\-@]").containsMatchIn(it)) "'$it" else it }.replace("\"", "\"\"") + "\""
        val linhas = doMes(mes).map { g ->
            listOf(
                g.data(), celula(g.texto("item")), celula(g.texto("categoria")),
                duasCasas(g.dinheiro("valor")).toPlainString().replace('.', ','), celula(g.texto("estabelecimento")),
            ).joinToString(";")
        }
        val texto = BOM + (listOf("data;item;categoria;valor;estabelecimento") + linhas).joinToString("\n")
        return Arquivo(texto.toByteArray(), "text/csv")
    }

    /** Grava o lançamento: parcelas viram uma linha por mês; divisão em partes iguais. */
    private fun lancar(corpo: Map<String, JsonElement>): List<Registro> {
        val parcelas = corpo.numero("parcelas")?.toInt()?.takeIf { it > 0 } ?: 1
        val reciboId = corpo.texto("reciboId") ?: novoId("recibo")
        val estabelecimento = corpo.texto("estabelecimento").orEmpty()
        val origem = corpo.texto("origem") ?: "Manual"
        val primeiroDia = LocalDate.parse(corpo.texto("data"))
        val novos = mutableListOf<Registro>()
        for (item in corpo.objetos("itens")) {
            val manual = origem == "Manual" && !item.texto("categoria").isNullOrEmpty()
            val categoria = if (manual) categoriaValida(item.texto("categoria")) else categoriaAutomatica(item.texto("item"), estabelecimento)
            val valor = item.dinheiro("valor")
            val parcela = valor.divide(BigDecimal(parcelas), 2, RoundingMode.FLOOR)
            for (i in 0 until parcelas) {
                novos += registro(
                    "id" to novoId("gasto"),
                    "data" to primeiroDia.plusMonths(i.toLong()).toString(),
                    "reciboId" to reciboId,
                    "estabelecimento" to estabelecimento,
                    "item" to item["item"],
                    "categoria" to categoria,
                    "valor" to if (i == parcelas - 1) valor - parcela * BigDecimal(parcelas - 1) else parcela,
                    "tipo" to (item.texto("tipo") ?: "Variavel"),
                    "origem" to origem,
                    "usuario" to titularId(),
                    "compartilhado" to corpo.logico("compartilhado"),
                    "registradoEm" to agora(),
                    "contaId" to corpo.texto("contaId"),
                    "parcela" to if (parcelas > 1) i + 1 else null,
                    "parcelas" to if (parcelas > 1) parcelas else null,
                    "moedaOriginal" to item.texto("moedaOriginal"),
                    "valorOriginal" to item.numero("valorOriginal"),
                )
            }
        }
        val dividir = corpo.textos("dividirCom")
        if (dividir.isNotEmpty()) {
            for (gasto in novos) {
                val parte = gasto.dinheiro("valor").divide(BigDecimal(dividir.size), 2, RoundingMode.HALF_UP)
                estado.partes[gasto.texto("id")!!] = dividir.associateWith { parte }
            }
        }
        estado.gastos += novos
        return novos
    }

    private fun guardarImagem(c: Map<String, JsonElement>): String {
        val id = novoId("arquivo")
        estado.imagens[id] = "data:${c.texto("mimeType") ?: "image/jpeg"};base64,${c.texto("imageBase64")}"
        return id
    }

    private fun arquivoDaImagem(dataUrl: String): Arquivo {
        val (cabecalho, conteudo) = dataUrl.removePrefix("data:").split(',', limit = 2)
        val bytes = if (cabecalho.endsWith(";base64")) Base64.getDecoder().decode(conteudo) else URLDecoder.decode(conteudo, "UTF-8").toByteArray()
        return Arquivo(bytes, cabecalho.substringBefore(';'))
    }

    private fun salvarMetaDoMes(id: String?, c: Map<String, JsonElement>): Registro {
        val existente = id?.let { exigir(estado.metasDoMes.achar(it), "Meta não encontrada.") }
        val alvo = c.dinheiro("alvo")
        val atual = c.dinheiro("atual")
        val meta = registro(
            "id" to (existente?.texto("id") ?: novoId("meta")), "titulo" to c.texto("titulo"), "alvo" to alvo, "atual" to atual,
            "mes" to mesAtual(), "concluida" to (atual >= alvo),
        )
        if (existente != null) return existente.apply { putAll(meta) }
        estado.metasDoMes += meta
        return meta
    }

    private fun salvarLugar(id: String?, c: Map<String, JsonElement>): Registro {
        val existente = id?.let { exigir(estado.lugares.achar(it), "Lugar não encontrado.") }
        val fotoAssetId = if (c.logico("imageBase64")) guardarImagem(c) else existente?.texto("fotoAssetId")
        val lugar = registro(
            "id" to (existente?.texto("id") ?: novoId("lugar")),
            "nome" to c["nome"],
            "categoria" to (c.texto("categoria") ?: ""),
            "localizacao" to (c.texto("localizacao") ?: ""),
            "nota" to c.numero("nota"),
            "comentario" to (c.texto("comentario") ?: ""),
            "data" to c["data"],
            "marcacoes" to (c["marcacoes"] ?: JsonArray(emptyList())),
            "fotoAssetId" to fotoAssetId,
            "temFoto" to (fotoAssetId != null),
            "valor" to c.dinheiro("valor"),
            "usuario" to (existente?.get("usuario") ?: titularId()),
        )
        if (existente != null) return existente.apply { putAll(lugar) }
        estado.lugares += lugar
        return lugar
    }

    private fun saldoDeMoedas() = registro("saldo" to estado.moedas, "historico" to estado.movimentos.sortedByDescending { it.data() })

    // ---------- Rotas ----------

    init {
        // Sessão: sempre a Ana, titular Premium.
        rota("POST", "/auth/(google|apple|entrar|cadastro|refresh)") {
            tokens(it.corpo.logico("refreshNoCorpo") || (it.id == "refresh" && it.corpo.texto("refreshToken") != null))
        }
        rota("PUT", "/me/pais") {
            estado.titular.apply {
                this["pais"] = it.corpo["pais"]
                this["cpfInformado"] = true
            }
        }
        rota("PUT", "/auth/me/senha") { estado.titular.apply { this["temSenha"] = true } }
        rota("POST", "/auth/logout") { }
        rota("POST", "/auth/senha/(esqueci|redefinir)") { }
        rota("GET", "/auth/me") { estado.titular }
        rota("PUT", "/auth/me/preferences") {
            estado.titular.apply { this["preferencias"] = JsonObject(this["preferencias"] as JsonObject + it.corpo) }
        }
        rota("PUT", "/auth/me/alerts") { estado.titular.apply { this["avisos"] = it.corpo } }
        rota("PUT", "/auth/me/sharing") {
            estado.titular.apply {
                this["compartilhamento"] = mapOf(
                    "mostraTudoAoParceiro" to it.corpo.logico("mostrarTudoAoParceiro"),
                    "compartilharPorPadrao" to it.corpo.logico("compartilharPorPadrao"),
                )
            }
        }
        rota("PUT", "/auth/me/modules") {
            estado.titular.apply {
                this["preferencias"] = JsonObject(this["preferencias"] as JsonObject + ("modulos" to (it.corpo["modulos"] ?: JsonNull)))
            }
        }
        // Foto de perfil: só na memória, como o resto da demo.
        rota("PUT", "/me/foto") {
            estado.titular.apply {
                this["foto"] = "data:image/jpeg;base64,${it.corpo.texto("imagemBase64")}"
                this["fotoPropria"] = true
            }
        }
        val semFoto: (Pedido) -> Any? = {
            estado.titular.apply {
                this["foto"] = null
                this["fotoPropria"] = false
            }
        }
        rota("DELETE", "/me/foto", semFoto)
        rota("PUT", "/me/foto/google", semFoto)
        rota("PUT", "/me/termos") { estado.titular.apply { this["termosAceitos"] = it.corpo.texto("versao") } }
        rota("GET", "/me/export") {
            val dados = registro("aviso" to "Dados de exemplo da demonstração", "gastos" to estado.gastos)
            Arquivo(json(dados).toString().toByteArray(), "application/json")
        }
        rota("DELETE", "/me") { throw ErroDaDemo(422, INDISPONIVEL) }
        rota("GET", "/users") { listOf(estado.titular, estado.membro) }

        // Família
        rota("GET", "/family") { familia() }
        rota("PUT", "/family") {
            estado.nomeDaFamilia = it.corpo.texto("nome")?.ifEmpty { null } ?: estado.nomeDaFamilia
            familia()
        }
        rota("POST", "/family/invites") {
            estado.convites += registro(
                "id" to novoId("convite"), "email" to it.corpo.texto("email")?.lowercase(), "venceEm" to hoje().plusDays(7).toString(),
            )
            familia()
        }
        rota("DELETE", "/family/invites/{id}") {
            remover(estado.convites, it.id)
            familia()
        }
        rota("DELETE", "/family/members/{id}") { throw ErroDaDemo(422, INDISPONIVEL) }
        rota("PUT", "/family/members/{id}/role") { throw ErroDaDemo(422, INDISPONIVEL) }
        rota("GET", "/family/invites/mine") { emptyList<Any>() }
        rota("GET", "/(family/)?history") { emptyList<Any>() }
        rota("POST", "/family/(leave|invites/.+/accept|partnership/(accept|decline))") { throw ErroDaDemo(422, INDISPONIVEL) }

        // Plano e pagamento: nada sai para a Stripe.
        rota("GET", "/billing/plan") { plano() }
        rota("POST", "/billing/(checkout|portal|refund)") {
            throw ErroDaDemo(422, "Pagamento indisponível na demonstração: esta conta de exemplo já é Premium.")
        }
        // A loja de mentira do app "compra" com um token falso; este marca a Ana como Premium pelo Google Play.
        rota("POST", "/billing/google-play/purchases") {
            if (it.corpo.texto("purchaseToken") == TOKEN_DE_OUTRA_CONTA) {
                throw ErroDaDemo(409, "Esta assinatura do Google Play é de outra conta do Piggu.", "OUTRA_CONTA")
            }
            assinadoNoGooglePlay = true
            plano()
        }

        // Open Finance: contas de exemplo; conectar banco de verdade não.
        rota("GET", "/banking/status") { mapOf("habilitado" to true) }
        rota("GET", "/banking/accounts") { estado.bancos }
        rota("POST", "/banking/sync") { estado.bancos }
        rota("GET", "/banking/transactions") { gastosDoBanco() }
        rota("POST", "/banking/(connect-token|items)") {
            throw ErroDaDemo(422, "Conectar banco (Pluggy) fica indisponível na demonstração. As contas abaixo são de exemplo.")
        }
        rota("DELETE", "/banking/connections/{id}") { p ->
            estado.bancos = estado.bancos.filter { it.texto("conexaoId") != p.id }
            estado.bancos
        }

        // Gastos
        rota("GET", "/expenses/search") { p ->
            val termo = semAcento(p.param("q"))
            if (termo.length < 2) {
                emptyList<Registro>()
            } else {
                estado.gastos.filter { semAcento("${it.texto("item")} ${it.texto("estabelecimento")}").contains(termo) }
                    .sortedByDescending { it.data() }.take(20)
            }
        }
        rota("GET", "/expenses/splits") { p ->
            acerto(p.mes()).map { registro("pessoa" to it.pessoa, "pagou" to it.pagou, "parte" to it.parte, "saldo" to it.saldo) }
        }
        rota("GET", "/expenses/splits/transfers") { transferencias(it.mes()) }
        rota("GET", "/expenses/export") { csvDoMes(it.mes()) }
        rota("POST", "/expenses/import/preview") { previaDoExtrato(it.corpo.texto("conteudo").orEmpty()) }
        rota("POST", "/expenses/import/preview-lines") { p ->
            p.corpo.objetos("linhas").map { linha(it.texto("data"), it.texto("descricao"), it.dinheiro("valor"), it.texto("idExterno")) }
        }
        rota("POST", "/expenses/import") { p ->
            val linhas = p.corpo.objetos("linhas")
            var importados = 0
            for (l in linhas) {
                if (estado.gastos.any { it.texto("idExterno") == l.texto("idExterno") }) continue
                val novo = lancar(
                    registro(
                        "data" to l["data"], "origem" to if (l.logico("categoria")) "Manual" else "Extrato", "contaId" to p.corpo["contaId"],
                        "itens" to listOf(registro("item" to l["descricao"], "valor" to l["valor"], "categoria" to l["categoria"])),
                    ),
                ).first()
                novo["origem"] = "Extrato"
                novo["idExterno"] = l["idExterno"]
                importados++
            }
            mapOf("importados" to importados, "pulados" to linhas.size - importados)
        }
        rota("GET", "/expenses") { p ->
            val mes = p.param("mes")
            val visao = p.param("visao")
            (if (mes != null) doMes(mes) else estado.gastos)
                .filter { visao != "meu" || it.texto("usuario") == titularId() }
                .filter { visao != "casal" || it.logico("compartilhado") }
                .sortedWith(compareByDescending<Registro> { it.data() }.thenByDescending { it.texto("registradoEm") })
        }
        rota("POST", "/expenses") { p ->
            // O reenvio da fila com o mesmo idDoEnvio devolve a primeira resposta (API_SPEC, Convenções).
            val idDoEnvio = p.corpo.texto("idDoEnvio")
            idDoEnvio?.let(estado.envios::get) ?: lancar(p.corpo).also { novos ->
                if (p.corpo.logico("recorrente")) {
                    for (g in novos) {
                        val fixa = novaContaFixa(g.texto("item"), g.texto("categoria"), g.dinheiro("valor"), g.data().takeLast(2).toInt(), true, "contaId" to g["contaId"])
                        estado.contasPagas += "${fixa.texto("id")}|${g.data().take(7)}"
                    }
                }
                if (idDoEnvio != null) estado.envios[idDoEnvio] = novos
            }
        }
        rota("PUT", "/expenses/{id}") { p ->
            exigir(estado.gastos.achar(p.id), "Gasto não encontrado.").apply {
                this["item"] = p.corpo["item"]
                this["categoria"] = categoriaValida(p.corpo.texto("categoria"))
                this["valor"] = p.corpo.dinheiro("valor")
            }
        }
        rota("DELETE", "/expenses/{id}") { p ->
            val gasto = exigir(estado.gastos.achar(p.id), "Gasto não encontrado.")
            val apagar = if (p.param("proximas") != null && gasto.numero("parcelas") != null) {
                estado.gastos.filter {
                    it.texto("reciboId") == gasto.texto("reciboId") && it.texto("item") == gasto.texto("item") &&
                        it.inteiro("parcelas") == gasto.inteiro("parcelas") && it.inteiro("parcela") >= gasto.inteiro("parcela")
                }
            } else {
                listOf(gasto)
            }
            apagar.forEach {
                remover(estado.gastos, it.texto("id")!!)
                estado.partes.remove(it.texto("id"))
            }
        }

        // Categorias e regras
        rota("GET", "/categories") { mapOf("categorias" to categorias()) }
        rota("POST", "/categories") { p ->
            val nome = p.corpo.texto("nome").orEmpty().trim()
            if (nome.length < 2 || categorias().any { semAcento(it) == semAcento(nome) }) throw ErroDaDemo(422, "Essa opção já existe.")
            estado.categoriasExtras += nome
            mapOf("categorias" to categorias())
        }
        rota("GET", "/categories/rules") { estado.regras.sortedWith { a, b -> COLLATOR.compare(a.texto("termo"), b.texto("termo")) } }
        rota("PUT", "/categories/rules") { p ->
            val termo = semAcento(p.corpo.texto("termo"))
            if (termo.length < 2) throw ErroDaDemo(422, "Digite um termo válido para a regra.")
            val categoria = categoriaValida(p.corpo.texto("categoria"))
            estado.regras.find { it.texto("termo") == termo }?.apply { this["categoria"] = categoria }
                ?: registro("id" to novoId("regra"), "termo" to termo, "categoria" to categoria).also { estado.regras += it }
        }
        rota("DELETE", "/categories/rules/{id}") { remover(estado.regras, it.id) }

        // Receitas
        rota("GET", "/incomes/categories") { CATEGORIAS_DE_RECEITA }
        rota("GET", "/incomes") { p -> estado.receitas.filter { it.data().startsWith(p.mes()) }.sortedByDescending { it.data() } }
        rota("POST", "/incomes") { p ->
            val c = p.corpo
            val receita = registro(
                "id" to novoId("receita"), "data" to c["data"], "descricao" to c["descricao"],
                "categoria" to (c.texto("categoria")?.ifEmpty { null } ?: "Outros"), "valor" to c.dinheiro("valor"),
                "usuario" to titularId(), "compartilhado" to false,
            )
            estado.receitas += receita
            if (c.logico("recorrente")) {
                val segunda: Array<Pair<String, Any?>> = if (c.logico("segundoDia")) arrayOf("segundoDia" to c.inteiro("segundoDia"), "segundoValor" to c.dinheiro("segundoValor")) else emptyArray()
                val fixa = novaContaFixa(
                    receita.texto("descricao"), receita.texto("categoria"), receita.dinheiro("valor"), receita.data().takeLast(2).toInt(), true,
                    "tipo" to "RECEITA", *segunda,
                )
                estado.contasPagas += "${fixa.texto("id")}|${receita.data().take(7)}"
            }
            receita
        }
        rota("GET", "/incomes/receivables") { estado.aReceber.sortedBy { it.data() } }
        rota("POST", "/incomes/receivables") { p ->
            registro(
                "id" to novoId("receber"), "quem" to p.corpo.texto("quem"), "descricao" to (p.corpo.texto("descricao") ?: ""),
                "valor" to p.corpo.dinheiro("valor"), "data" to p.corpo["data"],
            ).also { estado.aReceber += it }
        }
        rota("POST", "/incomes/receivables/{id}/receive") { p ->
            val conta = exigir(estado.aReceber.achar(p.id), "Valor a receber não encontrado.")
            remover(estado.aReceber, p.id)
            val quem = conta.texto("quem")
            val descricao = conta.texto("descricao").orEmpty()
            registro(
                "id" to novoId("receita"), "data" to hojeIso(), "descricao" to if (descricao.isNotEmpty()) "$quem · $descricao" else quem,
                "categoria" to "Reembolso", "valor" to conta.dinheiro("valor"), "usuario" to titularId(), "compartilhado" to false,
            ).also { estado.receitas += it }
        }
        rota("DELETE", "/incomes/receivables/{id}") { remover(estado.aReceber, it.id) }
        rota("DELETE", "/incomes/{id}") { remover(estado.receitas, it.id) }

        // Relatórios
        rota("GET", "/reports/month") { resumo(it.mes()) }
        rota("GET", "/reports/year") { p -> relatorioDoAno(p.param("ano")?.toIntOrNull()?.takeIf { it != 0 } ?: hoje().year) }

        // Contas fixas
        rota("GET", "/bills") { contasFixas(it.mes(), it.param("tipo") ?: "GASTO") }
        rota("POST", "/bills") { p ->
            val c = p.corpo
            novaContaFixa(c.texto("descricao"), categoriaValida(c.texto("categoria")), c.dinheiro("valor"), c.inteiro("dia"), c.logico("automatico"))
            Unit
        }
        rota("DELETE", "/bills/{id}") { remover(estado.contasFixas, it.id) }
        rota("POST", "/bills/{id}/pay") { p ->
            val conta = exigir(estado.contasFixas.achar(p.id), "Conta não encontrada.")
            val mes = p.mes()
            if (!estado.contasPagas.add("${p.id}|$mes")) throw ErroDaDemo(409, "Essa conta já foi paga neste mês.")
            val data = diaNoMes(mes, conta.inteiro("dia"))
            if (conta.texto("tipo") == "RECEITA") {
                estado.receitas += registro(
                    "id" to novoId("receita"), "data" to data, "descricao" to conta["descricao"], "categoria" to conta["categoria"],
                    "valor" to conta.dinheiro("valor"), "usuario" to titularId(), "compartilhado" to false,
                )
                null
            } else {
                lancar(
                    registro(
                        "data" to data, "origem" to "Conta fixa", "contaId" to conta["contaId"],
                        "itens" to listOf(registro("item" to conta["descricao"], "categoria" to conta["categoria"], "valor" to conta["valor"], "tipo" to "Fixo")),
                    ),
                ).first().apply { this["categoria"] = conta["categoria"] }
            }
        }

        // Orçamentos
        rota("GET", "/budgets") { orcamentos(it.mes()) }
        rota("PUT", "/budgets") { p ->
            val categoria = categoriaValida(p.corpo.texto("categoria"))
            val limite = p.corpo.dinheiro("limite")
            val existente = estado.orcamentos.find { it.texto("categoria") == categoria }
            if (existente != null) {
                existente["limite"] = limite
            } else {
                estado.orcamentos += registro("id" to novoId("orc"), "categoria" to categoria, "limite" to limite)
            }
            Unit
        }
        rota("DELETE", "/budgets/{id}") { remover(estado.orcamentos, it.id) }

        // Contas e cartões
        rota("GET", "/accounts") { estado.contas.map(::contaComFaturas).sortedWith { a, b -> COLLATOR.compare(a.texto("nome"), b.texto("nome")) } }
        rota("POST", "/accounts") { p ->
            val c = p.corpo
            val cartao = c.texto("tipo") == "CARTAO"
            val vencimento = c.inteiro("vencimento")
            val conta = registro(
                "id" to novoId("conta"),
                "nome" to c.texto("nome"),
                "tipo" to if (cartao) "CARTAO" else "CONTA",
                "fechamento" to if (cartao) (c.numero("fechamento")?.toInt() ?: if (vencimento > 7) vencimento - 7 else vencimento + 24) else null,
                "vencimento" to if (cartao) vencimento else null,
                "compartilhado" to c.logico("compartilhado"),
                "usuario" to titularId(),
            )
            estado.contas += conta
            contaComFaturas(conta)
        }
        rota("PUT", "/accounts/{id}/sharing") { p ->
            val conta = exigir(estado.contas.achar(p.id), "Conta ou cartão não encontrado.")
            conta["compartilhado"] = p.corpo.logico("compartilhado")
            contaComFaturas(conta)
        }
        rota("DELETE", "/accounts/{id}") { p ->
            remover(estado.contas, p.id)
            estado.gastos.filter { it.texto("contaId") == p.id }.forEach { it["contaId"] = null }
        }
        fun cartao(p: Pedido) = exigir(estado.contas.achar(p.id), "Conta ou cartão não encontrado.")
        rota("GET", "/accounts/{id}/statement") { p ->
            val conta = cartao(p)
            if (conta.texto("tipo") != "CARTAO") throw ErroDaDemo(422, "Fatura só existe para cartão.")
            val (fatura, gastos) = gastosDaFatura(conta, p.mes())
            mapOf("fatura" to fatura, "gastos" to gastos)
        }
        rota("PUT", "/accounts/{id}/statement/paid") { p ->
            val conta = cartao(p)
            val mes = p.mes()
            if (conta.texto("ultimaFaturaPaga").orEmpty() < mes) conta["ultimaFaturaPaga"] = mes
            Unit
        }
        rota("DELETE", "/accounts/{id}/statement/paid") { p ->
            val conta = cartao(p)
            val mes = p.mes()
            if (conta.texto("ultimaFaturaPaga").orEmpty() >= mes) conta["ultimaFaturaPaga"] = somarMes(mes, -1)
            Unit
        }

        // Metas, cofrinho, notas, produtos
        rota("GET", "/monthly-goals") { estado.metas.toMap() }
        rota("PUT", "/monthly-goals") { p ->
            estado.metas[p.corpo.texto("mes").orEmpty()] = p.corpo.dinheiro("limite")
            estado.metas.toMap()
        }
        rota("GET", "/piggy-bank") { cofrinho() }
        rota("POST", "/piggy-bank/deposits") { p ->
            val objetivo = p.corpo.texto("objetivo")
            if (objetivo != null) exigir(estado.objetivos.achar(objetivo), "Objetivo não encontrado.")
            registro(
                "id" to novoId("deposito"), "data" to (p.corpo.texto("data")?.ifEmpty { null } ?: hojeIso()), "valor" to p.corpo.dinheiro("valor"),
                "usuario" to titularId(), "registradoEm" to agora(), "objetivo" to objetivo,
            ).also { estado.depositos += it }
        }
        rota("DELETE", "/piggy-bank/deposits/{id}") { remover(estado.depositos, it.id) }
        rota("GET", "/piggy-bank/goals") { estado.objetivos.map(::situacaoDoObjetivo) }
        rota("POST", "/piggy-bank/goals") { p ->
            val objetivo = registro(
                "id" to novoId("objetivo"), "nome" to p.corpo.texto("nome"), "alvo" to p.corpo.dinheiro("alvo"),
                "prazo" to p.corpo.texto("prazo")?.ifEmpty { null }, "compartilhado" to true, "usuario" to titularId(),
            )
            estado.objetivos += objetivo
            situacaoDoObjetivo(objetivo)
        }
        rota("PUT", "/piggy-bank/goals/{id}") { p ->
            exigir(estado.objetivos.achar(p.id), "Objetivo não encontrado.").apply {
                this["nome"] = p.corpo.texto("nome")
                this["alvo"] = p.corpo.dinheiro("alvo")
                this["prazo"] = p.corpo.texto("prazo")?.ifEmpty { null }
            }
            Unit
        }
        rota("DELETE", "/piggy-bank/goals/{id}") { p ->
            remover(estado.objetivos, p.id)
            estado.depositos.filter { it.texto("objetivo") == p.id }.forEach { it["objetivo"] = null }
        }
        // Assistente: respostas montadas aqui, sem IA, a partir dos dados de exemplo.
        rota("GET", "/assistant") { situacaoDoAssistente() }
        rota("POST", "/assistant/consent") {
            estado.assistenteAutorizado = true
            situacaoDoAssistente()
        }
        rota("POST", "/assistant/ask") { p ->
            if (p.corpo["autorizoIa"] == JsonPrimitive(true)) estado.assistenteAutorizado = true
            if (!estado.assistenteAutorizado) throw ErroDaDemo(422, "Autorize o envio ao assistente para continuar.", "CONSENTIMENTO_NECESSARIO")
            estado.perguntasAoAssistente++
            respostaDoAssistente(p.corpo.texto("pergunta").orEmpty()).apply { this["restantes"] = situacaoDoAssistente()["restantes"] }
        }
        rota("GET", "/notes") { estado.notas }
        rota("POST", "/notes") { p ->
            val c = p.corpo
            registro(
                "id" to novoId("nota"), "titulo" to c["titulo"], "texto" to (c.texto("texto") ?: ""), "data" to c.texto("data"),
                "valor" to c.dinheiro("valor"), "categoria" to (c.texto("categoria") ?: "Outros"), "gastoId" to null,
                "usuario" to titularId(), "criadoEm" to agora(),
            ).also { estado.notas += it }
        }
        rota("DELETE", "/notes/{id}") { remover(estado.notas, it.id) }
        rota("GET", "/products/price-check") { p ->
            produtos().find { it.texto("chave") == semAcento(p.param("item")) }?.let { produto ->
                val media = produto.dinheiro("media")
                val compras = produto.inteiro("compras")
                val valor = p.param("valor")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val percentual = arredondar((valor - media).toDouble() / media.toDouble() * 100).toLong()
                registro("media" to media, "compras" to compras, "percentual" to percentual, "acima" to (compras >= 2 && percentual > 15))
            }
        }
        rota("GET", "/products") { produtos() }

        // Leitura de nota: devolve um cupom de exemplo.
        rota("GET", "/receipts/usage") { registro("usadas" to estado.leiturasNoMes, "limite" to 10, "restantes" to null) }
        rota("POST", "/receipts/parse") {
            estado.leiturasNoMes++
            registro(
                "reciboId" to novoId("recibo"),
                "estabelecimento" to "Supermercado Bom Preço",
                "data" to hojeIso(),
                "itens" to listOf(
                    registro("item" to "Arroz 5kg", "categoria" to "Alimentação", "valor" to BigDecimal("27.9")),
                    registro("item" to "Feijão carioca 1kg", "categoria" to "Alimentação", "valor" to BigDecimal("8.49")),
                    registro("item" to "Café 500g", "categoria" to "Alimentação", "valor" to BigDecimal("18.9")),
                    registro("item" to "Detergente", "categoria" to "Outros", "valor" to BigDecimal("2.79")),
                ),
                "origem" to "OCR",
                "aviso" to "Demonstração: este é um cupom de exemplo, a foto não foi lida.",
                "leiturasRestantes" to null,
            )
        }

        // Câmbio sem rede
        rota("GET", "/exchange-rate/currencies") {
            COTACOES.keys.map { codigo -> mapOf("codigo" to codigo, "nome" to Currency.getInstance(codigo).getDisplayName(PT_BR)) }
        }
        rota("GET", "/exchange-rate") { p ->
            val de = p.param("de") ?: "USD"
            val para = p.param("para") ?: "BRL"
            registro(
                "de" to de, "para" to para, "taxa" to arredondar((COTACOES[de] ?: 1.0) / (COTACOES[para] ?: 1.0), 4),
                "data" to hojeIso(), "fonte" to "Demonstração", "estimativa" to true, "desatualizada" to false,
            )
        }

        // Minhas metas (só o mês atual) e Girls Math
        rota("GET", "/personal-goals") { estado.metasDoMes }
        rota("POST", "/personal-goals") { salvarMetaDoMes(null, it.corpo) }
        rota("PUT", "/personal-goals/{id}") { salvarMetaDoMes(it.id, it.corpo) }
        rota("DELETE", "/personal-goals/{id}") { remover(estado.metasDoMes, it.id) }
        rota("GET", "/girls-math") { estado.girlsMath.sortedByDescending { it.data() } }
        rota("POST", "/girls-math") { p ->
            val c = p.corpo
            val fotoAssetId = if (c.logico("imageBase64")) guardarImagem(c) else null
            registro(
                "id" to novoId("gm"), "produto" to c.texto("produto"), "loja" to (c.texto("loja") ?: ""), "valor" to c.dinheiro("valor"),
                "data" to (c.texto("data") ?: hojeIso()), "fotoAssetId" to fotoAssetId, "temFoto" to (fotoAssetId != null),
            ).also { estado.girlsMath += it }
        }
        rota("DELETE", "/girls-math/{id}") { remover(estado.girlsMath, it.id) }

        // Lugares
        rota("GET", "/places/tags") { estado.marcadores.toList() }
        rota("POST", "/places/tags") { p ->
            estado.marcadores += p.corpo.texto("nome").orEmpty()
            estado.marcadores.toList()
        }
        rota("GET", "/places") { estado.lugares.sortedByDescending { it.data() } }
        rota("POST", "/places") { salvarLugar(null, it.corpo) }
        rota("PUT", "/places/{id}") { salvarLugar(it.id, it.corpo) }
        rota("DELETE", "/places/{id}") { remover(estado.lugares, it.id) }

        // Filmes
        rota("GET", "/movies/search") { p ->
            val termo = semAcento(p.param("busca") ?: p.param("q"))
            FILMES_DO_CATALOGO.filter { semAcento(it.texto("titulo")).contains(termo) }
        }
        rota("GET", "/movies/random") { FILMES_DO_CATALOGO[sorteio.nextInt(FILMES_DO_CATALOGO.size)] }
        rota("GET", "/movies") { estado.filmes }
        rota("POST", "/movies") { p ->
            val c = p.corpo
            if (estado.filmes.any { it["tmdbId"] == c["tmdbId"] }) throw ErroDaDemo(422, "Esse filme já está na lista.")
            registro(
                "id" to novoId("filme"), "tmdbId" to c["tmdbId"], "titulo" to c["titulo"], "ano" to (c.texto("ano") ?: ""), "poster" to "",
                "nota" to c.dinheiro("nota"), "sinopse" to (c.texto("sinopse") ?: ""), "assistido" to false,
                "avaliacoes" to emptyMap<String, Any>(), "usuario" to titularId(),
            ).also { estado.filmes += it }
        }
        rota("PATCH", "/movies/{id}/watched") { p ->
            exigir(estado.filmes.achar(p.id)).apply { this["assistido"] = p.param("assistido") != "false" }
        }
        rota("PUT", "/movies/{id}/rating") { p ->
            exigir(estado.filmes.achar(p.id)).apply {
                this["avaliacoes"] = JsonObject(this["avaliacoes"] as JsonObject + (titularId() to json(p.corpo.dinheiro("nota"))))
            }
        }
        rota("DELETE", "/movies/{id}") { remover(estado.filmes, it.id) }

        // Compras
        rota("GET", "/shopping/catalog") { p ->
            val termo = semAcento(p.param("busca") ?: p.param("q"))
            // O código também: o leitor de código de barras do app busca por ele (o OpenFoodFacts acha).
            PRODUTOS_DO_CATALOGO.filter { semAcento("${it.texto("nome")} ${it.texto("marca")} ${it.texto("codigo")}").contains(termo) }
        }
        rota("GET", "/shopping/items") { p ->
            val lista = p.param("lista")
            estado.compras.filter { lista == null || it.texto("lista") == lista }
        }
        rota("POST", "/shopping/items") { p ->
            val c = p.corpo
            registro(
                "id" to novoId("compra"), "item" to c["item"], "quantidade" to (c.texto("quantidade") ?: ""),
                "lista" to if (c.texto("lista") == "Desejos") "Desejos" else "Compras", "comprado" to false,
                "marca" to (c.texto("marca") ?: ""), "imagem" to (c.texto("imagem") ?: ""), "codigo" to (c.texto("codigo") ?: ""),
                "usuario" to titularId(),
            ).also { estado.compras += it }
        }
        rota("PATCH", "/shopping/items/{id}/purchased") { p ->
            exigir(estado.compras.achar(p.id)).apply { this["comprado"] = p.param("comprado") != "false" }
        }
        rota("DELETE", "/shopping/items/{id}") { remover(estado.compras, it.id) }

        // Mural e arquivos
        rota("GET", "/feed") { p ->
            val mes = p.param("mes")
            estado.fotos.filter { mes == null || it.texto("mesKey") == mes }.sortedByDescending { it.texto("criadoEm") }
        }
        rota("POST", "/feed") { p ->
            registro(
                "id" to novoId("feed"), "mesKey" to (p.corpo.texto("mesKey")?.ifEmpty { null } ?: mesAtual()),
                "assetId" to guardarImagem(p.corpo), "legenda" to "", "usuario" to titularId(), "criadoEm" to agora(),
            ).also { estado.fotos += it }
        }
        rota("PATCH", "/feed/{id}/caption") { p ->
            exigir(estado.fotos.achar(p.id)).apply { this["legenda"] = p.corpo.texto("legenda").orEmpty() }
        }
        rota("DELETE", "/feed/{id}") { remover(estado.fotos, it.id) }
        rota("POST", "/assets") { p ->
            registro(
                "id" to guardarImagem(p.corpo), "contentType" to (p.corpo.texto("mimeType") ?: "image/jpeg"),
                "tamanho" to p.corpo.texto("imageBase64").orEmpty().length, "contexto" to (p.corpo.texto("contexto") ?: ""), "criadoEm" to agora(),
            )
        }
        rota("GET", "/assets/{id}/content") { arquivoDaImagem(exigir(estado.imagens[it.id])) }
        rota("DELETE", "/assets/{id}") {
            estado.imagens.remove(it.id)
            Unit
        }

        // Fofocoins e prêmios
        rota("GET", "/coins") { saldoDeMoedas() }
        rota("POST", "/coins/adjustments") { p ->
            val valor = p.corpo.inteiro("valor")
            estado.moedas += valor
            estado.movimentos += registro("id" to novoId("moeda"), "data" to hojeIso(), "valor" to valor, "motivo" to p.corpo.texto("motivo").orEmpty(), "usuario" to titularId())
            saldoDeMoedas()
        }
        rota("GET", "/prizes/redemptions") { estado.resgates }
        rota("POST", "/prizes/{id}/redemptions") { p ->
            val premio = exigir(estado.premios.achar(p.id), "Prêmio não encontrado.")
            val preco = premio.inteiro("preco")
            if (estado.moedas < preco) throw ErroDaDemo(422, "Moedas insuficientes para esse prêmio.", "SALDO_INSUFICIENTE")
            estado.moedas -= preco
            estado.movimentos += registro("id" to novoId("moeda"), "data" to hojeIso(), "valor" to -preco, "motivo" to "Resgate: ${premio.texto("nome")}", "usuario" to titularId())
            registro(
                "id" to novoId("resgate"), "premio" to premio["nome"], "preco" to preco, "usuario" to titularId(), "status" to "RESGATADO",
                "data" to hojeIso(), "saldo" to estado.moedas,
            ).also { estado.resgates.add(0, it) }
        }
        rota("GET", "/prizes") { p -> estado.premios.filter { p.param("todos") == "true" || it.logico("ativo") } }
        rota("POST", "/prizes") { p ->
            registro(
                "id" to novoId("premio"), "nome" to p.corpo["nome"], "descricao" to (p.corpo.texto("descricao") ?: ""),
                "preco" to p.corpo.inteiro("preco"), "ativo" to (p.corpo["ativo"]?.takeIf { it != JsonNull } ?: true),
            ).also { estado.premios += it }
        }
        rota("PUT", "/prizes/{id}") { p ->
            exigir(estado.premios.achar(p.id)).apply {
                this["nome"] = p.corpo["nome"]
                this["descricao"] = p.corpo.texto("descricao") ?: ""
                this["preco"] = p.corpo.inteiro("preco")
                p.corpo["ativo"]?.takeIf { it != JsonNull }?.let { this["ativo"] = it }
            }
        }
        rota("DELETE", "/prizes/{id}") { remover(estado.premios, it.id) }
    }

    private companion object {
        const val INDISPONIVEL = "Indisponível na demonstração: aqui os dados são de exemplo."
        const val LIMITE_DO_ASSISTENTE = 150
        val JSON = "application/json; charset=utf-8".toMediaType()
        val BOM = Char(0xFEFF).toString()
        val ACENTOS = Regex("[\\u0300-\\u036f]")
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        val COLLATOR: Collator = Collator.getInstance(PT_BR)
        val COTACOES = linkedMapOf(
            "BRL" to 1.0, "USD" to 5.5, "EUR" to 6.0, "GBP" to 7.0, "ARS" to 0.0055, "CLP" to 0.0058, "UYU" to 0.14, "JPY" to 0.036,
        )
    }
}
