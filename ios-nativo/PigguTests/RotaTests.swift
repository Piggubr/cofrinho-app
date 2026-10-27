import Foundation
import Testing
@testable import Piggu

struct RotaTests {
    @Test(arguments: [
        ("piggu://gasto?valor=12.50", Rota.lancarGasto(valor: Decimal(string: "12.5"))),
        ("piggu://gasto?valor=12,50", .lancarGasto(valor: Decimal(string: "12.5"))),
        ("piggu://gasto", .lancarGasto(valor: nil)),
        // Os links do app Capacitor, ja salvos em atalhos.
        ("piggu://gastos?rapido=1&valor=42.00", .lancarGasto(valor: 42)),
        ("piggu://gastos?rapido=1", .lancarGasto(valor: nil)),
        ("piggu://gastos", .aba(.gastos)),
        ("piggu://painel", .aba(.painel)),
        ("piggu://relatorios", .aba(.relatorios)),
        ("piggu://perfil", .aba(.mais)),
        ("https://piggu.com.br/relatorios", .aba(.relatorios)),
        ("https://www.piggu.com.br/", .aba(.painel)),
    ])
    func abreARota(link: String, esperada: Rota) {
        #expect(Rota(url: URL(string: link)!) == esperada)
    }

    @Test(arguments: [
        "piggu://gasto?valor=abc",
        "piggu://gasto?valor=0",
        "piggu://gasto?valor=-5",
        "piggu://gasto?valor=2000000",
    ])
    func valorInvalidoAbreEmBranco(link: String) {
        #expect(Rota(url: URL(string: link)!) == .lancarGasto(valor: nil))
    }

    @Test(arguments: [
        "https://outro.site/gastos",
        "http://piggu.com.br/gastos",
        "piggu://receitas",
        "piggu://entrar?google=codigo",
        "mailto:ana@example.com",
    ])
    func ignoraOQueNaoConhece(link: String) {
        #expect(Rota(url: URL(string: link)!) == nil)
    }

    @Test func valorParaDigitarUsaVirgula() {
        #expect(Formatos.valorParaDigitar(12.5) == "12,50")
        #expect(Formatos.valorParaDigitar(1234) == "1234,00")
    }
}
