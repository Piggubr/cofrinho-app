package br.com.piggu.core.model

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
import org.junit.Test
import org.yaml.snakeyaml.Yaml

/**
 * Os tipos contra o contrato gerado do backend (os yaml de docs/api): cada data class tem
 * exatamente os campos do schema. Campo trocado aqui só aparece em produção, sem erro de
 * compilação; por isso o teste, como o contrato-da-api.spec.ts do site.
 */
class ContratoTest {

    /** [arquivo, schema, tipo do app] */
    private val tipos: List<Triple<String, String, KSerializer<*>>> = listOf(
        Triple("identity", "UserResponse", Usuario.serializer()),
        Triple("identity", "Preferencias", Preferencias.serializer()),
        Triple("identity", "Avisos", Avisos.serializer()),
        Triple("identity", "AvisosRequest", Avisos.serializer()),
        Triple("identity", "Compartilhamento", Compartilhamento.serializer()),
        Triple("identity", "TokenResponse", ParDeTokens.serializer()),
        Triple("identity", "FamiliaResponse", Familia.serializer()),
        Triple("identity", "Membro", MembroDaFamilia.serializer()),
        Triple("identity", "Convite", ConviteDaFamilia.serializer()),
        Triple("identity", "Evento", EventoDeAuditoria.serializer()),
        Triple("identity", "Precos", Precos.serializer()),
        Triple("identity", "PlanoResponse", InfoDoPlano.serializer()),
        Triple("identity", "SenhaLoginRequest", PedidoDeLogin.serializer()),
        Triple("identity", "GoogleLoginRequest", PedidoDoGoogle.serializer()),
        Triple("identity", "CadastroRequest", PedidoDeCadastro.serializer()),
        Triple("identity", "RefreshRequest", PedidoDeRefresh.serializer()),
        Triple("identity", "DefinirSenhaRequest", PedidoDeSenha.serializer()),
        Triple("identity", "ConfirmarEmailRequest", PedidoComToken.serializer()),
        Triple("identity", "EsqueciSenhaRequest", PedidoDeEmail.serializer()),
        Triple("identity", "ConviteRequest", PedidoDeEmail.serializer()),
        Triple("identity", "RedefinirSenhaRequest", PedidoDeSenhaNova.serializer()),
        Triple("identity", "PreferencesRequest", PedidoDePreferencias.serializer()),
        Triple("identity", "CompartilhamentoRequest", PedidoDeCompartilhamento.serializer()),
        Triple("identity", "AparelhoRequest", PedidoDeAparelho.serializer()),
        Triple("identity", "ModulosRequest", PedidoDeModulos.serializer()),
        Triple("identity", "PaisRequest", PedidoDePais.serializer()),
        Triple("identity", "SobreVoceRequest", PedidoSobreVoce.serializer()),
        Triple("identity", "TermosRequest", PedidoDeTermos.serializer()),
        Triple("identity", "FotoRequest", PedidoDeFoto.serializer()),
        Triple("identity", "UpdateUserRequest", PedidoDeAlteracaoDeUsuario.serializer()),
        Triple("identity", "NomeRequest", PedidoDeNome.serializer()),
        Triple("identity", "PapelRequest", PedidoDePapel.serializer()),
        Triple("identity", "CheckoutRequest", PedidoDeCheckout.serializer()),
        Triple("identity", "ErroDoApp", ErroDoApp.serializer()),
        Triple("identity", "ApiError", CorpoDeErro.serializer()),
        Triple("identity", "CampoInvalido", CampoInvalido.serializer()),

        Triple("finance", "ExpenseResponse", Gasto.serializer()),
        Triple("finance", "ExpenseItemRequest", ItemDeGasto.serializer()),
        Triple("finance", "SaveExpensesRequest", NovoLancamento.serializer()),
        Triple("finance", "UpdateExpenseRequest", EdicaoDeGasto.serializer()),
        Triple("finance", "ReceiptParseResponse", ReciboLido.serializer()),
        Triple("finance", "Item", ItemDoRecibo.serializer()),
        Triple("finance", "ReceiptParseRequest", PedidoDeLeitura.serializer()),
        Triple("finance", "Uso", UsoDeLeituras.serializer()),
        Triple("finance", "DepositResponse", Deposito.serializer()),
        Triple("finance", "DepositRequest", NovoDeposito.serializer()),
        Triple("finance", "SituacaoDoObjetivo", ObjetivoDoCofrinho.serializer()),
        Triple("finance", "ObjetivoRequest", PedidoDeObjetivo.serializer()),
        Triple("finance", "PiggyBankResponse", Cofrinho.serializer()),
        Triple("finance", "SituacaoDoAssistente", SituacaoDoAssistente.serializer()),
        Triple("finance", "FalaRequest", FalaDoAssistente.serializer()),
        Triple("finance", "PerguntaRequest", PedidoAoAssistente.serializer()),
        Triple("finance", "Resposta", RespostaDoAssistente.serializer()),
        Triple("finance", "AutorizacaoRequest", PedidoDeAutorizacao.serializer()),
        Triple("finance", "NoteResponse", Nota.serializer()),
        Triple("finance", "NoteRequest", NovaNota.serializer()),
        Triple("finance", "ProductResponse", Produto.serializer()),
        Triple("finance", "ExchangeRateResponse", Cotacao.serializer()),
        Triple("finance", "CurrencyResponse", MoedaDisponivel.serializer()),
        Triple("finance", "CategoriesResponse", Categorias.serializer()),
        Triple("finance", "CategoryRequest", PedidoDeNome.serializer()),
        Triple("finance", "IncomeResponse", Receita.serializer()),
        Triple("finance", "IncomeRequest", NovaReceita.serializer()),
        Triple("finance", "BillResponse", ContaFixa.serializer()),
        Triple("finance", "BillRequest", NovaContaFixa.serializer()),
        Triple("finance", "ReceivableResponse", AReceber.serializer()),
        Triple("finance", "ReceivableRequest", NovoAReceber.serializer()),
        Triple("finance", "ResumoDoMes", ResumoDoMes.serializer()),
        Triple("finance", "CategoriaNoMes", CategoriaNoMes.serializer()),
        Triple("finance", "RelatorioDoAno", RelatorioDoAno.serializer()),
        Triple("finance", "MesDoAno", MesDoAno.serializer()),
        Triple("finance", "CategoriaNoAno", CategoriaNoAno.serializer()),
        Triple("finance", "SituacaoDoOrcamento", Orcamento.serializer()),
        Triple("finance", "BudgetRequest", PedidoDeOrcamento.serializer()),
        Triple("finance", "MonthlyGoalRequest", PedidoDeMeta.serializer()),
        Triple("finance", "Regra", RegraDeCategoria.serializer()),
        Triple("finance", "RuleRequest", PedidoDeRegra.serializer()),
        Triple("finance", "Fatura", Fatura.serializer()),
        Triple("finance", "FaturaComGastos", FaturaComGastos.serializer()),
        Triple("finance", "Conta", ContaOuCartao.serializer()),
        Triple("finance", "AccountRequest", NovaConta.serializer()),
        Triple("finance", "CompartilhamentoRequest", PedidoDeCompartilhar.serializer()),
        Triple("finance", "Acerto", AcertoDeDivisao.serializer()),
        Triple("finance", "Transferencia", TransferenciaDoAcerto.serializer()),
        Triple("finance", "ConferenciaDePreco", ConferenciaDePreco.serializer()),
        Triple("finance", "LinhaDaPrevia", LinhaDoExtrato.serializer()),
        Triple("finance", "LinhaRequest", LinhaParaImportar.serializer()),
        Triple("finance", "ExtratoRequest", PedidoDeExtrato.serializer()),
        Triple("finance", "LinhasRequest", PedidoDeLinhas.serializer()),
        Triple("finance", "ImportRequest", PedidoDeImportacao.serializer()),
        Triple("finance", "Evento", EventoDeAuditoria.serializer()),
        Triple("finance", "ApiError", CorpoDeErro.serializer()),
        Triple("finance", "CampoInvalido", CampoInvalido.serializer()),

        Triple("banking", "BankAccountResponse", ContaBancaria.serializer()),
        Triple("banking", "ConnectTokenResponse", ConnectToken.serializer()),
        Triple("banking", "ConsentimentoRequest", PedidoDeConexao.serializer()),
        Triple("banking", "RegisterItemRequest", PedidoDeItem.serializer()),
        Triple("banking", "GastoDoBanco", GastoDoBanco.serializer()),
        Triple("banking", "ApiError", CorpoDeErro.serializer()),
        Triple("banking", "CampoInvalido", CampoInvalido.serializer()),

        Triple("lifestyle", "PlaceResponse", Lugar.serializer()),
        Triple("lifestyle", "PlaceRequest", NovoLugar.serializer()),
        Triple("lifestyle", "PlaceTagRequest", PedidoDeNome.serializer()),
        Triple("lifestyle", "PersonalGoalResponse", MetaDoMes.serializer()),
        Triple("lifestyle", "PersonalGoalRequest", NovaMetaDoMes.serializer()),
        Triple("lifestyle", "GirlsMathResponse", ItemDoGirlsMath.serializer()),
        Triple("lifestyle", "GirlsMathRequest", NovoItemDoGirlsMath.serializer()),
        Triple("lifestyle", "MovieResponse", Filme.serializer()),
        Triple("lifestyle", "TmdbMovie", FilmeDoCatalogo.serializer()),
        Triple("lifestyle", "MovieRatingRequest", PedidoDeAvaliacao.serializer()),
        Triple("lifestyle", "ShoppingItemResponse", ItemDeCompra.serializer()),
        Triple("lifestyle", "ShoppingItemRequest", NovoItemDeCompra.serializer()),
        Triple("lifestyle", "CatalogProduct", ProdutoDoCatalogo.serializer()),
        Triple("lifestyle", "ApiError", CorpoDeErro.serializer()),
        Triple("lifestyle", "CampoInvalido", CampoInvalido.serializer()),

        Triple("media", "FeedPhotoResponse", FotoDoFeed.serializer()),
        Triple("media", "FeedPhotoRequest", NovaFotoDoFeed.serializer()),
        Triple("media", "CaptionRequest", PedidoDeLegenda.serializer()),
        Triple("media", "AssetResponse", Arquivo.serializer()),
        Triple("media", "ApiError", CorpoDeErro.serializer()),
        Triple("media", "CampoInvalido", CampoInvalido.serializer()),

        Triple("rewards", "CoinBalanceResponse", SaldoDeMoedas.serializer()),
        Triple("rewards", "Movimento", MovimentoDeMoedas.serializer()),
        Triple("rewards", "CoinAdjustRequest", PedidoDeAjuste.serializer()),
        Triple("rewards", "PrizeResponse", Premio.serializer()),
        Triple("rewards", "PrizeRequest", NovoPremio.serializer()),
        Triple("rewards", "RedemptionResponse", Resgate.serializer()),
        Triple("rewards", "ApiError", CorpoDeErro.serializer()),
        Triple("rewards", "CampoInvalido", CampoInvalido.serializer()),
    )

    /** Schemas que o app ainda não usa: o site também não chama essas rotas. */
    private val foraDoApp = setOf(
        // Entrar com Apple é só do iPhone.
        "identity/AppleLoginRequest",
        // Login pelo navegador do app Capacitor; o nativo usa o SDK do Google.
        "identity/TrocaRequest",
        "identity/TrocaResponse",
        // O site informa o CPF por /me/pais.
        "identity/CpfRequest",
        // Envio de arquivo solto: as telas mandam a foto junto do lugar, do item ou do feed.
        "media/AssetUploadRequest",
    )

    private val pasta = File(System.getProperty("piggu.contrato") ?: "../../../docs/api")

    @Suppress("UNCHECKED_CAST")
    private fun schemas(arquivo: String): Map<String, Map<String, Any?>> {
        val yaml = File(pasta, "$arquivo.yaml").reader().use { Yaml().load<Map<String, Any?>>(it) }
        return (yaml["components"] as Map<String, Any?>)["schemas"] as Map<String, Map<String, Any?>>
    }

    @Test
    fun `cada tipo tem os campos do schema`() {
        val diferentes = tipos.mapNotNull { (arquivo, schema, tipo) ->
            @Suppress("UNCHECKED_CAST")
            val doContrato = (schemas(arquivo).getValue(schema)["properties"] as Map<String, Any?>).keys.sorted()
            val doApp = tipo.descriptor.elementNames.sorted()
            if (doContrato == doApp) null else "$arquivo/$schema: contrato $doContrato, app $doApp"
        }

        assertEquals("", diferentes.joinToString("\n"))
    }

    @Test
    fun `todo schema do contrato tem tipo no app ou esta de fora de proposito`() {
        val noContrato = listOf("identity", "finance", "banking", "lifestyle", "media", "rewards")
            .flatMap { arquivo -> schemas(arquivo).keys.map { "$arquivo/$it" } }
            .toSet()
        val cobertos = tipos.map { (arquivo, schema) -> "$arquivo/$schema" }.toSet()

        assertEquals(foraDoApp, noContrato - cobertos)
        assertEquals(emptySet<String>(), cobertos - noContrato)
    }
}
