import Foundation
import Testing
@testable import Piggu

/// Sem rede: todo pedido falha antes de chegar ao servidor.
private struct TransporteSemRede: Transporte {
    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        throw URLError(.notConnectedToInternet)
    }
}

private func pedido(_ item: String) -> NovoLancamento {
    NovoLancamento(data: "2026-10-27", origem: "Manual", itens: [ItemDeGasto(item: item, categoria: nil, valor: 10)],
                   idDoEnvio: "envio-\(item)")
}

private func itens(_ recebidos: [URLRequest]) -> [String] {
    recebidos.compactMap { pedido in
        pedido.httpBody.flatMap { try? JSONDecoder().decode(NovoLancamento.self, from: $0) }?.itens.first?.item
    }
}

@MainActor
struct FilaDeGastosTests {
    private let base = URL(string: "https://piggu.com.br/api")!

    @Test func enviaNaOrdemEEsvaziaAFila() async {
        let transporte = TransporteRoteirizado([(201, "[]"), (201, "[]")])
        let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), soNaMemoria: true)
        fila.usuario = "u1"
        fila.guardar(pedido("Pão"), agora: Date(timeIntervalSince1970: 1))
        fila.guardar(pedido("Leite"), agora: Date(timeIntervalSince1970: 2))
        #expect(fila.pendentes == 2)

        let enviados = await fila.enviar()

        #expect(enviados == 2)
        #expect(fila.pendentes == 0)
        let recebidos = await transporte.recebidos
        #expect(itens(recebidos) == ["Pão", "Leite"])
        // O mesmo id do envio original: o servidor nao duplica se o primeiro chegou.
        let primeiro = recebidos.first?.httpBody.flatMap { try? JSONDecoder().decode(NovoLancamento.self, from: $0) }
        #expect(primeiro?.idDoEnvio == "envio-Pão")
    }

    @Test func recusadoPeloServidorSaiDaFilaEContinua() async {
        let transporte = TransporteRoteirizado([(422, #"{"erro":"Valor inválido."}"#), (201, "[]")])
        let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), soNaMemoria: true)
        fila.usuario = "u1"
        fila.guardar(pedido("Errado"), agora: Date(timeIntervalSince1970: 1))
        fila.guardar(pedido("Certo"), agora: Date(timeIntervalSince1970: 2))

        let enviados = await fila.enviar()

        #expect(enviados == 1)
        #expect(fila.recusados == 1)
        #expect(fila.pendentes == 0)
    }

    @Test func semRedeOuServidorForaGuardaTudo() async {
        let semRede = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: TransporteSemRede()), soNaMemoria: true)
        semRede.usuario = "u1"
        semRede.guardar(pedido("Pão"))
        #expect(await semRede.enviar() == 0)
        #expect(semRede.pendentes == 1)

        let transporte = TransporteRoteirizado([(201, "[]"), (503, ""), (201, "[]")])
        let fora = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), soNaMemoria: true)
        fora.usuario = "u1"
        for (indice, item) in ["A", "B", "C"].enumerated() {
            fora.guardar(pedido(item), agora: Date(timeIntervalSince1970: Double(indice)))
        }
        #expect(await fora.enviar() == 1)
        // Parou no B: o B e o C esperam a proxima vez, na mesma ordem.
        #expect(fora.pendentes == 2)
        #expect(fora.recusados == 0)
    }

    @Test func soQuemGuardouEnvia() async {
        let transporte = TransporteRoteirizado([(201, "[]")])
        let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), soNaMemoria: true)
        fila.usuario = "u1"
        fila.guardar(pedido("Pão"))

        fila.usuario = "u2"
        #expect(fila.pendentes == 0)
        #expect(await fila.enviar() == 0)

        fila.usuario = "u1"
        #expect(fila.pendentes == 1)
        #expect(await fila.enviar() == 1)
    }
}
