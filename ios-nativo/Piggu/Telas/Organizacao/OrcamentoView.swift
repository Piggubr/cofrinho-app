import SwiftUI

/// Orcamento (features/budgets e o goals dentro dela): o limite total do mes (gratis) e o limite
/// por categoria (Premium), com alerta em 80% e 100%. Ver e apagar o que ja existe segue livre.
struct OrcamentoView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[String: Decimal]> = .carregando
    @State private var gastoDoMes: Decimal = 0
    @State private var orcamentos: [Orcamento] = []
    @State private var categorias: [String] = []
    @State private var mesDoLimite = Formatos.mes(Date())
    @State private var limite = ""
    @State private var categoria = ""
    @State private var limiteDaCategoria = ""
    @State private var salvando = false
    @State private var erro: String?
    @State private var editandoLimite: LimiteEmEdicao?

    private let mesAtual = Formatos.mes(Date())
    private var ehPremium: Bool { sessao.usuario?.plano == .premium }

    var body: some View {
        List {
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 3) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(metas, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                limiteDoMes(metas)
                definirLimite(metas)
                outrosMeses(metas)
                porCategoria
                novoPorCategoria
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Orçamento")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .sheet(item: $editandoLimite) { edicao in
            EditarLimiteView(edicao: edicao) { valor in try await salvar(valor, em: edicao.alvo) }
        }
    }

    // MARK: - Limite do mes (goals)

    @ViewBuilder
    private func limiteDoMes(_ metas: [String: Decimal]) -> some View {
        let meta = metas[mesAtual] ?? 0
        Section("Limite do mês") {
            if meta > 0 {
                let progresso = min(100, Int((gastoDoMes / meta * 100).paraDesenho.rounded()))
                let estourou = gastoDoMes > meta
                Text("\(Formatos.moeda(gastoDoMes)) de \(Formatos.moeda(meta))")
                    .font(.sora(.title3, peso: .semibold)).monospacedDigit()
                BarraDeProgresso(percentual: Double(progresso), estourado: estourou)
                Group {
                    if estourou {
                        Text("\(progresso)% usado · **limite ultrapassado**")
                    } else {
                        Text("\(progresso)% usado")
                    }
                }
                .font(.sora(.footnote))
                .foregroundStyle(estourou ? paleta.perigo : paleta.textoSuave)
            } else {
                EstadoVazio(icone: "target", titulo: "Nenhum limite definido para este mês ainda",
                            texto: "Com um limite, o Piggu avisa quando o mês estiver chegando perto.")
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private func definirLimite(_ metas: [String: Decimal]) -> some View {
        Section("Definir o limite") {
            EscolhaDeMes(titulo: "Mês", mes: $mesDoLimite)
                .onChange(of: mesDoLimite) { _, novo in
                    limite = metas[novo].map(Formatos.valorParaDigitar) ?? ""
                }
            LabeledContent("Total no mês (\(Formatos.simboloDaMoeda))") {
                CampoDeValor(rotulo: "Total no mês", texto: $limite).multilineTextAlignment(.trailing)
            }
            Button(salvando ? "Guardando..." : "Salvar limite") { Task { await salvarLimite() } }
                .disabled(salvando)
        }
        .listRowBackground(paleta.superficie)
    }

    private func outrosMeses(_ metas: [String: Decimal]) -> some View {
        Section("Limites dos outros meses") {
            if metas.isEmpty {
                Text("Nenhum limite cadastrado").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(metas.sorted { $0.key > $1.key }, id: \.key) { mes, valor in
                Button {
                    editandoLimite = LimiteEmEdicao(alvo: .mes(mes), titulo: Formatos.inicialMaiuscula(Formatos.nomeDoMes(chave: mes)),
                                                    rotulo: "Total no mês", valor: valor)
                } label: {
                    LabeledContent(Formatos.nomeDoMes(chave: mes)) {
                        Text(Formatos.moeda(valor)).monospacedDigit()
                    }
                }
                .tint(paleta.texto)
                .accessibilityHint("Toque para editar")
            }
        }
        .listRowBackground(paleta.superficie)
    }

    // MARK: - Por categoria

    private var porCategoria: some View {
        Section("Por categoria · \(Formatos.nomeDoMes(chave: mesAtual))") {
            if orcamentos.isEmpty {
                EstadoVazio(icone: "chart.pie", titulo: "Nenhuma categoria com limite ainda",
                            texto: "Escolha uma categoria, como mercado ou lazer, e quanto quer gastar nela.")
            }
            ForEach(orcamentos) { orcamento in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(orcamento.categoria)
                        Spacer()
                        if ehPremium {
                            Button { editar(orcamento) } label: { Image(systemName: "pencil") }
                                .buttonStyle(.borderless)
                                .accessibilityLabel("Mudar")
                        }
                    }
                    Text("\(Formatos.moeda(orcamento.gasto)) de \(Formatos.moeda(orcamento.limite)) · \(Formatos.porcentagem(orcamento.percentual))")
                        .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave).monospacedDigit()
                    BarraDeProgresso(percentual: min(100, orcamento.percentual.paraDesenho),
                                     estourado: orcamento.alerta == .estourou)
                        .accessibilityElement()
                        .accessibilityLabel(orcamento.categoria)
                        .accessibilityValue(Formatos.porcentagem(orcamento.percentual))
                }
                // A linha toda abre a edicao, nao so a canetinha.
                .contentShape(Rectangle())
                .onTapGesture { if ehPremium { editar(orcamento) } }
                .swipeActions {
                    Button(role: .destructive) { Task { await excluir(orcamento) } } label: {
                        Label("Apagar", systemImage: "trash")
                    }
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private var novoPorCategoria: some View {
        Section {
            if ehPremium {
                Picker("Categoria", selection: $categoria) {
                    Text("Escolha").tag("")
                    ForEach(categorias, id: \.self) { Text($0).tag($0) }
                }
                LabeledContent("Limite no mês") {
                    CampoDeValor(rotulo: "Limite no mês", texto: $limiteDaCategoria).multilineTextAlignment(.trailing)
                }
                Button("Salvar categoria") { Task { await salvarCategoria() } }.disabled(salvando)
            } else {
                Text("Defina um limite por categoria e receba aviso em 80% e 100%. Faz parte do Piggu Premium.")
                    .font(.sora(.footnote))
                LinkDoPremium(titulo: "Piggu Premium")
            }
        } header: {
            HStack {
                Text("Limite por categoria")
                Spacer()
                Label("Premium", systemImage: "star.fill").font(.sora(.caption, peso: .semibold))
            }
        } footer: {
            if ehPremium {
                Text("Você recebe um aviso quando a categoria chega a 80% e a 100% do limite no mês.")
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private func editar(_ orcamento: Orcamento) {
        editandoLimite = LimiteEmEdicao(alvo: .categoria(orcamento.categoria), titulo: orcamento.categoria,
                                        rotulo: "Limite no mês", valor: orcamento.limite)
    }

    // MARK: - API

    /// O que a folha de editar guardou; erro volta para ela mostrar.
    private func salvar(_ valor: Decimal, em alvo: LimiteEmEdicao.Alvo) async throws {
        switch alvo {
        case let .categoria(nome):
            try await sessao.cliente.definirOrcamento(categoria: nome, limite: valor)
            orcamentos = (try? await sessao.cliente.orcamentos(mesAtual)) ?? orcamentos
        case let .mes(mes):
            carga = .pronto(try await sessao.cliente.definirMeta(mes: mes, limite: valor))
            if mes == mesDoLimite { limite = Formatos.valorParaDigitar(valor) }
        }
    }

    private func carregar() async {
        let cliente = sessao.cliente
        let mes = mesAtual
        async let gastos = try? cliente.listarGastos(mes: mes)
        async let orcamentos = try? cliente.orcamentos(mes)
        async let categorias = try? cliente.listarCategorias()
        await sessao.cache.carregar("metas", mostrar: { carga = $0 }) { try await cliente.listarMetas() }
        gastoDoMes = (await gastos)?.reduce(0) { $0 + $1.valor } ?? gastoDoMes
        self.orcamentos = await orcamentos ?? self.orcamentos
        self.categorias = await categorias ?? self.categorias
        if limite.isEmpty, let meta = carga.valor?[mesDoLimite] {
            limite = Formatos.valorParaDigitar(meta)
        }
    }

    private func salvarLimite() async {
        guard let valor = Formatos.lerValor(limite), valor > 0 else {
            erro = String(localized: "Digite um valor válido para o limite.")
            return
        }
        salvando = true
        erro = nil
        defer { salvando = false }
        do {
            carga = .pronto(try await sessao.cliente.definirMeta(mes: mesDoLimite, limite: valor))
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func salvarCategoria() async {
        guard !categoria.isEmpty, let valor = Formatos.lerValor(limiteDaCategoria), valor > 0 else {
            erro = String(localized: "Escolha a categoria e um limite maior que zero.")
            return
        }
        salvando = true
        erro = nil
        defer { salvando = false }
        do {
            try await sessao.cliente.definirOrcamento(categoria: categoria, limite: valor)
            categoria = ""
            limiteDaCategoria = ""
            orcamentos = (try? await sessao.cliente.orcamentos(mesAtual)) ?? orcamentos
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func excluir(_ orcamento: Orcamento) async {
        do {
            try await sessao.cliente.excluirOrcamento(orcamento.id)
            orcamentos.removeAll { $0.id == orcamento.id }
        } catch {
            erro = error.localizedDescription
        }
    }
}

/// O limite que a folha esta mudando: o de uma categoria ou o total de um mes.
struct LimiteEmEdicao: Identifiable {
    enum Alvo { case categoria(String), mes(String) }
    let alvo: Alvo
    let titulo: String
    let rotulo: LocalizedStringKey
    let valor: Decimal
    var id: String { titulo }
}

/// Folha para mudar um limite que ja existe, aberta pela canetinha ou tocando na linha.
private struct EditarLimiteView: View {
    let edicao: LimiteEmEdicao
    let salvar: (Decimal) async throws -> Void

    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var texto: String
    @State private var salvando = false
    @State private var erro: String?

    init(edicao: LimiteEmEdicao, salvar: @escaping (Decimal) async throws -> Void) {
        self.edicao = edicao
        self.salvar = salvar
        _texto = State(initialValue: Formatos.valorParaDigitar(edicao.valor))
    }

    var body: some View {
        NavigationStack {
            Form {
                if let erro {
                    Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                LabeledContent(edicao.rotulo) {
                    CampoDeValor(rotulo: edicao.rotulo, texto: $texto).multilineTextAlignment(.trailing)
                }
                .listRowBackground(paleta.superficie)
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle(edicao.titulo)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { fechar() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(salvando ? "Guardando..." : "Salvar") { Task { await guardar() } }
                        .disabled(salvando)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func guardar() async {
        guard let valor = Formatos.lerValor(texto), valor > 0 else {
            erro = String(localized: "Digite um valor válido para o limite.")
            return
        }
        salvando = true
        erro = nil
        defer { salvando = false }
        do {
            try await salvar(valor)
            fechar()
        } catch {
            erro = error.localizedDescription
        }
    }
}
