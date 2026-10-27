import SwiftUI

/// Gastos do mes (GET /expenses?mes=), agrupados por dia.
struct GastosView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<[Gasto]> = .carregando

    var body: some View {
        NavigationStack {
            List {
                switch carga {
                case .carregando:
                    Section { EsqueletoDeLista() }
                        .listRowBackground(paleta.superficie)
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await carregar() } }
                        .listRowBackground(Color.clear)
                case let .pronto(gastos, guardadoEm):
                    if let guardadoEm {
                        AvisoDeGuardado(guardadoEm: guardadoEm)
                    }
                    if gastos.isEmpty {
                        EstadoVazio(icone: "tray", titulo: "Nenhum gasto neste mês",
                                    texto: "Lance o primeiro gasto do mês.",
                                    acao: ("Lançar um gasto", aoLancar))
                            .listRowBackground(Color.clear)
                    } else {
                        lista(gastos)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
            .navigationTitle("Gastos")
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)#\(rede.conectado)") { await carregar() }
        }
    }

    @ViewBuilder
    private func lista(_ gastos: [Gasto]) -> some View {
        Section {
            HStack {
                Text("Total do mês")
                Spacer()
                Text(Formatos.moeda(gastos.reduce(0) { $0 + $1.valor }))
                    .font(.sora(.body, peso: .semibold))
                    .monospacedDigit()
            }
            .accessibilityElement(children: .combine)
        }
        .listRowBackground(paleta.superficie)

        let porDia = Dictionary(grouping: gastos, by: \.data).sorted { $0.key > $1.key }
        ForEach(porDia, id: \.key) { dia, doDia in
            Section(Formatos.inicialMaiuscula(Formatos.diaPorExtenso(dia))) {
                ForEach(doDia) { gasto in
                    LinhaDeGasto(gasto: gasto)
                }
            }
            .listRowBackground(paleta.superficie)
        }
    }

    private func carregar() async {
        await sessao.cache.carregar("gastos/\(Formatos.mes(mes))", mostrar: { carga = $0 }) {
            try await sessao.cliente.listarGastos(mes: Formatos.mes(mes))
        }
    }
}

private struct LinhaDeGasto: View {
    let gasto: Gasto

    @Environment(\.paleta) private var paleta

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
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
            }
            Spacer()
            Text(Formatos.moeda(gasto.valor))
                .monospacedDigit()
        }
        .accessibilityElement(children: .combine)
    }
}
