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
