import SwiftUI

/// Relatorio do mes (GET /reports/month), como o primeiro cartao de
/// frontend/src/app/features/reports: gastos, mes anterior, projecao e cada categoria contra
/// o mes anterior. O ano mes a mes (Premium) entra na etapa 5.
struct RelatoriosView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<ResumoDoMes> = .carregando

    var body: some View {
        NavigationStack {
            List {
                switch carga {
                case .carregando:
                    Section { EsqueletoDeLista(linhas: 5) }
                        .listRowBackground(paleta.superficie)
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await carregar() } }
                        .listRowBackground(Color.clear)
                case let .pronto(resumo, guardadoEm):
                    if let guardadoEm {
                        AvisoDeGuardado(guardadoEm: guardadoEm)
                    }
                    numeros(resumo)
                    if resumo.porCategoria.isEmpty {
                        EstadoVazio(icone: "chart.bar", titulo: "Nenhum gasto neste mês",
                                    texto: "Assim que houver gastos, o relatório mostra para onde o dinheiro foi.",
                                    acao: ("Lançar um gasto", aoLancar))
                            .listRowBackground(Color.clear)
                    } else {
                        categorias(resumo.porCategoria)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
            .navigationTitle("Relatórios")
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)#\(rede.conectado)") { await carregar() }
        }
    }

    @ViewBuilder
    private func numeros(_ resumo: ResumoDoMes) -> some View {
        Section {
            LabeledContent("Gastos") { Text(Formatos.moeda(resumo.gastos)).foregroundStyle(paleta.texto) }
            LabeledContent {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Formatos.moeda(resumo.gastosMesAnterior)).foregroundStyle(paleta.texto)
                    if let variacao = resumo.variacao {
                        Text(textoDaVariacao(variacao))
                            .font(.sora(.footnote))
                            .foregroundStyle(variacao > 0 ? paleta.perigo : paleta.sucesso)
                    }
                }
            } label: {
                Text("Mês anterior")
            }
            if let projecao = resumo.projecaoDeGastos {
                LabeledContent("Projeção do mês") { Text(Formatos.moeda(projecao)).foregroundStyle(paleta.texto) }
            }
        }
        .monospacedDigit()
        .listRowBackground(paleta.superficie)
    }

    private func categorias(_ porCategoria: [ResumoDoMes.PorCategoria]) -> some View {
        // Como o site: a maior entre este mes e o anterior e a barra cheia.
        let maior = max(porCategoria.flatMap { [$0.total, $0.anterior] }.max()?.paraDesenho ?? 1, 0.01)
        return Section {
            ForEach(porCategoria) { categoria in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(categoria.categoria)
                        Spacer()
                        Text(Formatos.moeda(categoria.total)).monospacedDigit()
                    }
                    BarraDeValor(fracao: categoria.total.paraDesenho / maior,
                                 marca: categoria.anterior > 0 ? categoria.anterior.paraDesenho / maior : nil)
                }
                .padding(.vertical, 2)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(
                    "\(categoria.categoria): \(Formatos.moeda(categoria.total)). Mês anterior: \(Formatos.moeda(categoria.anterior))")
            }
        } header: {
            HStack(spacing: 14) {
                Label { Text("Este mês") } icon: { Circle().fill(paleta.serieGasto).frame(width: 10) }
                Label { Text("Mês anterior") } icon: { Capsule().fill(paleta.texto).frame(width: 3, height: 12) }
            }
            .font(.sora(.caption))
            .accessibilityHidden(true)
        }
        .listRowBackground(paleta.superficie)
    }

    /// "▲ 12,5% a mais", como o site.
    private func textoDaVariacao(_ variacao: Decimal) -> String {
        if variacao > 0 {
            return String(localized: "▲ \(Formatos.porcentagem(variacao)) a mais")
        }
        if variacao < 0 {
            return String(localized: "▼ \(Formatos.porcentagem(-variacao)) a menos")
        }
        return String(localized: "igual")
    }

    private func carregar() async {
        await sessao.cache.carregar("resumo/\(Formatos.mes(mes))", mostrar: { carga = $0 }) {
            try await sessao.cliente.resumoDoMes(Formatos.mes(mes))
        }
    }
}
