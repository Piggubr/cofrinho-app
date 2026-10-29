import SwiftUI

/// Minhas metas do mes (features/minhas-metas): metas pessoais com alvo e progresso. Sao so de
/// quem criou e valem para o mes: quando ele vira, a lista comeca vazia de novo. O anel conta as
/// concluidas, nao a soma do progresso, para uma meta grande nao esconder as pequenas.
struct MinhasMetasView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[MetaDoMes]> = .carregando
    @State private var formulario: FormularioDeMeta?
    @State private var apagando: MetaDoMes?
    @State private var salvando = false
    @State private var erro: String?

    private let rotuloDoMes = Formatos.nomeDoMes(chave: Formatos.mes(Date()))

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
                conteudo(metas)
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Minhas metas")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if let metas = carga.valor, !metas.isEmpty {
                    ShareLink(item: Self.textoParaCompartilhar(metas, rotuloDoMes: rotuloDoMes)) {
                        Label("Compartilhar", systemImage: "square.and.arrow.up")
                    }
                }
            }
        }
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .sheet(item: $formulario) { formulario in
            MetaView(meta: formulario.meta) { salva in
                if case let .pronto(metas, _) = carga {
                    carga = .pronto(metas.contains { $0.id == salva.id }
                        ? metas.map { $0.id == salva.id ? salva : $0 } : metas + [salva])
                }
            }
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar a meta \"\($0.titulo)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { meta in
            Button("Apagar", role: .destructive) { Task { await excluir(meta) } }
        }
    }

    @ViewBuilder
    private func conteudo(_ metas: [MetaDoMes]) -> some View {
        let concluidas = metas.filter(\.concluida).count
        Section {
            VStack(spacing: 10) {
                Text("Seu plano do mês").font(.sora(.caption, peso: .semibold)).textCase(.uppercase)
                    .foregroundStyle(paleta.primaria)
                Text(rotuloDoMes).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                AnelDasMetas(percentual: Self.percentualConcluido(metas))
                Text("Metas para alcançar este mês").font(.sora(.headline, peso: .semibold))
                Text("\(concluidas) de \(metas.count) concluídas").font(.sora(.subheadline, peso: .medium))
                Text(Self.mensagem(metas)).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .listRowBackground(paleta.superficie)

        Section {
            if metas.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Nenhuma meta criada.")
                    Text("Crie uma meta pequena para começar.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
            }
            ForEach(metas) { linha($0) }
            Button { formulario = FormularioDeMeta(meta: nil) } label: { Label("Criar meta", systemImage: "plus") }
        } footer: {
            Text("As metas expiram automaticamente quando o mês muda.")
        }
        .listRowBackground(paleta.superficie)
    }

    private func linha(_ meta: MetaDoMes) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Label {
                        Text(meta.titulo).strikethrough(meta.concluida)
                    } icon: {
                        if meta.concluida { Image(systemName: "checkmark").foregroundStyle(paleta.sucesso) }
                    }
                    Text("\(meta.atual) de \(meta.alvo)").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
                Spacer()
                if !meta.concluida {
                    Button("+1") { Task { await avancar(meta) } }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .disabled(salvando)
                        .accessibilityLabel("Mais um em \(meta.titulo)")
                }
            }
            BarraDeProgresso(percentual: min(100, Double(meta.atual) / Double(max(meta.alvo, 1)) * 100),
                             concluido: meta.concluida)
        }
        .padding(.vertical, 4)
        .swipeActions {
            Button(role: .destructive) { apagando = meta } label: { Label("Apagar", systemImage: "trash") }
            Button { formulario = FormularioDeMeta(meta: meta) } label: { Label("Editar", systemImage: "pencil") }
                .tint(paleta.primaria)
        }
    }

    // MARK: - Regras do site

    /// Metas concluidas sobre o total, de 0 a 100.
    nonisolated static func percentualConcluido(_ metas: [MetaDoMes]) -> Int {
        metas.isEmpty ? 0 : Int((Double(metas.filter(\.concluida).count) / Double(metas.count) * 100).rounded())
    }

    nonisolated static func mensagem(_ metas: [MetaDoMes]) -> String {
        if metas.isEmpty { return String(localized: "Crie sua jornada de metas.") }
        if metas.allSatisfy(\.concluida) { return String(localized: "Você concluiu todas as metas do mês.") }
        return String(localized: "Continue no seu ritmo.")
    }

    /// So titulo e progresso, nada de dinheiro.
    nonisolated static func textoParaCompartilhar(_ metas: [MetaDoMes], rotuloDoMes: String) -> String {
        ([String(localized: "Minhas metas de \(rotuloDoMes):")]
            + metas.map { "\($0.concluida ? "✅" : "⏳") \($0.titulo): \($0.atual) de \($0.alvo)" })
            .joined(separator: "\n")
    }

    // MARK: - API

    private func carregar() async {
        await sessao.cache.carregar("metas-do-mes", mostrar: { carga = $0 }) { try await sessao.cliente.listarMetasDoMes() }
    }

    /// O atalho do dia a dia: mais um passo sem abrir o formulario.
    private func avancar(_ meta: MetaDoMes) async {
        salvando = true
        defer { salvando = false }
        do {
            let salva = try await sessao.cliente.atualizarMetaDoMes(
                meta.id, NovaMetaDoMes(titulo: meta.titulo, alvo: meta.alvo, atual: meta.atual + 1))
            if case let .pronto(metas, _) = carga { carga = .pronto(metas.map { $0.id == salva.id ? salva : $0 }) }
            erro = nil
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func excluir(_ meta: MetaDoMes) async {
        do {
            try await sessao.cliente.excluirMetaDoMes(meta.id)
            if case let .pronto(metas, _) = carga { carga = .pronto(metas.filter { $0.id != meta.id }) }
        } catch {
            erro = error.localizedDescription
        }
    }
}

struct FormularioDeMeta: Identifiable {
    let id = UUID()
    let meta: MetaDoMes?
}

/// Criar ou editar uma meta: nome, quantidade desejada e progresso atual (numeros inteiros).
private struct MetaView: View {
    let meta: MetaDoMes?
    let aoSalvar: (MetaDoMes) -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var titulo = ""
    @State private var alvo = ""
    @State private var atual = "0"
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nome da meta", text: $titulo, prompt: Text("Ex.: Ler 2 livros"))
                        .onChange(of: titulo) { _, novo in if novo.count > 80 { titulo = String(novo.prefix(80)) } }
                    LabeledContent("Quantidade desejada") {
                        TextField("Alvo", text: $alvo).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                            .accessibilityLabel("Quantidade desejada")
                    }
                    LabeledContent("Progresso atual") {
                        TextField("Atual", text: $atual).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                            .accessibilityLabel("Progresso atual")
                    }
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }.listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle(meta == nil ? "Criar meta" : "Editar meta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(salvando ? "Guardando..." : "Salvar meta") { Task { await salvar() } }.disabled(salvando)
                }
            }
            .onAppear {
                guard let meta else { return }
                titulo = meta.titulo
                alvo = String(meta.alvo)
                atual = String(meta.atual)
            }
        }
    }

    private func salvar() async {
        let titulo = titulo.trimmingCharacters(in: .whitespaces)
        guard !titulo.isEmpty else {
            erro = String(localized: "Dê um nome para a meta.")
            return
        }
        guard let alvo = Int(alvo), alvo >= 1 else {
            erro = String(localized: "A quantidade desejada precisa ser um número inteiro a partir de 1.")
            return
        }
        guard let atual = Int(atual.isEmpty ? "0" : atual), atual >= 0 else {
            erro = String(localized: "O progresso precisa ser um número inteiro, zero ou mais.")
            return
        }
        salvando = true
        defer { salvando = false }
        let pedido = NovaMetaDoMes(titulo: titulo, alvo: alvo, atual: atual)
        do {
            let salva = if let meta {
                try await sessao.cliente.atualizarMetaDoMes(meta.id, pedido)
            } else {
                try await sessao.cliente.criarMetaDoMes(pedido)
            }
            aoSalvar(salva)
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
