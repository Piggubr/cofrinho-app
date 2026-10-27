import Foundation
import Observation
import SwiftUI

/// Cartao do Painel. Os ids sao os do site (core/ui/painel.ts), para a ordem salva valer igual.
enum IdDoBloco: String, Codable, CaseIterable, Sendable {
    case resumo, avisos, mes, cofrinho, objetivos, metas, categorias, notas
    case girlsMath = "girls-math"

    /// Nome na tela de organizar, na ordem padrao (BLOCOS_DO_PAINEL).
    var nome: LocalizedStringKey {
        switch self {
        case .resumo: "Resumo do mês"
        case .avisos: "Para ficar de olho"
        case .mes: "Gasto e limite do mês"
        case .cofrinho: "Cofrinho"
        case .objetivos: "Cofrinhos por objetivo"
        case .metas: "Minhas metas"
        case .categorias: "Por categoria"
        case .notas: "Suas notas"
        case .girlsMath: "Girls Math"
        }
    }

    /// De inicio aparece so o que vive apenas no Painel; o resto a pessoa liga.
    var padrao: Bool {
        switch self {
        case .resumo, .avisos, .mes, .cofrinho, .objetivos: true
        case .metas, .categorias, .notas, .girlsMath: false
        }
    }
}

struct BlocoDoPainel: Codable, Identifiable, Equatable, Sendable {
    let id: IdDoBloco
    var visivel: Bool
}

/// Ordem e visibilidade dos cartoes do Painel (PainelOrganizado do site).
///
/// Fica neste aparelho e por pessoa, na mesma chave e no mesmo JSON do site
/// (`piggu_painel_<id>`): o celular pode ter uma ordem e o computador outra.
@MainActor
@Observable
final class PainelOrganizado {
    static let padrao = IdDoBloco.allCases.map { BlocoDoPainel(id: $0, visivel: $0.padrao) }

    private(set) var blocos: [BlocoDoPainel]
    @ObservationIgnored private let chave: String
    @ObservationIgnored private let ajustes: UserDefaults

    init(usuario: String, ajustes: UserDefaults = .standard) {
        chave = "piggu_painel_\(usuario)"
        self.ajustes = ajustes
        blocos = Self.ordemValida(ajustes.data(forKey: chave))
    }

    var visiveis: [BlocoDoPainel] { blocos.filter(\.visivel) }

    /// Junta o que estava salvo com a lista atual: bloco que deixou de existir sai, bloco
    /// novo entra no fim, aparecendo ou nao como no padrao.
    static func ordemValida(_ salvo: Data?) -> [BlocoDoPainel] {
        guard let salvo,
              let itens = try? JSONSerialization.jsonObject(with: salvo) as? [[String: Any]] else {
            return padrao
        }
        var vistos = Set<IdDoBloco>()
        var blocos: [BlocoDoPainel] = []
        for item in itens {
            guard let id = (item["id"] as? String).flatMap(IdDoBloco.init), !vistos.contains(id) else { continue }
            vistos.insert(id)
            blocos.append(BlocoDoPainel(id: id, visivel: item["visivel"] as? Bool != false))
        }
        return blocos + padrao.filter { !vistos.contains($0.id) }
    }

    /// O soltar do arrastar na tela de organizar (List.onMove).
    func mover(de origem: IndexSet, para destino: Int) {
        var novos = blocos
        novos.move(fromOffsets: origem, toOffset: destino)
        gravar(novos)
    }

    /// O soltar do arrastar no proprio Painel: leva `ids` para antes de `antesDe` (nil, o fim).
    func mover(_ ids: [IdDoBloco], antesDe: IdDoBloco?) {
        var novos = blocos.filter { !ids.contains($0.id) }
        let movidos = blocos.filter { ids.contains($0.id) }
        let indice = antesDe.flatMap { alvo in novos.firstIndex { $0.id == alvo } } ?? novos.endIndex
        novos.insert(contentsOf: movidos, at: indice)
        gravar(novos)
    }

    func alternar(_ id: IdDoBloco) {
        gravar(blocos.map { $0.id == id ? BlocoDoPainel(id: id, visivel: !$0.visivel) : $0 })
    }

    func restaurar() {
        gravar(Self.padrao)
    }

    private func gravar(_ novos: [BlocoDoPainel]) {
        blocos = novos
        ajustes.set(try? JSONEncoder().encode(novos), forKey: chave)
    }
}
