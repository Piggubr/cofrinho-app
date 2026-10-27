import SwiftUI

/// Cartoes e bancos (features/accounts): as contas e cartoes de lancar, a fatura do cartao entre
/// os fechamentos com "Marcar como paga" (D-085), e os bancos do Open Finance (D-091).
struct ContasECartoesView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[ContaOuCartao]> = .carregando
    @State private var membros: [MembroDaFamilia] = []
    @State private var openFinance: OpenFinance?
    @State private var faturaAberta: FaturaNaTela?
    @State private var criando = false
    @State private var apagando: ContaOuCartao?
    @State private var erro: String?

    private var eu: String? { sessao.usuario?.id }
    private var temCompanhia: Bool { membros.count > 1 }

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
            case let .pronto(contas, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                Section("Suas contas") {
                    if contas.isEmpty {
                        Text("Nenhuma conta ainda. Cadastre para escolher no lançamento e ver a fatura do cartão.")
                            .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                    ForEach(contas) { linha($0) }
                    Button { criando = true } label: { Label("Nova conta ou cartão", systemImage: "plus") }
                }
                .listRowBackground(paleta.superficie)
            }
            if let openFinance, openFinance.habilitado {
                CartaoDosBancos(openFinance: openFinance, ehPremium: sessao.usuario?.plano == .premium)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Cartões e bancos")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .sheet(item: $faturaAberta) { aberta in
            FaturaView(cartao: aberta.cartao, mes: aberta.mes) { Task { await carregar() } }
        }
        .sheet(isPresented: $criando) {
            NovaContaView(temCompanhia: temCompanhia) { Task { await carregar() } }
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar \"\($0.nome)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { conta in
            Button("Apagar", role: .destructive) { Task { await executar { try await $0.excluirConta(conta.id) } } }
        } message: { _ in
            Text("Os gastos continuam, só sem a conta.")
        }
    }

    private func linha(_ conta: ContaOuCartao) -> some View {
        let minha = conta.usuario == nil || conta.usuario == eu
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: conta.tipo == .cartao ? "creditcard" : "building.columns")
                    .frame(width: 38, height: 38)
                    .foregroundStyle(paleta.primaria)
                    .background(paleta.suave, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(conta.nome).font(.sora(.body, peso: .medium))
                        if conta.compartilhado == true { Etiqueta(texto: String(localized: "do casal")) }
                    }
                    Group {
                        if !minha && temCompanhia {
                            Text("De \(PessoasDaFamilia.nome(conta.usuario, em: membros))")
                        }
                        if conta.tipo == .cartao {
                            Text("Fecha dia \(conta.fechamento ?? 0) · vence dia \(conta.vencimento ?? 0)")
                            if let fatura = conta.faturaAberta {
                                Text("Fatura aberta (\(Formatos.nomeDoMes(chave: fatura.mes))): \(Formatos.moeda(fatura.total))")
                                if let ultima = fatura.ultimaCompra, let fechamento = fatura.fechamento {
                                    Text("Compras até \(Formatos.dataCurta(ultima)) entram nesta fatura. A partir de \(Formatos.dataCurta(fechamento)), dia do fechamento, vão para a próxima.")
                                        .foregroundStyle(paleta.primaria)
                                }
                            }
                            if let fatura = conta.faturaAPagar {
                                if fatura.paga == true {
                                    Text("Fatura de \(Formatos.nomeDoMes(chave: fatura.mes)) paga")
                                } else {
                                    Text("A pagar: \(Formatos.moeda(fatura.total)) até \(Formatos.dataCurta(fatura.vencimento))")
                                }
                            }
                        } else {
                            Text("Conta")
                        }
                    }
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
                }
            }
            HStack {
                if conta.tipo == .cartao, let fatura = conta.faturaAberta {
                    Button("Ver fatura") { faturaAberta = FaturaNaTela(cartao: conta, mes: fatura.mes) }
                        .buttonStyle(.bordered).controlSize(.small)
                }
                if minha && temCompanhia {
                    Button(conta.compartilhado == true ? "Deixar só meu" : "Compartilhar") {
                        Task { await executar { try await $0.compartilharConta(conta.id, !(conta.compartilhado ?? false)) } }
                    }
                    .buttonStyle(.bordered).controlSize(.small)
                }
            }
        }
        .padding(.vertical, 4)
        .swipeActions {
            if minha {
                Button(role: .destructive) { apagando = conta } label: { Label("Apagar", systemImage: "trash") }
            }
        }
    }

    private func carregar() async {
        let cliente = sessao.cliente
        if openFinance == nil { openFinance = OpenFinance(cliente: cliente) }
        async let membros = try? cliente.verFamilia().membros
        await sessao.cache.carregar("contas", mostrar: { carga = $0 }) { try await cliente.contas() }
        self.membros = await membros ?? self.membros
        await openFinance?.carregar()
    }

    private func executar(_ acao: (ClienteDaAPI) async throws -> Void) async {
        do {
            try await acao(sessao.cliente)
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
        await carregar()
    }
}

struct FaturaNaTela: Identifiable {
    let cartao: ContaOuCartao
    let mes: String
    var id: String { cartao.id + mes }
}

/// A fatura de um cartao, andando para a anterior e a seguinte, com os gastos dela.
private struct FaturaView: View {
    let cartao: ContaOuCartao
    @State var mes: String
    let aoMudar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<FaturaComGastos> = .carregando
    @State private var ocupado = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            List {
                if let erro {
                    Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                switch carga {
                case .carregando:
                    Section { EsqueletoDeLista(linhas: 3) }.listRowBackground(paleta.superficie)
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
                case let .pronto(dados, _):
                    conteudo(dados)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle(cartao.nome)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Fechar") { fechar() } }
            }
            .safeAreaInset(edge: .top) {
                GrupoDeVidro(espaco: 6) {
                    seta("chevron.left", "Fatura anterior", -1)
                    Text(Formatos.nomeDoMes(chave: mes))
                        .font(.sora(.headline, peso: .semibold))
                        .padding(.horizontal, 18).frame(minHeight: 44)
                        .pigguVidro()
                    seta("chevron.right", "Próxima fatura", 1)
                }
                .padding(.vertical, 6)
            }
            .task(id: mes) { await carregar() }
        }
    }

    @ViewBuilder
    private func conteudo(_ dados: FaturaComGastos) -> some View {
        let fatura = dados.fatura
        Section {
            if let inicio = fatura.inicio, let ultima = fatura.ultimaCompra, let fechamento = fatura.fechamento {
                Text("Compras de \(Formatos.dataCurta(inicio)) a \(Formatos.dataCurta(ultima)) · fecha em \(Formatos.dataCurta(fechamento)) · vence em \(Formatos.dataCurta(fatura.vencimento))")
                    .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            Text(Formatos.moeda(fatura.total)).font(.sora(.title, peso: .bold)).monospacedDigit()
            HStack {
                if fatura.paga == true {
                    Etiqueta(texto: String(localized: "Paga"))
                    Spacer()
                    Button("Desfazer pagamento") { Task { await alternar(fatura) } }
                        .buttonStyle(.bordered).disabled(ocupado)
                } else {
                    Button("Marcar como paga") { Task { await alternar(fatura) } }
                        .botaoPrincipal().disabled(ocupado)
                }
            }
        }
        .listRowBackground(paleta.superficie)
        Section("Gastos") {
            if dados.gastos.isEmpty {
                Text("Nenhum gasto nesta fatura.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(dados.gastos) { gasto in
                LinhaDoCartao(titulo: gasto.item, detalhe: [
                    Formatos.dataCurta(gasto.data), gasto.categoria,
                    gasto.parcelas.map { String(localized: "parcela \(gasto.parcela ?? 0)/\($0)") },
                ].compactMap { $0 }.joined(separator: " · ")) {
                    Text(Formatos.moeda(gasto.valor)).monospacedDigit()
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private func seta(_ simbolo: String, _ rotulo: LocalizedStringKey, _ passo: Int) -> some View {
        Button { mes = DadosDoMes.somar(passo, a: mes) } label: {
            Image(systemName: simbolo).font(.body.weight(.semibold)).foregroundStyle(paleta.primaria)
                .frame(width: 44, height: 44).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .pigguVidro(em: Circle())
        .accessibilityLabel(rotulo)
    }

    private func carregar() async {
        do {
            carga = .pronto(try await sessao.cliente.fatura(cartao.id, mes: mes))
        } catch {
            carga = .falhou(error as? ErroDaAPI ?? .respostaInvalida)
        }
    }

    /// So tira a fatura do "a pagar" (com as anteriores); os gastos ja contaram nas compras.
    private func alternar(_ fatura: Fatura) async {
        ocupado = true
        erro = nil
        defer { ocupado = false }
        do {
            if fatura.paga == true {
                try await sessao.cliente.desfazerPagamentoDaFatura(cartao.id, mes: fatura.mes)
            } else {
                try await sessao.cliente.pagarFatura(cartao.id, mes: fatura.mes)
            }
            await carregar()
            aoMudar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}

/// Nova conta ou cartao. No cartao, o fechamento acompanha o vencimento (7 dias antes) ate a
/// pessoa mudar ele a mao.
private struct NovaContaView: View {
    let temCompanhia: Bool
    let aoCriar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var nome = ""
    @State private var tipo = ContaOuCartao.Tipo.cartao
    @State private var vencimento = ""
    @State private var fechamento = ""
    @State private var compartilhada = false
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nome", text: $nome, prompt: Text("Ex.: Nubank"))
                        .onChange(of: nome) { _, novo in if novo.count > 60 { nome = String(novo.prefix(60)) } }
                    Picker("Tipo", selection: $tipo) {
                        Text("Cartão de crédito").tag(ContaOuCartao.Tipo.cartao)
                        Text("Conta").tag(ContaOuCartao.Tipo.conta)
                    }
                    if tipo == .cartao {
                        LabeledContent("Dia do vencimento da fatura") {
                            TextField("Dia", text: Binding(get: { vencimento }, set: trocarVencimento))
                                .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                                .accessibilityLabel("Dia do vencimento da fatura")
                        }
                        LabeledContent("Dia do fechamento") {
                            TextField("Dia", text: $fechamento)
                                .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                                .accessibilityLabel("Dia do fechamento")
                        }
                    }
                    if temCompanhia {
                        Toggle("Do casal: tudo o que for lançado nela já nasce compartilhado (conta conjunta, cartão adicional)",
                               isOn: $compartilhada)
                    }
                } footer: {
                    if tipo == .cartao {
                        Text("O fechamento fica 7 dias antes do vencimento; mude se o seu banco usar outro dia. Compras feitas do dia do fechamento em diante vão para a fatura seguinte.")
                    }
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }.listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Nova conta ou cartão")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") { Task { await salvar() } }.disabled(salvando)
                }
            }
        }
    }

    private func trocarVencimento(_ novo: String) {
        let acompanhava = fechamento.isEmpty
            || (Int(vencimento).map { String(FaturaDoCartao.fechamentoPadrao($0)) } == fechamento)
        vencimento = novo
        if acompanhava, let dia = Int(novo), (1...31).contains(dia) {
            fechamento = String(FaturaDoCartao.fechamentoPadrao(dia))
        }
    }

    private func salvar() async {
        let cartao = tipo == .cartao
        let nome = nome.trimmingCharacters(in: .whitespaces)
        guard nome.count >= 2 else {
            erro = String(localized: "Digite o nome da conta ou do cartão.")
            return
        }
        let diaDoVencimento = Int(vencimento).flatMap { (1...31).contains($0) ? $0 : nil }
        let diaDoFechamento = Int(fechamento).flatMap { (1...31).contains($0) ? $0 : nil }
        if cartao && diaDoVencimento == nil {
            erro = String(localized: "Informe o dia do vencimento da fatura (1 a 31).")
            return
        }
        if cartao && diaDoFechamento == nil {
            erro = String(localized: "Informe o dia do fechamento (1 a 31).")
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.cliente.criarConta(NovaConta(
                nome: nome, tipo: tipo, fechamento: cartao ? diaDoFechamento : nil,
                vencimento: cartao ? diaDoVencimento : nil, compartilhado: temCompanhia && compartilhada))
            aoCriar()
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
