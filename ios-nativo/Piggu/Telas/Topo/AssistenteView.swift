import SwiftUI

/// Assistente de IA (Premium, assistente.ts do site): perguntas sobre o dinheiro da familia, so
/// leitura. A conversa fica so neste aparelho, por 30 dias, e sai ao sair da conta (vai no cache,
/// que a sessao apaga): o servidor nao guarda. No primeiro uso a pessoa autoriza o envio ao Gemini;
/// se gravar a autorizacao falhar, ela segue junto com a primeira pergunta.
struct AssistenteView: View {
    /// Uma fala guardada no aparelho; `pessoa` falso e a resposta, que pode sugerir uma tela.
    struct Fala: Codable, Equatable, Sendable {
        let pessoa: Bool
        let texto: String
        var tela: String?
        var em = Date()
    }

    static let chaveDaConversa = "assistente.conversa"
    static let diasGuardada = 30
    /// As mesmas do site (FALAS_NO_CONTEXTO e FALAS_GUARDADAS).
    private static let falasNoContexto = 10
    private static let falasGuardadas = 200
    private static let sugestoes: [LocalizedStringResource] = [
        "Quanto gastei este mês?",
        "Vou estourar algum orçamento?",
        "Onde dá para cortar gastos?",
        "Quanto falta para meus objetivos?",
    ]

    @Environment(Sessao.self) private var sessao
    @Environment(\.abrirRota) private var abrirRota
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var situacao: SituacaoDoAssistente?
    @State private var carregando = false
    /// A pessoa acabou de autorizar: a proxima pergunta leva a autorizacao.
    @State private var autorizandoAgora = false
    @State private var falas: [Fala] = []
    @State private var texto = ""
    @State private var pensando = false
    @State private var erro: String?
    @FocusState private var campoFocado: Bool

    private var ehPremium: Bool { sessao.usuario?.plano == .premium || sessao.usuario?.role == .admin }
    private var precisaAutorizar: Bool { situacao.map { !$0.autorizado } == true && !autorizandoAgora }

    var body: some View {
        NavigationStack {
            conteudo
                .background(paleta.fundo)
                .navigationTitle("Assistente")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Fechar") { fechar() }
                    }
                    if !falas.isEmpty {
                        ToolbarItem(placement: .primaryAction) {
                            Button("Nova conversa") { recomecar() }
                                .accessibilityHint("Apaga a conversa deste aparelho")
                        }
                    }
                }
        }
        .task { await abrir() }
    }

    @ViewBuilder
    private var conteudo: some View {
        if !ehPremium {
            explicacao {
                Text("Pergunte do seu jeito, como \"quanto gastei com mercado em setembro?\" ou \"vou estourar algum orçamento?\", e o Piggu responde com os seus números.")
                Button("Conhecer o Piggu Premium") { ir(.tela(.premium)) }
                    .botaoPrincipal()
            }
        } else if carregando {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if situacao?.habilitado == false {
            EstadoVazio(icone: "sparkles", titulo: "Assistente desligado", texto: "O assistente ainda não está ligado nesta instalação.")
        } else if precisaAutorizar {
            explicacao {
                Text("Para responder, o assistente envia ao Google (Gemini) só o que a pergunta pede: totais e gastos do período, sem nome nem e-mail. A conversa não fica guardada no Piggu: fica só neste aparelho por \(Self.diasGuardada) dias e sai quando você sai da conta.")
                Text("A IA pode errar e não faz recomendação de investimento.")
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
                NavigationLink("Aviso de Privacidade") { TextoLegalView(documento: .privacidade) }
                    .font(.sora(.footnote, peso: .semibold))
                Button("Autorizo e quero perguntar") { Task { await autorizar() } }
                    .botaoPrincipal()
            }
        } else {
            conversa
        }
    }

    private func explicacao(@ViewBuilder _ corpo: () -> some View) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                avatar(lado: 56)
                corpo()
            }
            .padding()
        }
    }

    private var conversa: some View {
        ScrollViewReader { rolagem in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if falas.isEmpty {
                        Text("Pergunte sobre gastos, orçamento, contas e cofrinho.")
                            .font(.sora(.subheadline))
                            .foregroundStyle(paleta.textoSuave)
                        ForEach(Self.sugestoes, id: \.key) { sugestao in
                            Button(String(localized: sugestao)) { Task { await perguntar(String(localized: sugestao)) } }
                                .buttonStyle(.bordered)
                        }
                    }
                    ForEach(Array(falas.enumerated()), id: \.offset) { _, fala in
                        vez(fala)
                    }
                    if pensando {
                        HStack(alignment: .top, spacing: 8) {
                            avatar(lado: 28)
                            Text("Pensando...").italic().foregroundStyle(paleta.textoSuave)
                        }
                    }
                    Color.clear.frame(height: 1).id("fim")
                }
                .padding()
            }
            .onChange(of: falas.count) { _, _ in withAnimation { rolagem.scrollTo("fim") } }
            .onChange(of: pensando) { _, _ in withAnimation { rolagem.scrollTo("fim") } }
            .onAppear { rolagem.scrollTo("fim") }
        }
        .safeAreaInset(edge: .bottom) { campo }
    }

    private func vez(_ fala: Fala) -> some View {
        HStack(alignment: .top, spacing: 8) {
            if fala.pessoa {
                Spacer(minLength: 40)
            } else {
                avatar(lado: 28)
            }
            VStack(alignment: .leading, spacing: 8) {
                Text(fala.texto)
                    .textSelection(.enabled)
                if let rota = Self.rota(fala.tela) {
                    botaoDeAbrir(rota)
                }
            }
            .padding(12)
            .foregroundStyle(fala.pessoa ? paleta.primariaContraste : paleta.texto)
            .background(fala.pessoa ? paleta.primaria : paleta.superficie,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            if !fala.pessoa { Spacer(minLength: 40) }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(fala.pessoa ? Text("Você: \(fala.texto)") : Text("Assistente: \(fala.texto)"))
    }

    private func botaoDeAbrir(_ rota: Rota) -> some View {
        Button(String(localized: "Abrir \(nome(rota))")) { ir(rota) }
            .buttonStyle(.bordered)
            .font(.sora(.footnote, peso: .semibold))
    }

    /// A tela que a resposta sugere, so se for uma aba ou uma tela do Mais.
    static func rota(_ tela: String?) -> Rota? {
        guard let tela, let url = URL(string: "piggu://\(tela)"), let rota = Rota(url: url) else { return nil }
        switch rota {
        case .aba, .tela: return rota
        default: return nil
        }
    }

    private func nome(_ rota: Rota) -> String {
        switch rota {
        case .aba(.painel): String(localized: "Painel")
        case .aba(.gastos): String(localized: "Gastos")
        case .aba(.receitas): String(localized: "Receitas")
        case .aba(.relatorios): String(localized: "Relatórios")
        case let .tela(tela): tela.titulo
        default: String(localized: "Mais")
        }
    }

    private var campo: some View {
        VStack(spacing: 6) {
            if let erro {
                Text(erro).font(.sora(.footnote)).foregroundStyle(paleta.perigo)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 8) {
                TextField("Sua pergunta", text: $texto, prompt: Text("Ex.: quanto gastei com mercado?"), axis: .vertical)
                    .lineLimit(1...4)
                    .focused($campoFocado)
                    .submitLabel(.send)
                    .onSubmit { Task { await perguntar(texto) } }
                    .onChange(of: texto) { _, novo in if novo.count > 500 { texto = String(novo.prefix(500)) } }
                    .padding(10)
                    .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                Button { Task { await perguntar(texto) } } label: {
                    Image(systemName: "arrow.up.circle.fill").font(.system(size: 32))
                }
                .disabled(pensando || texto.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel("Enviar")
                .frame(minWidth: 44, minHeight: 44)
            }
            if let situacao {
                Text("\(situacao.restantes) de \(situacao.limite) perguntas neste mês · a IA pode errar")
                    .font(.sora(.caption))
                    .foregroundStyle(paleta.textoSuave)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.bar)
    }

    private func avatar(lado: CGFloat) -> some View {
        Image("Logo")
            .resizable()
            .scaledToFit()
            .frame(width: lado, height: lado)
            .clipShape(Circle())
            .accessibilityHidden(true)
    }

    // MARK: - Acoes

    private func ir(_ rota: Rota) {
        fechar()
        abrirRota(rota)
    }

    private func abrir() async {
        falas = Self.podar(sessao.cache.ler(Self.chaveDaConversa, como: [Fala].self)?.valor ?? [])
        guard ehPremium, situacao == nil else { return }
        carregando = true
        defer { carregando = false }
        do {
            situacao = try await sessao.cliente.situacaoDoAssistente()
        } catch {
            erro = error.localizedDescription
        }
    }

    private func recomecar() {
        falas = []
        sessao.cache.guardar([Fala](), em: Self.chaveDaConversa)
        erro = nil
    }

    private func autorizar() async {
        autorizandoAgora = true
        // Sem gravar agora, a autorizacao vai junto com a primeira pergunta.
        if let gravada = try? await sessao.cliente.autorizarAssistente() {
            situacao = gravada
            autorizandoAgora = false
        }
    }

    private func perguntar(_ digitada: String) async {
        let pergunta = digitada.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !pergunta.isEmpty, !pensando else { return }
        let historico = falas.suffix(Self.falasNoContexto).map { PedidoAoAssistente.Fala(pessoa: $0.pessoa, texto: $0.texto) }
        let autorizo = autorizandoAgora
        mudarFalas { $0.append(Fala(pessoa: true, texto: pergunta)) }
        texto = ""
        erro = nil
        pensando = true
        defer { pensando = false }
        var pedido = PedidoAoAssistente(pergunta: pergunta, historico: historico, moeda: Formatos.codigoDaMoeda)
        if autorizo {
            pedido.autorizoIa = true
            pedido.versaoDoAviso = Conta.versaoDosTermos
        }
        do {
            let resposta = try await sessao.cliente.perguntarAoAssistente(pedido)
            mudarFalas { $0.append(Fala(pessoa: false, texto: resposta.texto, tela: resposta.tela)) }
            situacao?.autorizado = true
            situacao?.restantes = resposta.restantes
            autorizandoAgora = false
        } catch {
            // A pergunta volta para o campo, para nao ter de digitar de novo.
            mudarFalas { $0.removeLast() }
            texto = pergunta
            if (error as? ErroDaAPI)?.codigo == "CONSENTIMENTO_NECESSARIO" {
                autorizandoAgora = false
                situacao?.autorizado = false
                return
            }
            erro = error.localizedDescription
        }
    }

    /// Muda a conversa na tela e a guarda no aparelho.
    private func mudarFalas(_ mudanca: (inout [Fala]) -> Void) {
        mudanca(&falas)
        falas = Self.podar(falas)
        sessao.cache.guardar(falas, em: Self.chaveDaConversa)
    }

    /// So as falas dos ultimos 30 dias, e no maximo 200.
    static func podar(_ falas: [Fala], agora: Date = Date()) -> [Fala] {
        let limite = agora.addingTimeInterval(-Double(diasGuardada) * 24 * 60 * 60)
        return Array(falas.filter { $0.em >= limite }.suffix(falasGuardadas))
    }
}
