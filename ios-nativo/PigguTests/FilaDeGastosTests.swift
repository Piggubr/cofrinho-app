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

    @Test func recusadoPeloServidorSaiDaFilaEContinua() async throws {
        let transporte = TransporteRoteirizado([(422, #"{"erro":"Valor inválido."}"#), (201, "[]")])
        let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), soNaMemoria: true)
        fila.usuario = "u1"
        fila.guardar(pedido("Errado"), agora: Date(timeIntervalSince1970: 1))
        fila.guardar(pedido("Certo"), agora: Date(timeIntervalSince1970: 2))

        let enviados = await fila.enviar()

        #expect(enviados == 1)
        #expect(fila.pendentes == 0)
        // Fica guardado com o que era e o motivo, ate a pessoa dispensar (BUG-010).
        let recusado = try #require(fila.recusados.first)
        #expect(fila.recusados.count == 1)
        #expect(recusado.pedido.itens.first?.item == "Errado")
        #expect(recusado.valor == 10)
        #expect(recusado.pedido.data == "2026-10-27")
        #expect(recusado.motivo == "Valor inválido.")
        fila.dispensar(recusado)
        #expect(fila.recusados.isEmpty)
    }

    @Test func recusadoSobreviveAFecharOAppENaoEReenviado() async throws {
        let nome = "FilaDeTeste-\(UUID().uuidString)"
        let transporte = TransporteRoteirizado([(400, #"{"erro":"Digite um valor a partir de R$ 0,01."}"#)])
        do {
            let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), nome: nome)
            fila.usuario = "u1"
            fila.guardar(pedido("Zero"))
            #expect(await fila.enviar() == 0)
        }
        // "Abrir o app de novo": outra fila no mesmo arquivo.
        let reaberta = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), nome: nome)
        reaberta.usuario = "u1"
        let recusado = try #require(reaberta.recusados.first)
        #expect(recusado.motivo == "Digite um valor a partir de R$ 0,01.")
        #expect(reaberta.pendentes == 0)
        #expect(await reaberta.enviar() == 0)
        #expect(await transporte.recebidos.count == 1)
        reaberta.dispensar(recusado)
        #expect(reaberta.recusados.isEmpty)
    }

    @Test func naFilaEntraNoTotalDoMes() {
        let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: TransporteSemRede()), soNaMemoria: true)
        fila.usuario = "u1"
        fila.guardar(pedido("Pão"))
        var parcelado = pedido("TV")
        parcelado.parcelas = 4
        fila.guardar(parcelado)
        fila.guardar(NovoLancamento(data: "2026-09-01", itens: [ItemDeGasto(item: "Outro mês", categoria: nil, valor: 99)]))
        #expect(fila.valorNaFila(mes: "2026-10") == Decimal(string: "12.5"))
        #expect(fila.valorNaFila(mes: "2026-09") == 99)
    }

    @Test func cartaoApagadoNoSiteSaiDaFilaComoRecusado() async {
        let transporte = TransporteRoteirizado([
            (404, #"{"erro":"Conta ou cartao nao encontrado.","codigo":"NotFoundException"}"#), (201, "[]"),
        ])
        let fila = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: transporte), soNaMemoria: true)
        fila.usuario = "u1"
        fila.guardar(pedido("NoCartaoApagado"), agora: Date(timeIntervalSince1970: 1))
        fila.guardar(pedido("Certo"), agora: Date(timeIntervalSince1970: 2))

        #expect(await fila.enviar() == 1)
        #expect(fila.recusados.count == 1)
        #expect(fila.pendentes == 0)

        // 404 sem o codigo (proxy, rota que nao existe) segue parando a fila.
        let proxy = FilaDeGastos(cliente: ClienteDaAPI(urlBase: base, transporte: TransporteRoteirizado([(404, "")])),
                                 soNaMemoria: true)
        proxy.usuario = "u1"
        proxy.guardar(pedido("Pão"))
        #expect(await proxy.enviar() == 0)
        #expect(proxy.pendentes == 1)
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
        #expect(fora.recusados.isEmpty)
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
