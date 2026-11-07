import Foundation
import Testing
@testable import Piggu

/// Contrato de POST /expenses/import/card-statement e a conferencia (conferir-fatura.ts).
@MainActor
struct ConferirFaturaTests {
    private static func linha(_ id: String, tipo: String = "COMPRA", situacao: String, parcela: String = "null,\"parcelaTotal\":null",
                              gasto: String = "null") -> String {
        """
        {"data":"2026-10-28","descricao":"LOJA \(id)","valor":99.90,"tipo":"\(tipo)","parcelaNumero":\(parcela),
         "situacao":"\(situacao)","gastoId":null,"gastoDescricao":\(gasto),"gastoData":null,
         "categoria":"Mercado","idExterno":"fatura:\(id)"}
        """
    }

    private static let premium = """
    {"banco":"nubank","vencimento":"2026-11-10","total":612.40,"somaLida":612.40,"origem":"PROPRIA","aviso":null,
     "contagem":{"compras":4,"tarifas":1,"ignoradas":0,"jaLancadas":1,"confira":1,"faltam":3},
     "linhas":[\(linha("a", situacao: "FALTA")),\(linha("b", situacao: "FALTA", parcela: "2,\"parcelaTotal\":3")),
               \(linha("c", situacao: "CONFIRA", gasto: "\"Posto\"")),\(linha("d", tipo: "TARIFA", situacao: "FALTA")),
               \(linha("e", situacao: "JA_LANCADA"))]}
    """

    private func decodificar(_ json: String) throws -> ConferenciaDaFatura {
        try JSONDecoder().decode(ConferenciaDaFatura.self, from: Data(json.utf8))
    }

    @Test func decodificaARespostaGratuitaSemLinhas() throws {
        let c = try decodificar("""
        {"banco":null,"vencimento":null,"total":null,"somaLida":10,"origem":"PROPRIA","aviso":"Aviso",
         "contagem":{"compras":3,"tarifas":0,"ignoradas":0,"jaLancadas":2,"confira":0,"faltam":1},"linhas":null}
        """)
        #expect(c.linhas == nil)
        #expect(c.contagem.jaLancadas == 2 && c.contagem.faltam == 1)
        #expect(c.aviso == "Aviso")
    }

    @Test func decodificaARespostaPremium() throws {
        let c = try decodificar(Self.premium)
        let linhas = try #require(c.linhas)
        #expect(linhas.count == 5)
        #expect(linhas[1].parcelaNumero == 2 && linhas[1].parcelaTotal == 3)
        #expect(linhas[2].situacao == .confira && linhas[2].gastoDescricao == "Posto")
        #expect(linhas[3].tipo == .tarifa)
    }

    @Test func soAsComprasQueFaltamNascemMarcadas() throws {
        let linhas = try #require(decodificar(Self.premium).linhas)
        #expect(ConferirFaturaView.marcadasIniciais(linhas) == ["fatura:a", "fatura:b"])
    }

    @Test func agrupaFaltamConfiraTarifasEJaLancadas() throws {
        let linhas = try #require(decodificar(Self.premium).linhas)
        let g = ConferirFaturaView.grupos(linhas)
        #expect(g.faltam.map(\.idExterno) == ["fatura:a", "fatura:b"])
        #expect(g.confira.map(\.idExterno) == ["fatura:c"])
        #expect(g.tarifas.map(\.idExterno) == ["fatura:d"])
        #expect(g.jaLancadas.map(\.idExterno) == ["fatura:e"])
    }

    @Test func pedidoDeImportacaoLevaSoAsMarcadasComParcela() throws {
        var linhas = try #require(decodificar(Self.premium).linhas)
        linhas[1].categoria = "Roupas"
        let pedido = ConferirFaturaView.pedidoDeImportacao(linhas, marcadas: ["fatura:a", "fatura:b"], contaId: "c1")
        let json = try #require(JSONSerialization.jsonObject(with: JSONEncoder().encode(pedido)) as? [String: Any])
        #expect(json["contaId"] as? String == "c1")
        let enviadas = try #require(json["linhas"] as? [[String: Any]])
        #expect(enviadas.map { $0["idExterno"] as? String } == ["fatura:a", "fatura:b"])
        #expect(enviadas[0]["parcelaNumero"] == nil && enviadas[0]["parcelaTotal"] == nil)
        #expect(enviadas[1]["parcelaNumero"] as? Int == 2 && enviadas[1]["parcelaTotal"] as? Int == 3)
        #expect(enviadas[1]["categoria"] as? String == "Roupas")
    }

    @Test func pedidoSemCartaoNaoMandaContaId() throws {
        let pedido = PedidoDeConferencia(pdfBase64: "AAA", senha: nil, contaId: nil)
        let json = try #require(JSONSerialization.jsonObject(with: JSONEncoder().encode(pedido)) as? [String: Any])
        #expect(json["pdfBase64"] as? String == "AAA")
        #expect(json["contaId"] == nil && json["senha"] == nil && json["autorizoIa"] == nil)
    }

    @Test func limiteDeCincoMegabytes() {
        #expect(ConferirFaturaView.tamanhoMaximo == 5 * 1024 * 1024)
    }

    @Test func aDemonstracaoRespondeARota() async throws {
        let cliente = ClienteDaAPI(urlBase: URL(string: "https://piggu.com.br/api")!,
                                   transporte: TransporteDeDemonstracao(logado: true), cofre: nil)
        let c = try await cliente.conferirFatura(PedidoDeConferencia(pdfBase64: "AAA", senha: nil, contaId: nil))
        let g = ConferirFaturaView.grupos(try #require(c.linhas))
        #expect(!g.faltam.isEmpty && !g.confira.isEmpty && !g.tarifas.isEmpty && !g.jaLancadas.isEmpty)
    }

    @Test func linhaComTipoOuSituacaoDesconhecidaSaiSemDerrubarAResposta() throws {
        let c = try decodificar("""
        {"banco":null,"vencimento":null,"total":null,"somaLida":10,"origem":"PROPRIA","aviso":null,
         "contagem":{"compras":3,"tarifas":0,"ignoradas":0,"jaLancadas":0,"confira":0,"faltam":3},
         "linhas":[\(Self.linha("a", situacao: "FALTA")),\(Self.linha("b", situacao: "NOVA")),
                   \(Self.linha("c", tipo: "ESTORNO", situacao: "FALTA"))]}
        """)
        #expect(c.linhas?.map(\.idExterno) == ["fatura:a"])
    }

    @Test func quitaDepoisDeImportarOuSemNadaMarcado() {
        #expect(ConferirFaturaView.ofereceQuitar(cartao: true, mes: "2026-11", vencimento: "2026-11-10",
                                                 importou: true, leu: false, marcadas: 0))
        #expect(ConferirFaturaView.ofereceQuitar(cartao: true, mes: "2026-11", vencimento: nil,
                                                 importou: false, leu: true, marcadas: 0))
        #expect(!ConferirFaturaView.ofereceQuitar(cartao: true, mes: "2026-11", vencimento: "2026-11-10",
                                                  importou: false, leu: true, marcadas: 2))
        #expect(!ConferirFaturaView.ofereceQuitar(cartao: false, mes: "2026-11", vencimento: nil,
                                                  importou: true, leu: true, marcadas: 0))
    }

    @Test func faturaDeOutroMesNaoQuitaEAvisa() {
        #expect(!ConferirFaturaView.ofereceQuitar(cartao: true, mes: "2026-11", vencimento: "2026-12-10",
                                                  importou: true, leu: true, marcadas: 0))
        #expect(ConferirFaturaView.venceEm("2026-12-10", mes: "2026-11") == "12/2026")
        #expect(ConferirFaturaView.venceEm("2026-11-10", mes: "2026-11") == nil)
        #expect(ConferirFaturaView.venceEm(nil, mes: "2026-11") == nil)
    }

    @Test func aDemonstracaoRetiraAAutorizacaoDaFatura() async throws {
        let cliente = ClienteDaAPI(urlBase: URL(string: "https://piggu.com.br/api")!,
                                   transporte: TransporteDeDemonstracao(logado: true), cofre: nil)
        try await cliente.retirarAutorizacaoDaLeituraDeFatura()
    }
}
