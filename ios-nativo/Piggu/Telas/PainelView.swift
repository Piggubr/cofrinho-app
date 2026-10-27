import SwiftUI

/// Resumo do mes (GET /reports/month), como o card do painel do app web.
struct PainelView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void

    @Environment(Sessao.self) private var sessao
    @State private var mes = Date()
    @State private var resumo: ResumoDoMes?
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    SeletorDeMes(mes: $mes)
                }

                if let resumo {
                    conteudo(resumo)
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
            .navigationTitle(saudacao)
            .toolbar { BotaoDeLancar(acao: aoLancar) }
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)") { await carregar() }
        }
    }

    private var saudacao: String {
        sessao.usuario.map { "Olá, \($0.comoChamar)" } ?? "Painel"
    }

    @ViewBuilder
    private func conteudo(_ resumo: ResumoDoMes) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text("Sobra do mês")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(Formatos.moeda(resumo.sobra))
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(resumo.sobra < 0 ? Color.red : Color.accentColor)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                if let taxa = resumo.taxaDePoupanca {
                    Text("Você guardou \(Formatos.porcentagem(taxa)) do que entrou")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 6)

            LinhaDeValor(titulo: "Receitas", valor: resumo.receitas, icone: "arrow.down.circle.fill", cor: .green)
            LinhaDeValor(titulo: "Gastos", valor: resumo.gastos, icone: "arrow.up.circle.fill", cor: .accentColor)
            if let projecao = resumo.projecaoDeGastos {
                LinhaDeValor(titulo: "Projeção até o fim do mês", valor: projecao,
                             icone: "chart.line.uptrend.xyaxis", cor: .orange)
            }
        }

        Section("Comparado ao mês anterior") {
            LinhaDeValor(titulo: "Gastos no mês anterior", valor: resumo.gastosMesAnterior,
                         icone: "calendar", cor: .secondary)
            if let variacao = resumo.variacao {
                HStack {
                    Label("Variação", systemImage: variacao > 0 ? "arrow.up.right" : "arrow.down.right")
                    Spacer()
                    Text((variacao > 0 ? "+" : "") + Formatos.porcentagem(variacao))
                        .foregroundStyle(variacao > 0 ? .red : .green)
                        .monospacedDigit()
                }
            }
        }

        Section("Por categoria") {
            if resumo.porCategoria.isEmpty {
                Text("Nenhum gasto neste mês ainda.")
                    .foregroundStyle(.secondary)
            } else {
                let maior = resumo.porCategoria.map(\.total).max() ?? 1
                ForEach(resumo.porCategoria) { categoria in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(categoria.categoria)
                            Spacer()
                            Text(Formatos.moeda(categoria.total))
                                .monospacedDigit()
                        }
                        ProgressView(value: NSDecimalNumber(decimal: categoria.total).doubleValue,
                                     total: max(NSDecimalNumber(decimal: maior).doubleValue, 0.01))
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    private func carregar() async {
        do {
            resumo = try await sessao.cliente.resumoDoMes(Formatos.mes(mes))
            erro = nil
        } catch is CancellationError {
        } catch {
            resumo = nil
            erro = error.localizedDescription
        }
    }
}

private struct LinhaDeValor: View {
    let titulo: String
    let valor: Decimal
    let icone: String
    let cor: Color

    var body: some View {
        HStack {
            Label {
                Text(titulo)
            } icon: {
                Image(systemName: icone).foregroundStyle(cor)
            }
            Spacer()
            Text(Formatos.moeda(valor))
                .monospacedDigit()
        }
    }
}
