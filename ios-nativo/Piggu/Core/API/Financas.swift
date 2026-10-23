import Foundation

/// Endpoints do piggu-finance usados pelo app (espelho de finance.service.ts).
extension ClienteDaAPI {
    /// - Parameter mes: aaaa-mm; ausente, o backend devolve todos.
    func listarGastos(mes: String?) async throws -> [Gasto] {
        try await get("/expenses", query: ["mes": mes])
    }

    /// O backend devolve uma linha por item (e por parcela) lancado.
    func lancarGastos(_ pedido: NovoLancamento) async throws -> [Gasto] {
        try await post("/expenses", corpo: pedido)
    }

    /// Receitas, gastos, sobra e taxa de poupanca do mes.
    func resumoDoMes(_ mes: String) async throws -> ResumoDoMes {
        try await get("/reports/month", query: ["mes": mes])
    }

    func listarCategorias() async throws -> [String] {
        let resposta: Categorias = try await get("/categories")
        return resposta.categorias
    }
}
