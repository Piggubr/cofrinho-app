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

    /// A Siri e os widgets usam a mesma conta do Painel (BUG-002).
    @Test func widgetESiriUsamAMesmaContaDoPainel() throws {
        let contas = [conta(187), conta(119, .vencida), conta(50, .paga), conta(6_200, tipo: "RECEITA")]
        let painel = try #require(calcular(limite: 4_500, receitas: 9_000, gasto: 3_260, contas: contas))
        let widget = ResumoDoWidget(mes: "2026-11", gasto: 3_260, limite: 4_500, moeda: "BRL", meta: nil,
                                    metasConcluidas: 0, metasTotal: 0, tema: "chiclete", atualizadoEm: hoje,
                                    receitas: 9_000, contasAPagar: AindaDaParaGastar.contasAPagar(contas))
        #expect(widget.aindaDa?.resta == painel.resta)
        // Sem as contas, o widget nao inventa "R$ 0,00 de contas a pagar".
        let semContas = ResumoDoWidget(mes: "2026-11", gasto: 3_260, limite: 4_500, moeda: "BRL", meta: nil,
                                       metasConcluidas: 0, metasTotal: 0, tema: "chiclete", atualizadoEm: hoje)
        #expect(semContas.aindaDa == nil)
        Formatos.codigoDaMoeda = "BRL"
        #expect(QuantoGasteiIntent.fala(gasto: 3_260, conta: painel, mes: "2026-11")
            .contains("Ainda dá para gastar \(Formatos.moeda(painel.resta))"))
    }

    /// O cartão desenha os estados: carregando, erro, com saldo, passou do valor e sem teto.
    @MainActor @Test func oCartaoDesenhaOsEstados() throws {
        let estados: [ModeloDoPainel.EstadoDoAindaDa] = [
            .carregando, .falhou(.semConexao), .pronto(calcular(limite: 4_500, gasto: 3_260)),
            .pronto(calcular(limite: 100, gasto: 150)), .pronto(nil)]
        for estado in estados {
            let cartao = CartaoAindaDaParaGastar(mes: hoje, estado: estado, irPara: { _ in }).frame(width: 390)
            let imagem = try #require(ImageRenderer(content: cartao).uiImage)
            #expect(imagem.size.height > 0)
        }
    }

    /// Enquanto as contas nao chegam o cartao fica carregando, nunca com "R$ 0,00 de contas a
    /// pagar"; depois, o numero conta as contas e o que esta na fila do aparelho (BUG-002, BUG-010).
    @MainActor @Test func oPainelEsperaAsContasEContaAFila() async throws {
        let transporte = TransporteDeDemonstracao(logado: true, falha: nil, conta: nil)
        let sessao = Sessao(cliente: ClienteDaAPI(urlBase: URL(string: "https://piggu.com.br/api")!, transporte: transporte,
                                                  cofre: nil), cache: Cache(soNaMemoria: true))
        await sessao.restaurar()
        let modelo = ModeloDoPainel(sessao: sessao)
        let mes = Formatos.mes(Date())
        let nucleo = ModeloDoPainel.Nucleo(cofrinho: Cofrinho(depositos: [], totalDepositos: 0, totalGastos: 0, saldo: 0),
                                           gastos: [], metas: [mes: 2_500])
        #expect(modelo.aindaDa(nucleo, mes: mes) == .carregando)

        await modelo.carregar(mes: mes, visao: .tudo)
        guard case let .pronto(conta?) = modelo.aindaDa(nucleo, mes: mes) else {
            Issue.record("esperava o numero pronto")
            return
        }
        #expect(conta.contasAPagar > 0)
        #expect(conta.contasAPagar == AindaDaParaGastar.contasAPagar(modelo.contasFixas))

        sessao.fila.guardar(NovoLancamento(data: Formatos.dia(Date()), itens: [ItemDeGasto(item: "Pão", categoria: nil, valor: 10)]))
        guard case let .pronto(comFila?) = modelo.aindaDa(nucleo, mes: mes) else { return }
        #expect(comFila.gasto == 10)
        #expect(comFila.resta == conta.resta - 10)
    }
}
