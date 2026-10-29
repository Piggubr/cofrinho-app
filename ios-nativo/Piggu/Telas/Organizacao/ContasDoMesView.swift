import SwiftUI

/// Contas do mes, as contas fixas (features/bills): o que se repete todo mes. "Marcar como paga"
/// lanca o gasto do mes; com o automatico ligado, o Piggu lanca sozinho no dia do vencimento.
struct ContasDoMesView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<[ContaFixa]> = .carregando
    @State private var receitasFixas: [ContaFixa] = []
    @State private var marcadas: Set<String> = []
    @State private var ocupado = false
    @State private var aviso: String?
    @State private var erro: String?
    @State private var criando = false
    @State private var apagando: ContaFixa?

    private var chave: String { Formatos.mes(mes) }

    var body: some View {
        List {
            if let aviso {
                Faixa(texto: aviso, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 4) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(contas, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                lista(contas)
            }
            if !receitasFixas.isEmpty {
                Section {
                    ForEach(receitasFixas) { fixa in
                        LinhaDoCartao(titulo: fixa.descricao,
                                      detalhe: String(localized: "Todo dia \(fixa.dia) · \(fixa.categoria)")) {
                            Text(Formatos.moeda(fixa.valor + (fixa.segundoValor ?? 0))).monospacedDigit()
                        }
                        .swipeActions {
                            Button(role: .destructive) { apagando = fixa } label: {
                                Label("Parar de repetir", systemImage: "stop.circle")
                            }
                        }
                    }
                } header: {
                    Text("Receitas que entram todo mês")
                } footer: {
                    Text("As mesmas de Receitas. Nova receita fixa: em Receitas, marque \"Entra sozinha todo mês\".")
                }
                .listRowBackground(paleta.superficie)
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .comSeletorDeMes($mes)
        .navigationTitle("Contas do mês")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { criando = true } label: { Label("Nova conta fixa", systemImage: "plus") }
            }
        }
        .refreshable { await carregar() }
        .task(id: "\(chave)#\(rede.conectado)") { await carregar() }
        .sheet(isPresented: $criando) {
            NovaContaFixaView {
                aviso = String(localized: "Conta fixa criada.")
                Task { await carregar() }
            }
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar a conta fixa \"\($0.descricao)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { conta in
            Button("Apagar", role: .destructive) {
                Task { await executar(String(localized: "Conta fixa apagada.")) { try await $0.excluirContaFixa(conta.id) } }
            }
        } message: { _ in
            Text("Os gastos já lançados continuam.")
        }
    }

    @ViewBuilder
    private func lista(_ contas: [ContaFixa]) -> some View {
        let emAberto = contas.filter { $0.situacao != .paga }
        Section {
            if contas.isEmpty {
                EstadoVazio(icone: "doc.text", titulo: "Nenhuma conta fixa ainda",
                            texto: "Aluguel, luz, internet: cadastre uma vez e o Piggu lembra todo mês.",
                            acao: ("Cadastrar a primeira conta", { criando = true }))
                    .listRowBackground(Color.clear)
            }
            if emAberto.count > 1 {
                let todas = emAberto.allSatisfy { marcadas.contains($0.id) }
                let total = emAberto.filter { marcadas.contains($0.id) }.reduce(Decimal(0)) { $0 + $1.valor }
                LadoALado {
                    HStack {
                        Caixa(marcada: todas, rotulo: String(localized: "Todas em aberto")) {
                            marcadas = todas ? [] : Set(emAberto.map(\.id))
                        }
                        Text("Todas em aberto").font(.sora(.subheadline))
                    }
                    Spacer()
                    Button("Pagar \(marcadas.count) · \(Formatos.moeda(total))") { Task { await pagarMarcadas(emAberto) } }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .disabled(ocupado || marcadas.isEmpty)
                }
            }
            ForEach(contas) { conta in
                linha(conta, podeMarcar: conta.situacao != .paga && emAberto.count > 1)
            }
        } header: {
            Text("Em aberto: \(Formatos.moeda(emAberto.reduce(0) { $0 + $1.valor }))").monospacedDigit()
        }
        .listRowBackground(paleta.superficie)
    }

    private func linha(_ conta: ContaFixa, podeMarcar: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                if podeMarcar {
                    Caixa(marcada: marcadas.contains(conta.id), rotulo: conta.descricao) {
                        if marcadas.contains(conta.id) { marcadas.remove(conta.id) } else { marcadas.insert(conta.id) }
                    }
                }
                LadoALado {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(conta.descricao)
                        Text([Formatos.dataCurta(conta.vencimento), conta.categoria,
                              conta.automatico ? String(localized: "lança sozinha") : nil]
                            .compactMap { $0 }.joined(separator: " · "))
                            .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                    Spacer()
                    Text(Formatos.moeda(conta.valor)).font(.sora(.body, peso: .semibold)).monospacedDigit()
                }
            }
            LadoALado {
                situacao(conta.situacao)
                Spacer()
                if conta.situacao != .paga {
                    Button("Marcar como paga") { Task { await pagar(conta) } }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .disabled(ocupado)
                }
            }
        }
        .swipeActions {
            Button(role: .destructive) { apagando = conta } label: { Label("Apagar", systemImage: "trash") }
        }
        .swipeActions(edge: .leading) {
            if conta.situacao != .paga {
                Button { Task { await pagar(conta) } } label: { Label("Paga", systemImage: "checkmark") }
                    .tint(paleta.sucesso)
            }
        }
    }

    private func situacao(_ situacao: ContaFixa.Situacao) -> some View {
        let (texto, cor, fundo): (LocalizedStringKey, Color, Color) = switch situacao {
        case .paga: ("Paga", paleta.sucesso, paleta.sucessoSuave)
        case .vencida: ("Vencida", paleta.perigo, paleta.perigoSuave)
        case .pendente: ("A vencer", paleta.texto, paleta.suave)
        }
        return Text(texto)
            .font(.sora(.caption, peso: .semibold))
            .foregroundStyle(cor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(fundo, in: Capsule())
    }

    // MARK: - API

    private func carregar() async {
        marcadas = []
        let cliente = sessao.cliente
        let mes = chave
        async let receitas = try? cliente.receitasRecorrentes(mes)
        await sessao.cache.carregar("contas-do-mes/\(mes)", mostrar: { carga = $0 }) {
            try await cliente.contasFixas(mes)
        }
        receitasFixas = await receitas ?? receitasFixas
    }

    private func pagar(_ conta: ContaFixa) async {
        let mes = chave
        await executar(String(localized: "Conta lançada nos gastos.")) { try await $0.pagarContaFixa(conta.id, mes: mes) }
    }

    /// Lanca uma por vez; se uma falhar, as seguintes ficam para depois e a lista mostra o que entrou.
    private func pagarMarcadas(_ emAberto: [ContaFixa]) async {
        let ids = emAberto.map(\.id).filter(marcadas.contains)
        let mes = chave
        await executar(String(localized: "\(ids.count) contas lançadas nos gastos.")) { cliente in
            for id in ids { try await cliente.pagarContaFixa(id, mes: mes) }
        }
    }

    private func executar(_ mensagem: String, _ acao: (ClienteDaAPI) async throws -> Void) async {
        ocupado = true
        erro = nil
        aviso = nil
        defer { ocupado = false }
        do {
            try await acao(sessao.cliente)
            aviso = mensagem
        } catch {
            self.erro = error.localizedDescription
        }
        await carregar()
    }
}

/// Nova conta fixa: descricao, categoria, valor, dia do vencimento e se lanca sozinha.
private struct NovaContaFixaView: View {
    let aoCriar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var descricao = ""
    @State private var categoria = ""
    @State private var categorias: [String] = []
    @State private var valor = ""
    @State private var dia = "10"
    @State private var automatico = false
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Descrição", text: $descricao, prompt: Text("Aluguel"))
                        .onChange(of: descricao) { _, novo in if novo.count > 200 { descricao = String(novo.prefix(200)) } }
                    Picker("Categoria", selection: $categoria) {
                        ForEach(categorias, id: \.self) { Text($0).tag($0) }
                    }
                    LabeledContent("Valor") {
                        CampoDeValor(rotulo: "Valor", texto: $valor).multilineTextAlignment(.trailing)
                    }
                    LabeledContent("Dia do vencimento") {
                        TextField("Dia", text: $dia).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                            .accessibilityLabel("Dia do vencimento")
                    }
                    Toggle("Lançar sozinha no dia do vencimento", isOn: $automatico)
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }.listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Nova conta fixa")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Criar conta fixa") { Task { await criar() } }.disabled(salvando)
                }
            }
            .task {
                categorias = (try? await sessao.cliente.listarCategorias()) ?? []
                if categoria.isEmpty { categoria = categorias.first ?? "" }
            }
        }
    }

    private func criar() async {
        let descricao = descricao.trimmingCharacters(in: .whitespaces)
        guard !descricao.isEmpty, let valor = Formatos.lerValor(valor), valor > 0,
              let dia = Int(dia), (1...31).contains(dia) else {
            erro = String(localized: "Preencha descrição, valor e um dia de vencimento entre 1 e 31.")
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.cliente.criarContaFixa(NovaContaFixa(
                descricao: descricao, categoria: categoria, valor: valor, dia: dia, automatico: automatico))
            aoCriar()
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}

/// Caixa de marcar (o checkbox do site): so o icone; o nome vai para o VoiceOver.
private struct Caixa: View {
    let marcada: Bool
    let rotulo: String
    let alternar: () -> Void

    var body: some View {
        Button(action: alternar) {
            Image(systemName: marcada ? "checkmark.square.fill" : "square")
                .font(.title3)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(rotulo)
        .accessibilityAddTraits(marcada ? .isSelected : [])
    }
}
