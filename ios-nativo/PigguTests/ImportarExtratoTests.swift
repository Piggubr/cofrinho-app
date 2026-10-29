import Foundation
import Testing
import UniformTypeIdentifiers
@testable import Piggu

/// Leitura do arquivo e o contrato de /expenses/import (importar-extrato.ts e ExpenseController).
struct ImportarExtratoTests {
    /// O .ofx e o .qfx de qualquer banco caem no tipo que o seletor aceita.
    @Test func ofxEQfxSaoAceitosNoSeletor() {
        for ext in ["ofx", "qfx", "OFX"] {
            let tipo = UTType(filenameExtension: ext.lowercased())
            #expect(tipo.map { t in ImportarExtratoView.tipos.contains { t.conforms(to: $0) } } == true, "\(ext)")
        }
    }

    @Test func leUtf8ETiraOBom() {
        let dados = Data([0xEF, 0xBB, 0xBF]) + Data("data;descrição;valor".utf8)
        #expect(ImportarExtratoView.texto(de: dados) == "data;descrição;valor")
    }

    @Test func leLatin1QuandoNaoEUtf8() {
        // "PADARIA SÃO JOÃO" em ISO-8859-1, como o OFX de muitos bancos.
        let dados = "PADARIA SÃO JOÃO".data(using: .isoLatin1)!
        #expect(ImportarExtratoView.texto(de: dados) == "PADARIA SÃO JOÃO")
    }

    @Test func decodificaAPreviaEMontaOPedido() throws {
        let json = """
        [{"data":"2026-10-28","descricao":"PIX PADARIA","valor":18.50,"idExterno":"ofx:123",
          "categoria":"Alimentação","jaImportada":false}]
        """
        var linhas = try JSONDecoder().decode([LinhaDoExtrato].self, from: Data(json.utf8))
        linhas[0].categoria = "Mercado"

        let semConta = try JSONSerialization.jsonObject(
            with: JSONEncoder().encode(PedidoDeImportacao(linhas: linhas, contaId: nil))) as! [String: Any]
        #expect(semConta["contaId"] == nil)
        let linha = try #require((semConta["linhas"] as? [[String: Any]])?.first)
        #expect(linha["idExterno"] as? String == "ofx:123")
        #expect(linha["categoria"] as? String == "Mercado")
        #expect((linha["valor"] as? NSNumber)?.decimalValue == Decimal(string: "18.5"))

        let comConta = try JSONSerialization.jsonObject(
            with: JSONEncoder().encode(PedidoDeImportacao(linhas: linhas, contaId: "c1"))) as! [String: Any]
        #expect(comConta["contaId"] as? String == "c1")
    }
}
