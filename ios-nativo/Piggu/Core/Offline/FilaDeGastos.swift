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
    /// Recusado pelo servidor (BUG-010): fica guardado, fora do envio, ate a pessoa dispensar.
    var recusadoEm: Date?
    var motivo: String?

    init(usuario: String, pedido: Data, guardadoEm: Date) {
        self.usuario = usuario
        self.pedido = pedido
        self.guardadoEm = guardadoEm
    }
}

/// Gastos lancados sem sinal (na fila do mercado, no metro), como a FilaDeGastos do site.
///
/// Ficam no SwiftData e vao para o servidor, na ordem, quando a rede volta, ao abrir o app e
/// no "Enviar agora". Um envio recusado por dado invalido (400 ou 422) para de ser enviado e fica
/// em `recusados`, com o motivo, ate a pessoa dispensar (BUG-010); qualquer outra falha (sem rede, servidor fora, sessao encerrada) para ali e
/// guarda o resto. Nao e apagada ao sair da conta: e gasto que a pessoa ainda nao tem no
/// servidor, e vai quando ela entrar de novo.
@MainActor
@Observable
final class FilaDeGastos {
    /// Um gasto que o servidor recusou, com o que era e por que.
    struct Recusado: Identifiable, Equatable {
        let id: PersistentIdentifier
        let pedido: NovoLancamento
        let motivo: String
        var valor: Decimal { pedido.itens.reduce(0) { $0 + $1.valor } }
    }

    /// Os da pessoa logada esperando conexao.
    var pendentes: Int { naFila.count }
    /// Os pedidos esperando conexao: entram no total do mes e no "Ainda da", como pendentes.
    private(set) var naFila: [NovoLancamento] = []
    /// Recusados pelo servidor, guardados ate a pessoa dispensar (sobrevivem a fechar o app).
    private(set) var recusados: [Recusado] = []

    var usuario: String? {
        didSet { contar() }
    }

    @ObservationIgnored private let cliente: ClienteDaAPI
    @ObservationIgnored private let contexto: ModelContext
    @ObservationIgnored private var enviando = false

    /// `nome`: o arquivo no disco (os testes usam outro para provar que o recusado sobrevive).
    init(cliente: ClienteDaAPI, soNaMemoria: Bool = false, nome: String = "FilaDeGastos") {
        self.cliente = cliente
        let disco = ModelConfiguration(nome, isStoredInMemoryOnly: soNaMemoria)
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
        for guardado in daPessoa() where guardado.recusadoEm == nil {
            guard let pedido = try? JSONDecoder().decode(NovoLancamento.self, from: guardado.pedido) else {
                contexto.delete(guardado)
                continue
            }
            do {
                _ = try await cliente.lancarGastos(pedido)
                enviados += 1
                contexto.delete(guardado)
            } catch let erro as ErroDaAPI where Self.recusado(erro) {
                guardado.recusadoEm = Date()
                guardado.motivo = erro.localizedDescription
            } catch {
                break
            }
            try? contexto.save()
        }
        try? contexto.save()
        contar()
        return enviados
    }

    /// 400 e 422, e o 404 do Piggu (o cartao ou a conta foi apagado, D-147): nunca vai passar.
    private static func recusado(_ erro: ErroDaAPI) -> Bool {
        switch erro {
        case .servidor(status: 400, _, _), .servidor(status: 422, _, _),
             .servidor(status: 404, _, codigo: "NotFoundException"): true
        default: false
        }
    }

    /// A pessoa viu o recusado e tirou o aviso.
    func dispensar(_ recusado: Recusado) {
        if let guardado = contexto.model(for: recusado.id) as? GastoNaFila {
            contexto.delete(guardado)
            try? contexto.save()
        }
        contar()
    }

    private func daPessoa() -> [GastoNaFila] {
        guard let usuario else { return [] }
        let pedido = FetchDescriptor<GastoNaFila>(
            predicate: #Predicate { $0.usuario == usuario }, sortBy: [SortDescriptor(\.guardadoEm)])
        return (try? contexto.fetch(pedido)) ?? []
    }

    private func contar() {
        let todos = daPessoa()
        let decoder = JSONDecoder()
        naFila = todos.filter { $0.recusadoEm == nil }
            .compactMap { try? decoder.decode(NovoLancamento.self, from: $0.pedido) }
        recusados = todos.compactMap { guardado in
            guard guardado.recusadoEm != nil,
                  let pedido = try? decoder.decode(NovoLancamento.self, from: guardado.pedido) else { return nil }
            return Recusado(id: guardado.persistentModelID, pedido: pedido,
                            motivo: guardado.motivo ?? String(localized: "O servidor não aceitou este gasto."))
        }
    }

    /// Soma dos gastos na fila daquele mes (aaaa-mm), para o total e o "Ainda da".
    /// shortcut: o parcelado conta so a 1a parcela no mes da data, sem a regra da fatura do cartao;
    /// trocar pela conta do backend se a fila passar a guardar compras grandes no cartao.
    func valorNaFila(mes: String) -> Decimal {
        naFila.filter { $0.data.hasPrefix(mes) }.reduce(0) { soma, pedido in
            soma + pedido.itens.reduce(0) { $0 + $1.valor } / Decimal(max(pedido.parcelas ?? 1, 1))
        }
    }
}
