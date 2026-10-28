package br.com.piggu.core.testing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Dados de exemplo da demonstração, portados de frontend/src/app/demo/dados-da-demo.ts:
// uma família, três meses de vida financeira. Cada registro é o JSON que a API devolve.

internal typealias Registro = MutableMap<String, JsonElement>

/** Monta um registro com os valores convertidos para JSON, na ordem dada. */
internal fun registro(vararg pares: Pair<String, Any?>): Registro =
    pares.associateTo(LinkedHashMap()) { (chave, valor) -> chave to json(valor) }

/** BigDecimal é dinheiro (ou número com até duas casas): sai arredondado, sem notação científica. */
internal fun json(valor: Any?): JsonElement = when (valor) {
    null -> JsonNull
    is JsonElement -> valor
    is String -> JsonPrimitive(valor)
    is Boolean -> JsonPrimitive(valor)
    is BigDecimal -> JsonPrimitive(duasCasas(valor))
    is Number -> JsonPrimitive(valor)
    is Map<*, *> -> JsonObject(valor.entries.associate { (chave, v) -> chave.toString() to json(v) })
    is Iterable<*> -> JsonArray(valor.map(::json))
    else -> error("Sem conversão para JSON: ${valor::class}")
}

internal fun duasCasas(valor: BigDecimal): BigDecimal =
    valor.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

internal fun Map<String, JsonElement>.texto(chave: String): String? = (this[chave] as? JsonPrimitive)?.contentOrNull

internal fun Map<String, JsonElement>.numero(chave: String): BigDecimal? = texto(chave)?.trim()?.toBigDecimalOrNull()

internal fun Map<String, JsonElement>.dinheiro(chave: String): BigDecimal = numero(chave) ?: BigDecimal.ZERO

internal fun Map<String, JsonElement>.inteiro(chave: String): Int = numero(chave)?.toInt() ?: 0

internal operator fun Registro.set(chave: String, valor: Any?) {
    put(chave, json(valor))
}

internal val CATEGORIAS_BASE = listOf(
    "Aluguel", "Alimentação", "Transporte", "Farmácia/Saúde", "Lazer", "Assinaturas", "Gastos Extras", "Outros",
)

internal val CATEGORIAS_DE_RECEITA = listOf("Salário", "Extra", "Reembolso", "Investimentos", "Outros")

/** Catálogo de filmes para a busca da demo (o TMDB não é chamado). */
internal val FILMES_DO_CATALOGO = listOf(
    filmeDoCatalogo("demo-1", "Central do Brasil", "1998", "8", "Dora acompanha um menino em busca do pai."),
    filmeDoCatalogo("demo-2", "Cidade de Deus", "2002", "8.4", "A vida na Cidade de Deus entre os anos 60 e 80."),
    filmeDoCatalogo("demo-3", "O Auto da Compadecida", "2000", "8.5", "João Grilo e Chicó no sertão."),
    filmeDoCatalogo("demo-4", "Toy Story", "1995", "8", "Brinquedos ganham vida quando ninguém vê."),
    filmeDoCatalogo("demo-5", "Interestelar", "2014", "8.4", "Uma viagem pelo espaço para salvar a humanidade."),
)

internal val PRODUTOS_DO_CATALOGO = listOf(
    produtoDoCatalogo("7891000100103", "Leite condensado", "Moça", "395 g"),
    produtoDoCatalogo("7896004000015", "Café torrado e moído", "Pilão", "500 g"),
    produtoDoCatalogo("7891910000197", "Açúcar refinado", "União", "1 kg"),
    produtoDoCatalogo("7896036090244", "Arroz agulhinha", "Tio João", "5 kg"),
)

private fun filmeDoCatalogo(tmdbId: String, titulo: String, ano: String, nota: String, sinopse: String) =
    JsonObject(registro("tmdbId" to tmdbId, "titulo" to titulo, "ano" to ano, "poster" to "", "nota" to BigDecimal(nota), "sinopse" to sinopse))

private fun produtoDoCatalogo(codigo: String, nome: String, marca: String, quantidade: String) =
    JsonObject(registro("codigo" to codigo, "nome" to nome, "marca" to marca, "quantidade" to quantidade, "imagem" to ""))

/** Dia do mês (deslocamento 0 = este mês); dia 31 vira o último dia. */
internal fun diaNoMes(mes: YearMonth, dia: Int): String = mes.atDay(minOf(dia, mes.lengthOfMonth())).toString()

internal fun diaNoMes(mes: String, dia: Int): String = diaNoMes(YearMonth.parse(mes), dia)

/** Família Demo: Ana (titular, Premium) e Beto (membro), com três meses de dados até [hoje]. */
internal class EstadoDaDemo(hoje: LocalDate, fuso: ZoneId, private val novoId: (String) -> String) {
    private val hojeIso = hoje.toString()
    private val esteMes = YearMonth.from(hoje)
    private fun diaDoMes(deslocamento: Long, dia: Int) = diaNoMes(esteMes.plusMonths(deslocamento), dia)

    // O contrato diz date-time; o site manda só a data.
    private val premiumAte = hoje.plusYears(1).atStartOfDay(fuso).toInstant().toString()

    var titular = usuario("u-ana", "ana@demo.piggu.app", "Ana Souza", "TITULAR")
    var membro = usuario("u-beto", "beto@demo.piggu.app", "Beto Souza", "MEMBRO")
    var nomeDaFamilia = "Família Demo"
    val convites = mutableListOf(registro("id" to "convite-1", "email" to "vovo@exemplo.com", "venceEm" to hoje.plusDays(5).toString()))
    val categoriasExtras = mutableListOf("Pets")
    val gastos = mutableListOf<Registro>()

    /** Divisão: gastoId -> parte de cada pessoa. */
    val partes = mutableMapOf<String, Map<String, BigDecimal>>()
    val receitas = mutableListOf<Registro>()
    val aReceber = mutableListOf<Registro>()
    val contasFixas = mutableListOf(
        contaFixa("fixa-aluguel", "Aluguel", "Aluguel", "1800", 5, true),
        contaFixa("fixa-netflix", "Netflix", "Assinaturas", "55.9", 10, true),
        contaFixa("fixa-internet", "Internet", "Assinaturas", "119.9", 15, false),
        contaFixa("fixa-academia", "Academia", "Farmácia/Saúde", "99", 25, false),
    )

    /** "idDaConta|AAAA-MM" das contas fixas já pagas. */
    val contasPagas = mutableSetOf<String>()
    val orcamentos = mutableListOf(
        registro("id" to "orc-alimentacao", "categoria" to "Alimentação", "limite" to BigDecimal(900)),
        registro("id" to "orc-lazer", "categoria" to "Lazer", "limite" to BigDecimal(300)),
        registro("id" to "orc-transporte", "categoria" to "Transporte", "limite" to BigDecimal(400)),
    )

    /** ultimaFaturaPaga (AAAA-MM) é só da demo: as faturas até ela contam como pagas. */
    val contas = mutableListOf(
        registro("id" to "conta-nubank", "nome" to "Nubank", "tipo" to "CARTAO", "fechamento" to 5, "vencimento" to 12, "compartilhado" to false, "usuario" to "u-ana"),
        registro("id" to "conta-itau", "nome" to "Conta Itaú", "tipo" to "CONTA", "fechamento" to null, "vencimento" to null, "compartilhado" to false, "usuario" to "u-ana"),
    )
    val regras = mutableListOf(
        registro("id" to "regra-uber", "termo" to "uber", "categoria" to "Transporte"),
        registro("id" to "regra-padaria", "termo" to "padaria", "categoria" to "Alimentação"),
    )
    val metas = linkedMapOf<String, BigDecimal>()
    val depositos = mutableListOf<Registro>()
    val objetivos = mutableListOf(
        registro("id" to "objetivo-viagem", "nome" to "Viagem de férias", "alvo" to BigDecimal(6000), "prazo" to diaDoMes(8, 15), "compartilhado" to true, "usuario" to "u-ana"),
    )
    var assistenteAutorizado = false
    var perguntasAoAssistente = 0
    val notas = mutableListOf<Registro>()

    /** Minhas metas: só as do mês atual existem na demo. */
    val metasDoMes = mutableListOf(
        registro("id" to "meta-ler", "titulo" to "Ler 2 livros", "alvo" to 2, "atual" to 1, "mes" to esteMes.toString(), "concluida" to false),
        registro("id" to "meta-academia", "titulo" to "Ir à academia 12 vezes", "alvo" to 12, "atual" to 12, "mes" to esteMes.toString(), "concluida" to true),
        registro("id" to "meta-delivery", "titulo" to "No máximo 4 deliveries", "alvo" to 4, "atual" to 1, "mes" to esteMes.toString(), "concluida" to false),
    )
    val girlsMath = mutableListOf(
        registro("id" to "gm-bolsa", "produto" to "Bolsa de couro", "loja" to "Shopping Ibirapuera", "valor" to BigDecimal("349.9"), "data" to diaDoMes(0, 3), "fotoAssetId" to null, "temFoto" to false),
        registro("id" to "gm-tenis", "produto" to "Tênis de corrida", "loja" to "Loja online", "valor" to BigDecimal(499), "data" to diaDoMes(-1, 18), "fotoAssetId" to null, "temFoto" to false),
    )
    val lugares = mutableListOf<Registro>()
    val marcadores = mutableListOf("Romântico", "Barato", "Com as crianças", "Vista bonita")
    val filmes = mutableListOf<Registro>()
    val compras = mutableListOf<Registro>()
    val fotos = mutableListOf<Registro>()

    /** assetId -> data URL da imagem. */
    val imagens = mutableMapOf<String, String>()
    var moedas = 0L
    val movimentos = mutableListOf<Registro>()
    val premios = mutableListOf(
        registro("id" to "premio-cinema", "nome" to "Cinema com pipoca", "descricao" to "Sessão à escolha de quem resgatar", "preco" to 80, "ativo" to true),
        registro("id" to "premio-jantar", "nome" to "Jantar fora", "descricao" to "Restaurante novo da lista", "preco" to 150, "ativo" to true),
        registro("id" to "premio-folga", "nome" to "Dia sem louça", "descricao" to "O outro lava tudo", "preco" to 40, "ativo" to true),
    )
    val resgates = mutableListOf<Registro>()
    var bancos: List<Registro> = listOf(
        banco("banco-1", "Conta corrente", "•••• 1234", "3245.67", hoje, fuso),
        banco("banco-2", "Poupança", "•••• 9876", "12500", hoje, fuso),
    )
    var leiturasNoMes = 2

    /** POST /expenses já respondidos, pelo idDoEnvio (o reenvio da fila não lança de novo). */
    val envios = mutableMapOf<String, List<Registro>>()

    init {
        semear()
    }

    private fun usuario(id: String, email: String, nome: String, role: String) = registro(
        "id" to id,
        "email" to email,
        "nome" to nome,
        "primeiroNome" to nome.substringBefore(' '),
        "apelido" to null,
        "foto" to null,
        "fotoPropria" to false,
        "temFotoDoGoogle" to false,
        "role" to role,
        "ativo" to true,
        "permissoes" to emptyMap<String, Any>(),
        "preferencias" to mapOf(
            "moeda" to "BRL", "moedaCotacao" to "BRL", "moedaConversao" to "USD", "mostrarCotacao" to false,
            "fuso" to "America/Sao_Paulo", "idioma" to "pt-BR",
        ),
        "avisos" to mapOf("email" to true, "push" to false),
        "plano" to "PREMIUM",
        "premiumAte" to premiumAte,
        "familia" to "familia-demo",
        // O site deixa estes de fora (ausente conta como pronto); o app nativo recebe tudo pronto.
        "cpfInformado" to true,
        "termosAceitos" to VERSAO_DOS_TERMOS,
        "temSenha" to true,
        "emailConfirmado" to true,
        "pais" to "BR",
        "nascimento" to "1990-05-20",
        "compartilhamento" to mapOf("mostraTudoAoParceiro" to false, "compartilharPorPadrao" to false),
        "celularInformado" to true,
        "cadastroCompleto" to true,
    )

    private fun contaFixa(id: String, descricao: String, categoria: String, valor: String, dia: Int, automatico: Boolean) = registro(
        "id" to id, "descricao" to descricao, "categoria" to categoria, "valor" to BigDecimal(valor), "dia" to dia,
        "automatico" to automatico, "tipo" to "GASTO", "contaId" to null, "segundoDia" to null, "segundoValor" to null,
        "compartilhado" to false, "usuario" to "u-ana",
    )

    private fun banco(id: String, nome: String, numero: String, saldo: String, hoje: LocalDate, fuso: ZoneId) = registro(
        "id" to id, "conexaoId" to "conexao-demo", "instituicao" to "Banco Exemplo", "nome" to nome, "tipo" to "BANK",
        "numero" to numero, "saldo" to BigDecimal(saldo), "moeda" to "BRL", "status" to "UPDATED",
        "atualizadoEm" to hoje.atStartOfDay(fuso).plusHours(9).toInstant().toString(),
    )

    private fun gasto(data: String, item: String, categoria: String, valor: BigDecimal, vararg extras: Pair<String, Any?>): Registro? {
        if (data > hojeIso) return null
        val novo = registro(
            "id" to novoId("gasto"), "data" to data, "reciboId" to novoId("recibo"), "estabelecimento" to "", "item" to item,
            "categoria" to categoria, "valor" to valor, "tipo" to "Variavel", "origem" to "Manual", "usuario" to "u-ana",
            "registradoEm" to "${data}T12:00:00Z", "contaId" to null, "parcela" to null, "parcelas" to null,
            "moedaOriginal" to null, "valorOriginal" to null, "compartilhado" to false,
        )
        extras.forEach { (chave, v) -> novo[chave] = v }
        gastos += novo
        return novo
    }

    private fun semear() {
        for (deslocamento in -2L..0L) {
            fun dia(d: Int) = diaDoMes(deslocamento, d)
            val mes = esteMes.plusMonths(deslocamento).toString()
            // Pequena variação de um mês para o outro, para os gráficos terem forma.
            val f = BigDecimal(listOf("0.92", "1.08", "1")[(deslocamento + 2).toInt()])
            fun vezes(valor: String) = BigDecimal(valor).multiply(f).setScale(2, RoundingMode.HALF_UP)

            metas[mes] = BigDecimal(4500)
            depositos += registro("id" to novoId("deposito"), "data" to dia(1), "valor" to BigDecimal(4000), "usuario" to "u-ana", "registradoEm" to "${dia(1)}T09:00:00Z", "objetivo" to null)
            depositos += registro("id" to novoId("deposito"), "data" to dia(6), "valor" to BigDecimal(500), "usuario" to "u-beto", "registradoEm" to "${dia(6)}T09:00:00Z", "objetivo" to "objetivo-viagem")
            receitas += receita(dia(5), "Salário Ana", "Salário", 6500, "u-ana")
            receitas += receita(dia(5), "Salário Beto", "Salário", 4200, "u-beto")
            if (deslocamento == -1L) receitas += receita(dia(18), "Freela de design", "Extra", 800, "u-ana")

            // Contas fixas: meses passados todos pagos; neste, as que já venceram (menos a academia).
            for (fixa in contasFixas) {
                val vence = dia(fixa.inteiro("dia"))
                if (vence > hojeIso || (deslocamento == 0L && fixa.texto("id") == "fixa-academia")) continue
                gasto(vence, fixa.texto("descricao")!!, fixa.texto("categoria")!!, fixa.dinheiro("valor"), "tipo" to "Fixo", "origem" to "Conta fixa")
                contasPagas += "${fixa.texto("id")}|$mes"
            }

            gasto(dia(3), "Compras do mês", "Alimentação", vezes("412.3"), "estabelecimento" to "Supermercado Bom Preço", "contaId" to "conta-itau")
            gasto(dia(7), "Uber Centro", "Transporte", vezes("23.5"), "contaId" to "conta-nubank")
            gasto(dia(12), "Remédios", "Farmácia/Saúde", vezes("89.9"), "estabelecimento" to "Drogaria Popular")
            gasto(dia(14), "Jantar de sexta", "Lazer", vezes("156"), "estabelecimento" to "Cantina da Nonna", "contaId" to "conta-nubank")?.let { jantar ->
                val metade = jantar.dinheiro("valor").divide(BigDecimal(2))
                partes[jantar.texto("id")!!] = mapOf("u-ana" to metade, "u-beto" to metade)
            }
            gasto(dia(17), "Feira e hortifruti", "Alimentação", vezes("187.45"), "estabelecimento" to "Feira da Praça")
            gasto(dia(20), "Combustível", "Transporte", vezes("250"), "estabelecimento" to "Posto Shell", "contaId" to "conta-nubank", "usuario" to "u-beto")
            gasto(dia(23), "Cinema", "Lazer", vezes("64"), "contaId" to "conta-nubank", "usuario" to "u-beto")
            gasto(dia(26), "Padaria", "Alimentação", vezes("38.5"), "estabelecimento" to "Padaria Pão Quente")
            gasto(dia(28), "Ração do Thor", "Pets", vezes("129.9"), "estabelecimento" to "Pet Shop Amigo")

            // Fone em 3x no cartão, comprado há dois meses: uma parcela por mês.
            gasto(dia(8), "Fone de ouvido", "Gastos Extras", BigDecimal(133), "contaId" to "conta-nubank", "parcela" to deslocamento.toInt() + 3, "parcelas" to 3, "reciboId" to "recibo-fone")
        }

        // Compra em dólar no mês passado.
        gasto(diaDoMes(-1, 21), "Livro importado", "Gastos Extras", BigDecimal(110), "moedaOriginal" to "USD", "valorOriginal" to BigDecimal(20))

        notas += nota(diaDoMes(0, 15), "Aniversário da Bia", "Comprar presente até sexta", 0, "Outros", "u-ana")
        notas += nota(diaDoMes(0, 25), "Revisão do carro", "Agendada na oficina do bairro", 450, "Transporte", "u-beto")
        notas += nota(diaDoMes(1, 2), "IPVA", "Primeira parcela", 380, "Transporte", "u-ana")

        lugares += lugar("Cantina da Nonna", "Restaurante", "Vila Madalena, São Paulo", 5, "Melhor lasanha da cidade", diaDoMes(-1, 14), listOf("Romântico"), 156, "u-ana")
        lugares += lugar("Parque Ibirapuera", "Passeio", "São Paulo", 4, "Piquenique no domingo", diaDoMes(-2, 9), listOf("Barato", "Com as crianças"), 0, "u-beto")
        lugares += lugar("Café do Mirante", "Café", "Campos do Jordão", 5, "Vista incrível no fim de tarde", diaDoMes(-2, 22), listOf("Vista bonita"), 48, "u-ana")

        filmes += filme("Divertida Mente 2", "2024", "A Riley entra na adolescência e novas emoções aparecem.", true, mapOf("u-ana" to 5, "u-beto" to 4))
        filmes += filme("Duna: Parte Dois", "2024", "Paul Atreides se une aos Fremen.", true, mapOf("u-beto" to 5))
        filmes += filme("Ainda Estou Aqui", "2024", "A história de Eunice Paiva.", false, emptyMap())

        compras += compra("Leite integral", "6 caixas", "Compras", false, "Italac")
        compras += compra("Café 500g", "2", "Compras", false, "Pilão")
        compras += compra("Detergente", "3", "Compras", true, "")
        compras += compra("Air fryer", "1", "Desejos", false, "Mondial")
        compras += compra("Kindle", "1", "Desejos", false, "Amazon")

        for ((deslocamento, cores, emoji, legenda) in listOf(
            Foto(-2, "#f5a3c0" to "#b85c7d", "🏖️", "Praia no feriado"),
            Foto(-1, "#a3c4f5" to "#5c6fb8", "🎂", "Aniversário do Beto"),
            Foto(0, "#b7e0a5" to "#5e9a4b", "🐶", "Thor no parque"),
        )) {
            val assetId = novoId("foto")
            imagens[assetId] = fotoDeExemplo(cores.first, cores.second, emoji, legenda)
            fotos += registro(
                "id" to novoId("feed"), "mesKey" to esteMes.plusMonths(deslocamento).toString(), "assetId" to assetId,
                "legenda" to legenda, "usuario" to "u-ana", "criadoEm" to "${diaDoMes(deslocamento, 10)}T18:00:00Z",
            )
        }

        movimentos += movimento(diaDoMes(-1, 28), 50, "Meta do mês cumprida", "u-ana")
        movimentos += movimento(diaDoMes(-1, 30), -40, "Resgate: Dia sem louça", "u-beto")
        movimentos += movimento(diaDoMes(0, 2), 110, "Cofrinho do mês", "u-ana")
        moedas = movimentos.sumOf { it.inteiro("valor").toLong() }
        resgates += registro(
            "id" to novoId("resgate"), "premio" to "Dia sem louça", "preco" to 40, "usuario" to "u-beto",
            "status" to "RESGATADO", "data" to diaDoMes(-1, 30), "saldo" to 10,
        )
    }

    private data class Foto(val deslocamento: Long, val cores: Pair<String, String>, val emoji: String, val legenda: String)

    private fun receita(data: String, descricao: String, categoria: String, valor: Int, usuario: String) = registro(
        "id" to novoId("receita"), "data" to data, "descricao" to descricao, "categoria" to categoria,
        "valor" to BigDecimal(valor), "usuario" to usuario, "compartilhado" to false,
    )

    private fun nota(data: String, titulo: String, texto: String, valor: Int, categoria: String, usuario: String) = registro(
        "id" to novoId("nota"), "titulo" to titulo, "texto" to texto, "data" to data, "valor" to BigDecimal(valor),
        "categoria" to categoria, "gastoId" to null, "usuario" to usuario, "criadoEm" to "${data}T08:00:00Z",
    )

    @Suppress("LongParameterList")
    private fun lugar(
        nome: String, categoria: String, localizacao: String, nota: Int, comentario: String, data: String,
        marcacoes: List<String>, valor: Int, usuario: String,
    ) = registro(
        "id" to novoId("lugar"), "nome" to nome, "categoria" to categoria, "localizacao" to localizacao, "nota" to nota,
        "comentario" to comentario, "data" to data, "marcacoes" to marcacoes, "fotoAssetId" to null, "temFoto" to false,
        "valor" to BigDecimal(valor), "usuario" to usuario,
    )

    private fun filme(titulo: String, ano: String, sinopse: String, assistido: Boolean, avaliacoes: Map<String, Int>) = registro(
        "id" to novoId("filme"), "tmdbId" to novoId("tmdb"), "titulo" to titulo, "ano" to ano, "poster" to "",
        "nota" to BigDecimal("7.8"), "sinopse" to sinopse, "assistido" to assistido, "avaliacoes" to avaliacoes, "usuario" to "u-ana",
    )

    private fun compra(item: String, quantidade: String, lista: String, comprado: Boolean, marca: String) = registro(
        "id" to novoId("compra"), "item" to item, "quantidade" to quantidade, "lista" to lista, "comprado" to comprado,
        "marca" to marca, "imagem" to "", "codigo" to "", "usuario" to "u-ana",
    )

    private fun movimento(data: String, valor: Int, motivo: String, usuario: String) =
        registro("id" to novoId("moeda"), "data" to data, "valor" to valor, "motivo" to motivo, "usuario" to usuario)

    /** Foto de exemplo desenhada na hora (SVG), sem baixar nada. */
    private fun fotoDeExemplo(cor1: String, cor2: String, emoji: String, texto: String): String {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600">""" +
            """<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="$cor1"/>""" +
            """<stop offset="1" stop-color="$cor2"/></linearGradient></defs>""" +
            """<rect width="600" height="600" fill="url(#g)"/>""" +
            """<text x="300" y="300" font-size="160" text-anchor="middle" dominant-baseline="middle">$emoji</text>""" +
            """<text x="300" y="470" font-size="40" fill="#fff" text-anchor="middle" font-family="sans-serif">$texto</text>""" +
            "</svg>"
        return "data:image/svg+xml;charset=utf-8," + URLEncoder.encode(svg, "UTF-8").replace("+", "%20")
    }

    companion object {
        /** Versão vigente dos Termos e do Aviso (Consentimentos.VERSAO_DO_AVISO no backend). */
        const val VERSAO_DOS_TERMOS = "2026-10-16"
    }
}
