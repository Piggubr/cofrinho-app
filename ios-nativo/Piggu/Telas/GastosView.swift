import SwiftUI
import TipKit

/// Gastos do mes (features/expenses): lista por dia, busca, filtros por categoria e forma de
/// pagamento, editar, apagar (com as parcelas seguintes), divisao do mes e regras de categoria.
/// Lancar fica no + (o mesmo formulario do site).
struct GastosView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void
    /// Gasto achado no Spotlight: a tela volta ao mes atual e abre a edicao dele.
    @Binding var abrirGasto: String?
    /// Editou ou apagou: as outras abas recarregam.
    let aoMudar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<[Gasto]> = .carregando
    @State private var busca = ""
    @State private var deOutrosMeses: [Gasto] = []
    @State private var filtroDeCategoria: String?
    @State private var filtroDePagamento = FiltroDePagamento.todas
    @State private var categorias: [String] = []
    @State private var contas: [ContaOuCartao] = []
    @State private var membros: [MembroDaFamilia] = []
    @State private var regras: [RegraDeCategoria] = []
    @State private var acerto: [AcertoDeDivisao] = []
    @State private var transferencias: [TransferenciaDoAcerto] = []
    @State private var editando: Gasto?
    @State private var apagando: Gasto?
    @State private var sugestao: RegraSugerida?
    @State private var aviso: String?
    @State private var erro: String?
    @State private var termoDaRegra = ""
    @State private var categoriaDaRegra = ""

    var body: some View {
        NavigationStack {
            List {
                TipView(Dicas.Busca()).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                avisos
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
                    lista(gastos)
                    extras
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .safeAreaInset(edge: .top) {
                VStack(spacing: 4) {
                    SeletorDeMes(mes: $mes)
                    filtros
                }
            }
            .navigationTitle("Gastos")
            .searchable(text: $busca, prompt: Text("Buscar gastos"))
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)#\(rede.conectado)") { await carregar() }
            .task(id: busca) { await buscarEmOutrosMeses() }
            .onChange(of: abrirGasto, initial: true) { _, _ in mostrarGastoPedido() }
            .onChange(of: carga.valor) { _, _ in mostrarGastoPedido() }
            .sheet(item: $editando) { gasto in
                EditarGastoView(gasto: gasto, categorias: $categorias,
                                podeCompartilhar: ehMeu(gasto) && membros.count > 1) { salvo in
                    if salvo.categoria != gasto.categoria {
                        sugestao = RegraSugerida(termo: salvo.item, categoria: salvo.categoria)
                    }
                    aoMudar()
                }
            }
            .confirmationDialog(apagando.map { String(localized: "Apagar \"\($0.item)\"?") } ?? "",
                                isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                                titleVisibility: .visible, presenting: apagando) { gasto in
                let seguintes = (gasto.parcelas ?? 0) - (gasto.parcela ?? 0)
                if gasto.parcela != nil, seguintes > 0 {
                    Button("Apagar só esta", role: .destructive) { Task { await apagar(gasto, eAsProximas: false) } }
                    Button("Apagar esta e as \(seguintes) próximas", role: .destructive) {
                        Task { await apagar(gasto, eAsProximas: true) }
                    }
                } else {
                    Button("Apagar", role: .destructive) { Task { await apagar(gasto, eAsProximas: false) } }
                }
            } message: { gasto in
                if let parcela = gasto.parcela, let parcelas = gasto.parcelas, parcelas > parcela {
                    Text("Esta é a parcela \(parcela) de \(parcelas).")
                }
            }
        }
    }

    // MARK: - Partes da lista

    @ViewBuilder
    private var avisos: some View {
        if let aviso {
            Faixa(texto: aviso, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
        if let erro {
            Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
        if sessao.fila.pendentes > 0 {
            HStack {
                Label(sessao.fila.pendentes == 1
                      ? String(localized: "1 gasto esperando conexão")
                      : String(localized: "\(sessao.fila.pendentes) gastos esperando conexão"),
                      systemImage: "icloud.and.arrow.up")
                Spacer()
                Button("Enviar agora") {
                    Task {
                        if await sessao.fila.enviar() > 0 { aoMudar() }
                    }
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            .font(.sora(.footnote))
            .listRowBackground(paleta.alertaSuave)
        }
        if sessao.fila.recusados > 0 {
            Faixa(texto: String(localized: "\(sessao.fila.recusados) gasto(s) guardado(s) sem conexão foram recusados pelo servidor; lance de novo."),
                  tipo: .erro)
                .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
        if let sugestao {
            VStack(alignment: .leading, spacing: 8) {
                Text("Sempre usar \(sugestao.categoria) para \"\(sugestao.termo)\"?")
                HStack {
                    Button("Criar regra") { Task { await criarRegra(sugestao.termo, sugestao.categoria) } }
                        .buttonStyle(.bordered)
                    Button("Agora não") { self.sugestao = nil }
                        .buttonStyle(.borderless)
                }
            }
            .font(.sora(.footnote))
            .listRowBackground(paleta.sucessoSuave)
        }
    }

    @ViewBuilder
    private func lista(_ gastos: [Gasto]) -> some View {
        let filtrados = gastos.filter(passaNosFiltros)
        if gastos.isEmpty {
            Section {
                EstadoVazio(icone: "doc.text", titulo: "Nenhum gasto neste mês",
                            texto: "Lance o primeiro gasto ou traga o extrato do banco de uma vez.",
                            acao: ("Lançar um gasto", aoLancar))
                LinkDoSite(titulo: "Importar extrato", caminho: "/gastos#importar")
                    .frame(maxWidth: .infinity)
            }
            .listRowBackground(Color.clear)
        } else {
            Section {
                HStack {
                    Text(filtrando ? "Total filtrado" : "Total do mês")
                    Spacer()
                    Text(Formatos.moeda(filtrados.reduce(0) { $0 + $1.valor }))
                        .font(.sora(.body, peso: .semibold))
                        .monospacedDigit()
                }
                .accessibilityElement(children: .combine)
                if filtrando {
                    Button("Limpar filtros") {
                        busca = ""
                        filtroDeCategoria = nil
                        filtroDePagamento = .todas
                    }
                    .font(.sora(.footnote))
                }
            }
            .listRowBackground(paleta.superficie)

            if filtrados.isEmpty {
                ContentUnavailableView.search(text: busca)
                    .listRowBackground(Color.clear)
            }
            let porDia = Dictionary(grouping: filtrados, by: \.data).sorted { $0.key > $1.key }
            ForEach(porDia, id: \.key) { dia, doDia in
                Section(Formatos.inicialMaiuscula(Formatos.diaPorExtenso(dia))) {
                    ForEach(doDia) { linha($0, comData: false) }
                }
                .listRowBackground(paleta.superficie)
            }
        }
    }

    private func linha(_ gasto: Gasto, comData: Bool) -> some View {
        Button { editando = gasto } label: {
            LinhaDeGasto(gasto: gasto, comData: comData,
                         dono: !ehMeu(gasto) && membros.count > 1 ? PessoasDaFamilia.nome(gasto.usuario, em: membros) : nil)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            if gasto.compartilhado != true || ehMeu(gasto) {
                Button(role: .destructive) { apagando = gasto } label: { Label("Apagar", systemImage: "trash") }
            }
            Button { editando = gasto } label: { Label("Editar", systemImage: "pencil") }
                .tint(paleta.primaria)
        }
        .accessibilityHint("Toque para editar")
    }

    @ViewBuilder
    private var extras: some View {
        if !busca.isEmpty, !deOutrosMeses.isEmpty {
            Section("Em outros meses") {
                ForEach(deOutrosMeses) { linha($0, comData: true) }
            }
            .listRowBackground(paleta.superficie)
        }

        if !acerto.isEmpty {
            Section("Divisão do mês") {
                ForEach(Array(transferencias.enumerated()), id: \.offset) { _, transferencia in
                    Text("\(nome(transferencia.de)) paga \(Formatos.moeda(transferencia.valor)) para \(nome(transferencia.para)).")
                        .font(.sora(.subheadline, peso: .medium))
                }
                ForEach(Array(acerto.enumerated()), id: \.offset) { _, pessoa in
                    LinhaDoCartao(titulo: nome(pessoa.pessoa),
                                  detalhe: String(localized: "Pagou \(Formatos.moeda(pessoa.pagou)) · parte \(Formatos.moeda(pessoa.parte))")) {
                        Group {
                            if pessoa.saldo > 0 {
                                Text("recebe \(Formatos.moeda(pessoa.saldo))")
                            } else if pessoa.saldo < 0 {
                                Text("deve \(Formatos.moeda(-pessoa.saldo))")
                            } else {
                                Text("quite")
                            }
                        }
                        .monospacedDigit()
                    }
                }
            }
            .listRowBackground(paleta.superficie)
        }

        Section {
            ForEach(regras) { regra in
                Text("\(regra.termo) → \(regra.categoria)")
                    .swipeActions {
                        Button(role: .destructive) { Task { await excluirRegra(regra) } } label: {
                            Label("Apagar", systemImage: "trash")
                        }
                    }
            }
            if regras.isEmpty {
                Text("Nenhuma regra ainda. Ao corrigir a categoria de um gasto, o Piggu sugere uma.")
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
            }
            TextField("Quando tiver", text: $termoDaRegra)
                .onChange(of: termoDaRegra) { _, novo in if novo.count > 100 { termoDaRegra = String(novo.prefix(100)) } }
            Picker("Usar", selection: $categoriaDaRegra) {
                ForEach(categorias, id: \.self) { Text($0).tag($0) }
            }
            Button("Salvar regra") { Task { await criarRegra(termoDaRegra, categoriaDaRegra) } }
        } header: {
            Text("Regras de categoria")
        } footer: {
            Text("Com a categoria em \"Automática\", o Piggu usa estas regras pelo nome do item ou do estabelecimento, e depois a categoria da última compra do mesmo produto. Também valem para as notas lidas por foto.")
        }
        .listRowBackground(paleta.superficie)
    }

    /// Filtros em vidro, flutuando sobre a lista abaixo do seletor de mes.
    private var filtros: some View {
        GrupoDeVidro(espaco: 8) {
            Menu {
                Picker("Categoria", selection: $filtroDeCategoria) {
                    Text("Todas as categorias").tag(String?.none)
                    ForEach(categorias, id: \.self) { Text($0).tag(String?.some($0)) }
                }
            } label: {
                chip(filtroDeCategoria ?? String(localized: "Categoria"), ativo: filtroDeCategoria != nil, icone: "tag")
            }
            .accessibilityLabel("Filtrar por categoria")
            Menu {
                Picker("Forma de pagamento", selection: $filtroDePagamento) {
                    ForEach(FiltroDePagamento.allCases, id: \.self) { Text($0.nome).tag($0) }
                }
            } label: {
                chip(filtroDePagamento == .todas ? String(localized: "Pagamento") : String(localized: filtroDePagamento.nome),
                     ativo: filtroDePagamento != .todas, icone: "creditcard")
            }
            .accessibilityLabel("Filtrar por forma de pagamento")
        }
        .padding(.bottom, 4)
    }

    private func chip(_ texto: String, ativo: Bool, icone: String) -> some View {
        Label(texto, systemImage: ativo ? "\(icone).fill" : icone)
            .font(.sora(.footnote, peso: ativo ? .semibold : .regular))
            .lineLimit(1)
            .foregroundStyle(ativo ? paleta.primaria : paleta.texto)
            .padding(.horizontal, 14)
            .frame(minHeight: 36)
            .pigguVidro()
    }

    // MARK: - Regras de exibicao

    private var filtrando: Bool {
        !busca.isEmpty || filtroDeCategoria != nil || filtroDePagamento != .todas
    }

    private func passaNosFiltros(_ gasto: Gasto) -> Bool {
        let termo = busca.trimmingCharacters(in: .whitespaces)
        if !termo.isEmpty {
            let texto = [gasto.item, gasto.estabelecimento ?? "", gasto.categoria].joined(separator: " ")
            guard texto.range(of: termo, options: [.caseInsensitive, .diacriticInsensitive]) != nil else { return false }
        }
        if let filtroDeCategoria, gasto.categoria != filtroDeCategoria { return false }
        let tipo = contas.first { $0.id == gasto.contaId }?.tipo
        switch filtroDePagamento {
        case .todas: return true
        case .cartao: return tipo == .cartao
        case .debito: return tipo == .conta
        case .semConta: return tipo == nil
        }
    }

    /// Do compartilhado, so quem lancou apaga ou volta para "so meu".
    private func ehMeu(_ gasto: Gasto) -> Bool { gasto.usuario == sessao.usuario?.id }
    private func nome(_ pessoa: String?) -> String { PessoasDaFamilia.nome(pessoa, em: membros) }

    private func mostrarGastoPedido() {
        guard let id = abrirGasto else { return }
        if Formatos.mes(mes) != Formatos.mes(Date()) {
            mes = Date()
            return
        }
        if let gasto = carga.valor?.first(where: { $0.id == id }) {
            editando = gasto
            abrirGasto = nil
        }
    }

    // MARK: - API

    private func carregar() async {
        let mesDaTela = Formatos.mes(mes)
        let cliente = sessao.cliente
        async let categorias = try? cliente.listarCategorias()
        async let contas = try? cliente.contas()
        async let membros = try? cliente.verFamilia().membros
        async let regras = try? cliente.regrasDeCategoria()
        async let acerto = try? cliente.acertoDoMes(mesDaTela)
        await sessao.cache.carregar("gastos/\(mesDaTela)", mostrar: { carga = $0 }) {
            try await cliente.listarGastos(mes: mesDaTela)
        }
        if let lista = await categorias {
            self.categorias = lista
            if categoriaDaRegra.isEmpty { categoriaDaRegra = lista.first ?? "" }
        }
        self.contas = await contas ?? self.contas
        self.membros = await membros ?? self.membros
        self.regras = await regras ?? self.regras
        self.acerto = await acerto ?? []
        // Sem gasto dividido no mes nao ha acerto: poupa o pedido de quem paga quem.
        transferencias = self.acerto.isEmpty ? [] : (try? await cliente.quemDeveAQuem(mesDaTela)) ?? []
    }

    /// A busca olha tambem os outros meses (/expenses/search), como a busca do topo do site.
    private func buscarEmOutrosMeses() async {
        let termo = busca.trimmingCharacters(in: .whitespaces)
        guard termo.count >= 2 else {
            deOutrosMeses = []
            return
        }
        try? await Task.sleep(for: .milliseconds(350))
        guard !Task.isCancelled else { return }
        let mesDaTela = Formatos.mes(mes)
        deOutrosMeses = ((try? await sessao.cliente.buscarGastos(termo)) ?? []).filter { !$0.data.hasPrefix(mesDaTela) }
    }

    private func apagar(_ gasto: Gasto, eAsProximas: Bool) async {
        do {
            try await sessao.cliente.excluirGasto(gasto.id, eAsProximas: eAsProximas)
            erro = nil
            aoMudar()
        } catch {
            erro = error.localizedDescription
        }
    }

    private func criarRegra(_ termo: String, _ categoria: String) async {
        let termo = termo.trimmingCharacters(in: .whitespaces)
        guard !termo.isEmpty, !categoria.isEmpty else {
            erro = String(localized: "Digite o termo e escolha a categoria.")
            return
        }
        do {
            let regra = try await sessao.cliente.definirRegra(termo: termo, categoria: categoria)
            sugestao = nil
            termoDaRegra = ""
            erro = nil
            aviso = String(localized: "Regra salva: \"\(regra.termo)\" vai para \(regra.categoria).")
            regras = (try? await sessao.cliente.regrasDeCategoria()) ?? regras
        } catch {
            erro = error.localizedDescription
        }
    }

    private func excluirRegra(_ regra: RegraDeCategoria) async {
        do {
            try await sessao.cliente.excluirRegra(regra.id)
            regras.removeAll { $0.id == regra.id }
        } catch {
            erro = error.localizedDescription
        }
    }
}

struct RegraSugerida: Equatable {
    let termo: String
    let categoria: String
}

enum FiltroDePagamento: CaseIterable, Hashable {
    case todas, cartao, debito, semConta

    var nome: LocalizedStringResource {
        switch self {
        case .todas: "Todas as formas"
        case .cartao: "Cartão de crédito"
        case .debito: "Débito/Pix"
        case .semConta: "Sem conta informada"
        }
    }
}

/// Uma linha da lista, com o detalhe do site: categoria, parcela, estabelecimento, origem,
/// valor na moeda da compra, compartilhado e de quem e.
private struct LinhaDeGasto: View {
    let gasto: Gasto
    let comData: Bool
    let dono: String?

    @Environment(\.paleta) private var paleta

    private var detalhe: String {
        var partes: [String] = []
        if comData { partes.append(Formatos.dataCurta(gasto.data)) }
        partes.append(gasto.categoria)
        if let parcela = gasto.parcela, let parcelas = gasto.parcelas {
            partes.append(String(localized: "parcela \(parcela)/\(parcelas)"))
        }
        if let estabelecimento = gasto.estabelecimento, !estabelecimento.isEmpty {
            partes.append(estabelecimento)
        }
        if let origem = gasto.origem, !origem.isEmpty, origem != "Manual" {
            partes.append(origem)
        }
        if let moeda = gasto.moedaOriginal, let original = gasto.valorOriginal {
            partes.append(Formatos.moeda(original, codigo: moeda))
        }
        if gasto.compartilhado == true {
            partes.append(String(localized: "compartilhado"))
        }
        if let dono {
            partes.append(String(localized: "de \(dono)"))
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
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
