import SwiftUI

/// Resumo do mes (GET /reports/month), como o card do painel do app web.
struct PainelView: View {
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
                    Section { EsqueletoDeLista(linhas: 4) }
                        .listRowBackground(paleta.superficie)
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await carregar() } }
                        .listRowBackground(Color.clear)
                case let .pronto(resumo, guardadoEm):
                    if let guardadoEm {
                        AvisoDeGuardado(guardadoEm: guardadoEm)
                    }
                    if resumo.receitas == 0 && resumo.gastos == 0 {
                        EstadoVazio(icone: "doc.text", titulo: "Nenhum gasto lançado neste mês",
                                    texto: "Toque no + para lançar o primeiro.",
                                    acao: ("Lançar um gasto", aoLancar))
                            .listRowBackground(Color.clear)
                    } else {
                        conteudo(resumo)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
            .navigationTitle(saudacao)
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)#\(rede.conectado)") { await carregar() }
        }
    }

    private var saudacao: String {
        sessao.usuario.map { String(localized: "Olá, \($0.comoChamar)") } ?? String(localized: "Painel")
    }

    @ViewBuilder
    private func conteudo(_ resumo: ResumoDoMes) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text("Sobra do mês")
                    .font(.sora(.subheadline))
                    .foregroundStyle(paleta.textoSuave)
                Text(Formatos.moeda(resumo.sobra))
                    .font(.sora(.largeTitle, peso: .bold))
                    .foregroundStyle(resumo.sobra < 0 ? paleta.perigo : paleta.primaria)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                if let taxa = resumo.taxaDePoupanca {
                    Text("Você guardou \(Formatos.porcentagem(taxa)) do que entrou")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
            }
            .padding(.vertical, 6)
            .accessibilityElement(children: .combine)

            LinhaDeValor(titulo: "Receitas", valor: resumo.receitas, icone: "arrow.down.circle.fill",
                         cor: paleta.serieReceita)
            LinhaDeValor(titulo: "Gastos", valor: resumo.gastos, icone: "arrow.up.circle.fill",
                         cor: paleta.serieGasto)
            if let projecao = resumo.projecaoDeGastos {
                LinhaDeValor(titulo: "Projeção até o fim do mês", valor: projecao,
                             icone: "chart.line.uptrend.xyaxis", cor: paleta.alerta)
            }
        }
        .listRowBackground(paleta.superficie)

        Section("Comparado ao mês anterior") {
            LinhaDeValor(titulo: "Gastos no mês anterior", valor: resumo.gastosMesAnterior,
                         icone: "calendar", cor: paleta.textoSuave)
            if let variacao = resumo.variacao {
                HStack {
                    Label("Variação", systemImage: variacao > 0 ? "arrow.up.right" : "arrow.down.right")
                    Spacer()
                    Text((variacao > 0 ? "+" : "") + Formatos.porcentagem(variacao))
                        .foregroundStyle(variacao > 0 ? paleta.perigo : paleta.sucesso)
                        .monospacedDigit()
                }
                .accessibilityElement(children: .combine)
            }
        }
        .listRowBackground(paleta.superficie)

        if !resumo.porCategoria.isEmpty {
            Section("Por categoria") {
                let maior = resumo.porCategoria.map(\.total).max()?.paraDesenho ?? 1
                ForEach(resumo.porCategoria) { categoria in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(categoria.categoria)
                            Spacer()
                            Text(Formatos.moeda(categoria.total))
                                .monospacedDigit()
                        }
                        BarraDeValor(fracao: categoria.total.paraDesenho / max(maior, 0.01))
                    }
                    .padding(.vertical, 2)
                    .accessibilityElement(children: .combine)
                }
            }
            .listRowBackground(paleta.superficie)
        }
    }

    private func carregar() async {
        await sessao.cache.carregar("resumo/\(Formatos.mes(mes))", mostrar: { carga = $0 }) {
            try await sessao.cliente.resumoDoMes(Formatos.mes(mes))
        }
    }
}

private struct LinhaDeValor: View {
    let titulo: LocalizedStringKey
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
        .accessibilityElement(children: .combine)
    }
}
