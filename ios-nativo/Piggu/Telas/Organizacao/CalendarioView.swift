import Charts
import SwiftUI

/// Calendario (features/calendar): a grade do mes com o gasto e os lembretes de cada dia, o
/// que aconteceu no dia escolhido, o grafico de receitas e gastos acumulados e as notas.
///
/// Uma nota com valor vira tambem um gasto: o backend cria os dois juntos e apagar a nota apaga
/// o gasto. No iPad e em janela larga, a grade e o dia ficam lado a lado.
struct CalendarioView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @Environment(\.horizontalSizeClass) private var largura
    @State private var mes = Date()
    @State private var carga: Carga<DadosDoCalendario> = .carregando
    @State private var diaEscolhido: String?
    @State private var criando = false
    @State private var apagando: Nota?
    @State private var erro: String?

    private var chave: String { Formatos.mes(mes) }

    var body: some View {
        Group {
            switch carga {
            case .carregando:
                List { Section { EsqueletoDeLista(linhas: 5) }.listRowBackground(paleta.superficie) }
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }
            case let .pronto(dados, guardadoEm):
                if largura == .regular {
                    HStack(alignment: .top, spacing: 0) {
                        List { mesInteiro(dados, guardadoEm: guardadoEm) }
                            .frame(maxWidth: .infinity)
                        List { doDia(dados); notas(dados) }
                            .frame(maxWidth: 420)
                    }
                } else {
                    List {
                        mesInteiro(dados, guardadoEm: guardadoEm)
                        doDia(dados)
                        notas(dados)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
        .navigationTitle("Calendário")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { criando = true } label: { Label("Nova nota", systemImage: "plus") }
            }
        }
        .refreshable { await carregar() }
        .task(id: "\(chave)#\(rede.conectado)") {
            diaEscolhido = nil
            await carregar()
        }
        .sheet(isPresented: $criando) {
            NovaNotaView(data: diaEscolhido.flatMap(Formatos.data(deDia:)) ?? Date(),
                         categorias: carga.valor?.categorias ?? []) {
                Task { await carregar() }
            }
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar \"\($0.titulo)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { nota in
            Button("Apagar", role: .destructive) { Task { await excluir(nota) } }
        } message: { nota in
            if nota.gastoId != nil {
                Text("O gasto lançado junto também será apagado.")
            }
        }
    }

    // MARK: - Partes

    @ViewBuilder
    private func mesInteiro(_ dados: DadosDoCalendario, guardadoEm: Date?) -> some View {
        if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
        if let erro {
            Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
        Section {
            GradeDoMes(mes: mes, dados: dados, escolhido: $diaEscolhido)
        }
        .listRowBackground(paleta.superficie)
        Section("Receitas e gastos do mês") {
            GraficoDoMes(mes: chave, dados: dados, escolhido: diaEscolhido)
        }
        .listRowBackground(paleta.superficie)
    }

    @ViewBuilder
    private func doDia(_ dados: DadosDoCalendario) -> some View {
        if let dia = diaEscolhido {
            let gastos = dados.gastos.filter { $0.data == dia }
            let receitas = dados.receitas.filter { $0.data == dia }
            let lembretes = dados.notas.filter { $0.data == dia }
            Section(Formatos.dataCurta(dia)) {
                if gastos.isEmpty && receitas.isEmpty && lembretes.isEmpty {
                    Text("Nada registrado neste dia.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
                if !gastos.isEmpty {
                    Text("Gastos").font(.sora(.subheadline, peso: .semibold))
                    ForEach(gastos) { gasto in
                        LinhaDoCartao(titulo: gasto.item, detalhe: gasto.categoria) {
                            Text(Formatos.moeda(gasto.valor)).monospacedDigit()
                        }
                    }
                }
                if !receitas.isEmpty {
                    Text("Receitas").font(.sora(.subheadline, peso: .semibold))
                    ForEach(receitas) { receita in
                        LinhaDoCartao(titulo: receita.descricao, detalhe: receita.categoria) {
                            Text("+\(Formatos.moeda(receita.valor))").monospacedDigit().foregroundStyle(paleta.sucesso)
                        }
                    }
                }
                if !lembretes.isEmpty {
                    Text("Lembretes").font(.sora(.subheadline, peso: .semibold))
                    ForEach(lembretes) { linhaDaNota($0) }
                }
            }
            .listRowBackground(paleta.superficie)
        } else if largura == .regular {
            Section {
                Text("Toque num dia para ver o que aconteceu nele.")
                    .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            .listRowBackground(paleta.superficie)
        }
    }

    private func notas(_ dados: DadosDoCalendario) -> some View {
        Section {
            let semData = dados.notas.filter { $0.data == nil }
            if semData.isEmpty {
                Text("Nenhuma nota solta.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(semData) { linhaDaNota($0) }
            Button { criando = true } label: { Label("Nova nota", systemImage: "plus") }
        } header: {
            Text("Sem data")
        }
        .listRowBackground(paleta.superficie)
    }

    private func linhaDaNota(_ nota: Nota) -> some View {
        LinhaDoCartao(titulo: nota.titulo, detalhe: nota.texto) {
            if let valor = nota.valor, valor > 0 {
                Text(Formatos.moeda(valor)).monospacedDigit()
            }
        }
        .swipeActions {
            Button(role: .destructive) { apagando = nota } label: { Label("Apagar", systemImage: "trash") }
        }
    }

    // MARK: - API

    private func carregar() async {
        let cliente = sessao.cliente
        let mes = chave
        await sessao.cache.carregar("calendario/\(mes)", mostrar: { carga = $0 }) {
            async let notas = cliente.listarNotas()
            async let gastos = cliente.listarGastos(mes: mes)
            async let receitas = cliente.listarReceitas(mes)
            async let categorias = cliente.listarCategorias()
            return try await DadosDoCalendario(notas: notas, gastos: gastos, receitas: receitas, categorias: categorias)
        }
    }

    private func excluir(_ nota: Nota) async {
        do {
            try await sessao.cliente.excluirNota(nota.id)
            erro = nil
            await carregar()
        } catch {
            erro = error.localizedDescription
        }
    }
}

struct DadosDoCalendario: Codable, Equatable, Sendable {
    let notas: [Nota]
    let gastos: [Gasto]
    let receitas: [Receita]
    let categorias: [String]
}

/// A grade do mes: o numero do dia, o gasto dele e um ponto quando tem lembrete.
private struct GradeDoMes: View {
    let mes: Date
    let dados: DadosDoCalendario
    @Binding var escolhido: String?

    @Environment(\.paleta) private var paleta
    private let colunas = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

    var body: some View {
        let dias = Calendario.grade(Formatos.mes(mes))
        let hoje = Formatos.dia(Date())
        LazyVGrid(columns: colunas, spacing: 4) {
            ForEach(["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"], id: \.self) { nome in
                Text(LocalizedStringKey(nome)).font(.sora(.caption, peso: .semibold)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(Array(dias.enumerated()), id: \.offset) { _, dia in
                if let dia {
                    celula(dia, hoje: hoje)
                } else {
                    Color.clear.frame(minHeight: 48)
                }
            }
        }
        .padding(.vertical, 4)
        // Sete colunas no iPhone: acima disso o dia de dois digitos quebra. O VoiceOver le cada dia.
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
    }

    private func celula(_ dia: String, hoje: String) -> some View {
        let gasto = dados.gastos.filter { $0.data == dia }.reduce(Decimal(0)) { $0 + $1.valor }
        let lembretes = dados.notas.filter { $0.data == dia }.count
        let selecionado = escolhido == dia
        return Button {
            escolhido = selecionado ? nil : dia
        } label: {
            VStack(spacing: 2) {
                Text(String(Int(dia.suffix(2)) ?? 0))
                    .font(.sora(.subheadline, peso: dia == hoje ? .bold : .regular))
                if gasto > 0 {
                    Text(gasto.formatted(.number.notation(.compactName).precision(.fractionLength(0...1))
                        .locale(Formatos.localidade)))
                        .font(.sora(.caption2, peso: .medium))
                        .foregroundStyle(paleta.serieGasto)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                if lembretes > 0 {
                    Circle().fill(paleta.primaria).frame(width: 5, height: 5)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(selecionado ? paleta.suave : .clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay {
                if dia == hoje {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(paleta.primaria, lineWidth: 1.5)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(rotulo(dia, gasto: gasto, lembretes: lembretes))
        .accessibilityAddTraits(selecionado ? .isSelected : [])
    }

    private func rotulo(_ dia: String, gasto: Decimal, lembretes: Int) -> String {
        var partes = [Formatos.diaPorExtenso(dia)]
        if gasto > 0 { partes.append(String(localized: "gasto de \(Formatos.moeda(gasto))")) }
        if lembretes > 0 { partes.append(String(localized: "tem lembrete")) }
        return partes.joined(separator: ", ")
    }
}

/// Receitas e gastos acumulados no mes (grafico-do-mes.ts), com a tabela por semana.
private struct GraficoDoMes: View {
    let mes: String
    let dados: DadosDoCalendario
    let escolhido: String?

    @State private var diaEmFoco: Int?
    @Environment(\.paleta) private var paleta

    var body: some View {
        let serie = Calendario.serie(mes: mes, gastos: dados.gastos, receitas: dados.receitas)
        let receitas = serie.last?.receitasAteAqui ?? 0
        let gastos = serie.last?.gastosAteAqui ?? 0
        if receitas == 0 && gastos == 0 {
            Text("Nenhuma receita ou gasto neste mês até agora.")
                .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
        } else {
            HStack(alignment: .top) {
                numero("Receitas", receitas)
                numero("Gastos", gastos)
                numero("Sobra", receitas - gastos, negativo: receitas < gastos)
            }
            let destaque = diaEmFoco ?? escolhido.flatMap { $0.hasPrefix(mes) ? Int($0.suffix(2)) : nil }
            Chart {
                ForEach(serie) { dia in
                    LineMark(x: .value("Dia", dia.dia), y: .value("Valor", dia.receitasAteAqui.paraDesenho),
                             series: .value("Série", "Receitas acumuladas"))
                        .foregroundStyle(paleta.serieReceita)
                    LineMark(x: .value("Dia", dia.dia), y: .value("Valor", dia.gastosAteAqui.paraDesenho),
                             series: .value("Série", "Gastos acumulados"))
                        .foregroundStyle(paleta.serieGasto)
                }
                if let destaque, let ponto = serie.first(where: { $0.dia == destaque }) {
                    RuleMark(x: .value("Dia", destaque))
                        .foregroundStyle(paleta.textoSuave.opacity(0.5))
                        .annotation(position: .top, overflowResolution: .init(x: .fit, y: .disabled)) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Até o dia \(ponto.dia)").font(.sora(.caption, peso: .semibold))
                                Text("Receitas \(Formatos.moeda(ponto.receitasAteAqui))")
                                Text("Gastos \(Formatos.moeda(ponto.gastosAteAqui))")
                                Text("Sobra \(Formatos.moeda(ponto.receitasAteAqui - ponto.gastosAteAqui))")
                            }
                            .font(.sora(.caption2))
                            .padding(8)
                            .background(paleta.superficieFunda, in: RoundedRectangle(cornerRadius: 10))
                        }
                }
            }
            .chartForegroundStyleScale(["Receitas acumuladas": paleta.serieReceita, "Gastos acumulados": paleta.serieGasto])
            .chartLegend(position: .top, alignment: .leading)
            .chartXScale(domain: 1...Calendario.diasNoMes(mes))
            .chartXSelection(value: $diaEmFoco)
            .chartYAxis {
                AxisMarks(values: .automatic(desiredCount: 3)) { valor in
                    AxisGridLine()
                    AxisValueLabel {
                        if let numero = valor.as(Double.self) {
                            // "R$ 2,5 mil", como o eixo do site (moeda compacta so existe no iOS 18).
                            Text("\(Formatos.simboloDaMoeda) \(numero.formatted(.number.notation(.compactName).precision(.fractionLength(0...1)).locale(Formatos.localidade)))")
                        }
                    }
                }
            }
            .frame(height: 200)
            .accessibilityLabel("Receitas e gastos acumulados no mês")
            DisclosureGroup("Ver por semana") {
                ForEach(Calendario.semanas(serie, mes: mes), id: \.de) { semana in
                    LabeledContent("\(semana.de) a \(semana.ate)") {
                        VStack(alignment: .trailing) {
                            Text("+\(Formatos.moeda(semana.receitas))").foregroundStyle(paleta.sucesso)
                            Text("−\(Formatos.moeda(semana.gastos))")
                            Text(Formatos.moeda(semana.receitas - semana.gastos))
                                .foregroundStyle(semana.receitas < semana.gastos ? paleta.perigo : paleta.texto)
                        }
                        .font(.sora(.footnote)).monospacedDigit()
                    }
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
}

/// As contas do calendario, iguais as do site (datas.ts e grafico-do-mes.ts).
enum Calendario {
    struct Dia: Identifiable, Equatable {
        let dia: Int
        let receitas: Decimal
        let gastos: Decimal
        let receitasAteAqui: Decimal
        let gastosAteAqui: Decimal

        var id: Int { dia }
    }

    struct Semana: Equatable {
        let de: Int
        let ate: Int
        let receitas: Decimal
        let gastos: Decimal
    }

    static func diasNoMes(_ mes: String) -> Int {
        guard let inicio = Formatos.data(deDia: mes + "-01") else { return 30 }
        return Calendar(identifier: .gregorian).range(of: .day, in: .month, for: inicio)?.count ?? 30
    }

    /// Os dias do mes na grade de domingo a sabado; nil e o espaco antes do dia 1.
    static func grade(_ mes: String) -> [String?] {
        guard let inicio = Formatos.data(deDia: mes + "-01") else { return [] }
        let semana = Calendar(identifier: .gregorian).component(.weekday, from: inicio) - 1
        return Array(repeating: nil, count: semana) + (1...diasNoMes(mes)).map { String(format: "%@-%02d", mes, $0) }
    }

    /// Dia a dia e acumulado. No mes corrente para em hoje; num mes futuro fica vazia.
    static func serie(mes: String, gastos: [Gasto], receitas: [Receita], hoje: String = Formatos.dia(Date())) -> [Dia] {
        let mesDeHoje = String(hoje.prefix(7))
        let ultimo = mes < mesDeHoje ? diasNoMes(mes) : mes == mesDeHoje ? (Int(hoje.suffix(2)) ?? 0) : 0
        var gastoPorDia = [Int: Decimal]()
        var receitaPorDia = [Int: Decimal]()
        for gasto in gastos where gasto.data.hasPrefix(mes) { gastoPorDia[Int(gasto.data.suffix(2)) ?? 0, default: 0] += gasto.valor }
        for receita in receitas where receita.data.hasPrefix(mes) {
            receitaPorDia[Int(receita.data.suffix(2)) ?? 0, default: 0] += receita.valor
        }
        var serie: [Dia] = []
        var receitasAteAqui: Decimal = 0
        var gastosAteAqui: Decimal = 0
        for dia in stride(from: 1, through: ultimo, by: 1) {
            receitasAteAqui += receitaPorDia[dia] ?? 0
            gastosAteAqui += gastoPorDia[dia] ?? 0
            serie.append(Dia(dia: dia, receitas: receitaPorDia[dia] ?? 0, gastos: gastoPorDia[dia] ?? 0,
                             receitasAteAqui: receitasAteAqui, gastosAteAqui: gastosAteAqui))
        }
        return serie
    }

    /// Uma linha por semana do mes (1 a 7, 8 a 14...).
    static func semanas(_ serie: [Dia], mes: String) -> [Semana] {
        let total = diasNoMes(mes)
        return stride(from: 1, through: total, by: 7).map { de in
            let ate = min(de + 6, total)
            let dias = serie.filter { (de...ate).contains($0.dia) }
            return Semana(de: de, ate: ate, receitas: dias.reduce(0) { $0 + $1.receitas },
                          gastos: dias.reduce(0) { $0 + $1.gastos })
        }
    }
}

/// Nova nota: com valor, ela tambem lanca um gasto (e apagar a nota apaga o gasto).
private struct NovaNotaView: View {
    let data: Date
    let categorias: [String]
    let aoCriar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var titulo = ""
    @State private var texto = ""
    @State private var comData = true
    @State private var dia = Date()
    @State private var valor = ""
    @State private var categoria = ""
    @State private var salvando = false
    @State private var erro: String?

    private var viraGasto: Bool { (Formatos.lerValor(valor) ?? 0) > 0 }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Título", text: $titulo)
                        .onChange(of: titulo) { _, novo in if novo.count > 150 { titulo = String(novo.prefix(150)) } }
                    Toggle("Com data", isOn: $comData)
                    if comData {
                        DatePicker("Data", selection: $dia, displayedComponents: .date)
                    }
                    LabeledContent("Valor do evento (\(Formatos.simboloDaMoeda))") {
                        CampoDeValor(rotulo: "Valor do evento", texto: $valor, prompt: String(localized: "Deixe vazio se não for pago"))
                            .multilineTextAlignment(.trailing)
                    }
                    if viraGasto {
                        Picker("Categoria", selection: $categoria) {
                            ForEach(categorias, id: \.self) { Text($0).tag($0) }
                        }
                    }
                    TextField("Detalhes", text: $texto, axis: .vertical)
                        .lineLimit(3...6)
                        .onChange(of: texto) { _, novo in if novo.count > 1500 { texto = String(novo.prefix(1500)) } }
                } footer: {
                    if viraGasto {
                        Text("Com valor preenchido, esta nota também lança um gasto. Apagar a nota apaga o gasto junto.")
                    }
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }.listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Nova nota")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(salvando ? "Guardando..." : "Criar nota") { Task { await criar() } }.disabled(salvando)
                }
            }
            .onAppear {
                dia = data
                if categoria.isEmpty { categoria = categorias.first ?? "" }
            }
        }
    }

    private func criar() async {
        let titulo = titulo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !titulo.isEmpty else {
            erro = String(localized: "Digite o título da nota.")
            return
        }
        if viraGasto && !comData {
            erro = String(localized: "Escolha a data do evento pago.")
            return
        }
        salvando = true
        defer { salvando = false }
        let texto = texto.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            _ = try await sessao.cliente.criarNota(NovaNota(
                titulo: titulo, texto: texto, data: comData ? Formatos.dia(dia) : nil,
                valor: Formatos.lerValor(valor) ?? 0, categoria: viraGasto ? categoria : nil))
            aoCriar()
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
