import Foundation
import Testing
@testable import Piggu

@MainActor
struct PainelOrganizadoTests {
    private func ajustesVazios() -> UserDefaults {
        let nome = "PainelOrganizadoTests.\(UUID().uuidString)"
        let ajustes = UserDefaults(suiteName: nome)!
        ajustes.removePersistentDomain(forName: nome)
        return ajustes
    }

    @Test func semNadaSalvoEOPadraoDoSite() {
        let padrao = PainelOrganizado.ordemValida(nil)
        #expect(padrao.map(\.id) == [.resumo, .avisos, .mes, .cofrinho, .objetivos, .metas, .categorias, .notas, .girlsMath])
        #expect(padrao.filter(\.visivel).map(\.id) == [.resumo, .avisos, .mes, .cofrinho, .objetivos])
    }

    @Test func juntaOSalvoPeloSiteComOsBlocosDeHoje() {
        // Bloco que nao existe mais sai, repetido conta uma vez, sem "visivel" aparece,
        // e o que faltar entra no fim como no padrao.
        let salvo = Data("""
        [{"id":"girls-math","visivel":true},{"id":"velho"},{"id":"mes","visivel":false},
         {"id":"girls-math","visivel":false},{"id":"cofrinho"}]
        """.utf8)
        let blocos = PainelOrganizado.ordemValida(salvo)
        #expect(blocos.prefix(3) == [
            BlocoDoPainel(id: .girlsMath, visivel: true),
            BlocoDoPainel(id: .mes, visivel: false),
            BlocoDoPainel(id: .cofrinho, visivel: true),
        ])
        #expect(blocos.dropFirst(3).map(\.id) == [.resumo, .avisos, .objetivos, .metas, .categorias, .notas])
        #expect(PainelOrganizado.ordemValida(Data("não é json".utf8)) == PainelOrganizado.padrao)
    }

    @Test func moverEsconderEGuardarNaChaveDaPessoa() throws {
        let ajustes = ajustesVazios()
        let painel = PainelOrganizado(usuario: "u1", ajustes: ajustes)

        painel.mover([.cofrinho], antesDe: .resumo)
        painel.mover([.avisos], antesDe: nil)
        painel.alternar(.mes)
        painel.mover(de: IndexSet(integer: 0), para: 2)

        let esperado: [IdDoBloco] = [.resumo, .cofrinho, .mes, .objetivos, .metas, .categorias, .notas, .girlsMath, .avisos]
        #expect(painel.blocos.map(\.id) == esperado)
        #expect(!painel.visiveis.contains { $0.id == .mes })

        // O mesmo JSON do site, lido de novo por outra instancia.
        let salvo = try #require(ajustes.data(forKey: "piggu_painel_u1"))
        #expect(PainelOrganizado.ordemValida(salvo) == painel.blocos)
        #expect(PainelOrganizado(usuario: "u2", ajustes: ajustes).blocos == PainelOrganizado.padrao)

        painel.restaurar()
        #expect(PainelOrganizado(usuario: "u1", ajustes: ajustes).blocos == PainelOrganizado.padrao)
    }
}
