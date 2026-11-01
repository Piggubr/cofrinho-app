import CoreSpotlight
import Foundation
import WidgetKit

/// O que o app entrega ao iPhone fora dele: o resumo dos widgets e da Siri (no App Group) e os
/// gastos do mes na busca do iPhone (Core Spotlight). Roda ao abrir o app e depois de lancar um
/// gasto; ao sair da conta, tudo e apagado.
@MainActor
enum AtualizadorDoSistema {
    static let dominioDosGastos = "br.com.piggu.app.gastos"

    static func atualizar(_ sessao: Sessao) async {
        guard let dono = sessao.usuario?.id else { return }
        // Com a trava do app ligada, os numeros e os gastos nao ficam fora dele (widget da tela
        // bloqueada, busca do iPhone) (D-162).
        if UserDefaults.standard.bool(forKey: TravaDoApp.chave) {
            apagar()
            return
        }
        let cliente = sessao.cliente
        let mes = Formatos.mes(Date())
        async let gastos = try? cliente.listarGastos(mes: mes)
        async let limites = try? cliente.listarMetas()
        async let metas = try? cliente.listarMetasDoMes()
        guard let gastos = await gastos else { return }
        let lista = await metas ?? []
        let limite = (await limites)?[mes]
        // A resposta pode chegar depois de sair da conta: so grava se a mesma pessoa segue la (D-162).
        guard !Task.isCancelled, sessao.usuario?.id == dono else { return }
        resumo(mes: mes, gastos: gastos, limite: limite, metas: lista).gravar()
        WidgetCenter.shared.reloadAllTimelines()
        await indexar(gastos)
        if sessao.usuario?.id != dono {
            apagar()
        }
    }

    static func resumo(mes: String, gastos: [Gasto], limite: Decimal?, metas: [MetaDoMes]) -> ResumoDoWidget {
        let meta = metas.first { !$0.concluida } ?? metas.last
        return ResumoDoWidget(
            mes: mes, gasto: gastos.reduce(0) { $0 + $1.valor }, limite: (limite ?? 0) > 0 ? limite : nil,
            moeda: Formatos.codigoDaMoeda,
            meta: meta.map { ResumoDoWidget.Meta(titulo: $0.titulo, atual: $0.atual, alvo: $0.alvo) },
            metasConcluidas: metas.filter(\.concluida).count, metasTotal: metas.count,
            tema: UserDefaults.standard.string(forKey: Tema.chave) ?? Tema.chiclete.rawValue, atualizadoEm: Date())
    }

    /// Os gastos do mes atual na busca do iPhone; tocar abre o gasto no app.
    static func indexar(_ gastos: [Gasto]) async {
        let indice = CSSearchableIndex.default()
        try? await indice.deleteSearchableItems(withDomainIdentifiers: [dominioDosGastos])
        let itens = gastos.map { gasto in
            let atributos = CSSearchableItemAttributeSet(contentType: .content)
            atributos.title = gasto.item
            atributos.contentDescription = [Formatos.moeda(gasto.valor), gasto.categoria, Formatos.dataCurta(gasto.data),
                                            gasto.estabelecimento ?? ""].filter { !$0.isEmpty }.joined(separator: " · ")
            atributos.keywords = [gasto.categoria, gasto.estabelecimento ?? "", "gasto", "Piggu"].filter { !$0.isEmpty }
            return CSSearchableItem(uniqueIdentifier: gasto.id, domainIdentifier: dominioDosGastos, attributeSet: atributos)
        }
        try? await indice.indexSearchableItems(itens)
    }

    /// Ao sair da conta: os numeros e os gastos sao da pessoa.
    static func apagar() {
        ResumoDoWidget.apagar()
        WidgetCenter.shared.reloadAllTimelines()
        CSSearchableIndex.default().deleteSearchableItems(withDomainIdentifiers: [dominioDosGastos])
    }
}
