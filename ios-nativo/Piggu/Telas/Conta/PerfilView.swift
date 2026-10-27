import SwiftUI
import UIKit

/// Perfil (features/profile): foto (galeria e camera), nome, moeda e cotacao, avisos,
/// compartilhamento, o Painel, as telas do menu, aparencia, cor e icone do app, autorizacoes da
/// IA, senha, os dados da pessoa e, para o admin, as contas da instalacao.
struct PerfilView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @Environment(TravaDoApp.self) private var trava
    @AppStorage(Tema.chave) private var tema = Tema.chiclete
    @AppStorage(ModoDeAparencia.chave) private var modo = ModoDeAparencia.automatico
    @State private var membros: [MembroDaFamilia] = []
    @State private var moedas: [MoedaDisponivel] = []
    @State private var apelido = ""
    @State private var preferencias = Preferencias()
    @State private var avisoPorEmail = false
    @State private var avisoNoCelular = false
    @State private var mostrarTudoAoParceiro = false
    @State private var compartilharPorPadrao = false
    @State private var modulos: Set<String> = []
    @State private var iconeAtual = IconeDoApp.padrao
    @State private var organizando = false
    @State private var painel: PainelOrganizado?
    @State private var salvando = false
    @State private var aviso: String?
    @State private var abrindoPremium = false
    @State private var erro: String?
    @State private var excluindo = false
    @State private var contas: [Usuario] = []

    static let todosOsModulos: [(id: String, nome: LocalizedStringKey)] = [
        ("compras", "Compras"), ("lugares", "Lugares"), ("filmes", "Filmes"), ("fotos", "Fotos"),
        ("premios", "Prêmios"), ("girls-math", "Girls Math"),
    ]

    private var usuario: Usuario? { sessao.usuario }
    private var ehPremium: Bool { usuario?.plano == .premium || usuario?.role == .admin }
    private var ehTitular: Bool { [.titular, .admin].contains(usuario?.role) }
    /// O outro do casal: para o titular, o parceiro; para o parceiro, o titular.
    private var parceiro: MembroDaFamilia? {
        let outros = membros.filter { $0.id != usuario?.id }
        if usuario?.role == .parceiro { return outros.first { $0.papel == .titular || $0.papel == .admin } }
        if ehTitular { return outros.first { $0.papel == .parceiro } }
        return nil
    }

    var body: some View {
        List {
            if let aviso {
                Faixa(texto: aviso, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            if let usuario {
                identidade(usuario)
                moeda
                avisos(usuario)
                if membros.count > 1 { compartilhamento }
                seguranca
                organizar
                telasDoMenu
                aparencia
                corEIcone
                SecaoDaSenha(usuario: usuario)
                autorizacoesDaIA
                seusDados
                if usuario.role == .admin { admin }
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Perfil")
        .navigationDestination(isPresented: $abrindoPremium) { PremiumView() }
        .disabled(salvando)
        .task { await carregar() }
        .sheet(isPresented: $organizando) {
            if let painel { OrganizarPainelView(painel: painel) }
        }
        .confirmarExclusaoDaConta(isPresented: $excluindo)
    }

    // MARK: - Secoes

    private func identidade(_ usuario: Usuario) -> some View {
        Section {
            HStack(spacing: 16) {
                FotoDaPessoa(foto: usuario.foto, nome: usuario.nome, lado: 76)
                    .overlay(alignment: .bottomTrailing) {
                        Image(systemName: "camera.fill").font(.caption).padding(6)
                            .foregroundStyle(paleta.primariaContraste).background(paleta.primaria, in: Circle())
                    }
                VStack(alignment: .leading, spacing: 4) {
                    Text(usuario.nome).font(.sora(.headline, peso: .semibold))
                    Text(usuario.email).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    Etiqueta(texto: FamiliaView.papel(usuario.role))
                    NavigationLink("Ver minha família", value: TelaDoMais.familia).font(.sora(.footnote))
                }
            }
            BotoesDeFoto(tituloDaGaleria: usuario.foto == nil ? "Colocar foto" : "Trocar foto") { imagem in
                Task { await trocarFoto(imagem) }
            }
            if usuario.temFotoDoGoogle == true && (usuario.fotoPropria == true || usuario.foto == nil) {
                Button("Usar foto do Google") {
                    Task { await atualizar(String(localized: "Voltamos para a foto do Google.")) { try await $0.usarFotoDoGoogle() } }
                }
            }
            if usuario.foto != nil {
                Button("Remover foto", role: .destructive) {
                    Task { await atualizar(String(localized: "Foto removida.")) { try await $0.removerFoto() } }
                }
            }
            HStack {
                TextField("Como quer ser chamado?", text: $apelido)
                    .textContentType(.nickname)
                    .onChange(of: apelido) { _, novo in if novo.count > 120 { apelido = String(novo.prefix(120)) } }
                Button("Salvar nome") { Task { await salvarApelido() } }.buttonStyle(.borderless)
            }
        }
        .listRowBackground(paleta.superficie)
    }

    /// Cada escolha ja salva, sem botao, como no site.
    private var moeda: some View {
        Section {
            Picker("Moeda do app", selection: Binding(
                get: { preferencias.moeda ?? "BRL" }, set: { preferencias.moeda = $0; salvarPreferencias() })) {
                ForEach(moedasNaLista, id: \.codigo) { Text("\($0.codigo) · \($0.nome)").tag($0.codigo) }
            }
            Toggle("Mostrar a cotação no topo do app", isOn: Binding(
                get: { preferencias.mostrarCotacao ?? true }, set: { preferencias.mostrarCotacao = $0; salvarPreferencias() }))
            if preferencias.mostrarCotacao ?? true {
                Picker("Cotação entre", selection: Binding(
                    get: { preferencias.moedaCotacao ?? preferencias.moeda ?? "BRL" },
                    set: { preferencias.moedaCotacao = $0; salvarPreferencias() })) {
                    ForEach(moedasNaLista, id: \.codigo) { Text("\($0.codigo) · \($0.nome)").tag($0.codigo) }
                }
                Picker("e", selection: Binding(
                    get: { preferencias.moedaConversao ?? "BRL" },
                    set: { preferencias.moedaConversao = $0; salvarPreferencias() })) {
                    ForEach(moedasNaLista, id: \.codigo) { Text("\($0.codigo) · \($0.nome)").tag($0.codigo) }
                }
            }
        } header: {
            Text("Moeda")
        } footer: {
            Text("É a moeda em que os valores aparecem; os valores já lançados não são convertidos. A cotação mostra quanto vale 1 da primeira moeda na segunda, como 1 USD = R$ 5,00. Ela não muda com a moeda do app.")
        }
        .disabled(moedas.isEmpty)
        .listRowBackground(paleta.superficie)
    }

    /// A lista da API e, enquanto ela nao chega, as moedas que a pessoa ja usa.
    private var moedasNaLista: [MoedaDisponivel] {
        guard moedas.isEmpty else { return moedas }
        let codigos = Set([preferencias.moeda, preferencias.moedaCotacao, preferencias.moedaConversao].compactMap { $0 })
        return codigos.sorted().map { MoedaDisponivel(codigo: $0, nome: Formatos.nomeDaMoeda($0)) }
    }

    private func avisos(_ usuario: Usuario) -> some View {
        Section {
            Toggle("Por e-mail, para \(usuario.email)", isOn: Binding(
                get: { avisoPorEmail }, set: { avisoPorEmail = $0; Task { await salvarAvisos() } }))
            Toggle("Notificação neste iPhone", isOn: Binding(
                get: { avisoNoCelular }, set: { avisoNoCelular = $0; Task { await salvarAvisos() } }))
        } header: {
            Text("Avisos")
        } footer: {
            Text("Fatura do cartão chegando, orçamento em 80% e 100% e conta do mês vencendo, mesmo com o app fechado. No máximo uma vez por dia, e só quando há novidade.")
        }
        .listRowBackground(paleta.superficie)
    }

    /// Travar com Face ID (ou Touch ID, ou o codigo) ao abrir e ao voltar do fundo.
    private var seguranca: some View {
        Section {
            Toggle("Travar com \(trava.nomeDaBiometria ?? String(localized: "o código do iPhone"))", isOn: Binding(
                get: { trava.ligada }, set: { novo in Task { await trava.ligar(novo) } }))
        } header: {
            Text("Segurança")
        } footer: {
            Text("Pede a sua biometria ao abrir o Piggu e ao voltar para ele. Vale para este aparelho.")
        }
        .listRowBackground(paleta.superficie)
    }

    /// No iPhone a permissao do sistema vem antes de gravar: sem ela o aviso no celular fica desligado.
    private func salvarAvisos() async {
        var push = avisoNoCelular
        var recusou = false
        if push, await !Notificacoes.ativar() {
            push = false
            recusou = true
            avisoNoCelular = false
        }
        let email = avisoPorEmail
        await atualizar(recusou ? nil : String(localized: "Avisos salvos.")) { try await $0.salvarAvisos(email: email, push: push) }
        if recusou {
            erro = String(localized: "Permita as notificações do Piggu nos Ajustes do iPhone e tente de novo.")
        } else if erro != nil {
            // A chave volta para o que esta gravado: nao pode ficar ligada sem ter salvo.
            avisoPorEmail = sessao.usuario?.avisos?.email ?? false
            avisoNoCelular = sessao.usuario?.avisos?.push ?? false
        }
    }

    private var compartilhamento: some View {
        Section {
            if let parceiro {
                Toggle("\(parceiro.nome) vê tudo o que eu lanço", isOn: Binding(
                    get: { mostrarTudoAoParceiro }, set: { mostrarTudoAoParceiro = $0; salvarCompartilhamento() }))
            }
            Toggle("Lançamentos novos já nascem compartilhados", isOn: Binding(
                get: { compartilharPorPadrao }, set: { compartilharPorPadrao = $0; salvarCompartilhamento() }))
        } header: {
            Text("Compartilhamento")
        } footer: {
            Text("Cada um vê o que é seu e o que foi marcado como compartilhado. O que é só seu continua só seu, a não ser que você libere acima.")
        }
        .listRowBackground(paleta.superficie)
    }

    private var organizar: some View {
        Section {
            Button("Organizar o Painel") { organizando = true }
        } footer: {
            Text("Ligue os blocos que quer ver na tela inicial e arraste para mudar a ordem. Vale para este aparelho.")
        }
        .listRowBackground(paleta.superficie)
    }

    /// As telas de estilo de vida no menu da pessoa (PUT /auth/me/modules).
    private var telasDoMenu: some View {
        Section {
            ForEach(Self.todosOsModulos, id: \.id) { modulo in
                Toggle(modulo.nome, isOn: Binding(
                    get: { modulos.contains(modulo.id) },
                    set: { ligado in
                        if ligado { modulos.insert(modulo.id) } else { modulos.remove(modulo.id) }
                        let lista = Self.todosOsModulos.map(\.id).filter(modulos.contains)
                        Task { await atualizar(nil) { try await $0.salvarModulos(lista) } }
                    }))
            }
        } header: {
            Text("Telas do menu")
        } footer: {
            Text("Desligue as telas de estilo de vida que você não usa; elas saem do Mais.")
        }
        .listRowBackground(paleta.superficie)
    }

    private var aparencia: some View {
        Section {
            Picker("Aparência", selection: $modo) {
                ForEach(ModoDeAparencia.allCases) { Text($0.nome).tag($0) }
            }
            .pickerStyle(.segmented)
        } header: {
            Text("Aparência")
        } footer: {
            Text("No automático o Piggu segue o modo claro ou escuro do aparelho. Vale para este aparelho.")
        }
        .listRowBackground(paleta.superficie)
    }

    private var corEIcone: some View {
        Section {
            Picker("Cor do Piggu", selection: Binding(get: { tema }, set: { novo in
                if novo.premium && !ehPremium { convidarParaOPremium() } else { tema = novo }
            })) {
                ForEach(Tema.allCases) { opcao in
                    Group {
                        if opcao.premium && !ehPremium {
                            Text("\(Text(opcao.nome)) · Premium")
                        } else {
                            Text(opcao.nome)
                        }
                    }
                    .tag(opcao)
                }
            }
            if UIApplication.shared.supportsAlternateIcons {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Ícone do app").font(.sora(.subheadline, peso: .semibold))
                    Text("No modo escuro o iPhone usa a versão escura de cada ícone. O sistema avisa a cada troca.")
                        .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 12)], spacing: 12) {
                        ForEach(IconeDoApp.allCases) { icone in
                            botaoDoIcone(icone)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        } header: {
            HStack {
                Text("Cor do Piggu")
                Spacer()
                if !ehPremium { Text("Mais cores no Premium").font(.sora(.caption, peso: .semibold)) }
            }
        } footer: {
            Text("Vale para este aparelho e acompanha o modo claro ou escuro.")
        }
        .listRowBackground(paleta.superficie)
    }

    private func botaoDoIcone(_ icone: IconeDoApp) -> some View {
        let travado = icone.premium && !ehPremium
        let escolhido = icone == iconeAtual
        return Button {
            Task { await escolher(icone) }
        } label: {
            VStack(spacing: 4) {
                icone.previa.resizable().scaledToFit().frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .stroke(escolhido ? paleta.primaria : .clear, lineWidth: 3).padding(-3))
                    .overlay(alignment: .topTrailing) {
                        if travado {
                            Image(systemName: "star.fill").font(.caption2).padding(4)
                                .foregroundStyle(paleta.primariaContraste).background(paleta.primaria, in: Circle())
                                .offset(x: 6, y: -6)
                        }
                    }
                    .opacity(travado ? 0.6 : 1)
                Text(icone.nome).font(.sora(.caption2)).lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(icone.nome)
        .accessibilityAddTraits(escolhido ? .isSelected : [])
    }

    private var autorizacoesDaIA: some View {
        Section {
            Button("Retirar autorização da foto da nota") {
                Task { await executar(String(localized: "Pronto. A foto da nota só vai ao Google se você autorizar de novo.")) {
                    try await $0.retirarAutorizacaoDaLeituraDeNota() } }
            }
            Button("Retirar autorização do assistente") {
                Task { await executar(String(localized: "Pronto. O assistente só envia dados ao Google se você autorizar de novo.")) {
                    try await $0.retirarAutorizacaoDoAssistente() } }
            }
        } header: {
            Text("Autorizações da IA")
        } footer: {
            Text("Você autoriza o envio ao Google (Gemini) na primeira vez que lê uma nota pela foto ou pergunta ao assistente. Pode retirar quando quiser; o Piggu volta a perguntar antes de enviar.")
        }
        .listRowBackground(paleta.superficie)
    }

    private var seusDados: some View {
        Section {
            ShareLink(item: ArquivoDosMeusDados(cliente: sessao.cliente),
                      preview: SharePreview(String(localized: "Meus dados do Piggu"))) {
                Label("Baixar meus dados", systemImage: "square.and.arrow.down")
            }
            Button("Excluir minha conta", role: .destructive) { excluindo = true }
        } header: {
            Text("Seus dados")
        } footer: {
            Text("Baixe uma cópia de tudo o que o Piggu guarda sobre você ou exclua a conta. Os dois são gratuitos em qualquer plano.")
        }
        .listRowBackground(paleta.superficie)
    }

    private var admin: some View {
        Section("Contas da instalação") {
            if contas.isEmpty {
                Text("Nenhuma conta criada ainda.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(contas, id: \.id) { conta in
                VStack(alignment: .leading, spacing: 6) {
                    Text(conta.nome)
                    Text(conta.email).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    HStack {
                        Picker("Perfil", selection: Binding(get: { conta.role }, set: { papel in
                            Task { await executar(nil) { try await $0.alterarConta(conta.id, papel: papel) }; await carregarContas() }
                        })) {
                            ForEach([PigguRole.admin, .titular, .parceiro, .membro], id: \.self) { Text($0.rawValue).tag($0) }
                        }
                        Spacer()
                        Button(conta.ativo ? "Desativar" : "Ativar") {
                            Task { await executar(nil) { try await $0.alterarConta(conta.id, ativo: !conta.ativo) }; await carregarContas() }
                        }
                        .buttonStyle(.bordered).controlSize(.small)
                    }
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    // MARK: - API

    private func carregar() async {
        guard let usuario else { return }
        apelido = usuario.apelido ?? ""
        preferencias = usuario.preferencias ?? Preferencias()
        avisoPorEmail = usuario.avisos?.email ?? false
        avisoNoCelular = usuario.avisos?.push ?? false
        mostrarTudoAoParceiro = usuario.compartilhamento?.mostraTudoAoParceiro ?? false
        compartilharPorPadrao = usuario.compartilhamento?.compartilharPorPadrao ?? false
        // Ausente (conta antiga, demo) liga todas, como no site.
        modulos = Set(usuario.preferencias?.modulos ?? Self.todosOsModulos.map(\.id))
        iconeAtual = IconeDoApp.atual
        painel = PainelOrganizado(usuario: usuario.id)
        async let membros = try? sessao.cliente.verFamilia().membros
        async let moedas = try? sessao.cliente.listarMoedas()
        self.membros = await membros ?? []
        self.moedas = await moedas ?? []
        if usuario.role == .admin { await carregarContas() }
    }

    private func carregarContas() async {
        contas = (try? await sessao.cliente.listarContas()) ?? contas
    }

    private func trocarFoto(_ imagem: UIImage) async {
        guard let jpeg = Imagem.fotoDePerfil(imagem) else {
            erro = String(localized: "Não consegui ler essa imagem.")
            return
        }
        await atualizar(String(localized: "Foto trocada.")) { try await $0.trocarFoto(jpeg: jpeg) }
    }

    private func salvarApelido() async {
        let apelido = apelido.trimmingCharacters(in: .whitespaces)
        guard !apelido.isEmpty else {
            erro = String(localized: "Conte como podemos te chamar.")
            return
        }
        await atualizar(String(localized: "Nome salvo.")) { try await $0.salvarApelido(apelido) }
    }

    private func salvarPreferencias() {
        let pedido = Preferencias(moeda: preferencias.moeda ?? "BRL",
                                  moedaCotacao: preferencias.moedaCotacao ?? preferencias.moeda,
                                  moedaConversao: preferencias.moedaConversao ?? "BRL",
                                  mostrarCotacao: preferencias.mostrarCotacao ?? true)
        Task {
            await atualizar(nil) { try await $0.salvarPreferencias(pedido) }
            // Nao salvou: os campos voltam para o que esta gravado.
            if erro != nil { preferencias = sessao.usuario?.preferencias ?? Preferencias() }
        }
    }

    private func salvarCompartilhamento() {
        let mostrar = mostrarTudoAoParceiro && parceiro != nil
        let padrao = compartilharPorPadrao
        Task {
            await atualizar(String(localized: "Compartilhamento salvo.")) {
                try await $0.salvarCompartilhamento(mostrarTudoAoParceiro: mostrar, compartilharPorPadrao: padrao)
            }
            if erro != nil {
                mostrarTudoAoParceiro = sessao.usuario?.compartilhamento?.mostraTudoAoParceiro ?? false
                compartilharPorPadrao = sessao.usuario?.compartilhamento?.compartilharPorPadrao ?? false
            }
        }
    }

    private func escolher(_ icone: IconeDoApp) async {
        guard !(icone.premium && !ehPremium) else {
            convidarParaOPremium()
            return
        }
        do {
            try await icone.aplicar()
            iconeAtual = IconeDoApp.atual
        } catch {
            // A pessoa recusou ou o sistema nao deixou agora: o icone fica como estava.
            iconeAtual = IconeDoApp.atual
        }
    }

    /// Quem nao assina pela familia recebe o recado; o titular vai para os planos.
    private func convidarParaOPremium() {
        if ehTitular {
            abrindoPremium = true
        } else {
            aviso = String(localized: "Essa personalização é do Piggu Premium. Quem cuida da assinatura é o titular da família.")
        }
    }

    private func atualizar(_ mensagem: String?, _ acao: (ClienteDaAPI) async throws -> Usuario) async {
        salvando = true
        erro = nil
        aviso = nil
        defer { salvando = false }
        do {
            sessao.atualizar(try await acao(sessao.cliente))
            aviso = mensagem
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func executar(_ mensagem: String?, _ acao: (ClienteDaAPI) async throws -> Void) async {
        erro = nil
        aviso = nil
        do {
            try await acao(sessao.cliente)
            aviso = mensagem
        } catch {
            self.erro = error.localizedDescription
        }
    }
}

/// Senha de acesso: criar (conta so do Google) ou trocar, com o e-mail sem confirmar e o CPF.
/// Erro e aviso ficam no proprio cartao: o topo da tela fica longe dali.
private struct SecaoDaSenha: View {
    let usuario: Usuario

    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @State private var atual = ""
    @State private var nova = ""
    @State private var repita = ""
    @State private var salvando = false
    @State private var reenviando = false
    @State private var aviso: String?
    @State private var erro: String?

    var body: some View {
        Section {
            if usuario.emailConfirmado == false {
                Text("Seu e-mail ainda não foi confirmado. Abra o link que mandamos para \(usuario.email) (ou entre uma vez pelo Google com ele); só então dá para aceitar convite de família.")
                    .font(.sora(.footnote))
                Button("Reenviar e-mail de confirmação") { Task { await reenviar() } }.disabled(reenviando)
            }
            if usuario.cpfInformado == false {
                LabeledContent("Documento ainda não informado (opcional).") {
                    Link("Informar agora", destination: URL(string: "piggu://cpf")!)
                }
                .font(.sora(.footnote))
            }
            if let erro { Text(erro).foregroundStyle(paleta.perigo).font(.sora(.footnote)) }
            if let aviso { Text(aviso).foregroundStyle(paleta.sucesso).font(.sora(.footnote)) }
            if usuario.temSenha == true {
                SecureField("Senha atual", text: $atual).textContentType(.password)
            }
            SecureField("Nova senha", text: $nova).textContentType(.newPassword)
            SecureField("Repita a nova senha", text: $repita).textContentType(.newPassword)
            Button(usuario.temSenha == true ? "Trocar senha" : "Criar senha") { Task { await salvar() } }
                .disabled(salvando)
        } header: {
            Text("Senha de acesso")
        } footer: {
            if usuario.temSenha == true {
                Text("Você entra com \(usuario.email) e sua senha, ou pelo Google. Trocar a senha encerra as sessões nos outros aparelhos. Pelo menos 8 caracteres, com letras e números.")
            } else {
                Text("Hoje você entra pelo Google. Crie uma senha para entrar também com \(usuario.email). Pelo menos 8 caracteres, com letras e números.")
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private func salvar() async {
        erro = nil
        aviso = nil
        let trocando = usuario.temSenha == true
        if trocando && atual.isEmpty {
            erro = String(localized: "Informe a senha atual.")
            return
        }
        if let problema = Conta.problemaDaSenha(nova, email: usuario.email) {
            erro = problema
            return
        }
        guard nova == repita else {
            erro = String(localized: "As duas senhas não são iguais.")
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            sessao.atualizar(try await sessao.cliente.definirSenha(nova, senhaAtual: trocando ? atual : nil))
            atual = ""
            nova = ""
            repita = ""
            aviso = trocando ? String(localized: "Senha trocada. Os outros aparelhos vão pedir para entrar de novo.")
                : String(localized: "Senha criada. Agora você também entra com e-mail e senha.")
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func reenviar() async {
        reenviando = true
        defer { reenviando = false }
        do {
            try await sessao.reenviarConfirmacao()
            aviso = String(localized: "Mandamos um link novo. Ele vale por 24 horas.")
        } catch {
            erro = error.localizedDescription
        }
    }
}

/// O JSON de tudo o que o Piggu guarda sobre a pessoa, baixado so quando ela escolhe onde mandar.
struct ArquivoDosMeusDados: Transferable {
    let cliente: ClienteDaAPI

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .json) { arquivo in
            let dados = try await arquivo.cliente.exportarMeusDados()
            let destino = FileManager.default.temporaryDirectory.appending(path: "piggu-meus-dados.json")
            try dados.write(to: destino, options: .atomic)
            return SentTransferredFile(destino)
        }
    }
}
