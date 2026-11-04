import Foundation
import Testing
@testable import Piggu

/// Começo do mês (D-165): o mês passado como referência no Painel.
@MainActor
struct MesPassadoTests {
    private func gasto(_ id: String, _ data: String, _ categoria: String, _ valor: Decimal,
                       registradoEm: String? = nil) -> Gasto {
        Gasto(id: id, data: data, reciboId: nil, estabelecimento: nil, item: id, categoria: categoria,
              valor: valor, tipo: nil, origem: nil, usuario: nil, registradoEm: registradoEm, contaId: nil,
              parcela: nil, parcelas: nil, moedaOriginal: nil, valorOriginal: nil, compartilhado: nil)
    }

    @Test func recentesSaoOsCincoUltimosPorDataEHora() {
        let gastos = [
            gasto("a", "2026-10-01", "Casa", 1), gasto("b", "2026-10-30", "Casa", 1),
            gasto("c", "2026-10-15", "Casa", 1), gasto("d", "2026-10-30", "Casa", 1, registradoEm: "2026-10-30T20:00:00"),
            gasto("e", "2026-10-20", "Casa", 1), gasto("f", "2026-10-10", "Casa", 1),
        ]
        #expect(ModeloDoPainel.recentes(gastos).map(\.id) == ["d", "b", "e", "c", "f"])
    }

    @Test func categoriasSomadasDaMaiorParaAMenor() {
        let totais = ModeloDoPainel.porCategoria([
            gasto("a", "2026-10-01", "Casa", 50), gasto("b", "2026-10-02", "Mercado", 80),
            gasto("c", "2026-10-03", "Casa", 40),
        ])
        #expect(totais.map(\.categoria) == ["Casa", "Mercado"])
        #expect(totais.map(\.total) == [90, 80])
    }
}
