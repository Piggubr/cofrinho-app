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

    /// O cupom do Lidl que veio errado: o "Val.Total" da tabela do IVA (7,32) passava pelo total.
    @Test func cupomDePortugalSeparaOsItensEIgnoraATabelaDoIva() {
        let linhas = ["Lidl & Companhia", "NIF 503 340 855", "Descrição EUR",
                      "Pão de forma 1,29 B", "Iogurte grego x 8 6,32 B", "Poupança Lidl Plus -0,80",
                      "Banana 1,49 A", "Leite meio gordo 2,49 A", "Poupança Lidl Plus -0,60",
                      "Azeite de oliva 2,99 A", "Descafeinado 3,59 B", "Cebola 0,95 A",
                      "Total 17,72", "Multibanco 17,72", "Taxa IVA Val.Total Val.IVA", "A 6% 7,32 0,41", "B 23% 10,40 1,37"]
        let leitura = LeitorDeCupom.ler(linhas, hoje: hoje)
        #expect(leitura.total == Decimal(string: "17.72"))
        #expect(leitura.itens.map(\.descricao) == ["Pão de forma", "Iogurte grego", "Banana", "Leite meio gordo",
                                                   "Azeite de oliva", "Descafeinado", "Cebola"])
        #expect(leitura.itens.map(\.valor) == ["1.29", "5.52", "1.49", "1.89", "2.99", "3.59", "0.95"].map { Decimal(string: $0)! })
        #expect(leitura.itens.reduce(Decimal(0)) { $0 + $1.valor } == Decimal(string: "17.72"))
    }

    @Test func itensDoCupomBrasileiroTiramCodigoEQuantidade() {
        let leitura = LeitorDeCupom.ler(["001 7891234 ARROZ 5KG 1 UN X 24,90 24,90", "002 FEIJAO 1KG 2 UN X 8,90 17,80",
                                         "SUBTOTAL 42,70", "TOTAL R$ 42,70"], hoje: hoje)
        #expect(leitura.itens == [.init(descricao: "Arroz 5kg", valor: Decimal(string: "24.9")!),
                                  .init(descricao: "Feijao 1kg", valor: Decimal(string: "17.8")!)])
    }

    /// A camera le a coluna dos nomes e a dos precos em blocos separados, com o papel torto.
    @Test func remontaAsLinhasPelaPosicaoComOPapelInclinado() {
        // Inclinacao de 0,1 para baixo a cada unidade para a direita; cada linha tem 10 de altura.
        func caixa(_ x: CGFloat, _ y: CGFloat, largura: CGFloat, linhas: CGFloat = 1) -> [CGPoint] {
            let altura = 10 * linhas
            return [CGPoint(x: x, y: y + 0.1 * x), CGPoint(x: x + largura, y: y + 0.1 * (x + largura)),
                    CGPoint(x: x + largura, y: y + altura + 0.1 * (x + largura)), CGPoint(x: x, y: y + altura + 0.1 * x)]
        }
        let trechos: [LeitorDeCupom.Trecho] = [
            .init(texto: "1,49 A\n2,49 A", cantos: caixa(200, 0, largura: 40, linhas: 2)),
            .init(texto: "Total", cantos: caixa(0, 20, largura: 40)),
            .init(texto: "Banana", cantos: caixa(0, 0, largura: 50)),
            .init(texto: "17,72", cantos: caixa(200, 20, largura: 40)),
            .init(texto: "Leite", cantos: caixa(0, 10, largura: 40)),
        ]
        #expect(LeitorDeCupom.linhas(trechos) == ["Banana 1,49 A", "Leite 2,49 A", "Total 17,72"])
        #expect(LeitorDeCupom.ler(trechos, hoje: hoje).total == Decimal(string: "17.72"))
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
        // Sem as contas do mes, nada de "Ainda da" inflado (BUG-002).
        #expect(resumo.aindaDa == nil)
        let comContas = AtualizadorDoSistema.resumo(mes: "2026-10", gastos: [gasto, gasto], limite: 100, metas: metas, contas: [])
        #expect(comContas.aindaDa?.resta == 40)
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
        // A mesma conta do Painel: limite menos gasto e contas a pagar (D-195).
        let comSaldo = AindaDaParaGastar.calcular(limite: 2500, receitas: nil, gasto: Decimal(string: "1039.39")!,
                                                  contasAPagar: 640, mes: "2026-10")
        #expect(QuantoGasteiIntent.fala(gasto: Decimal(string: "1039.39")!, conta: comSaldo, mes: "2026-10")
            == "Em outubro de 2026 você gastou \(r(Decimal(string: "1039.39")!)). Ainda dá para gastar \(r(Decimal(string: "820.61")!)), contando as contas a pagar.")
        let passou = AindaDaParaGastar.calcular(limite: 200, receitas: nil, gasto: 300, contasAPagar: 0, mes: "2026-10")
        #expect(QuantoGasteiIntent.fala(gasto: 300, conta: passou, mes: "2026-10")
            == "Em outubro de 2026 você gastou \(r(300)). Já passou \(r(100)) do que dava para gastar, contando as contas a pagar.")
        #expect(QuantoGasteiIntent.fala(gasto: 300, conta: nil, mes: "2026-10") == "Em outubro de 2026 você gastou \(r(300)).")
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
        #expect(Rota(url: URL(string: "piggu://gastos?id=abc&mes=2026-09")!) == .verGasto(id: "abc", mes: "2026-09"))
    }

    @Test @MainActor func conversaDoAssistenteGuardaSo30DiasE200Falas() {
        let velha = AssistenteView.Fala(pessoa: true, texto: "velha", em: hoje.addingTimeInterval(-31 * 86_400))
        let recentes = (0..<205).map { AssistenteView.Fala(pessoa: $0 % 2 == 0, texto: "\($0)", em: hoje) }
        let podada = AssistenteView.podar([velha] + recentes, agora: hoje)
        #expect(podada.count == 200)
        #expect(podada.first?.texto == "5")
        #expect(podada.last?.texto == "204")
    }

    @Test @MainActor func assistenteSoSugereAbasETelas() {
        #expect(AssistenteView.rota("relatorios") == .aba(.relatorios))
        #expect(AssistenteView.rota("orcamentos") == .tela(.orcamento))
        #expect(AssistenteView.rota("cpf") == nil)
        #expect(AssistenteView.rota("nao-existe") == nil)
        #expect(AssistenteView.rota(nil) == nil)
    }
}

/// Um resumo sem limite, para o teste acima.
private enum AtalhoDeResumo {
    @MainActor static var semLimite: ResumoDoWidget {
        AtualizadorDoSistema.resumo(mes: "2026-10", gastos: [], limite: 0, metas: [])
    }
}
