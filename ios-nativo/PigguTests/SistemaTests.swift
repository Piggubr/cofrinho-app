import Foundation
import Testing
@testable import Piggu

/// Leitor do cupom, o resumo dos widgets, a fala da Siri, as notificacoes e os links novos.
struct SistemaTests {
    private let hoje = Formatos.data(deDia: "2026-10-27")!

    @Test func leOTotalEADataDeUmCupom() {
        let cupom = """
        SUPERMERCADO BOM PRECO LTDA
        CNPJ 12.345.678/0001-90
        26/10/2026 18:42:10
        ARROZ 5KG            1 UN   24,90
        FEIJAO 1KG           2 UN   17,80
        SUBTOTAL                    42,70
        DESCONTO                     2,70
        TOTAL R$                    40,00
        Val Aprox Tributos R$ 9,80 (24,5%) Lei 12.741
        """
        let leitura = LeitorDeCupom.ler(cupom.components(separatedBy: "\n"), hoje: hoje)
        #expect(leitura.total == 40)
        #expect(leitura.data == "2026-10-26")
    }

    @Test func valorAPagarGanhaDoTotalEOValorPodeVirNaLinhaDeBaixo() {
        let linhas = ["Qtd. total de itens 3", "VALOR TOTAL", "1.234,56", "Valor a pagar R$ 1.200,00", "05/10/26"]
        let leitura = LeitorDeCupom.ler(linhas, hoje: hoje)
        #expect(leitura.total == Decimal(string: "1200"))
        #expect(leitura.data == "2026-10-05")
    }

    @Test func naoChutaSemTotalNemDataFutura() {
        let leitura = LeitorDeCupom.ler(["ARROZ 24,90", "31/12/2026", "32/01/2026"], hoje: hoje)
        #expect(leitura.total == nil)
        #expect(leitura.data == nil)
        #expect(LeitorDeCupom.valores("TOTAL 12.50") == [Decimal(string: "12.5")!])
    }

    @MainActor
    @Test func resumoDoWidgetSemTokenComAMetaAberta() throws {
        Formatos.codigoDaMoeda = "BRL"
        let gasto = Gasto(id: "g1", data: "2026-10-26", reciboId: nil, estabelecimento: nil, item: "Pão", categoria: "Mercado",
                          valor: 30, tipo: nil, origem: nil, usuario: nil, registradoEm: nil, contaId: nil, parcela: nil,
                          parcelas: nil, moedaOriginal: nil, valorOriginal: nil, compartilhado: nil)
        let metas = [MetaDoMes(id: "a", titulo: "Ler", alvo: 1, atual: 1, concluida: true),
                     MetaDoMes(id: "b", titulo: "Caminhar", alvo: 10, atual: 6, concluida: false)]
        let resumo = AtualizadorDoSistema.resumo(mes: "2026-10", gastos: [gasto, gasto], limite: 100, metas: metas)
        #expect(resumo.gasto == 60)
        #expect(resumo.restante == 40)
        #expect(resumo.fracaoDoLimite == 0.6)
        #expect(resumo.meta?.titulo == "Caminhar")
        #expect(resumo.metasConcluidas == 1)
        // Limite zero e o mesmo que nenhum.
        #expect(AtalhoDeResumo.semLimite.limite == nil)

        let ajustes = try #require(UserDefaults(suiteName: "SistemaTests.\(UUID().uuidString)"))
        resumo.gravar(ajustes)
        #expect(ResumoDoWidget.ler(ajustes) == resumo)
        let json = String(data: try #require(ajustes.data(forKey: ResumoDoWidget.chave)), encoding: .utf8) ?? ""
        #expect(!json.contains("token"))
        ResumoDoWidget.apagar(ajustes)
        #expect(ResumoDoWidget.ler(ajustes) == nil)
    }

    @MainActor
    @Test func falaDaSiri() {
        Formatos.codigoDaMoeda = "BRL"
        // O formatador poe um espaco inquebravel depois do "R$": o esperado usa o mesmo.
        let r = { (valor: Decimal) in Formatos.moeda(valor) }
        #expect(QuantoGasteiIntent.fala(gasto: Decimal(string: "1039.39")!, limite: 2500, mes: "2026-10")
            == "Em outubro de 2026 você gastou \(r(Decimal(string: "1039.39")!)). Ainda dá para gastar \(r(Decimal(string: "1460.61")!)) do limite de \(r(2500)).")
        #expect(QuantoGasteiIntent.fala(gasto: 300, limite: 200, mes: "2026-10")
            == "Em outubro de 2026 você gastou \(r(300)). Passou \(r(100)) do limite de \(r(200)).")
        #expect(QuantoGasteiIntent.fala(gasto: 300, limite: nil, mes: "2026-10") == "Em outubro de 2026 você gastou \(r(300)).")
    }

    @MainActor
    @Test func notificacaoESiriAbremATelaCerta() {
        #expect(Notificacoes.destino(nil).absoluteString == "piggu://painel")
        #expect(Notificacoes.destino("piggu://contas-fixas").absoluteString == "piggu://contas-fixas")
        #expect(Notificacoes.destino("https://outro.site/x").absoluteString == "piggu://painel")
        #expect(Notificacoes.hexadecimal(Data([0x0a, 0xff, 0x01])) == "0aff01")
        #expect(LancarGastoIntent.link(valor: 12.5).absoluteString == "piggu://gasto?valor=12.50")
        #expect(LancarGastoIntent.link(valor: nil).absoluteString == "piggu://gasto")
        #expect(Rota(url: LancarGastoIntent.link(valor: 12.5)) == .lancarGasto(valor: Decimal(string: "12.5")))
        #expect(Rota(url: URL(string: "piggu://gastos?id=abc")!) == .verGasto(id: "abc"))
        #expect(Rota(url: URL(string: "piggu://lugares")!) == .tela(.lugares))
        #expect(Rota(url: URL(string: "https://piggu.com.br/feed")!) == .tela(.fotos))
    }
}

/// Um resumo sem limite, para o teste acima.
private enum AtalhoDeResumo {
    @MainActor static var semLimite: ResumoDoWidget {
        AtualizadorDoSistema.resumo(mes: "2026-10", gastos: [], limite: 0, metas: [])
    }
}
