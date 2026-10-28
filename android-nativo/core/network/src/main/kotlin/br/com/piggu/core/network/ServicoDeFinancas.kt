package br.com.piggu.core.network

import br.com.piggu.core.model.AReceber
import br.com.piggu.core.model.AcertoDeDivisao
import br.com.piggu.core.model.Categorias
import br.com.piggu.core.model.Cofrinho
import br.com.piggu.core.model.ConferenciaDePreco
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.Cotacao
import br.com.piggu.core.model.Decimal
import br.com.piggu.core.model.Deposito
import br.com.piggu.core.model.EdicaoDeGasto
import br.com.piggu.core.model.EventoDeAuditoria
import br.com.piggu.core.model.FaturaComGastos
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.LinhaDoExtrato
import br.com.piggu.core.model.MoedaDisponivel
import br.com.piggu.core.model.Nota
import br.com.piggu.core.model.NovaConta
import br.com.piggu.core.model.NovaContaFixa
import br.com.piggu.core.model.NovaNota
import br.com.piggu.core.model.NovaReceita
import br.com.piggu.core.model.NovoAReceber
import br.com.piggu.core.model.NovoDeposito
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.model.ObjetivoDoCofrinho
import br.com.piggu.core.model.Orcamento
import br.com.piggu.core.model.PedidoAoAssistente
import br.com.piggu.core.model.PedidoDeAutorizacao
import br.com.piggu.core.model.PedidoDeCompartilhar
import br.com.piggu.core.model.PedidoDeExtrato
import br.com.piggu.core.model.PedidoDeImportacao
import br.com.piggu.core.model.PedidoDeLeitura
import br.com.piggu.core.model.PedidoDeLinhas
import br.com.piggu.core.model.PedidoDeMeta
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.PedidoDeObjetivo
import br.com.piggu.core.model.PedidoDeOrcamento
import br.com.piggu.core.model.PedidoDeRegra
import br.com.piggu.core.model.Produto
import br.com.piggu.core.model.Receita
import br.com.piggu.core.model.ReciboLido
import br.com.piggu.core.model.RegraDeCategoria
import br.com.piggu.core.model.RelatorioDoAno
import br.com.piggu.core.model.RespostaDoAssistente
import br.com.piggu.core.model.ResultadoDaImportacao
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.model.SituacaoDoAssistente
import br.com.piggu.core.model.TransferenciaDoAcerto
import br.com.piggu.core.model.UsoDeLeituras
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * piggu-finance (finance.service.ts): gastos, metas, cofrinho, notas, produtos, categorias,
 * recibos, câmbio, receitas, contas fixas, orçamentos, contas e cartões e o assistente.
 * Mês sempre `AAAA-MM`; [br.com.piggu.core.model.Visao.parametro] na `visao`.
 */
interface ServicoDeFinancas {
    @GET("expenses")
    suspend fun listarGastos(@Query("mes") mes: String? = null, @Query("visao") visao: String? = null): List<Gasto>

    @POST("expenses")
    suspend fun lancarGastos(@Body pedido: NovoLancamento): List<Gasto>

    @PUT("expenses/{id}")
    suspend fun editarGasto(@Path("id") id: String, @Body edicao: EdicaoDeGasto): Gasto

    /** @param proximas `true` apaga também as parcelas seguintes; nulo não manda o filtro */
    @DELETE("expenses/{id}")
    suspend fun excluirGasto(@Path("id") id: String, @Query("proximas") proximas: Boolean? = null)

    /** Busca global: até 20 gastos pelo item ou estabelecimento. */
    @GET("expenses/search")
    suspend fun buscarGastos(@Query("q") q: String): List<Gasto>

    /** Lê o extrato (OFX ou CSV) e devolve a prévia; nada é gravado. */
    @POST("expenses/import/preview")
    suspend fun previaDoExtrato(@Body pedido: PedidoDeExtrato): List<LinhaDoExtrato>

    /** A mesma prévia para linhas já separadas, como os gastos lidos do banco conectado. */
    @POST("expenses/import/preview-lines")
    suspend fun previaDeLinhas(@Body pedido: PedidoDeLinhas): List<LinhaDoExtrato>

    @POST("expenses/import")
    suspend fun importarExtrato(@Body pedido: PedidoDeImportacao): ResultadoDaImportacao

    /** O CSV dos gastos do mês. */
    @Streaming
    @GET("expenses/export")
    suspend fun exportarGastos(@Query("mes") mes: String): ResponseBody

    @GET("expenses/splits")
    suspend fun acertoDoMes(@Query("mes") mes: String): List<AcertoDeDivisao>

    @GET("expenses/splits/transfers")
    suspend fun quemDeveAQuem(@Query("mes") mes: String): List<TransferenciaDoAcerto>

    /** @return mapa de AAAA-MM para o limite daquele mês */
    @GET("monthly-goals")
    suspend fun listarMetas(): Map<String, Decimal>

    @PUT("monthly-goals")
    suspend fun definirMeta(@Body pedido: PedidoDeMeta): Map<String, Decimal>

    @GET("piggy-bank")
    suspend fun consultarCofrinho(): Cofrinho

    @POST("piggy-bank/deposits")
    suspend fun depositar(@Body deposito: NovoDeposito): Deposito

    @DELETE("piggy-bank/deposits/{id}")
    suspend fun excluirDeposito(@Path("id") id: String)

    @GET("piggy-bank/goals")
    suspend fun listarObjetivos(): List<ObjetivoDoCofrinho>

    @POST("piggy-bank/goals")
    suspend fun criarObjetivo(@Body pedido: PedidoDeObjetivo): ObjetivoDoCofrinho

    @PUT("piggy-bank/goals/{id}")
    suspend fun alterarObjetivo(@Path("id") id: String, @Body pedido: PedidoDeObjetivo)

    @DELETE("piggy-bank/goals/{id}")
    suspend fun excluirObjetivo(@Path("id") id: String)

    @GET("assistant")
    suspend fun situacaoDoAssistente(): SituacaoDoAssistente

    @POST("assistant/ask")
    suspend fun perguntarAoAssistente(@Body pedido: PedidoAoAssistente): RespostaDoAssistente

    /** Grava a autorização de enviar perguntas e totais ao Gemini, no toque em "Autorizo". */
    @POST("assistant/consent")
    suspend fun autorizarAssistente(@Body pedido: PedidoDeAutorizacao): SituacaoDoAssistente

    /** Retira a autorização (LGPD art. 8, par. 5). */
    @DELETE("assistant/consent")
    suspend fun retirarAutorizacaoDoAssistente()

    @GET("notes")
    suspend fun listarNotas(): List<Nota>

    @POST("notes")
    suspend fun criarNota(@Body nota: NovaNota): Nota

    @DELETE("notes/{id}")
    suspend fun excluirNota(@Path("id") id: String)

    @GET("products")
    suspend fun listarProdutos(): List<Produto>

    /**
     * Corpo vazio (204) quando o produto ainda não tem histórico: por isso o [Response].
     * @param valor o preço com `toPlainString()`, para não ir em notação científica
     */
    @GET("products/price-check")
    suspend fun conferirPreco(@Query("item") item: String, @Query("valor") valor: String): Response<ConferenciaDePreco>

    @GET("categories")
    suspend fun listarCategorias(): Categorias

    @POST("categories")
    suspend fun criarCategoria(@Body pedido: PedidoDeNome): Categorias

    @GET("categories/rules")
    suspend fun regrasDeCategoria(): List<RegraDeCategoria>

    /** Cria a regra; se o termo já tem uma, troca a categoria. */
    @PUT("categories/rules")
    suspend fun definirRegra(@Body pedido: PedidoDeRegra): RegraDeCategoria

    @DELETE("categories/rules/{id}")
    suspend fun excluirRegra(@Path("id") id: String)

    @GET("incomes")
    suspend fun listarReceitas(@Query("mes") mes: String): List<Receita>

    @GET("incomes/categories")
    suspend fun categoriasDeReceita(): List<String>

    @POST("incomes")
    suspend fun lancarReceita(@Body receita: NovaReceita): Receita

    @DELETE("incomes/{id}")
    suspend fun excluirReceita(@Path("id") id: String)

    @GET("incomes/receivables")
    suspend fun aReceber(): List<AReceber>

    @POST("incomes/receivables")
    suspend fun criarAReceber(@Body conta: NovoAReceber): AReceber

    /** "Recebi": vira receita de hoje. */
    @POST("incomes/receivables/{id}/receive")
    suspend fun receber(@Path("id") id: String, @Body vazio: JsonObject = JsonObject(emptyMap())): Receita

    @DELETE("incomes/receivables/{id}")
    suspend fun excluirAReceber(@Path("id") id: String)

    @GET("bills")
    suspend fun contasFixas(@Query("mes") mes: String): List<ContaFixa>

    /** Receitas que entram sozinhas todo mês. */
    @GET("bills")
    suspend fun receitasRecorrentes(@Query("mes") mes: String, @Query("tipo") tipo: String = "RECEITA"): List<ContaFixa>

    @POST("bills")
    suspend fun criarContaFixa(@Body conta: NovaContaFixa)

    @DELETE("bills/{id}")
    suspend fun excluirContaFixa(@Path("id") id: String)

    /** Lança o gasto do mês; o mesmo mês duas vezes é recusado (JA_PAGA). */
    @POST("bills/{id}/pay")
    suspend fun pagarContaFixa(
        @Path("id") id: String,
        @Query("mes") mes: String,
        @Body vazio: JsonObject = JsonObject(emptyMap()),
    ): Gasto

    @GET("budgets")
    suspend fun orcamentos(@Query("mes") mes: String): List<Orcamento>

    /** Cria ou muda o limite da categoria (Premium). */
    @PUT("budgets")
    suspend fun definirOrcamento(@Body pedido: PedidoDeOrcamento)

    @DELETE("budgets/{id}")
    suspend fun excluirOrcamento(@Path("id") id: String)

    @GET("reports/month")
    suspend fun resumoDoMes(@Query("mes") mes: String, @Query("visao") visao: String? = null): ResumoDoMes

    /** Premium. */
    @GET("reports/year")
    suspend fun relatorioDoAno(@Query("ano") ano: Int): RelatorioDoAno

    @GET("accounts")
    suspend fun contas(): List<ContaOuCartao>

    @POST("accounts")
    suspend fun criarConta(@Body conta: NovaConta): ContaOuCartao

    /** Conta ou cartão do casal: só quem criou liga ou desliga. */
    @PUT("accounts/{id}/sharing")
    suspend fun compartilharConta(@Path("id") id: String, @Body pedido: PedidoDeCompartilhar)

    @DELETE("accounts/{id}")
    suspend fun excluirConta(@Path("id") id: String)

    /** A fatura do cartão que fecha no mês, com os gastos. */
    @GET("accounts/{id}/statement")
    suspend fun fatura(@Path("id") id: String, @Query("mes") mes: String): FaturaComGastos

    /** Marca a fatura do mês como paga, com as anteriores; não lança gasto. */
    @PUT("accounts/{id}/statement/paid")
    suspend fun pagarFatura(@Path("id") id: String, @Query("mes") mes: String)

    @DELETE("accounts/{id}/statement/paid")
    suspend fun desfazerPagamentoDaFatura(@Path("id") id: String, @Query("mes") mes: String)

    @GET("receipts/usage")
    suspend fun usoDeLeituras(): UsoDeLeituras

    /** Retira a autorização de enviar a foto da nota ao Gemini (LGPD art. 8, par. 5). */
    @DELETE("receipts/consent")
    suspend fun retirarAutorizacaoDaLeituraDeNota()

    /** Lê a nota pela foto. Nada é gravado: a pessoa confere antes de lançar. */
    @POST("receipts/parse")
    suspend fun lerRecibo(@Body pedido: PedidoDeLeitura): ReciboLido

    @GET("exchange-rate")
    suspend fun consultarCotacao(@Query("de") de: String?, @Query("para") para: String?): Cotacao

    @GET("exchange-rate/currencies")
    suspend fun listarMoedas(): List<MoedaDisponivel>

    /** O que mudou no dinheiro. Junte com o das pessoas por [historicoDaFamilia]. */
    @GET("history")
    suspend fun historicoDoDinheiro(@Query("limite") limite: Int = 50): List<EventoDeAuditoria>
}
