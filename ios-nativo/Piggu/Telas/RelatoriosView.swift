import Charts
import SwiftUI
import TipKit
import UniformTypeIdentifiers

/// Relatorios (features/reports): o mes por categoria contra o anterior, com projecao (gratis), o
/// ano mes a mes, receitas contra gastos, e para onde foi o dinheiro (Premium). Exporta os gastos
/// do mes em CSV, como o "Exportar" do site, pelo compartilhar do sistema.
struct RelatoriosView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<ResumoDoMes> = .carregando
    @State private var doAno: RelatorioDoAno?
    @State private var erroDoAno: String?
    @State private var mesEmFoco: String?

    private var chave: String { Formatos.mes(mes) }
    private var ano: Int { Calendar.current.component(.year, from: mes) }
    private var ehPremium: Bool { sessao.usuario?.plano == .premium }

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
                    TipView(Dicas.Relatorios()).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                    numeros(resumo)
                    if resumo.porCategoria.isEmpty {
                        EstadoVazio(icone: "chart.bar", titulo: "Nenhum gasto neste mês",
                                    texto: "Assim que houver gastos, o relatório mostra para onde o dinheiro foi.",
                                    acao: ("Lançar um gasto", aoLancar))
                            .listRowBackground(Color.clear)
                    } else {
                        categorias(resumo.porCategoria)
                    }
                    secaoDoAno
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
            .navigationTitle("Relatórios")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    ShareLink(item: CSVDosGastos(mes: chave, cliente: sessao.cliente),
                              preview: SharePreview(String(localized: "Gastos de \(Formatos.nomeDoMes(chave: chave))"))) {
                        Label("Exportar gastos do mês (CSV)", systemImage: "square.and.arrow.up")
                    }
                    .disabled(!rede.conectado)
                }
            }
            .refreshable { await carregar() }
            .task(id: "\(chave)#\(versaoDosDados)#\(rede.conectado)") { await carregar() }
            .task(id: "\(ano)#\(versaoDosDados)#\(rede.conectado)#\(ehPremium)") { await carregarAno() }
        }
    }

    // MARK: - O mes

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

    /// Cada categoria deste mes (barra) contra o mes anterior (o traco), como o site.
    private func categorias(_ porCategoria: [ResumoDoMes.PorCategoria]) -> some View {
        Section {
            Chart(porCategoria) { linha in
                BarMark(x: .value("Este mês", linha.total.paraDesenho), y: .value("Categoria", linha.categoria))
                    .foregroundStyle(paleta.serieGasto)
                    .cornerRadius(5)
                    .annotation(position: .trailing) {
                        Text(Formatos.moeda(linha.total)).font(.sora(.caption)).monospacedDigit()
                            .foregroundStyle(paleta.textoSuave)
                    }
                if linha.anterior > 0 {
                    RectangleMark(x: .value("Mês anterior", linha.anterior.paraDesenho),
                                  y: .value("Categoria", linha.categoria), width: 3, height: 20)
                        .foregroundStyle(paleta.texto)
                }
            }
            .chartXAxis(.hidden)
            .chartXScale(domain: 0...(porCategoria.flatMap { [$0.total, $0.anterior] }.max()?.paraDesenho ?? 1) * 1.45)
            .chartYAxis { AxisMarks { _ in AxisValueLabel().font(.sora(.footnote)) } }
            .frame(height: CGFloat(porCategoria.count) * 36 + 8)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(porCategoria.map {
                "\($0.categoria): \(Formatos.moeda($0.total)). Mês anterior: \(Formatos.moeda($0.anterior))"
            }.joined(separator: ". "))
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

    // MARK: - O ano (Premium)

    @ViewBuilder
    private var secaoDoAno: some View {
        Section {
            if !ehPremium {
                Text("Veja o ano mês a mês, receitas contra gastos e para onde foi o dinheiro. Faz parte do Piggu Premium.")
                    .font(.sora(.footnote))
                LinkDoSite(titulo: "Piggu Premium", caminho: "/plano")
            } else if let erroDoAno {
                Faixa(texto: erroDoAno, tipo: .erro)
            } else if let doAno {
                ano(doAno)
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        } header: {
            HStack {
                Text("Ano de \(String(ano))")
                Spacer()
                Label("Premium", systemImage: "star.fill").font(.sora(.caption, peso: .semibold))
            }
        }
        .listRowBackground(paleta.superficie)
        if ehPremium, let doAno, !doAno.porCategoria.isEmpty {
            Section("Para onde foi o dinheiro") {
                Chart(doAno.porCategoria) { linha in
                    BarMark(x: .value("Total", linha.total.paraDesenho), y: .value("Categoria", linha.categoria))
                        .foregroundStyle(paleta.serieGasto)
                        .cornerRadius(5)
                        .annotation(position: .trailing) {
                            Text(Formatos.moeda(linha.total)).font(.sora(.caption)).monospacedDigit()
                                .foregroundStyle(paleta.textoSuave)
                        }
                }
                .chartXAxis(.hidden)
                .chartXScale(domain: 0...((doAno.porCategoria.first?.total.paraDesenho ?? 1) * 1.5))
                .chartYAxis { AxisMarks { _ in AxisValueLabel().font(.sora(.footnote)) } }
                .frame(height: CGFloat(doAno.porCategoria.count) * 34 + 8)
            }
            .listRowBackground(paleta.superficie)
        }
    }

    @ViewBuilder
    private func ano(_ relatorio: RelatorioDoAno) -> some View {
        HStack(alignment: .top) {
            numero("Receitas", relatorio.receitas)
            numero("Gastos", relatorio.gastos)
            numero("Sobra", relatorio.sobra, negativo: relatorio.sobra < 0)
        }
        Chart {
            ForEach(relatorio.meses) { linha in
                BarMark(x: .value("Mês", Self.mesCurto(linha.mes)), y: .value("Valor", linha.receitas.paraDesenho))
                    .foregroundStyle(by: .value("Série", String(localized: "Receitas")))
                    .position(by: .value("Série", String(localized: "Receitas")))
                    .cornerRadius(3)
                BarMark(x: .value("Mês", Self.mesCurto(linha.mes)), y: .value("Valor", linha.gastos.paraDesenho))
                    .foregroundStyle(by: .value("Série", String(localized: "Gastos")))
                    .position(by: .value("Série", String(localized: "Gastos")))
                    .cornerRadius(3)
            }
            if let mesEmFoco, let linha = relatorio.meses.first(where: { Self.mesCurto($0.mes) == mesEmFoco }) {
                RuleMark(x: .value("Mês", mesEmFoco))
                    .foregroundStyle(paleta.textoSuave.opacity(0.25))
                    .lineStyle(StrokeStyle(lineWidth: 24))
                    .zIndex(-1)
                    .annotation(position: .top, overflowResolution: .init(x: .fit, y: .disabled)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(mesEmFoco) \(String(relatorio.ano))").font(.sora(.caption, peso: .semibold))
                            Text("Receitas \(Formatos.moeda(linha.receitas))")
                            Text("Gastos \(Formatos.moeda(linha.gastos))")
                            Text("Sobra \(Formatos.moeda(linha.sobra))")
                        }
                        .font(.sora(.caption2))
                        .padding(8)
                        .background(paleta.superficieFunda, in: RoundedRectangle(cornerRadius: 10))
                    }
            }
        }
        .chartForegroundStyleScale([String(localized: "Receitas"): paleta.serieReceita,
                                    String(localized: "Gastos"): paleta.serieGasto])
        .chartLegend(position: .top, alignment: .leading)
        .chartXSelection(value: $mesEmFoco)
        .chartYAxis(.hidden)
        .frame(height: 220)
        .accessibilityLabel("Receitas e gastos de cada mês de \(String(relatorio.ano))")
        DisclosureGroup("Ver em tabela") {
            ForEach(relatorio.meses) { linha in
                LabeledContent(Self.mesCurto(linha.mes)) {
                    VStack(alignment: .trailing) {
                        Text("+\(Formatos.moeda(linha.receitas))").foregroundStyle(paleta.sucesso)
                        Text("−\(Formatos.moeda(linha.gastos))")
                        Text(Formatos.moeda(linha.sobra)).foregroundStyle(linha.sobra < 0 ? paleta.perigo : paleta.texto)
                    }
                    .font(.sora(.footnote)).monospacedDigit()
                }
            }
        }
    }

    private func numero(_ rotulo: LocalizedStringKey, _ valor: Decimal, negativo: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(rotulo).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            Text(Formatos.moeda(valor)).font(.sora(.subheadline, peso: .semibold)).monospacedDigit()
                .foregroundStyle(negativo ? paleta.perigo : paleta.texto)
                .minimumScaleFactor(0.7).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private static let mesesCurtos = ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"]

    /// "2026-10" vira "Out", como o eixo do site.
    static func mesCurto(_ mes: String) -> String {
        Int(mes.suffix(2)).flatMap { (1...12).contains($0) ? mesesCurtos[$0 - 1] : nil } ?? mes
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

    // MARK: - API

    private func carregar() async {
        await sessao.cache.carregar("resumo/\(chave)", mostrar: { carga = $0 }) {
            try await sessao.cliente.resumoDoMes(Formatos.mes(mes))
        }
    }

    private func carregarAno() async {
        guard ehPremium else { return }
        let ano = ano
        if doAno?.ano != ano { doAno = nil }
        do {
            doAno = try await sessao.cliente.relatorioDoAno(ano)
            erroDoAno = nil
        } catch is CancellationError {
        } catch {
            erroDoAno = error.localizedDescription
        }
    }
}

/// Os gastos do mes em CSV, baixados so quando a pessoa escolhe onde mandar (o "Exportar" do site).
struct CSVDosGastos: Transferable {
    let mes: String
    let cliente: ClienteDaAPI

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .commaSeparatedText) { csv in
            let dados = try await csv.cliente.exportarGastos(csv.mes)
            let arquivo = FileManager.default.temporaryDirectory.appending(path: "piggu-gastos-\(csv.mes).csv")
            try dados.write(to: arquivo, options: .atomic)
            return SentTransferredFile(arquivo)
        }
    }
}
