import SwiftUI

/// Mínimo, tetos e datas iguais no backend, no site e nos apps (BUG-008, BUG-023, D-189, D-195).
enum Limites {
    static let minimo = Decimal(string: "0.01")!
    /// Gasto (lançar, editar, item, conta fixa), orçamento, meta do mês e limite.
    static let gasto: Decimal = 1_000_000
    /// Receita, valor a receber e depósito no cofrinho.
    static let receita: Decimal = 10_000_000

    /// nil quando o valor serve; senão, o texto do erro.
    static func problema(_ valor: Decimal?, maximo: Decimal = gasto) -> String? {
        guard let valor, valor >= minimo else { return String(localized: "Digite um valor a partir de R$ 0,01.") }
        guard valor <= maximo else {
            return maximo == receita ? String(localized: "O valor máximo é R$ 10.000.000,00.")
                                     : String(localized: "O valor máximo é R$ 1.000.000,00.")
        }
        return nil
    }

    /// O que o backend aceita: de 01/01/2000 até hoje + 2 anos.
    static func datas(hoje: Date = Date()) -> ClosedRange<Date> {
        let inicio = Formatos.data(deDia: "2000-01-01") ?? .distantPast
        return inicio...(Calendar.current.date(byAdding: .year, value: 2, to: hoje) ?? hoje)
    }

    /// Data futura ou de mais de 1 ano atrás: o app pergunta antes de lançar.
    static func pedeConfirmacao(_ data: Date, hoje: Date = Date()) -> Bool {
        let umAnoAtras = Calendar.current.date(byAdding: .year, value: -1, to: hoje) ?? hoje
        return Formatos.dia(data) > Formatos.dia(hoje) || Formatos.dia(data) < Formatos.dia(umAnoAtras)
    }
}

extension View {
    /// "Essa data é 01/01/2030. Lançar mesmo assim?" com "Lançar" e "Corrigir a data".
    func confirmacaoDeData(_ data: Date, mostrando: Binding<Bool>, lancar: @escaping () -> Void) -> some View {
        alert("Tem certeza?", isPresented: mostrando) {
            Button("Lançar", action: lancar)
            Button("Corrigir a data", role: .cancel) {}
        } message: {
            Text("Essa data é \(data.formatted(.dateTime.day(.twoDigits).month(.twoDigits).year().locale(Formatos.localidade))). Lançar mesmo assim?")
        }
    }
}
