import Foundation

/// Endpoints do piggu-finance usados pelo app (espelho de finance.service.ts).
extension ClienteDaAPI {
    // MARK: - Gastos

    /// - Parameter mes: aaaa-mm; ausente, o backend devolve todos.
    /// - Parameter visao: recorte do painel (so o meu ou so o compartilhado).
    func listarGastos(mes: String?, visao: Visao = .tudo) async throws -> [Gasto] {
        try await get("/expenses", query: ["mes": mes, "visao": visao.parametro])
    }

    /// O backend devolve uma linha por item (e por parcela) lancado.
    func lancarGastos(_ pedido: NovoLancamento) async throws -> [Gasto] {
        try await post("/expenses", corpo: pedido)
    }

    func editarGasto(_ id: String, _ edicao: EdicaoDeGasto) async throws -> Gasto {
        try await put("/expenses/\(id)", corpo: edicao)
    }

    /// - Parameter eAsProximas: na parcela, apaga tambem as seguintes.
    func excluirGasto(_ id: String, eAsProximas: Bool = false) async throws {
        try await delete("/expenses/\(id)", query: ["proximas": eAsProximas ? "true" : nil])
    }

    /// Busca em todos os meses: ate 20 gastos pelo item ou estabelecimento.
    func buscarGastos(_ termo: String) async throws -> [Gasto] {
        try await get("/expenses/search", query: ["q": termo])
    }

    func acertoDoMes(_ mes: String) async throws -> [AcertoDeDivisao] {
        try await get("/expenses/splits", query: ["mes": mes])
    }

    func quemDeveAQuem(_ mes: String) async throws -> [TransferenciaDoAcerto] {
        try await get("/expenses/splits/transfers", query: ["mes": mes])
    }

    /// Nulo quando o produto ainda nao tem historico de preco (204).
    func conferirPreco(item: String, valor: Decimal) async -> ConferenciaDePreco? {
        try? await get("/products/price-check", query: ["item": item, "valor": "\(valor)"])
    }

    // MARK: - Categorias e regras

    func listarCategorias() async throws -> [String] {
        let resposta: Categorias = try await get("/categories")
        return resposta.categorias
    }

    /// Devolve a lista nova, ja com a criada.
    func criarCategoria(_ nome: String) async throws -> [String] {
        let resposta: Categorias = try await post("/categories", corpo: ["nome": nome])
        return resposta.categorias
    }

    func regrasDeCategoria() async throws -> [RegraDeCategoria] {
        try await get("/categories/rules")
    }

    /// Cria a regra; se o termo ja tem uma, troca a categoria.
    func definirRegra(termo: String, categoria: String) async throws -> RegraDeCategoria {
        try await put("/categories/rules", corpo: ["termo": termo, "categoria": categoria])
    }

    func excluirRegra(_ id: String) async throws {
        try await delete("/categories/rules/\(id)")
    }

    // MARK: - Painel

    /// Receitas, gastos, sobra e taxa de poupanca do mes.
    func resumoDoMes(_ mes: String, visao: Visao = .tudo) async throws -> ResumoDoMes {
        try await get("/reports/month", query: ["mes": mes, "visao": visao.parametro])
    }

    /// Mapa de aaaa-mm para o limite daquele mes.
    func listarMetas() async throws -> [String: Decimal] {
        try await get("/monthly-goals")
    }

    func consultarCofrinho() async throws -> Cofrinho {
        try await get("/piggy-bank")
    }

    func depositar(_ deposito: NovoDeposito) async throws {
        let _: Deposito = try await post("/piggy-bank/deposits", corpo: deposito)
    }

    func excluirDeposito(_ id: String) async throws {
        try await delete("/piggy-bank/deposits/\(id)")
    }

    func listarObjetivos() async throws -> [ObjetivoDoCofrinho] {
        try await get("/piggy-bank/goals")
    }

    func salvarObjetivo(_ pedido: PedidoDeObjetivo, id: String?) async throws {
        if let id {
            let _: SemCorpo = try await put("/piggy-bank/goals/\(id)", corpo: pedido)
        } else {
            let _: ObjetivoDoCofrinho = try await post("/piggy-bank/goals", corpo: pedido)
        }
    }

    func excluirObjetivo(_ id: String) async throws {
        try await delete("/piggy-bank/goals/\(id)")
    }

    func listarNotas() async throws -> [Nota] {
        try await get("/notes")
    }

    func criarNota(_ nota: NovaNota) async throws -> Nota {
        try await post("/notes", corpo: nota)
    }

    func excluirNota(_ id: String) async throws {
        try await delete("/notes/\(id)")
    }

    func orcamentos(_ mes: String) async throws -> [Orcamento] {
        try await get("/budgets", query: ["mes": mes])
    }

    func contasFixas(_ mes: String) async throws -> [ContaFixa] {
        try await get("/bills", query: ["mes": mes])
    }

    func contas() async throws -> [ContaOuCartao] {
        try await get("/accounts")
    }

    func consultarCotacao(de: String, para: String) async throws -> Cotacao {
        try await get("/exchange-rate", query: ["de": de, "para": para])
    }

    // MARK: - Receitas e a receber

    func listarReceitas(_ mes: String) async throws -> [Receita] {
        try await get("/incomes", query: ["mes": mes])
    }

    func categoriasDeReceita() async throws -> [String] {
        try await get("/incomes/categories")
    }

    func lancarReceita(_ receita: NovaReceita) async throws {
        let _: Receita = try await post("/incomes", corpo: receita)
    }

    func excluirReceita(_ id: String) async throws {
        try await delete("/incomes/\(id)")
    }

    /// Receitas que entram sozinhas todo mes.
    func receitasRecorrentes(_ mes: String) async throws -> [ContaFixa] {
        try await get("/bills", query: ["mes": mes, "tipo": "RECEITA"])
    }

    /// Para de lancar a receita (ou conta) todo mes; as ja lancadas ficam.
    func excluirContaFixa(_ id: String) async throws {
        try await delete("/bills/\(id)")
    }

    func aReceber() async throws -> [AReceber] {
        try await get("/incomes/receivables")
    }

    func criarAReceber(_ conta: NovoAReceber) async throws {
        let _: AReceber = try await post("/incomes/receivables", corpo: conta)
    }

    /// "Recebi": vira receita de hoje.
    func receber(_ id: String) async throws {
        let _: Receita = try await post("/incomes/receivables/\(id)/receive", corpo: [String: String]())
    }

    func excluirAReceber(_ id: String) async throws {
        try await delete("/incomes/receivables/\(id)")
    }
}

/// Organizacao: contas do mes, orcamentos, calendario, relatorios, contas e cartoes.
extension ClienteDaAPI {
    func criarContaFixa(_ conta: NovaContaFixa) async throws {
        let _: SemCorpo = try await post("/bills", corpo: conta)
    }

    /// Lanca o gasto do mes; o mesmo mes duas vezes e recusado.
    func pagarContaFixa(_ id: String, mes: String) async throws {
        let _: Gasto = try await post("/bills/\(id)/pay", corpo: [String: String](), query: ["mes": mes])
    }

    /// Cria ou muda o limite da categoria (Premium).
    func definirOrcamento(categoria: String, limite: Decimal) async throws {
        let _: SemCorpo = try await put("/budgets", corpo: PedidoDeOrcamento(categoria: categoria, limite: limite))
    }

    func excluirOrcamento(_ id: String) async throws {
        try await delete("/budgets/\(id)")
    }

    /// Limite total do mes; devolve o mapa atualizado.
    func definirMeta(mes: String, limite: Decimal) async throws -> [String: Decimal] {
        try await put("/monthly-goals", corpo: PedidoDeMeta(mes: mes, limite: limite))
    }

    /// Ano mes a mes e por categoria (Premium).
    func relatorioDoAno(_ ano: Int) async throws -> RelatorioDoAno {
        try await get("/reports/year", query: ["ano": String(ano)])
    }

    /// CSV dos gastos do mes, como o "Exportar" do site.
    func exportarGastos(_ mes: String) async throws -> Data {
        try await dados("/expenses/export", query: ["mes": mes])
    }

    func criarConta(_ conta: NovaConta) async throws {
        let _: ContaOuCartao = try await post("/accounts", corpo: conta)
    }

    /// Conta ou cartao do casal: so quem criou liga ou desliga.
    func compartilharConta(_ id: String, _ compartilhado: Bool) async throws {
        let _: SemCorpo = try await put("/accounts/\(id)/sharing", corpo: ["compartilhado": compartilhado])
    }

    func excluirConta(_ id: String) async throws {
        try await delete("/accounts/\(id)")
    }

    /// Fatura do cartao que fecha no mes (aaaa-mm), com os gastos.
    func fatura(_ id: String, mes: String) async throws -> FaturaComGastos {
        try await get("/accounts/\(id)/statement", query: ["mes": mes])
    }

    /// So tira a fatura (e as anteriores) do "a pagar"; nao lanca gasto (D-085).
    func pagarFatura(_ id: String, mes: String) async throws {
        try await put("/accounts/\(id)/statement/paid", query: ["mes": mes])
    }

    func desfazerPagamentoDaFatura(_ id: String, mes: String) async throws {
        try await delete("/accounts/\(id)/statement/paid", query: ["mes": mes])
    }

    /// Token que abre o widget da Pluggy; leva a autorizacao, que o backend grava junto.
    func gerarConnectToken(versaoDoAviso: String) async throws -> ConnectToken {
        try await post("/banking/connect-token", corpo: PedidoDeConexao(autorizo: true, versaoDoAviso: versaoDoAviso))
    }

    /// Registra o banco que o widget conectou e devolve as contas.
    func registrarItem(_ itemId: String) async throws -> [ContaBancaria] {
        try await post("/banking/items", corpo: ["itemId": itemId])
    }
}

private struct PedidoDeOrcamento: Codable, Sendable {
    let categoria: String
    let limite: Decimal
}

private struct PedidoDeMeta: Codable, Sendable {
    let mes: String
    let limite: Decimal
}

private struct PedidoDeConexao: Codable, Sendable {
    let autorizo: Bool
    let versaoDoAviso: String
}

/// piggu-lifestyle, piggu-banking e identity: so o que o Painel mostra.
extension ClienteDaAPI {
    func listarMetasDoMes() async throws -> [MetaDoMes] {
        try await get("/personal-goals")
    }

    func listarGirlsMath() async throws -> [ItemDoGirlsMath] {
        try await get("/girls-math")
    }

    func verFamilia() async throws -> Familia {
        try await get("/family")
    }

    /// Open Finance desligado ou fora do ar esconde o cartao dos bancos.
    func openFinanceHabilitado() async -> Bool {
        let situacao: SituacaoDoOpenFinance? = try? await get("/banking/status")
        return situacao?.habilitado ?? false
    }

    func contasBancarias() async throws -> [ContaBancaria] {
        try await get("/banking/accounts")
    }

    func sincronizarBancos() async throws -> [ContaBancaria] {
        try await post("/banking/sync", corpo: [String: String]())
    }

    func desconectarBanco(_ conexaoId: String) async throws -> [ContaBancaria] {
        try await delete("/banking/connections/\(conexaoId)")
    }

    func salvarPreferencias(_ preferencias: Preferencias) async throws -> Usuario {
        try await put("/auth/me/preferences", corpo: preferencias)
    }
}

/// Metas, Girls Math, Premios, Familia e Perfil (lifestyle, rewards, identity, media).
extension ClienteDaAPI {
    // MARK: - Minhas metas

    func criarMetaDoMes(_ meta: NovaMetaDoMes) async throws -> MetaDoMes {
        try await post("/personal-goals", corpo: meta)
    }

    func atualizarMetaDoMes(_ id: String, _ meta: NovaMetaDoMes) async throws -> MetaDoMes {
        try await put("/personal-goals/\(id)", corpo: meta)
    }

    func excluirMetaDoMes(_ id: String) async throws {
        try await delete("/personal-goals/\(id)")
    }

    // MARK: - Girls Math

    func criarNoGirlsMath(_ item: NovoItemDoGirlsMath) async throws -> ItemDoGirlsMath {
        try await post("/girls-math", corpo: item)
    }

    func excluirDoGirlsMath(_ id: String) async throws {
        try await delete("/girls-math/\(id)")
    }

    /// Foto guardada no piggu-media; pede o token, por isso nao vai direto num AsyncImage.
    func imagem(_ assetId: String) async throws -> Data {
        try await dados("/assets/\(assetId)/content")
    }

    // MARK: - Premios (Fofocoins)

    func consultarSaldoDeMoedas() async throws -> SaldoDeMoedas {
        try await get("/coins")
    }

    /// Credito ou debito (negativo) com o motivo; so quem cuida dos premios.
    func ajustarMoedas(_ valor: Int, motivo: String) async throws -> SaldoDeMoedas {
        try await post("/coins/adjustments", corpo: AjusteDeMoedas(valor: valor, motivo: motivo))
    }

    /// - Parameter todos: inclui os desativados (quem cuida dos premios).
    func listarPremios(todos: Bool) async throws -> [Premio] {
        try await get("/prizes", query: ["todos": todos ? "true" : nil])
    }

    func criarPremio(_ premio: NovoPremio) async throws {
        let _: Premio = try await post("/prizes", corpo: premio)
    }

    func excluirPremio(_ id: String) async throws {
        try await delete("/prizes/\(id)")
    }

    func resgatar(_ premioId: String) async throws -> Resgate {
        try await post("/prizes/\(premioId)/redemptions", corpo: [String: String]())
    }

    func listarResgates() async throws -> [Resgate] {
        try await get("/prizes/redemptions")
    }

    // MARK: - Familia

    func renomearFamilia(_ nome: String) async throws -> Familia {
        try await put("/family", corpo: ["nome": nome])
    }

    func convidar(_ email: String) async throws -> Familia {
        try await post("/family/invites", corpo: ["email": email])
    }

    func cancelarConvite(_ id: String) async throws -> Familia {
        try await delete("/family/invites/\(id)")
    }

    func removerMembro(_ id: String) async throws -> Familia {
        try await delete("/family/members/\(id)")
    }

    /// O titular convida para parceiro (vale quando a pessoa aceita) ou volta a membro.
    func mudarPapel(_ id: String, para papel: PigguRole) async throws -> Familia {
        try await put("/family/members/\(id)/role", corpo: ["papel": papel.rawValue])
    }

    func aceitarParceria() async throws {
        let _: SemCorpo = try await post("/family/partnership/accept", corpo: [String: String]())
    }

    func recusarParceria() async throws -> Familia {
        try await post("/family/partnership/decline", corpo: [String: String]())
    }

    /// Convites de outras familias para o meu e-mail.
    func convitesParaMim() async throws -> [ConviteDaFamilia] {
        try await get("/family/invites/mine")
    }

    func aceitarConvite(_ id: String) async throws {
        let _: SemCorpo = try await post("/family/invites/\(id)/accept", corpo: [String: String]())
    }

    func sairDaFamilia() async throws {
        let _: SemCorpo = try await post("/family/leave", corpo: [String: String]())
    }

    /// O dinheiro (/history) e as pessoas (/family/history) juntos, do mais novo para o mais velho.
    func historicoDaFamilia(limite: Int = 50) async -> [EventoDeAuditoria] {
        async let dinheiro: [EventoDeAuditoria]? = try? get("/history", query: ["limite": String(limite)])
        async let pessoas: [EventoDeAuditoria]? = try? get("/family/history", query: ["limite": String(limite)])
        let todos = (await dinheiro ?? []) + (await pessoas ?? [])
        return Array(todos.sorted { $0.quando > $1.quando }.prefix(limite))
    }

    // MARK: - Perfil

    func listarMoedas() async throws -> [MoedaDisponivel] {
        try await get("/exchange-rate/currencies")
    }

    func salvarAvisos(email: Bool, push: Bool) async throws -> Usuario {
        try await put("/auth/me/alerts", corpo: ["email": email, "push": push])
    }

    func salvarCompartilhamento(mostrarTudoAoParceiro: Bool, compartilharPorPadrao: Bool) async throws -> Usuario {
        try await put("/auth/me/sharing", corpo: ["mostrarTudoAoParceiro": mostrarTudoAoParceiro,
                                                  "compartilharPorPadrao": compartilharPorPadrao])
    }

    /// Telas de estilo de vida ligadas no menu da pessoa.
    func salvarModulos(_ modulos: [String]) async throws -> Usuario {
        try await put("/auth/me/modules", corpo: ["modulos": modulos])
    }

    func salvarApelido(_ apelido: String) async throws -> Usuario {
        try await put("/me/sobre-voce", corpo: PedidoSobreVoce(apelido: apelido, nascimento: nil))
    }

    /// Foto ja recortada e reduzida no aparelho, em JPEG.
    func trocarFoto(jpeg: Data) async throws -> Usuario {
        try await put("/me/foto", corpo: ["imagemBase64": jpeg.base64EncodedString(), "mimeType": "image/jpeg"])
    }

    func removerFoto() async throws -> Usuario {
        try await delete("/me/foto")
    }

    func usarFotoDoGoogle() async throws -> Usuario {
        try await put("/me/foto/google", corpo: [String: String]())
    }

    /// Copia de tudo o que o Piggu guarda sobre a pessoa (LGPD), em JSON.
    func exportarMeusDados() async throws -> Data {
        try await dados("/me/export")
    }

    func retirarAutorizacaoDaLeituraDeNota() async throws {
        try await delete("/receipts/consent")
    }

    func retirarAutorizacaoDoAssistente() async throws {
        try await delete("/assistant/consent")
    }

    // MARK: - Assistente

    func situacaoDoAssistente() async throws -> SituacaoDoAssistente {
        try await get("/assistant")
    }

    /// Grava a autorizacao de enviar perguntas e totais ao Gemini, no toque em "Autorizo".
    func autorizarAssistente() async throws -> SituacaoDoAssistente {
        try await post("/assistant/consent", corpo: ["versaoDoAviso": Conta.versaoDosTermos])
    }

    func perguntarAoAssistente(_ pedido: PedidoAoAssistente) async throws -> RespostaDoAssistente {
        try await post("/assistant/ask", corpo: pedido)
    }

    // MARK: - Premium

    func verPlano() async throws -> PlanoDaConta {
        try await get("/billing/plan")
    }

    /// Depois da compra (ou ao restaurar): o backend confere a transacao com a Apple e devolve o plano.
    func confirmarCompraDaApple(_ transacaoAssinada: String) async throws -> PlanoDaConta {
        try await post("/billing/app-store/transactions", corpo: ["transacao": transacaoAssinada])
    }

    // MARK: - Admin

    func listarContas() async throws -> [Usuario] {
        try await get("/users")
    }

    func alterarConta(_ id: String, papel: PigguRole? = nil, ativo: Bool? = nil) async throws {
        let _: Usuario = try await patch("/users/\(id)", corpo: AlteracaoDeConta(role: papel, ativo: ativo))
    }
}

private struct AjusteDeMoedas: Codable, Sendable {
    let valor: Int
    let motivo: String
}

private struct AlteracaoDeConta: Codable, Sendable {
    let role: PigguRole?
    let ativo: Bool?
}

/// Compras, Lugares, Filmes e Fotos (piggu-lifestyle e piggu-media).
extension ClienteDaAPI {
    func listarCompras() async throws -> [ItemDeCompra] {
        try await get("/shopping/items")
    }

    func criarItemDeCompra(_ item: NovoItemDeCompra) async throws -> ItemDeCompra {
        try await post("/shopping/items", corpo: item)
    }

    func marcarComprado(_ id: String, _ comprado: Bool) async throws {
        let _: ItemDeCompra = try await patch("/shopping/items/\(id)/purchased", query: ["comprado": comprado ? "true" : "false"])
    }

    func excluirItemDeCompra(_ id: String) async throws {
        try await delete("/shopping/items/\(id)")
    }

    func buscarNoCatalogo(_ busca: String) async throws -> [ProdutoDoCatalogo] {
        try await get("/shopping/catalog", query: ["busca": busca])
    }

    func listarLugares() async throws -> [Lugar] {
        try await get("/places")
    }

    func listarMarcadores() async throws -> [String] {
        try await get("/places/tags")
    }

    func criarMarcador(_ nome: String) async throws -> [String] {
        try await post("/places/tags", corpo: ["nome": nome])
    }

    func salvarLugar(_ lugar: NovoLugar, id: String?) async throws -> Lugar {
        if let id { return try await put("/places/\(id)", corpo: lugar) }
        return try await post("/places", corpo: lugar)
    }

    func excluirLugar(_ id: String) async throws {
        try await delete("/places/\(id)")
    }

    func listarFilmes() async throws -> [Filme] {
        try await get("/movies")
    }

    func buscarFilmes(_ busca: String) async throws -> [FilmeDoCatalogo] {
        try await get("/movies/search", query: ["busca": busca])
    }

    /// - Parameter genero: id de genero do TMDB; nil sorteia entre todos.
    func sortearFilme(genero: String?) async throws -> FilmeDoCatalogo {
        try await get("/movies/random", query: ["genero": genero])
    }

    func adicionarFilme(_ filme: FilmeDoCatalogo) async throws -> Filme {
        try await post("/movies", corpo: filme)
    }

    func marcarFilmeAssistido(_ id: String, _ assistido: Bool) async throws -> Filme {
        try await patch("/movies/\(id)/watched", query: ["assistido": assistido ? "true" : "false"])
    }

    func avaliarFilme(_ id: String, nota: Int) async throws -> Filme {
        try await put("/movies/\(id)/rating", corpo: ["nota": nota])
    }

    func excluirFilme(_ id: String) async throws {
        try await delete("/movies/\(id)")
    }

    func listarFeed(_ mes: String) async throws -> [FotoDoFeed] {
        try await get("/feed", query: ["mes": mes])
    }

    func publicarNoFeed(mes: String, jpeg: Data) async throws -> FotoDoFeed {
        try await post("/feed", corpo: ["mesKey": mes, "imageBase64": jpeg.base64EncodedString(), "mimeType": "image/jpeg"])
    }

    func legendar(_ id: String, _ legenda: String) async throws -> FotoDoFeed {
        try await patch("/feed/\(id)/caption", corpo: ["legenda": legenda])
    }

    func excluirDoFeed(_ id: String) async throws {
        try await delete("/feed/\(id)")
    }
}
