import Foundation
import Observation
import SwiftData

/// Um gasto lancado sem sinal, com o pedido do jeito que ia para a API.
@Model
final class GastoNaFila {
    /// De quem e: so essa pessoa envia, mesmo que outra entre no mesmo aparelho.
    var usuario: String
    var pedido: Data
    var guardadoEm: Date

    init(usuario: String, pedido: Data, guardadoEm: Date) {
        self.usuario = usuario
        self.pedido = pedido
        self.guardadoEm = guardadoEm
    }
}

/// Gastos lancados sem sinal (na fila do mercado, no metro), como a FilaDeGastos do site.
///
/// Ficam no SwiftData e vao para o servidor, na ordem, quando a rede volta, ao abrir o app e
/// no "Enviar agora". Um envio recusado por dado invalido (400 ou 422) sai da fila e conta em
/// `recusados`; qualquer outra falha (sem rede, servidor fora, sessao encerrada) para ali e
/// guarda o resto. Nao e apagada ao sair da conta: e gasto que a pessoa ainda nao tem no
/// servidor, e vai quando ela entrar de novo.
@MainActor
@Observable
final class FilaDeGastos {
    /// Os da pessoa logada esperando conexao.
    private(set) var pendentes = 0
    /// Recusados pelo servidor no ultimo envio, para a tela avisar.
    var recusados = 0

    var usuario: String? {
        didSet { contar() }
    }

    @ObservationIgnored private let cliente: ClienteDaAPI
    @ObservationIgnored private let contexto: ModelContext
    @ObservationIgnored private var enviando = false

    init(cliente: ClienteDaAPI, soNaMemoria: Bool = false) {
        self.cliente = cliente
        let disco = ModelConfiguration("FilaDeGastos", isStoredInMemoryOnly: soNaMemoria)
        let container = (try? ModelContainer(for: GastoNaFila.self, configurations: disco))
            // Banco que nao abre: a fila vale ate fechar o app, em vez de o app nao abrir.
            ?? (try! ModelContainer(for: GastoNaFila.self,
                                    configurations: ModelConfiguration(isStoredInMemoryOnly: true)))
        contexto = ModelContext(container)
    }

    func guardar(_ pedido: NovoLancamento, agora: Date = Date()) {
        guard let usuario, let json = try? JSONEncoder().encode(pedido) else { return }
        contexto.insert(GastoNaFila(usuario: usuario, pedido: json, guardadoEm: agora))
        try? contexto.save()
        contar()
    }

    /// Manda os da pessoa logada, na ordem em que foram guardados.
    /// - Returns: quantos o servidor aceitou.
    @discardableResult
    func enviar() async -> Int {
        guard !enviando, pendentes > 0 else { return 0 }
        enviando = true
        defer { enviando = false }
        var enviados = 0
        var recusados = 0
        for guardado in daPessoa() {
            guard let pedido = try? JSONDecoder().decode(NovoLancamento.self, from: guardado.pedido) else {
                contexto.delete(guardado)
                continue
            }
            do {
                _ = try await cliente.lancarGastos(pedido)
                enviados += 1
            } catch ErroDaAPI.servidor(status: 400, _, _), ErroDaAPI.servidor(status: 422, _, _) {
                recusados += 1
            } catch {
                break
            }
            contexto.delete(guardado)
            try? contexto.save()
        }
        try? contexto.save()
        self.recusados = recusados
        contar()
        return enviados
    }

    private func daPessoa() -> [GastoNaFila] {
        guard let usuario else { return [] }
        let pedido = FetchDescriptor<GastoNaFila>(
            predicate: #Predicate { $0.usuario == usuario }, sortBy: [SortDescriptor(\.guardadoEm)])
        return (try? contexto.fetch(pedido)) ?? []
    }

    private func contar() {
        pendentes = daPessoa().count
    }
}
