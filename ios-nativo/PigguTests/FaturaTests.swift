import Testing
@testable import Piggu

/// Os mesmos casos de frontend/src/app/core/ui/fatura.spec.ts.
struct FaturaTests {
    @Test(arguments: [
        // Na vespera do fechamento cai na fatura do mes; no dia do fechamento, na seguinte.
        (8, 15, "2026-10-07", "2026-10", "2026-10-15"),
        (8, 15, "2026-10-08", "2026-11", "2026-11-15"),
        // Vencimento antes do fechamento fica no mes seguinte; dia 31 vira o fim do mes curto.
        (29, 5, "2026-10-30", "2026-11", "2026-12-05"),
        (31, 10, "2026-02-28", "2026-03", "2026-04-10"),
        // Virada do ano.
        (25, 5, "2026-12-26", "2027-01", "2027-02-05"),
    ])
    func emQueFaturaCaiACompra(fechamento: Int, vencimento: Int, compra: String, mes: String, venceEm: String) {
        let fatura = FaturaDoCartao.daCompra(fechamento: fechamento, vencimento: vencimento, compra: compra)
        #expect(fatura?.mes == mes)
        #expect(fatura?.vencimento == venceEm)
    }
}
