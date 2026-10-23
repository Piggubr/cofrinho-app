import SwiftUI

/// Gastos do mes (GET /expenses?mes=), agrupados por dia.
struct GastosView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void

    @Environment(Sessao.self) private var sessao
    @State private var mes = Date()
    @State private var gastos: [Gasto]?
    @State private var erro: String?

    private var porDia: [(dia: String, gastos: [Gasto])] {
        Dictionary(grouping: gastos ?? [], by: \.data)
            .sorted { $0.key > $1.key }
            .map { (dia: $0.key, gastos: $0.value) }
    }

    private var total: Decimal {
        (gastos ?? []).reduce(0) { $0 + $1.valor }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    SeletorDeMes(mes: $mes)
                    if let gastos, !gastos.isEmpty {
                        HStack {
                            Text("Total do mês")
                            Spacer()
                            Text(Formatos.moeda(total)).bold().monospacedDigit()
                        }
                    }
                }

                if let gastos {
                    if gastos.isEmpty {
                        ContentUnavailableView {
                            Label("Nenhum gasto neste mês", systemImage: "tray")
                        } description: {
                            Text("Toque no + para lançar o primeiro.")
                        }
                        .listRowBackground(Color.clear)
                    }
                    ForEach(porDia, id: \.dia) { grupo in
                        Section(Formatos.diaPorExtenso(grupo.dia).capitalized(with: Formatos.localidade)) {
                            ForEach(grupo.gastos) { gasto in
                                LinhaDeGasto(gasto: gasto)
                            }
                        }
                    }
                } else if let erro {
                    ContentUnavailableView {
                        Label("Não deu para carregar", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(erro)
                    } actions: {
                        Button("Tentar de novo") { Task { await carregar() } }
                    }
                } else {
                    HStack { Spacer(); ProgressView(); Spacer() }
                        .listRowBackground(Color.clear)
                }
            }
            .navigationTitle("Gastos")
            .toolbar { BotaoDeLancar(acao: aoLancar) }
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)") { await carregar() }
        }
    }

    private func carregar() async {
        do {
            gastos = try await sessao.cliente.listarGastos(mes: Formatos.mes(mes))
            erro = nil
        } catch is CancellationError {
        } catch {
            gastos = nil
            erro = error.localizedDescription
        }
    }
}

private struct LinhaDeGasto: View {
    let gasto: Gasto

    private var detalhe: String {
        var partes: [String] = []
        if let estabelecimento = gasto.estabelecimento, !estabelecimento.isEmpty {
            partes.append(estabelecimento)
        }
        partes.append(gasto.categoria)
        if let parcela = gasto.parcela, let parcelas = gasto.parcelas {
            partes.append("\(parcela)/\(parcelas)")
        }
        return partes.joined(separator: " · ")
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(gasto.item)
                Text(detalhe)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(Formatos.moeda(gasto.valor))
                .monospacedDigit()
        }
    }
}
