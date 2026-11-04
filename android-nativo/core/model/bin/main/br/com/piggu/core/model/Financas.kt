package br.com.piggu.core.model

import kotlinx.serialization.Serializable

// Tipos do piggu-finance (docs/api/finance.yaml). Regra de nulos em Conta.kt.

/** Recorte do painel. Vai só na query, e só quando não é tudo. */
enum class Visao(val parametro: String?) { TUDO(null), MEU("meu"), CASAL("casal") }

@Serializable(with = SituacaoDaContaFixa.Serializador::class)
enum class SituacaoDaContaFixa {
    PAGA, PENDENTE, VENCIDA, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<SituacaoDaContaFixa>(entries, DESCONHECIDO)
}

/** RECEITA: a que entra todo mês (o salário). */
@Serializable(with = TipoDeContaFixa.Serializador::class)
enum class TipoDeContaFixa {
    GASTO, RECEITA, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<TipoDeContaFixa>(entries, DESCONHECIDO)
}

@Serializable(with = TipoDeConta.Serializador::class)
enum class TipoDeConta {
    CONTA, CARTAO, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<TipoDeConta>(entries, DESCONHECIDO)
}

/** Alerta em 80% (ATENCAO) e em 100% (ESTOUROU). */
@Serializable(with = AlertaDoOrcamento.Serializador::class)
enum class AlertaDoOrcamento {
    OK, ATENCAO, ESTOUROU, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<AlertaDoOrcamento>(entries, DESCONHECIDO)
}

/** OCR: leitor próprio, a foto não saiu do servidor. GEMINI: leitura por IA. */
@Serializable(with = OrigemDaLeitura.Serializador::class)
enum class OrigemDaLeitura {
    OCR, GEMINI, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<OrigemDaLeitura>(entries, DESCONHECIDO)
}

/** ExpenseResponse. */
@Serializable
data class Gasto(
    val id: String,
    /** aaaa-mm-dd */
    val data: String,
    val reciboId: String? = null,
    val estabelecimento: String? = null,
    val item: String,
    val categoria: String,
    val valor: Decimal,
    val tipo: String? = null,
    val origem: String? = null,
    /** Id de quem lançou; nulo quando a conta foi excluída. */
    val usuario: String? = null,
    val registradoEm: String? = null,
    val contaId: String? = null,
    /** Parcela N de M; nulas quando o gasto é à vista. */
    val parcela: Int? = null,
    val parcelas: Int? = null,
    /** Compra em outra moeda: o [valor] já é o convertido. */
    val moedaOriginal: String? = null,
    val valorOriginal: Decimal? = null,
    val compartilhado: Boolean? = null,
)

/** ExpenseItemRequest. Categoria nula: o backend escolhe pelas regras. */
@Serializable
data class ItemDeGasto(
    val item: String,
    val categoria: String? = null,
    val valor: Decimal,
    val tipo: String? = null,
    val moedaOriginal: String? = null,
    val valorOriginal: Decimal? = null,
)

/** SaveExpensesRequest, corpo de POST `/expenses`. */
@Serializable
data class NovoLancamento(
    val data: String,
    val estabelecimento: String? = null,
    val reciboId: String? = null,
    val origem: String? = null,
    val itens: List<ItemDeGasto>,
    val contaId: String? = null,
    /** 2 a 48 divide cada item em uma linha por mês. */
    val parcelas: Int? = null,
    /** Ids das pessoas da família que dividem o gasto. */
    val dividirCom: List<String>? = null,
    val recorrente: Boolean? = null,
    val compartilhado: Boolean? = null,
    /** UUID do envio: reenviar da fila com o mesmo id não duplica o gasto. */
    val idDoEnvio: String? = null,
)

/** UpdateExpenseRequest. `compartilhado` nulo fica como está (só quem lançou muda). */
@Serializable
data class EdicaoDeGasto(
    val item: String,
    val categoria: String? = null,
    val valor: Decimal,
    val compartilhado: Boolean? = null,
)

/** ReceiptParseResponse: nada é gravado, a pessoa confere antes de lançar. */
@Serializable
data class ReciboLido(
    val reciboId: String,
    val estabelecimento: String,
    val data: String,
    val itens: List<ItemDoRecibo> = emptyList(),
    val origem: OrigemDaLeitura,
    /** O que conferir com mais cuidado; nulo quando a soma bateu com o total. */
    val aviso: String? = null,
    /** Leituras grátis que sobram no mês; nulo no Premium. */
    val leiturasRestantes: Int? = null,
)

@Serializable
data class ItemDoRecibo(val item: String, val categoria: String, val valor: Decimal)

/** ReceiptParseRequest. `autorizoIa` e `versaoDoAviso` só quando a pessoa autorizou agora. */
@Serializable
data class PedidoDeLeitura(
    val imageBase64: String,
    val mimeType: String,
    val autorizoIa: Boolean? = null,
    val versaoDoAviso: String? = null,
)

/** Uso: leituras de nota pela foto no mês da família; [restantes] nulo no Premium. */
@Serializable
data class UsoDeLeituras(val usadas: Int, val limite: Int, val restantes: Int? = null)

/** DepositResponse. */
@Serializable
data class Deposito(
    val id: String,
    val data: String,
    val valor: Decimal,
    val usuario: String? = null,
    val registradoEm: String,
    /** Cofrinho por objetivo que recebeu; nulo é o cofrinho geral. */
    val objetivo: String? = null,
)

/** DepositRequest. Sem [objetivo], vai para o cofrinho geral. */
@Serializable
data class NovoDeposito(val valor: Decimal, val data: String? = null, val objetivo: String? = null)

/** SituacaoDoObjetivo: o cofrinho por objetivo e onde ele está. */
@Serializable
data class ObjetivoDoCofrinho(
    val id: String,
    val nome: String,
    val alvo: Decimal,
    val prazo: String? = null,
    val guardado: Decimal,
    val falta: Decimal,
    /** 0 a 100. */
    val percentual: Int,
    /** Meses até o prazo contando o atual; nulo sem prazo. */
    val mesesRestantes: Int? = null,
    /** Quanto guardar por mês para chegar no prazo; nulo sem prazo. */
    val porMes: Decimal? = null,
    val concluido: Boolean,
    val atrasado: Boolean,
    /** Quem criou: só essa pessoa apaga. */
    val usuario: String? = null,
    val compartilhado: Boolean? = null,
)

/** ObjetivoRequest. Na edição, `compartilhado` nulo mantém como está. */
@Serializable
data class PedidoDeObjetivo(
    val nome: String,
    val alvo: Decimal,
    val prazo: String? = null,
    val compartilhado: Boolean? = null,
)

/** PiggyBankResponse. */
@Serializable
data class Cofrinho(
    val depositos: List<Deposito> = emptyList(),
    val totalDepositos: Decimal,
    val totalGastos: Decimal,
    val saldo: Decimal,
)

/** Assistente de IA: se está ligado, se a pessoa autorizou o envio e quanto sobra no mês. */
@Serializable
data class SituacaoDoAssistente(
    val habilitado: Boolean,
    val autorizado: Boolean,
    val limite: Int,
    val restantes: Int,
)

/** FalaRequest: uma fala da conversa, que só o aparelho guarda; [pessoa] falso é a resposta. */
@Serializable
data class FalaDoAssistente(val pessoa: Boolean, val texto: String)

/** PerguntaRequest. */
@Serializable
data class PedidoAoAssistente(
    val pergunta: String,
    val historico: List<FalaDoAssistente> = emptyList(),
    val moeda: String,
    val autorizoIa: Boolean? = null,
    val versaoDoAviso: String? = null,
)

/** Resposta do assistente; [tela] é a rota que ele sugere abrir, nula quando nenhuma. */
@Serializable
data class RespostaDoAssistente(val texto: String, val tela: String? = null, val restantes: Int)

/** AutorizacaoRequest: o "Autorizo" do envio ao Gemini. */
@Serializable
data class PedidoDeAutorizacao(val versaoDoAviso: String)

/** NoteResponse. Com valor, a nota lançou um gasto junto ([gastoId]). */
@Serializable
data class Nota(
    val id: String,
    val titulo: String,
    val texto: String? = null,
    val data: String? = null,
    val valor: Decimal? = null,
    val categoria: String? = null,
    val gastoId: String? = null,
    val usuario: String? = null,
    val criadoEm: String,
)

/** NoteRequest. */
@Serializable
data class NovaNota(
    val titulo: String,
    val texto: String? = null,
    val data: String? = null,
    val valor: Decimal? = null,
    val categoria: String? = null,
)

/** ProductResponse: o histórico de preço de um produto. */
@Serializable
data class Produto(
    val chave: String,
    val nome: String,
    val categoria: String,
    val ultimo: Decimal,
    val menor: Decimal,
    val maior: Decimal,
    val media: Decimal,
    val compras: Int,
    val data: String? = null,
    val variacao: Decimal,
)

/** ExchangeRateResponse. */
@Serializable
data class Cotacao(
    val de: String,
    val para: String,
    val taxa: Decimal,
    val data: String,
    val fonte: String? = null,
    val estimativa: Boolean? = null,
    val desatualizada: Boolean? = null,
)

/** CurrencyResponse: moeda que a fonte de câmbio conhece. */
@Serializable
data class MoedaDisponivel(val codigo: String, val nome: String)

/** CategoriesResponse: as de fábrica mais as criadas. */
@Serializable
data class Categorias(val categorias: List<String> = emptyList())

/** IncomeResponse. */
@Serializable
data class Receita(
    val id: String,
    val data: String,
    val descricao: String,
    val categoria: String,
    val valor: Decimal,
    val usuario: String? = null,
    val compartilhado: Boolean? = null,
)

/** IncomeRequest. A segunda parcela só na recorrente que entra em duas vezes (D-087). */
@Serializable
data class NovaReceita(
    val data: String,
    val descricao: String,
    val categoria: String,
    val valor: Decimal,
    val compartilhado: Boolean? = null,
    /** Entra sozinha no mesmo dia, todo mês. */
    val recorrente: Boolean = false,
    val segundoDia: Int? = null,
    val segundoValor: Decimal? = null,
)

/** BillResponse: conta fixa no mês, quando vence e se já foi lançada. */
@Serializable
data class ContaFixa(
    val id: String,
    val descricao: String,
    val categoria: String,
    val valor: Decimal,
    /** 1 a 31; 31 vira o último dia do mês. */
    val dia: Int,
    /** Lançada sozinha no dia do vencimento. */
    val automatico: Boolean,
    val vencimento: String,
    val situacao: SituacaoDaContaFixa,
    val tipo: TipoDeContaFixa? = null,
    /** Cartão ou conta que paga o gasto todo mês. */
    val contaId: String? = null,
    /** Segunda parcela da receita (o salário do dia 20 e do dia 5). */
    val segundoDia: Int? = null,
    val segundoValor: Decimal? = null,
    val usuario: String? = null,
    val compartilhado: Boolean? = null,
)

/** BillRequest. */
@Serializable
data class NovaContaFixa(
    val descricao: String,
    val categoria: String,
    val valor: Decimal,
    val dia: Int,
    val automatico: Boolean,
    val compartilhado: Boolean? = null,
)

/** ReceivableResponse: dinheiro que alguém deve; só vira receita no "Recebi". */
@Serializable
data class AReceber(
    val id: String,
    val quem: String,
    val descricao: String? = null,
    val valor: Decimal,
    /** Data combinada para receber. */
    val data: String,
)

/** ReceivableRequest. */
@Serializable
data class NovoAReceber(
    val quem: String,
    val descricao: String? = null,
    val valor: Decimal,
    val data: String,
)

/** Card do mês: receitas - gastos = sobra; sobra / receitas = taxa de poupança. */
@Serializable
data class ResumoDoMes(
    val mes: String,
    val receitas: Decimal,
    val gastos: Decimal,
    val sobra: Decimal,
    /** Em porcentagem; nula quando o mês não tem receita. */
    val taxaDePoupanca: Decimal? = null,
    val gastosMesAnterior: Decimal,
    /** Contra o mês anterior, em porcentagem; nula se o anterior não teve gasto. */
    val variacao: Decimal? = null,
    /** Só no mês corrente: o ritmo de gasto até hoje levado ao fim do mês. */
    val projecaoDeGastos: Decimal? = null,
    val porCategoria: List<CategoriaNoMes> = emptyList(),
)

@Serializable
data class CategoriaNoMes(val categoria: String, val total: Decimal, val anterior: Decimal)

/** Relatório do ano (Premium): mês a mês e por categoria. */
@Serializable
data class RelatorioDoAno(
    val ano: Int,
    val receitas: Decimal,
    val gastos: Decimal,
    val sobra: Decimal,
    val meses: List<MesDoAno> = emptyList(),
    val porCategoria: List<CategoriaNoAno> = emptyList(),
)

@Serializable
data class MesDoAno(val mes: String, val receitas: Decimal, val gastos: Decimal, val sobra: Decimal)

@Serializable
data class CategoriaNoAno(val categoria: String, val total: Decimal)

/** SituacaoDoOrcamento: o orçamento de uma categoria no mês. */
@Serializable
data class Orcamento(
    val id: String,
    val categoria: String,
    val limite: Decimal,
    val gasto: Decimal,
    val percentual: Decimal,
    val alerta: AlertaDoOrcamento,
)

/** BudgetRequest: cria ou muda o limite da categoria (Premium). */
@Serializable
data class PedidoDeOrcamento(val categoria: String, val limite: Decimal)

/** MonthlyGoalRequest: o limite de gastos do mês (AAAA-MM). */
@Serializable
data class PedidoDeMeta(val mes: String, val limite: Decimal)

/** Regra: o termo já vem normalizado (sem acento, minúsculo). */
@Serializable
data class RegraDeCategoria(val id: String, val termo: String, val categoria: String)

/** RuleRequest: se o termo já tem regra, troca a categoria. */
@Serializable
data class PedidoDeRegra(val termo: String, val categoria: String)

/** Fatura do cartão: os gastos entre o dia seguinte ao fechamento anterior e o fechamento. */
@Serializable
data class Fatura(
    val mes: String,
    val inicio: String? = null,
    /** Véspera do fechamento: o último dia de compra que ainda entra nesta fatura. */
    val ultimaCompra: String? = null,
    val fechamento: String? = null,
    val vencimento: String,
    val total: Decimal,
    /** Marcada como paga (ela ou uma seguinte): sai do "a pagar"; os gastos não mudam. */
    val paga: Boolean? = null,
)

@Serializable
data class FaturaComGastos(val fatura: Fatura, val gastos: List<Gasto> = emptyList())

/** Conta: conta ou cartão; no cartão, a fatura aberta e a fechada que ainda vai vencer. */
@Serializable
data class ContaOuCartao(
    val id: String,
    val nome: String,
    val tipo: TipoDeConta,
    val fechamento: Int? = null,
    val vencimento: Int? = null,
    val faturaAberta: Fatura? = null,
    val faturaAPagar: Fatura? = null,
    /** Quem criou: só essa pessoa apaga ou desfaz o compartilhamento. */
    val usuario: String? = null,
    /** Do casal: o que for lançado nela já nasce compartilhado. */
    val compartilhado: Boolean? = null,
)

/** AccountRequest. */
@Serializable
data class NovaConta(
    val nome: String,
    val tipo: TipoDeConta,
    val fechamento: Int? = null,
    val vencimento: Int? = null,
    val compartilhado: Boolean? = null,
)

/** CompartilhamentoRequest do finance: liga ou desliga a conta do casal. */
@Serializable
data class PedidoDeCompartilhar(val compartilhado: Boolean)

/** Acerto: saldo positivo tem a receber; negativo deve. */
@Serializable
data class AcertoDeDivisao(
    /** Nulo quando a conta foi excluída. */
    val pessoa: String? = null,
    val pagou: Decimal,
    val parte: Decimal,
    val saldo: Decimal,
)

/** Transferencia: [de] paga [valor] para [para]. */
@Serializable
data class TransferenciaDoAcerto(
    val de: String? = null,
    val para: String? = null,
    val valor: Decimal,
)

/** Preço digitado contra a média: acima quando passa 15% com 2 compras ou mais. */
@Serializable
data class ConferenciaDePreco(
    val media: Decimal,
    val compras: Int,
    val percentual: Decimal,
    val acima: Boolean,
)

/** LinhaDaPrevia: linha do extrato na prévia da importação. */
@Serializable
data class LinhaDoExtrato(
    val data: String,
    val descricao: String,
    val valor: Decimal,
    val idExterno: String,
    val categoria: String,
    val jaImportada: Boolean,
)

/** LinhaRequest: na prévia de linhas vai sem categoria; na importação, com a escolhida. */
@Serializable
data class LinhaParaImportar(
    val data: String,
    val descricao: String,
    val valor: Decimal,
    val idExterno: String,
    val categoria: String? = null,
)

/** ExtratoRequest: o OFX ou CSV como texto. */
@Serializable
data class PedidoDeExtrato(val conteudo: String)

/** LinhasRequest. */
@Serializable
data class PedidoDeLinhas(val linhas: List<LinhaParaImportar>)

/** ImportRequest. */
@Serializable
data class PedidoDeImportacao(val linhas: List<LinhaParaImportar>, val contaId: String? = null)

@Serializable
data class ResultadoDaImportacao(val importados: Int, val pulados: Int)
