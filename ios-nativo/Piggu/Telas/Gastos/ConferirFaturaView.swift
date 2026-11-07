import SwiftUI
import UniformTypeIdentifiers

/// Conferir a fatura do cartao (conferir-fatura.ts): le o PDF no servidor e mostra o que ja foi
/// lancado e o que falta. O plano gratuito ve so as contagens; o Premium ve as linhas por grupo e
/// adiciona as que faltam pela importacao. Nada grava antes do "Adicionar".
struct ConferirFaturaView: View {
    /// Cartao da fatura aberta (Contas). Sem ele, a pessoa escolhe um dos `cartoes` (Gastos).
    var cartao: ContaOuCartao?
    var cartoes: [ContaOuCartao] = []
    /// Mes (aaaa-mm) da fatura aberta: o que "Marcar fatura como paga" quita.
    var mes: String?
    /// Gravou: as outras telas recarregam.
    let aoImportar: () -> Void

    /// 5 MB viram ate 6,99 milhoes de caracteres em base64, dentro dos 7 milhoes do servidor.
    static let tamanhoMaximo = 5 * 1024 * 1024

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var escolhendo = false
    @State private var ocupado = false
    @State private var cartaoEscolhido: String?
    @State private var categorias: [String] = []
    /// O PDF fica na memoria para a senha e a autorizacao reenviarem sem escolher de novo.
    @State private var pdfBase64 = ""
    @State private var senha = ""
    @State private var precisaSenha = false
    @State private var pedidoDeIa: String?
    @State private var convite = false
    @State private var erro: String?
    @State private var resultado: String?
    @State private var paga = false
    @State private var conferencia: ConferenciaDaFatura?
    /// Vencimento lido no PDF; fica depois de importar, quando a conferencia some.
    @State private var vencimento: String?
    @State private var linhas: [LinhaDaConferencia] = []
    @State private var marcadas: Set<String> = []

    private var contaId: String? { cartao?.id ?? cartaoEscolhido }
    private var grupos: Grupos { Self.grupos(linhas) }

    var body: some View {
        NavigationStack {
            List {
                entrada
                avisos
                if let conferencia { resultadoDaLeitura(conferencia) }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Conferir fatura")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fechar") { fechar() } }
                if !marcadas.isEmpty {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Adicionar \(marcadas.count)") { Task { await adicionar() } }.disabled(ocupado)
                    }
                }
            }
            .fileImporter(isPresented: $escolhendo, allowedContentTypes: [.pdf]) { escolha in
                Task { await ler(escolha) }
            }
            // Outro cartao: o que foi lido era do anterior e nao pode ser importado no novo.
            .onChange(of: cartaoEscolhido) { _, _ in limpar() }
            .task { categorias = (try? await sessao.cliente.listarCategorias()) ?? [] }
        }
    }

    // MARK: - Partes da tela

    private var entrada: some View {
        Section {
            Text("Escolha o PDF da fatura do cartão. O Piggu mostra o que já está lançado e o que falta.")
                .font(.sora(.subheadline))
            if cartao == nil, !cartoes.isEmpty {
                Picker("Cartão da fatura", selection: $cartaoEscolhido) {
                    Text("Sem cartão").tag(String?.none)
                    ForEach(cartoes) { Text($0.nome).tag(String?.some($0.id)) }
                }
            }
            Button { escolhendo = true } label: { Label("Escolher PDF da fatura", systemImage: "doc.badge.plus") }
                .disabled(ocupado)
            if precisaSenha {
                SecureField("Senha do PDF", text: $senha)
                    .textContentType(.password)
                    .accessibilityLabel("Senha do PDF")
                Button("Tentar de novo") { Task { await enviar() } }.disabled(ocupado || senha.isEmpty)
            }
        }
        .listRowBackground(paleta.superficie)
    }

    @ViewBuilder
    private var avisos: some View {
        if ocupado {
            Section { ProgressView("Lendo a fatura...") }.listRowBackground(paleta.superficie)
        }
        if let pedidoDeIa {
            Section {
                Text(pedidoDeIa).font(.sora(.subheadline))
                Button("Autorizar e continuar") { Task { await enviar(autorizando: true) } }.disabled(ocupado)
                Button("Cancelar", role: .cancel) { limpar() }
            }
            .listRowBackground(paleta.superficie)
        }
        if let erro {
            Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
        if convite {
            Section {
                Text("A conferência linha a linha faz parte do Piggu Premium.").font(.sora(.subheadline))
                LinkDoPremium(titulo: "Conhecer o Piggu Premium")
            }
            .listRowBackground(paleta.superficie)
        }
        if let resultado {
            Faixa(texto: resultado, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
        if cartao != nil, let outro = Self.venceEm(vencimento, mes: mes) {
            Text("Esta fatura vence em \(outro), não é a do mês aberto.")
                .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave).listRowBackground(paleta.superficie)
        }
        if let cartao, let mes, !paga,
           Self.ofereceQuitar(cartao: true, mes: mes, vencimento: vencimento, importou: resultado != nil,
                              leu: conferencia != nil, marcadas: marcadas.count) {
            Button("Marcar fatura como paga") { Task { await pagar(cartao, mes) } }
                .disabled(ocupado).listRowBackground(paleta.superficie)
        }
        if paga {
            Faixa(texto: String(localized: "Fatura marcada como paga."), tipo: .sucesso)
                .listRowBackground(Color.clear).listRowInsets(EdgeInsets())
        }
    }

    @ViewBuilder
    private func resultadoDaLeitura(_ c: ConferenciaDaFatura) -> some View {
        Section {
            Text("\(c.contagem.jaLancadas) já lançadas, faltam \(c.contagem.faltam)")
                .font(.sora(.body, peso: .semibold))
            if c.contagem.confira > 0 {
                Text("Para conferir: \(c.contagem.confira)").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            if let aviso = c.aviso { Text(aviso).font(.sora(.footnote)) }
        }
        .listRowBackground(paleta.superficie)
        if c.linhas != nil {
            grupo(String(localized: "Faltam"), grupos.faltam)
            grupo(String(localized: "Confira"), grupos.confira)
            grupo(String(localized: "Tarifas"), grupos.tarifas)
            if !grupos.jaLancadas.isEmpty {
                Section {
                    DisclosureGroup("Já lançadas (\(grupos.jaLancadas.count))") {
                        ForEach(grupos.jaLancadas) { l in
                            LadoALado {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(l.descricao)
                                    Text(Formatos.dataCurta(l.data)).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                                }
                                Spacer()
                                Text(Formatos.moeda(l.valor)).monospacedDigit()
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }
                }
                .listRowBackground(paleta.superficie)
            }
        }
    }

    @ViewBuilder
    private func grupo(_ titulo: String, _ lista: [LinhaDaConferencia]) -> some View {
        if !lista.isEmpty {
            Section {
                ForEach(lista) { linhaMarcavel($0) }
            } header: {
                Text("\(titulo) (\(lista.count))")
            }
            .listRowBackground(paleta.superficie)
        }
    }

    private func linhaMarcavel(_ l: LinhaDaConferencia) -> some View {
        let marcada = marcadas.contains(l.idExterno)
        let detalhe = [
            Formatos.dataCurta(l.data),
            l.parcelaTotal.map { String(localized: "parcela \(l.parcelaNumero ?? 0)/\($0)") },
            l.gastoDescricao.map { String(localized: "parecido com \($0) (\(l.gastoData.map(Formatos.dataCurta) ?? ""))") },
        ].compactMap { $0 }.joined(separator: " · ")
        return VStack(alignment: .leading, spacing: 4) {
            Button {
                if marcada { marcadas.remove(l.idExterno) } else { marcadas.insert(l.idExterno) }
            } label: {
                LadoALado {
                    Image(systemName: marcada ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(marcada ? paleta.primaria : paleta.textoSuave)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l.descricao)
                        Text(detalhe).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                    Spacer()
                    Text(Formatos.moeda(l.valor)).monospacedDigit()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(marcada ? .isSelected : [])
            .accessibilityHint("Toque para marcar ou desmarcar")
            if let i = linhas.firstIndex(where: { $0.idExterno == l.idExterno }) {
                Picker("Categoria de \(l.descricao)", selection: $linhas[i].categoria) {
                    ForEach(categorias.contains(l.categoria) ? categorias : [l.categoria] + categorias, id: \.self) {
                        Text($0).tag($0)
                    }
                }
                .labelsHidden()
                .font(.sora(.footnote))
            }
        }
    }

    // MARK: - Logica

    struct Grupos {
        let faltam, confira, tarifas, jaLancadas: [LinhaDaConferencia]
    }

    static func grupos(_ linhas: [LinhaDaConferencia]) -> Grupos {
        Grupos(
            faltam: linhas.filter { $0.tipo == .compra && $0.situacao == .falta },
            confira: linhas.filter { $0.tipo == .compra && $0.situacao == .confira },
            tarifas: linhas.filter { $0.tipo == .tarifa && $0.situacao != .jaLancada },
            jaLancadas: linhas.filter { $0.situacao == .jaLancada })
    }

    /// So as compras que faltam nascem marcadas; "Confira" e tarifas a pessoa decide.
    static func marcadasIniciais(_ linhas: [LinhaDaConferencia]) -> Set<String> {
        Set(linhas.filter { $0.tipo == .compra && $0.situacao == .falta }.map(\.idExterno))
    }

    /// mm/aaaa do vencimento lido quando ele cai em outro mes que o aberto; nil quando bate ou nao veio.
    static func venceEm(_ vencimento: String?, mes: String?) -> String? {
        guard let vencimento, let mes, vencimento.count >= 7, !vencimento.hasPrefix(mes) else { return nil }
        return "\(vencimento.dropFirst(5).prefix(2))/\(vencimento.prefix(4))"
    }

    /// Com o cartao aberto, quita depois de importar ou quando nada esta marcado; nunca a fatura de outro mes.
    static func ofereceQuitar(cartao: Bool, mes: String?, vencimento: String?, importou: Bool, leu: Bool,
                              marcadas: Int) -> Bool {
        guard cartao, mes != nil, venceEm(vencimento, mes: mes) == nil else { return false }
        return importou || (leu && marcadas == 0)
    }

    static func pedidoDeImportacao(_ linhas: [LinhaDaConferencia], marcadas: Set<String>,
                                   contaId: String?) -> PedidoDeImportacao {
        PedidoDeImportacao(
            linhas: linhas.filter { marcadas.contains($0.idExterno) }.map {
                LinhaDoExtrato(data: $0.data, descricao: $0.descricao, valor: $0.valor, idExterno: $0.idExterno,
                               categoria: $0.categoria, jaImportada: false,
                               parcelaNumero: $0.parcelaNumero, parcelaTotal: $0.parcelaTotal)
            },
            contaId: contaId)
    }

    private func limpar() {
        pdfBase64 = ""
        senha = ""
        precisaSenha = false
        pedidoDeIa = nil
        convite = false
        erro = nil
        resultado = nil
        paga = false
        conferencia = nil
        vencimento = nil
        linhas = []
        marcadas = []
    }

    private func ler(_ escolha: Result<URL, Error>) async {
        limpar()
        let dados: Data
        do {
            let url = try escolha.get()
            let liberado = url.startAccessingSecurityScopedResource()
            defer { if liberado { url.stopAccessingSecurityScopedResource() } }
            dados = try Data(contentsOf: url)
        } catch {
            erro = String(localized: "Não consegui abrir esse PDF.")
            return
        }
        guard dados.count <= Self.tamanhoMaximo else {
            erro = String(localized: "PDF grande demais (máximo 5 MB).")
            return
        }
        pdfBase64 = dados.base64EncodedString()
        await enviar()
    }

    private func enviar(autorizando: Bool = false) async {
        erro = nil
        convite = false
        pedidoDeIa = nil
        ocupado = true
        defer { ocupado = false }
        var pedido = PedidoDeConferencia(pdfBase64: pdfBase64, senha: senha.isEmpty ? nil : senha, contaId: contaId)
        if autorizando {
            pedido.autorizoIa = true
            pedido.versaoDoAviso = Conta.versaoDosTermos
        }
        do {
            let c = try await sessao.cliente.conferirFatura(pedido)
            precisaSenha = false
            conferencia = c
            vencimento = c.vencimento
            convite = c.linhas == nil
            linhas = c.linhas ?? []
            marcadas = Self.marcadasIniciais(linhas)
        } catch {
            let codigo = (error as? ErroDaAPI)?.codigo
            let mensagem = error.localizedDescription
            switch codigo {
            case "PDF_COM_SENHA":
                precisaSenha = true
                erro = mensagem
            case "CONSENTIMENTO_NECESSARIO", "AVISO_DESATUALIZADO":
                pedidoDeIa = mensagem
            case "PLANO_PREMIUM":
                convite = true
                erro = mensagem
            default:
                erro = mensagem
            }
        }
    }

    private func adicionar() async {
        erro = nil
        ocupado = true
        defer { ocupado = false }
        do {
            let pedido = Self.pedidoDeImportacao(linhas, marcadas: marcadas, contaId: contaId)
            let feito = try await sessao.cliente.importarExtrato(pedido.linhas, contaId: pedido.contaId)
            conferencia = nil
            linhas = []
            marcadas = []
            resultado = feito.pulados > 0
                ? String(localized: "Adicionadas: \(feito.importados). Já estavam no Piggu: \(feito.pulados).")
                : String(localized: "Adicionadas: \(feito.importados).")
            aoImportar()
        } catch {
            erro = error.localizedDescription
        }
    }

    private func pagar(_ cartao: ContaOuCartao, _ mes: String) async {
        ocupado = true
        defer { ocupado = false }
        do {
            try await sessao.cliente.pagarFatura(cartao.id, mes: mes)
            paga = true
            aoImportar()
        } catch {
            erro = error.localizedDescription
        }
    }
}
