import SwiftUI

/// Familia (features/family): quem faz parte, convites, parceria e o historico. O titular convida,
/// remove e muda o papel; quem e membro ou parceiro ve a mesma lista e pode sair da familia.
struct FamiliaView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<Familia> = .carregando
    @State private var recebidos: [ConviteDaFamilia] = []
    @State private var historico: [EventoDeAuditoria]?
    @State private var nome = ""
    @State private var email = ""
    @State private var ocupado = false
    @State private var aviso: String?
    @State private var erro: String?
    @State private var confirmacao: Confirmacao?

    private var eu: String? { sessao.usuario?.id }
    /// Titular da familia; o admin vale como titular na propria.
    private var ehTitular: Bool { [.titular, .admin].contains(sessao.usuario?.role) }

    var body: some View {
        List {
            if let aviso {
                Faixa(texto: aviso, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            ForEach(recebidos) { convite in
                Section {
                    Text("Convite para entrar na \(convite.familia ?? "")").font(.sora(.body, peso: .semibold))
                    Text("Vale até \(Formatos.dataCurta(convite.venceEm))").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    Button("Entrar nessa família") { confirmacao = .aceitarConvite(convite) }
                }
                .listRowBackground(paleta.suave)
            }
            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 3) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(familia, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                conteudo(familia)
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Família")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .confirmationDialog(confirmacao?.titulo ?? "", isPresented: Binding(
            get: { confirmacao != nil }, set: { if !$0 { confirmacao = nil } }),
            titleVisibility: .visible, presenting: confirmacao) { pedido in
            Button(pedido.botao, role: pedido.destrutiva ? .destructive : nil) { Task { await confirmar(pedido) } }
        } message: { pedido in
            Text(pedido.texto)
        }
    }

    @ViewBuilder
    private func conteudo(_ familia: Familia) -> some View {
        if familia.membros.contains(where: { $0.id == eu && $0.parceriaPendente == true }) {
            Section {
                Text("O titular convidou você para ser parceiro").font(.sora(.body, peso: .semibold))
                Text("Se aceitar, você e o titular passam a ver e editar o que um e o outro lançam, e o que lançam os menores de 18 anos. Se recusar, continua vendo só o que você lança.")
                    .font(.sora(.footnote))
                HStack {
                    Button("Aceitar") { confirmacao = .aceitarParceria }.buttonStyle(.borderedProminent)
                    Button("Recusar") {
                        Task { await executar(String(localized: "Convite recusado. Você continua vendo só o que lança.")) {
                            try await $0.recusarParceria() } }
                    }
                    .buttonStyle(.bordered).disabled(ocupado)
                }
            }
            .listRowBackground(paleta.suave)
        }

        Section {
            if ehTitular {
                HStack {
                    TextField("Nome da família", text: $nome)
                        .onChange(of: nome) { _, novo in if novo.count > 120 { nome = String(novo.prefix(120)) } }
                    Button("Salvar nome") { Task { await renomear() } }.buttonStyle(.borderless).disabled(ocupado)
                }
            }
            ForEach(familia.membros) { pessoa in
                VStack(alignment: .leading, spacing: 6) {
                    Text(pessoa.nome)
                    Text([pessoa.email ?? "", Self.papel(pessoa.papel),
                          pessoa.parceriaPendente == true ? String(localized: "convidado para parceiro") : ""]
                        .filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    if ehTitular && (pessoa.papel == .membro || pessoa.papel == .parceiro) {
                        HStack {
                            Button(pessoa.parceriaPendente == true ? "Cancelar convite de parceiro"
                                   : pessoa.papel == .parceiro ? "Voltar a membro" : "Convidar para parceiro") {
                                mudarPapel(pessoa)
                            }
                            .buttonStyle(.bordered).controlSize(.small)
                            Button("Remover", role: .destructive) { confirmacao = .remover(pessoa) }
                                .buttonStyle(.bordered).controlSize(.small)
                        }
                        .disabled(ocupado)
                    }
                }
                .padding(.vertical, 2)
            }
        } header: {
            HStack {
                Text(familia.nome)
                Spacer()
                Etiqueta(texto: Self.plano(familia))
            }
        }
        .listRowBackground(paleta.superficie)

        Section("Histórico") {
            if let historico {
                if historico.isEmpty {
                    Text("Nenhuma mudança registrada ainda.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
                ForEach(historico) { evento in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(Self.descrever(evento, membros: familia.membros))
                        if let mudanca = Self.mudanca(evento) {
                            Text(mudanca).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                        }
                        Text(Self.quando(evento)).font(.sora(.caption)).foregroundStyle(paleta.textoSuave)
                    }
                }
            } else {
                Text("Quem lançou, alterou ou apagou cada coisa, e quando.").font(.sora(.footnote))
                Button("Ver histórico") { Task { historico = await sessao.cliente.historicoDaFamilia() } }
            }
        }
        .listRowBackground(paleta.superficie)

        if ehTitular {
            Section {
                HStack {
                    TextField("E-mail", text: $email, prompt: Text("pessoa@email.com"))
                        .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .textContentType(.emailAddress)
                    Button("Convidar") { Task { await convidar() } }.buttonStyle(.borderless).disabled(ocupado)
                }
                if (familia.convites ?? []).isEmpty {
                    Text("Nenhum convite pendente.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
                ForEach(familia.convites ?? []) { convite in
                    LinhaDoCartao(titulo: convite.email, detalhe: String(localized: "Vale até \(Formatos.dataCurta(convite.venceEm))")) {
                        Button("Cancelar") {
                            Task { await executar(String(localized: "Convite cancelado.")) { try await $0.cancelarConvite(convite.id) } }
                        }
                        .buttonStyle(.bordered).controlSize(.small)
                    }
                }
            } header: {
                Text("Convidar alguém")
            } footer: {
                Text("A pessoa entra com a conta Google desse e-mail e já cai na sua família como membro (no plano casal, a primeira pessoa adulta que aceitar já entra como parceiro). O convite vale 7 dias. Cada membro vê só o que lança. Para você e seu marido ou sua esposa verem o que um e o outro lançam, use \"Convidar para parceiro\" na lista acima; vale quando a pessoa aceitar.")
            }
            .listRowBackground(paleta.superficie)
        } else {
            Section {
                Text("Só o titular convida e remove pessoas e cuida do plano.").font(.sora(.footnote))
                Button("Sair da família", role: .destructive) { confirmacao = .sair }
            }
            .listRowBackground(paleta.superficie)
        }
    }

    // MARK: - Textos do site

    nonisolated static func papel(_ papel: PigguRole) -> String {
        switch papel {
        case .admin: String(localized: "Admin")
        case .titular: String(localized: "Titular")
        case .parceiro: String(localized: "Parceiro")
        case .membro: String(localized: "Membro")
        }
    }

    nonisolated static func plano(_ familia: Familia) -> String {
        guard familia.plano == .premium else { return String(localized: "Gratuito") }
        if familia.planoFamilia == true { return String(localized: "Premium família") }
        if familia.planoCasal == true { return String(localized: "Premium casal") }
        return String(localized: "Premium individual")
    }

    nonisolated private static let acoes: [String: String.LocalizationValue] = [
        "CRIOU": "lançou", "EDITOU": "alterou", "APAGOU": "apagou", "IMPORTOU": "importou", "PAGOU": "pagou",
        "MUDOU_PAPEL": "mudou o papel de", "REMOVEU": "tirou da família", "ENTROU": "entrou na família",
        "SAIU": "saiu da família", "CONVIDOU": "convidou",
    ]

    nonisolated private static let entidades: [String: String.LocalizationValue] = [
        "gasto": "gasto", "receita": "receita", "meta": "meta do mês", "orcamento": "orçamento",
        "conta-fixa": "conta fixa", "fatura": "fatura do cartão", "deposito": "depósito no cofrinho",
    ]

    /// "Bia apagou gasto", com o nome de quem esta na familia; quem saiu aparece como ex-membro.
    nonisolated static func descrever(_ evento: EventoDeAuditoria, membros: [MembroDaFamilia]) -> String {
        let autor = PessoasDaFamilia.nome(evento.autor, em: membros)
        let acao = acoes[evento.acao].map { String(localized: $0) } ?? evento.acao
        let entidade = evento.entidade == "pessoa" ? "" : entidades[evento.entidade].map { String(localized: $0) } ?? evento.entidade
        return [autor, acao, entidade].filter { !$0.isEmpty }.joined(separator: " ")
    }

    nonisolated static func mudanca(_ evento: EventoDeAuditoria) -> String? {
        switch (evento.antes, evento.depois) {
        case let (antes?, depois?): "\(antes) → \(depois)"
        case let (antes?, nil): antes
        case let (nil, depois?): depois
        case (nil, nil): nil
        }
    }

    nonisolated static func quando(_ evento: EventoDeAuditoria) -> String {
        let leitor = ISO8601DateFormatter()
        leitor.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let data = leitor.date(from: evento.quando) ?? ISO8601DateFormatter().date(from: evento.quando)
        return data?.formatted(.dateTime.day().month(.twoDigits).year().hour().minute().locale(Formatos.localidade))
            ?? evento.quando
    }

    // MARK: - Acoes

    enum Confirmacao: Identifiable {
        case aceitarConvite(ConviteDaFamilia), aceitarParceria, convidarParceiro(MembroDaFamilia),
             voltarAMembro(MembroDaFamilia), remover(MembroDaFamilia), sair

        var id: String {
            switch self {
            case let .aceitarConvite(convite): "convite-\(convite.id)"
            case .aceitarParceria: "parceria"
            case let .convidarParceiro(pessoa): "parceiro-\(pessoa.id)"
            case let .voltarAMembro(pessoa): "membro-\(pessoa.id)"
            case let .remover(pessoa): "remover-\(pessoa.id)"
            case .sair: "sair"
            }
        }

        var titulo: String {
            switch self {
            case let .aceitarConvite(convite): String(localized: "Entrar na \(convite.familia ?? "")?")
            case .aceitarParceria: String(localized: "Aceitar ser parceiro?")
            case let .convidarParceiro(pessoa): String(localized: "Convidar \(pessoa.nome) para ser parceiro?")
            case let .voltarAMembro(pessoa): String(localized: "Voltar \(pessoa.nome) a membro?")
            case let .remover(pessoa): String(localized: "Tirar \(pessoa.nome) da família?")
            case .sair: String(localized: "Sair da família?")
            }
        }

        var texto: String {
            switch self {
            case .aceitarConvite:
                String(localized: "Você sai da família atual: se for a última pessoa dela, tudo o que está lá é apagado; se outras pessoas ficarem, o que você lançou fica com elas. Depois disso é preciso entrar de novo.")
            case .aceitarParceria:
                String(localized: "Você e o titular passam a ver e editar o que um e o outro lançam, e o que lançam os menores de 18 anos. Depois disso é preciso entrar de novo.")
            case .convidarParceiro:
                String(localized: "Quando a pessoa aceitar, vocês dois passam a ver e editar o que um e o outro lançam, e o que lançam os menores de 18 anos. Plano, convites e pessoas continuam só com você.")
            case .voltarAMembro:
                String(localized: "Essa pessoa passa a ver só o que ela lança, e você deixa de ver o dela.")
            case .remover:
                String(localized: "A pessoa leva o que lançou e a família deixa de ver.")
            case .sair:
                String(localized: "Você leva o que lançou para uma família só sua e precisa entrar de novo.")
            }
        }

        var botao: LocalizedStringKey {
            switch self {
            case .aceitarConvite: "Entrar nessa família"
            case .aceitarParceria: "Aceitar"
            case .convidarParceiro: "Convidar para parceiro"
            case .voltarAMembro: "Voltar a membro"
            case .remover: "Remover"
            case .sair: "Sair da família"
            }
        }

        var destrutiva: Bool {
            switch self {
            case .remover, .sair, .aceitarConvite: true
            default: false
            }
        }
    }

    /// O titular convida para parceiro, desiste do convite ou volta o parceiro a membro.
    private func mudarPapel(_ pessoa: MembroDaFamilia) {
        if pessoa.parceriaPendente == true {
            Task { await executar(String(localized: "Convite de parceria cancelado.")) { try await $0.mudarPapel(pessoa.id, para: .membro) } }
        } else {
            confirmacao = pessoa.papel == .parceiro ? .voltarAMembro(pessoa) : .convidarParceiro(pessoa)
        }
    }

    private func confirmar(_ pedido: Confirmacao) async {
        switch pedido {
        case let .aceitarConvite(convite):
            await sairDepois { try await $0.aceitarConvite(convite.id) }
        case .aceitarParceria:
            await sairDepois { try await $0.aceitarParceria() }
        case let .convidarParceiro(pessoa):
            await executar(String(localized: "Convite enviado. \(pessoa.nome) vê o convite na tela Família e decide se aceita.")) {
                try await $0.mudarPapel(pessoa.id, para: .parceiro)
            }
        case let .voltarAMembro(pessoa):
            await executar(String(localized: "Papel alterado. A mudança vale quando a pessoa entrar de novo.")) {
                try await $0.mudarPapel(pessoa.id, para: .membro)
            }
        case let .remover(pessoa):
            await executar(String(localized: "Pessoa removida da família.")) { try await $0.removerMembro(pessoa.id) }
        case .sair:
            do {
                try await sessao.cliente.sairDaFamilia()
                await sessao.sair()
            } catch {
                erro = error.localizedDescription
            }
        }
    }

    /// Entrar em outra familia ou virar parceiro muda o papel no token: e preciso entrar de novo.
    private func sairDepois(_ acao: (ClienteDaAPI) async throws -> Void) async {
        do {
            try await acao(sessao.cliente)
            await sessao.entrarDeNovo()
        } catch {
            erro = error.localizedDescription
        }
    }

    private func carregar() async {
        let cliente = sessao.cliente
        async let convites = try? cliente.convitesParaMim()
        await sessao.cache.carregar("familia", mostrar: { carga = $0 }) { try await cliente.verFamilia() }
        recebidos = await convites ?? recebidos
        if let familia = carga.valor, nome.isEmpty { nome = familia.nome }
    }

    private func renomear() async {
        let nome = nome.trimmingCharacters(in: .whitespaces)
        guard !nome.isEmpty else {
            erro = String(localized: "Digite o nome da família.")
            return
        }
        await executar(String(localized: "Nome salvo.")) { try await $0.renomearFamilia(nome) }
    }

    private func convidar() async {
        let email = email.trimmingCharacters(in: .whitespaces)
        guard Conta.pareceEmail(email) else {
            erro = String(localized: "Digite um e-mail válido.")
            return
        }
        await executar(String(localized: "Convite criado. Avise a pessoa para entrar com esse e-mail.")) { try await $0.convidar(email) }
        if erro == nil { self.email = "" }
    }

    private func executar(_ mensagem: String, _ acao: (ClienteDaAPI) async throws -> Familia) async {
        ocupado = true
        erro = nil
        aviso = nil
        defer { ocupado = false }
        do {
            let familia = try await acao(sessao.cliente)
            carga = .pronto(familia)
            nome = familia.nome
            aviso = mensagem
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
