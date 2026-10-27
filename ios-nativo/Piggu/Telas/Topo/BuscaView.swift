import SwiftUI

/// Busca global (a lupa do site, busca-global.ts): telas, gastos, lugares, filmes e compras.
/// Gastos sao buscados no servidor, em todos os meses; o resto, que e lista curta da familia, e
/// filtrado aqui. Tocar num resultado fecha a busca e abre a tela (o gasto, no mes dele).
struct BuscaView: View {
    private struct Resultado: Identifiable {
        let id: String
        let simbolo: String
        let titulo: String
        var detalhe = ""
        let rota: Rota
    }

    @Environment(Sessao.self) private var sessao
    @Environment(\.abrirRota) private var abrirRota
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var termo = ""
    @State private var buscaAtiva = true
    @State private var gastos: [Gasto] = []
    @State private var lugares: [Lugar] = []
    @State private var filmes: [Filme] = []
    @State private var compras: [ItemDeCompra] = []

    private var termoLimpo: String { termo.trimmingCharacters(in: .whitespaces) }

    var body: some View {
        NavigationStack {
            List {
                grupo("Telas", telas)
                if termoLimpo.count >= 2 {
                    grupo("Gastos", gastos.map {
                        Resultado(id: "g\($0.id)", simbolo: "creditcard", titulo: $0.item,
                                  detalhe: "\(Formatos.dataCurta($0.data)) · \(Formatos.moeda($0.valor))",
                                  rota: .verGasto(id: $0.id, mes: String($0.data.prefix(7))))
                    })
                    grupo("Lugares", lugares.filter { tem($0.nome, $0.localizacao, $0.categoria) }.prefix(8).map {
                        Resultado(id: "l\($0.id)", simbolo: "mappin.and.ellipse", titulo: $0.nome,
                                  detalhe: $0.localizacao ?? "", rota: .tela(.lugares))
                    })
                    grupo("Filmes", filmes.filter { tem($0.titulo) }.prefix(8).map {
                        Resultado(id: "f\($0.id)", simbolo: "film", titulo: $0.titulo, detalhe: $0.ano ?? "", rota: .tela(.filmes))
                    })
                    grupo("Compras", compras.filter { tem($0.item, $0.marca) }.prefix(8).map {
                        Resultado(id: "c\($0.id)", simbolo: "cart", titulo: $0.item, detalhe: $0.lista.rawValue, rota: .tela(.compras))
                    })
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .overlay {
                if !termoLimpo.isEmpty, semResultado {
                    ContentUnavailableView.search(text: termoLimpo)
                }
            }
            .searchable(text: $termo, isPresented: $buscaAtiva, placement: .navigationBarDrawer(displayMode: .always),
                        prompt: Text("Gastos, lugares, filmes, compras e telas"))
            .navigationTitle("Buscar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fechar") { fechar() }
                }
            }
            .task { await carregarListas() }
            .task(id: termoLimpo) { await buscarGastos() }
        }
    }

    @ViewBuilder
    private func grupo(_ titulo: LocalizedStringKey, _ resultados: [Resultado]) -> some View {
        if !resultados.isEmpty {
            Section(titulo) {
                ForEach(resultados) { resultado in
                    Button { ir(resultado.rota) } label: {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(resultado.titulo).foregroundStyle(paleta.texto)
                                if !resultado.detalhe.isEmpty {
                                    Text(resultado.detalhe).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                                }
                            }
                        } icon: {
                            Image(systemName: resultado.simbolo).foregroundStyle(paleta.primaria)
                        }
                    }
                }
            }
            .listRowBackground(paleta.superficie)
        }
    }

    /// As abas e as telas que a pessoa ve no Mais (modulos ligados; o Premium so para o titular).
    private var telas: [Resultado] {
        guard !termoLimpo.isEmpty else { return [] }
        let abas: [Resultado] = [
            Resultado(id: "painel", simbolo: "chart.pie.fill", titulo: String(localized: "Painel"), rota: .aba(.painel)),
            Resultado(id: "gastos", simbolo: "list.bullet.rectangle.fill", titulo: String(localized: "Gastos"), rota: .aba(.gastos)),
            Resultado(id: "receitas", simbolo: "arrow.down.circle.fill", titulo: String(localized: "Receitas"), rota: .aba(.receitas)),
            Resultado(id: "relatorios", simbolo: "chart.bar.xaxis", titulo: String(localized: "Relatórios"), rota: .aba(.relatorios)),
        ]
        let modulos = sessao.usuario?.preferencias?.modulos
        let ehTitular = [.titular, .admin].contains(sessao.usuario?.role)
        let doMais = TelaDoMais.allCases
            .filter { tela in tela.modulo.map { modulos?.contains($0) ?? true } ?? true }
            .filter { $0 != .premium || ehTitular }
            .map { Resultado(id: $0.rawValue, simbolo: $0.simbolo, titulo: $0.titulo, rota: .tela($0)) }
        return (abas + doMais).filter { tem($0.titulo) }
    }

    private var semResultado: Bool {
        telas.isEmpty && gastos.isEmpty
            && !lugares.contains { tem($0.nome, $0.localizacao, $0.categoria) }
            && !filmes.contains { tem($0.titulo) } && !compras.contains { tem($0.item, $0.marca) }
    }

    /// Sem acento e sem caixa: "pao" acha "Pão".
    private func tem(_ textos: String?...) -> Bool {
        textos.contains { $0?.localizedStandardContains(termoLimpo) == true }
    }

    private func ir(_ rota: Rota) {
        fechar()
        abrirRota(rota)
    }

    private func carregarListas() async {
        let cliente = sessao.cliente
        async let lugares = try? cliente.listarLugares()
        async let filmes = try? cliente.listarFilmes()
        async let compras = try? cliente.listarCompras()
        self.lugares = await lugares ?? []
        self.filmes = await filmes ?? []
        self.compras = await compras ?? []
    }

    private func buscarGastos() async {
        guard termoLimpo.count >= 2 else {
            gastos = []
            return
        }
        try? await Task.sleep(for: .milliseconds(250))
        guard !Task.isCancelled else { return }
        gastos = (try? await sessao.cliente.buscarGastos(termoLimpo)) ?? []
    }
}
