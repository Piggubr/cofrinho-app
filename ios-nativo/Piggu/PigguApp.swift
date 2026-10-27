import SwiftUI

@main
struct PigguApp: App {
    @State private var sessao: Sessao
    @State private var rede = MonitorDeRede()

    init() {
        Fonte.registrar()
        #if DEBUG
        // -PigguDemo YES: dados inventados, sem rede (ver TransporteDeDemonstracao).
        let ajustes = UserDefaults.standard
        let demo = ajustes.bool(forKey: "PigguDemo")
        let cliente = demo
            ? ClienteDaAPI(transporte: TransporteDeDemonstracao(logado: ajustes.bool(forKey: "PigguDemoLogado")))
            : ClienteDaAPI()
        #else
        let demo = false
        let cliente = ClienteDaAPI()
        #endif
        _sessao = State(initialValue: Sessao(cliente: cliente, cache: Cache(soNaMemoria: demo)))
    }

    /// Hospedeiro dos testes: sem a interface, que restauraria a sessao na API de verdade a
    /// cada rodada.
    private let soHospedandoTestes = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    var body: some Scene {
        WindowGroup {
            if soHospedandoTestes {
                Color.clear
            } else {
                raiz
            }
        }
    }

    private var raiz: some View {
        RaizView()
            .environment(sessao)
            .environment(rede)
            .environment(\.locale, Formatos.localidade)
    }
}

/// Decide entre login e app pelo estado da sessao, aplica a cor do Piggu e recebe os links.
struct RaizView: View {
    @Environment(Sessao.self) private var sessao
    @AppStorage(Tema.chave) private var temaEscolhido = Tema.chiclete
    @AppStorage(ModoDeAparencia.chave) private var modo = ModoDeAparencia.automatico
    /// Link que chegou antes do login: abre depois que a pessoa entra.
    @State private var rota: Rota?
    /// Link da conta (e-mail, termos), aberto por cima de qualquer tela.
    @State private var folha: Rota?

    /// A cor do Premium volta ao Chiclete quando o Premium acaba, como no site.
    private var tema: Tema {
        temaEscolhido.premium && sessao.usuario?.plano != .premium ? .chiclete : temaEscolhido
    }

    var body: some View {
        Group {
            switch sessao.estado {
            case .verificando:
                VStack(spacing: 16) {
                    Image("Logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                        .accessibilityLabel("Piggu")
                    ProgressView()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(tema.paleta.fundo)
            case .deslogado:
                LoginView()
            case let .logado(usuario) where usuario.termosPendentes:
                AceiteView()
            case let .logado(usuario) where usuario.sobreVocePendente:
                SobreVoceView()
            case .logado:
                PrincipalView(rota: $rota)
            }
        }
        .sheet(item: $folha) { rota in
            NavigationStack {
                FolhaDaConta(rota: rota)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Fechar") { folha = nil }
                        }
                    }
            }
        }
        .environment(\.paleta, tema.paleta)
        .preferredColorScheme(modo.esquema)
        .tint(tema.paleta.primaria)
        .font(.sora())
        .animation(.default, value: sessao.estado)
        .task {
            await sessao.restaurar()
            #if DEBUG
            // -PigguLink piggu://termos: abre um link ao entrar, para os prints.
            if let link = UserDefaults.standard.string(forKey: "PigguLink"), let url = URL(string: link) {
                abrir(url)
            }
            #endif
        }
        .onOpenURL(perform: abrir)
        // Os links dos textos (Termos, Aviso de Privacidade) abrem aqui, nao no Safari.
        .environment(\.openURL, OpenURLAction { url in
            guard let rota = Rota(url: url), rota.abreFolha else { return .systemAction }
            abrir(url)
            return .handled
        })
    }

    private func abrir(_ url: URL) {
        if EntradaComGoogle.receber(url) {
            return
        }
        guard let nova = Rota(url: url) else { return }
        if nova == .cpf && sessao.usuario == nil {
            // O documento e da conta: sem sessao nao ha onde gravar.
            return
        }
        if nova.abreFolha {
            folha = nova
        } else {
            rota = nova
        }
    }
}

/// O que um link da conta abre por cima do app.
private struct FolhaDaConta: View {
    let rota: Rota

    var body: some View {
        switch rota {
        case let .confirmarEmail(token):
            ConfirmarEmailView(token: token)
        case let .redefinirSenha(token):
            RedefinirSenhaView(token: token)
        case let .textoLegal(documento):
            TextoLegalView(documento: documento)
        case .esqueciSenha:
            EsqueciSenhaView()
        case .cpf:
            CpfView()
        case .aba, .tela, .lancarGasto:
            EmptyView()
        }
    }
}
