import Foundation
import SwiftData

/// Uma resposta da API guardada no aparelho, em JSON, do jeito que veio.
@Model
final class RespostaGuardada {
    /// "resumo/2026-10", "gastos/2026-10"
    @Attribute(.unique) var chave: String
    var json: Data
    var guardadaEm: Date

    init(chave: String, json: Data, guardadaEm: Date) {
        self.chave = chave
        self.json = json
        self.guardadaEm = guardadaEm
    }
}

/// O resumo e os gastos de cada mes aberto, para o app abrir sem internet: a tela mostra o
/// guardado na hora e troca pelo da API quando ele chega. Apagado ao sair da conta.
@MainActor
final class Cache {
    private let contexto: ModelContext
    let soNaMemoria: Bool

    /// - Parameter soNaMemoria: demonstracao e testes, que nao podem misturar dados com os de verdade.
    init(soNaMemoria: Bool = false) {
        self.soNaMemoria = soNaMemoria
        let disco = ModelConfiguration(isStoredInMemoryOnly: soNaMemoria)
        let container = (try? ModelContainer(for: RespostaGuardada.self, configurations: disco))
            // Banco corrompido ou migracao que falhou: segue sem cache em vez de nao abrir.
            ?? (try! ModelContainer(for: RespostaGuardada.self,
                                    configurations: ModelConfiguration(isStoredInMemoryOnly: true)))
        contexto = ModelContext(container)
    }

    func ler<Valor: Decodable>(_ chave: String, como _: Valor.Type = Valor.self) -> (valor: Valor, em: Date)? {
        guard let guardada = buscar(chave),
              let valor = try? JSONDecoder().decode(Valor.self, from: guardada.json) else {
            return nil
        }
        return (valor, guardada.guardadaEm)
    }

    func guardar<Valor: Encodable>(_ valor: Valor, em chave: String, agora: Date = Date()) {
        guard let json = try? JSONEncoder().encode(valor) else { return }
        if let guardada = buscar(chave) {
            guardada.json = json
            guardada.guardadaEm = agora
        } else {
            contexto.insert(RespostaGuardada(chave: chave, json: json, guardadaEm: agora))
        }
        try? contexto.save()
    }

    func apagarTudo() {
        try? contexto.delete(model: RespostaGuardada.self)
        try? contexto.save()
    }

    /// Mostra o guardado (se houver) e busca na API; deu certo, guarda e mostra o novo. Sem
    /// rede, fica o guardado com o aviso da hora, ou o erro se nao havia nada.
    func carregar<Valor: Codable & Sendable>(
        _ chave: String,
        mostrar: (Carga<Valor>) -> Void,
        buscar: () async throws -> Valor
    ) async {
        let guardado = ler(chave, como: Valor.self)
        mostrar(guardado.map { .pronto($0.valor) } ?? .carregando)
        do {
            let valor = try await buscar()
            guardar(valor, em: chave)
            mostrar(.pronto(valor))
        } catch is CancellationError {
        } catch {
            let erro = error as? ErroDaAPI ?? .respostaInvalida
            if let guardado, erro != .sessaoEncerrada {
                mostrar(.pronto(guardado.valor, guardadoEm: guardado.em))
            } else {
                mostrar(.falhou(erro))
            }
        }
    }

    private func buscar(_ chave: String) -> RespostaGuardada? {
        var pedido = FetchDescriptor<RespostaGuardada>(predicate: #Predicate { $0.chave == chave })
        pedido.fetchLimit = 1
        return try? contexto.fetch(pedido).first
    }
}
