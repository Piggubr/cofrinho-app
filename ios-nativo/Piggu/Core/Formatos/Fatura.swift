import Foundation

/// Em que fatura cai uma compra, com as mesmas regras do servidor e do site (core/ui/fatura.ts):
/// antes do dia do fechamento, na fatura do mes; do fechamento em diante, na do mes seguinte.
enum FaturaDoCartao {
    /// - Parameter compra: aaaa-mm-dd
    /// - Returns: o mes da fatura (aaaa-mm) e o vencimento dela (aaaa-mm-dd).
    static func daCompra(fechamento: Int, vencimento: Int, compra: String) -> (mes: String, vencimento: String)? {
        let partes = compra.split(separator: "-").compactMap { Int($0) }
        guard partes.count == 3 else { return nil }
        let (ano, mes, dia) = (partes[0], partes[1], partes[2])
        let fechaNoMes = min(fechamento, diasNoMes(ano, mes))
        let (anoDaFatura, mesDaFatura) = somar(dia < fechaNoMes ? 0 : 1, a: ano, mes)
        let (anoDoVencimento, mesDoVencimento) = somar(vencimento > fechamento ? 0 : 1, a: anoDaFatura, mesDaFatura)
        let diaDoVencimento = min(vencimento, diasNoMes(anoDoVencimento, mesDoVencimento))
        return (String(format: "%04d-%02d", anoDaFatura, mesDaFatura),
                String(format: "%04d-%02d-%02d", anoDoVencimento, mesDoVencimento, diaDoVencimento))
    }

    private static func somar(_ meses: Int, a ano: Int, _ mes: Int) -> (Int, Int) {
        let total = ano * 12 + (mes - 1) + meses
        return (total / 12, total % 12 + 1)
    }

    private static func diasNoMes(_ ano: Int, _ mes: Int) -> Int {
        let calendario = Calendar(identifier: .gregorian)
        let data = calendario.date(from: DateComponents(year: ano, month: mes, day: 1))!
        return calendario.range(of: .day, in: .month, for: data)!.count
    }
}
