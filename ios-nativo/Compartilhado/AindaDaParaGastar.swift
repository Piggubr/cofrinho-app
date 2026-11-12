import Foundation

/// O número fixo no topo do Painel (D-164), o mesmo da Siri e dos widgets (BUG-002, D-195): o teto
/// do mês menos o gasto e as contas fixas de gasto ainda não pagas. O teto é o limite do mês ou,
/// sem ele, as receitas do mês. A mesma conta do `calcularContaDoMes` do site.
struct AindaDaParaGastar: Codable, Equatable, Sendable {
    let teto: Decimal
    let tetoEhOLimite: Bool
    let gasto: Decimal
    let contasAPagar: Decimal
    /// Só no mês de hoje e com saldo: dividido pelos dias que faltam, hoje incluído.
    let porDia: Decimal?

    /// Negativo é o que passou.
    var resta: Decimal { teto - gasto - contasAPagar }

    /// nil sem limite e sem receita: o cartão convida a definir um dos dois.
    static func calcular(limite: Decimal, receitas: Decimal?, gasto: Decimal, contasAPagar: Decimal,
                         mes: String, hoje: Date = Date(), calendario: Calendar = .current) -> AindaDaParaGastar? {
        let tetoEhOLimite = limite > 0
        guard let teto = tetoEhOLimite ? limite : receitas.flatMap({ $0 > 0 ? $0 : nil }) else { return nil }
        let resta = teto - gasto - contasAPagar
        var porDia: Decimal?
        let hojeNoMes = calendario.dateComponents([.year, .month, .day], from: hoje)
        let mesDeHoje = String(format: "%04d-%02d", hojeNoMes.year ?? 0, hojeNoMes.month ?? 0)
        if resta > 0, mes == mesDeHoje, let dias = calendario.range(of: .day, in: .month, for: hoje)?.count {
            var dividido = resta / Decimal(dias - (hojeNoMes.day ?? 1) + 1)
            var arredondado = Decimal()
            NSDecimalRound(&arredondado, &dividido, 2, .down)
            porDia = arredondado
        }
        return AindaDaParaGastar(teto: teto, tetoEhOLimite: tetoEhOLimite, gasto: gasto, contasAPagar: contasAPagar, porDia: porDia)
    }
}
