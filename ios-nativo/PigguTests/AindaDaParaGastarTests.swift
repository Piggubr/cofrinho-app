import Foundation
import SwiftUI
import Testing
@testable import Piggu

/// O número fixo no topo do Painel (D-164).
struct AindaDaParaGastarTests {
    /// 02/11/2026 ao meio-dia: faltam 29 dias no mês, hoje incluído.
    private let hoje = Formatos.data(deDia: "2026-11-02")!.addingTimeInterval(12 * 3_600)

    private func conta(_ valor: Decimal, _ situacao: ContaFixa.Situacao = .pendente, tipo: String? = "GASTO") -> ContaFixa {
        ContaFixa(id: "\(valor)", descricao: "Luz", categoria: "Casa", valor: valor, dia: 10, automatico: false,
                  vencimento: "2026-11-10", situacao: situacao, tipo: tipo, segundoDia: nil, segundoValor: nil,
                  contaId: nil, usuario: nil, compartilhado: nil)
    }

    private func calcular(limite: Decimal = 0, receitas: Decimal? = nil, gasto: Decimal = 0, contas: [ContaFixa] = [],
                          mes: String = "2026-11") -> AindaDaParaGastar? {
        AindaDaParaGastar.calcular(limite: limite, receitas: receitas, gasto: gasto, contas: contas, mes: mes, hoje: hoje)
    }

    @Test func comLimiteTiraOGastoESoAsContasDeGastoQueFaltamPagar() throws {
        let conta = try #require(calcular(limite: 4_500, receitas: 9_000, gasto: 3_260,
                                          contas: [conta(187), conta(119, .vencida), conta(50, .paga), conta(6_200, tipo: "RECEITA")]))
        #expect(conta.tetoEhOLimite)
        #expect(conta.contasAPagar == 306)
        #expect(conta.resta == 934)
        #expect(conta.porDia == Decimal(string: "32.2"))
    }

    @Test func semLimiteOTetoSaoAsReceitas() throws {
        let conta = try #require(calcular(receitas: 6_200, gasto: 1_000, contas: [conta(500)]))
        #expect(!conta.tetoEhOLimite)
        #expect(conta.resta == 4_700)
    }

    @Test func semLimiteESemReceitaNaoHaNumero() {
        #expect(calcular(gasto: 10) == nil)
        #expect(calcular(receitas: 0) == nil)
    }

    @Test func passouDoTetoFicaNegativoESemValorPorDia() throws {
        let conta = try #require(calcular(limite: 100, gasto: 150))
        #expect(conta.resta == -50)
        #expect(conta.porDia == nil)
    }

    @Test func mesPassadoNaoDividePorDia() throws {
        #expect(try #require(calcular(limite: 100, mes: "2026-10")).porDia == nil)
    }

    /// O cartão desenha os três estados: com saldo, passou do valor e sem teto.
    @MainActor @Test func oCartaoDesenhaOsTresEstados() throws {
        let estados = [calcular(limite: 4_500, gasto: 3_260), calcular(limite: 100, gasto: 150), nil]
        for conta in estados {
            let cartao = CartaoAindaDaParaGastar(mes: hoje, conta: conta, irPara: { _ in }).frame(width: 390)
            let imagem = try #require(ImageRenderer(content: cartao).uiImage)
            #expect(imagem.size.height > 0)
        }
    }
}
