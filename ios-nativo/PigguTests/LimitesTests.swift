import Foundation
import Testing
@testable import Piggu

/// Minimo, tetos e datas do contrato da auditoria (BUG-008, BUG-023, D-195).
struct LimitesTests {
    @Test func valorMinimoETetoPorCampo() {
        #expect(Limites.problema(nil) == "Digite um valor a partir de R$ 0,01.")
        #expect(Limites.problema(0) == "Digite um valor a partir de R$ 0,01.")
        #expect(Limites.problema(Decimal(string: "0.01")) == nil)
        #expect(Limites.problema(1_000_000) == nil)
        #expect(Limites.problema(Decimal(string: "1000000.01")) == "O valor máximo é R$ 1.000.000,00.")
        #expect(Limites.problema(10_000_000, maximo: Limites.receita) == nil)
        #expect(Limites.problema(10_000_001, maximo: Limites.receita) == "O valor máximo é R$ 10.000.000,00.")
    }

    @Test func dataFuturaOuDeMaisDeUmAnoPedeConfirmacao() throws {
        let hoje = try #require(Formatos.data(deDia: "2026-11-11"))
        let data = { (dia: String) in Formatos.data(deDia: dia)! }
        #expect(!Limites.pedeConfirmacao(hoje, hoje: hoje))
        #expect(!Limites.pedeConfirmacao(data("2025-11-11"), hoje: hoje))
        #expect(Limites.pedeConfirmacao(data("2025-11-10"), hoje: hoje))
        #expect(Limites.pedeConfirmacao(data("2026-11-12"), hoje: hoje))
        #expect(Limites.datas(hoje: hoje).contains(data("2000-01-01")))
        #expect(!Limites.datas(hoje: hoje).contains(data("1999-12-31")))
        #expect(!Limites.datas(hoje: hoje).contains(data("2028-11-12")))
    }

    /// A demonstracao recusa o que o backend recusa, com as mesmas mensagens.
    @Test func demonstracaoRecusaComoOBackend() throws {
        let hoje = try #require(Formatos.data(deDia: "2026-11-11"))
        let gasto = { (valor: Any, data: String) -> [String: Any] in
            ["data": data, "itens": [["item": "Pão", "valor": valor]]]
        }
        #expect(TransporteDeDemonstracao.recusa("POST", "/expenses", gasto(0, "2026-11-11"), hoje: hoje)
            == "Digite um valor a partir de R$ 0,01.")
        #expect(TransporteDeDemonstracao.recusa("POST", "/expenses", gasto(1_000_001, "2026-11-11"), hoje: hoje)
            == "O valor máximo é R$ 1.000.000,00.")
        #expect(TransporteDeDemonstracao.recusa("POST", "/expenses", gasto(10, "1900-01-01"), hoje: hoje)
            == "Confira a data: use uma data entre 2000 e 2028.")
        #expect(TransporteDeDemonstracao.recusa("POST", "/expenses", gasto(10, "2026-11-11"), hoje: hoje) == nil)
        #expect(TransporteDeDemonstracao.recusa("PUT", "/expenses/abc", ["valor": 0], hoje: hoje) != nil)
        #expect(TransporteDeDemonstracao.recusa("POST", "/incomes", ["valor": 5_000_000, "data": "2026-11-11"], hoje: hoje) == nil)
        #expect(TransporteDeDemonstracao.recusa("POST", "/incomes", ["valor": 10_000_001, "data": "2026-11-11"], hoje: hoje)
            == "O valor máximo é R$ 10.000.000,00.")
        #expect(TransporteDeDemonstracao.recusa("POST", "/piggy-bank/deposits", ["valor": 0], hoje: hoje) != nil)
        #expect(TransporteDeDemonstracao.recusa("GET", "/expenses", [:], hoje: hoje) == nil)
    }

    /// Tocar de novo depois de um erro manda o mesmo id; mudou a receita, id novo (D-192).
    @Test func receitaReaproveitaOIdDoEnvio() {
        let receita = NovaReceita(data: "2026-11-11", descricao: "Salário", categoria: "Salário", valor: 5_000,
                                  compartilhado: false, recorrente: false)
        let primeira = receita.comIdDoEnvio(repetindo: nil)
        #expect(primeira.idDoEnvio != nil)
        #expect(receita.comIdDoEnvio(repetindo: primeira).idDoEnvio == primeira.idDoEnvio)
        let outra = NovaReceita(data: "2026-11-11", descricao: "Salário", categoria: "Salário", valor: 5_001,
                                compartilhado: false, recorrente: false)
        #expect(outra.comIdDoEnvio(repetindo: primeira).idDoEnvio != primeira.idDoEnvio)
    }

    /// 503 e 504 do gateway (D-191) com o texto do contrato, com ou sem corpo.
    @Test func servicoForaDoArTemOTextoDoContrato() {
        let texto = "Essa parte do Piggu está fora do ar agora. Tente de novo em instantes."
        #expect(ErroDaAPI.servidor(status: 503, mensagem: nil).localizedDescription == texto)
        #expect(ErroDaAPI.servidor(status: 504, mensagem: "Gateway Timeout", codigo: "SERVICO_INDISPONIVEL").localizedDescription == texto)
    }
}
