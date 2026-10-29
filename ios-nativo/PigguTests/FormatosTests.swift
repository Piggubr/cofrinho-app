import Foundation
import Testing
@testable import Piggu

struct FormatosTests {
    @Test(arguments: [
        ("12,50", "12.5"),
        ("12,5", "12.5"),
        ("0,99", "0.99"),
        (",50", "0.5"),
        ("42", "42"),
        ("1.234,56", "1234.56"),
        ("1.234", "1234"),
        ("1.234.567,89", "1234567.89"),
        ("12.5", "12.5"),
        ("12.50", "12.5"),
        ("1,234.56", "1234.56"),
        ("R$ 15,90", "15.9"),
        (" 7,00 ", "7"),
    ])
    func leValorComVirgulaOuPonto(texto: String, esperado: String) {
        #expect(Formatos.lerValor(texto) == Decimal(string: esperado, locale: Locale(identifier: "en_US_POSIX")))
    }

    @Test(arguments: ["", "  ", "abc", "12,345", "1,2,3", "12,5a", "-5", "1.2.3", "R$", ",", "0.500"])
    func recusaValorInvalido(texto: String) {
        #expect(Formatos.lerValor(texto) == nil)
    }

    @Test func formataMoedaEmReais() {
        // O pt_BR separa "R$" do numero com espaco nao quebravel.
        let texto = Formatos.moeda(Decimal(string: "1234.5")!).replacingOccurrences(of: "\u{00A0}", with: " ")
        #expect(texto == "R$ 1.234,50")
    }

    @Test func soAPrimeiraLetraFicaMaiuscula() {
        #expect(Formatos.inicialMaiuscula("outubro de 2026") == "Outubro de 2026")
        #expect(Formatos.inicialMaiuscula("sexta-feira, 23 de outubro") == "Sexta-feira, 23 de outubro")
        #expect(Formatos.inicialMaiuscula("") == "")
    }

    @Test func formataMesEDiaComoAAPI() throws {
        let data = try #require(Formatos.data(deDia: "2026-03-07"))
        #expect(Formatos.mes(data) == "2026-03")
        #expect(Formatos.dia(data) == "2026-03-07")
        #expect(Formatos.mes(Formatos.somarMeses(-3, a: data)) == "2025-12")
    }
}
