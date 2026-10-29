import SwiftUI

/// Premios (features/rewards): as Fofocoins e os premios da familia. Ajustar o saldo e cuidar dos
/// premios e de quem cuida deles (titular, admin e parceiro); resgatar e de quem tem as moedas. O
/// backend recusa de qualquer jeito, mas a tela nao oferece o que sempre daria erro.
struct PremiosView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<DadosDosPremios> = .carregando
    @State private var historico = false
    @State private var ajuste = ""
    @State private var motivo = ""
    @State private var nome = ""
    @State private var descricao = ""
    @State private var preco = ""
    @State private var resgatando: Premio?
    @State private var removendo: Premio?
    @State private var aviso: String?
    @State private var erro: String?

    /// Titular (ou admin) e parceiro.
    private var cuidaDosPremios: Bool {
        [.titular, .admin, .parceiro].contains(sessao.usuario?.role)
    }

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
            case let .pronto(dados, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                moedas(dados.saldo)
                if cuidaDosPremios { novoPremio }
                disponiveis(dados)
                resgates(dados.resgates)
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Prêmios")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .confirmationDialog(resgatando.map { String(localized: "Resgatar \"\($0.nome)\" por \($0.preco) Fofocoins?") } ?? "",
                            isPresented: Binding(get: { resgatando != nil }, set: { if !$0 { resgatando = nil } }),
                            titleVisibility: .visible, presenting: resgatando) { premio in
            Button("Resgatar") { Task { await resgatar(premio) } }
        }
        .confirmationDialog(removendo.map { String(localized: "Tirar \"\($0.nome)\" da lista?") } ?? "",
                            isPresented: Binding(get: { removendo != nil }, set: { if !$0 { removendo = nil } }),
                            titleVisibility: .visible, presenting: removendo) { premio in
            Button("Remover", role: .destructive) {
                Task { await executar(nil) { try await $0.excluirPremio(premio.id) } }
            }
        }
    }

    private func moedas(_ saldo: SaldoDeMoedas) -> some View {
        Section {
            LabeledContent("Fofocoins") {
                Text("\(saldo.saldo)").font(.sora(.title, peso: .bold)).foregroundStyle(paleta.primaria)
                    .monospacedDigit().contentTransition(.numericText())
            }
            if cuidaDosPremios {
                LabeledContent("Quantidade") {
                    TextField("Use negativo para debitar", text: $ajuste)
                        .keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing)
                        .accessibilityLabel("Quantidade")
                }
                TextField("Motivo", text: $motivo, prompt: Text("Ex.: Arrumou a cozinha"))
                    .onChange(of: motivo) { _, novo in if novo.count > 200 { motivo = String(novo.prefix(200)) } }
                Button("Ajustar") { Task { await ajustar() } }
            }
            DisclosureGroup(historico ? "Esconder histórico" : "Ver histórico", isExpanded: $historico) {
                if saldo.historico.isEmpty {
                    Text("Nenhuma movimentação ainda.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
                ForEach(saldo.historico) { movimento in
                    LinhaDoCartao(titulo: movimento.motivo, detalhe: Formatos.dataCurta(movimento.data)) {
                        Text(movimento.valor > 0 ? "+\(movimento.valor)" : "\(movimento.valor)")
                            .monospacedDigit()
                            .foregroundStyle(movimento.valor > 0 ? paleta.sucesso : movimento.valor < 0 ? paleta.perigo : paleta.texto)
                    }
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private var novoPremio: some View {
        Section("Novo prêmio") {
            TextField("Nome", text: $nome)
                .onChange(of: nome) { _, novo in if novo.count > 100 { nome = String(novo.prefix(100)) } }
            TextField("Descrição", text: $descricao)
                .onChange(of: descricao) { _, novo in if novo.count > 300 { descricao = String(novo.prefix(300)) } }
            LabeledContent("Preço em Fofocoins") {
                TextField("Preço", text: $preco).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                    .accessibilityLabel("Preço em Fofocoins")
            }
            Button("Criar") { Task { await criarPremio() } }
        }
        .listRowBackground(paleta.superficie)
    }

    private func disponiveis(_ dados: DadosDosPremios) -> some View {
        Section("Disponíveis") {
            if dados.premios.isEmpty {
                EstadoVazio(icone: "trophy", titulo: "Nenhum prêmio cadastrado",
                            texto: "Crie prêmios para a família trocar pelas Fofocoins.")
            }
            ForEach(dados.premios) { premio in
                VStack(alignment: .leading, spacing: 6) {
                    Text(premio.nome).font(.sora(.body, peso: .semibold)).foregroundStyle(premio.ativo ? paleta.texto : paleta.textoSuave)
                    if let descricao = premio.descricao, !descricao.isEmpty {
                        Text(descricao).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                    HStack {
                        Text("\(premio.preco) Fofocoins").font(.sora(.subheadline, peso: .medium)).foregroundStyle(paleta.primaria)
                        Spacer()
                        if premio.ativo {
                            Button("Resgatar") { resgatando = premio }
                                .buttonStyle(.borderedProminent)
                                .controlSize(.small)
                                .disabled(dados.saldo.saldo < premio.preco)
                        } else {
                            Etiqueta(texto: String(localized: "Desativado"))
                        }
                    }
                }
                .padding(.vertical, 4)
                .swipeActions {
                    if cuidaDosPremios && premio.ativo {
                        Button(role: .destructive) { removendo = premio } label: { Label("Remover", systemImage: "trash") }
                    }
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private func resgates(_ resgates: [Resgate]) -> some View {
        Section("Resgates") {
            if resgates.isEmpty {
                Text("Nenhum resgate ainda.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(resgates) { resgate in
                LinhaDoCartao(titulo: resgate.premio,
                              detalhe: "\(PessoasDaFamilia.nome(resgate.usuario, em: carga.valor?.membros ?? [])) · \(resgate.status)") {
                    Text("\(resgate.preco)").monospacedDigit()
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    // MARK: - API

    private func carregar() async {
        let cliente = sessao.cliente
        let todos = cuidaDosPremios
        await sessao.cache.carregar("premios", mostrar: { carga = $0 }) {
            async let saldo = cliente.consultarSaldoDeMoedas()
            async let premios = cliente.listarPremios(todos: todos)
            async let resgates = cliente.listarResgates()
            async let membros = try? cliente.verFamilia().membros
            return try await DadosDosPremios(saldo: saldo, premios: premios, resgates: resgates, membros: membros ?? [])
        }
    }

    private func ajustar() async {
        guard let valor = Int(ajuste.trimmingCharacters(in: .whitespaces)), valor != 0 else {
            erro = String(localized: "Digite uma quantidade válida de Fofocoins.")
            return
        }
        let motivo = motivo.trimmingCharacters(in: .whitespaces)
        guard !motivo.isEmpty else {
            erro = String(localized: "Explique o motivo do ajuste.")
            return
        }
        await executar(String(localized: "Saldo atualizado.")) { _ = try await $0.ajustarMoedas(valor, motivo: motivo) }
        if erro == nil {
            ajuste = ""
            self.motivo = ""
        }
    }

    private func criarPremio() async {
        let nome = nome.trimmingCharacters(in: .whitespaces)
        guard !nome.isEmpty else {
            erro = String(localized: "Digite o nome do prêmio.")
            return
        }
        guard let preco = Int(preco), preco > 0 else {
            erro = String(localized: "Digite um preço válido.")
            return
        }
        let pedido = NovoPremio(nome: nome, descricao: descricao.trimmingCharacters(in: .whitespaces), preco: preco, ativo: true)
        await executar(nil) { try await $0.criarPremio(pedido) }
        if erro == nil {
            self.nome = ""
            descricao = ""
            self.preco = ""
        }
    }

    private func resgatar(_ premio: Premio) async {
        do {
            let resgate = try await sessao.cliente.resgatar(premio.id)
            aviso = String(localized: "Resgatado. Saldo agora: \(resgate.saldo) Fofocoins.")
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
        await carregar()
    }

    private func executar(_ mensagem: String?, _ acao: (ClienteDaAPI) async throws -> Void) async {
        do {
            try await acao(sessao.cliente)
            erro = nil
            aviso = mensagem
        } catch {
            erro = error.localizedDescription
        }
        await carregar()
    }
}

struct DadosDosPremios: Codable, Equatable, Sendable {
    let saldo: SaldoDeMoedas
    let premios: [Premio]
    let resgates: [Resgate]
    let membros: [MembroDaFamilia]
}
