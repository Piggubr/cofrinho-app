import SwiftUI

@main
struct PigguApp: App {
    @State private var sessao: Sessao

    init() {
        #if DEBUG
        // -PigguDemo YES: dados inventados, sem rede (ver TransporteDeDemonstracao).
        let ajustes = UserDefaults.standard
        let cliente = ajustes.bool(forKey: "PigguDemo")
            ? ClienteDaAPI(transporte: TransporteDeDemonstracao(logado: ajustes.bool(forKey: "PigguDemoLogado")))
            : ClienteDaAPI()
        #else
        let cliente = ClienteDaAPI()
        #endif
        _sessao = State(initialValue: Sessao(cliente: cliente))
    }

    var body: some Scene {
        WindowGroup {
            RaizView()
                .environment(sessao)
                .environment(\.locale, Formatos.localidade)
        }
    }
}

/// Decide entre login e app pelo estado da sessao.
struct RaizView: View {
    @Environment(Sessao.self) private var sessao

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
                    ProgressView()
                }
            case .deslogado:
                LoginView()
            case .logado:
                PrincipalView()
            }
        }
        .animation(.default, value: sessao.estado)
        .task { await sessao.restaurar() }
    }
}
