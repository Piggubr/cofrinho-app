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

    var body: some Scene {
        WindowGroup {
            RaizView()
                .environment(sessao)
                .environment(rede)
                .environment(\.locale, Formatos.localidade)
        }
    }
}

/// Decide entre login e app pelo estado da sessao, aplica a cor do Piggu e recebe os links.
struct RaizView: View {
    @Environment(Sessao.self) private var sessao
    @AppStorage(Tema.chave) private var temaEscolhido = Tema.chiclete
    /// Link que chegou antes do login: abre depois que a pessoa entra.
    @State private var rota: Rota?

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
            case .logado:
                PrincipalView(rota: $rota)
            }
        }
        .environment(\.paleta, tema.paleta)
        .tint(tema.paleta.primaria)
        .font(.sora())
        .animation(.default, value: sessao.estado)
        .task { await sessao.restaurar() }
        .onOpenURL { url in
            rota = Rota(url: url)
        }
    }
}
