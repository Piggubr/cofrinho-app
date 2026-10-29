import Foundation
import Testing
@testable import Piggu

/// Os mesmos casos de frontend/src/app/features/calendar/grafico-do-mes.spec.ts.
struct CalendarioTests {
    private func gasto(_ id: String, _ data: String, _ valor: Decimal) -> Gasto {
        Gasto(id: id, data: data, reciboId: nil, estabelecimento: nil, item: "Item", categoria: "Casa", valor: valor,
              tipo: nil, origem: nil, usuario: nil, registradoEm: nil, contaId: nil, parcela: nil, parcelas: nil,
              moedaOriginal: nil, valorOriginal: nil, compartilhado: nil)
    }

    private func receita(_ id: String, _ data: String, _ valor: Decimal) -> Receita {
        Receita(id: id, data: data, descricao: "Salário", categoria: "Salário", valor: valor, usuario: nil,
                compartilhado: nil)
    }

    private var gastos: [Gasto] {
        [gasto("g1", "2026-09-02", 100), gasto("g2", "2026-09-02", 50), gasto("g3", "2026-09-20", 400),
         gasto("g4", "2026-08-31", 999)]
    }

    private var receitas: [Receita] { [receita("r1", "2026-09-05", 3000), receita("r2", "2026-09-15", 200)] }

    @Test func mesPassadoVaiAteOUltimoDiaAcumulando() {
        let serie = Calendario.serie(mes: "2026-09", gastos: gastos, receitas: receitas, hoje: "2026-10-20")
        #expect(serie.count == 30)
        #expect(serie[1].gastos == 150)
        #expect(serie[1].gastosAteAqui == 150)
        #expect(serie[4].receitasAteAqui == 3000)
        #expect(serie.last?.receitasAteAqui == 3200)
        #expect(serie.last?.gastosAteAqui == 550)
    }

    @Test func mesCorrenteParaEmHojeEFuturoFicaVazio() {
        #expect(Calendario.serie(mes: "2026-09", gastos: gastos, receitas: receitas, hoje: "2026-09-10").count == 10)
        #expect(Calendario.serie(mes: "2026-11", gastos: gastos, receitas: receitas, hoje: "2026-10-20").isEmpty)
    }

    @Test func semanasDoMesEGrade() {
        let serie = Calendario.serie(mes: "2026-09", gastos: gastos, receitas: receitas, hoje: "2026-10-20")
        let semanas = Calendario.semanas(serie, mes: "2026-09")
        #expect(semanas.map(\.de) == [1, 8, 15, 22, 29])
        #expect(semanas.last?.ate == 30)
        #expect(semanas[2].receitas == 200)
        #expect(semanas[2].gastos == 400)
        // Outubro de 2026 comeca numa quinta: quatro espacos antes do dia 1.
        let grade = Calendario.grade("2026-10")
        #expect(grade.prefix(4).allSatisfy { $0 == nil })
        #expect(grade[4] == "2026-10-01")
        #expect(grade.last == "2026-10-31")
        #expect(Calendario.diasNoMes("2028-02") == 29)
    }
}
