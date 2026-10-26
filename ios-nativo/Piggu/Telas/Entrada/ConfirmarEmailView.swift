import SwiftUI

/// Destino do link do e-mail de confirmacao (features/confirmar-email): manda o token e
/// mostra o resultado.
struct ConfirmarEmailView: View {
    enum Estado: Equatable {
        case confirmando, pronto, erro(String)
    }

    let token: String

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @State private var estado = Estado.confirmando

    var body: some View {
        Group {
            switch estado {
            case .confirmando:
                TelaDeEntrada(titulo: "Confirmando seu e-mail…") {
                    ProgressView()
                }
            case .pronto:
                TelaDeEntrada(titulo: "E-mail confirmado", subtitulo: "Pronto, seu e-mail está confirmado no Piggu.") {
                    BotaoDeEnviar(titulo: "Abrir o Piggu") { fechar() }
                }
            case let .erro(mensagem):
                TelaDeEntrada(titulo: "Não deu para confirmar") {
                    AvisoDaEntrada(texto: mensagem)
                    BotaoDeEnviar(titulo: "Tentar de novo") { await confirmar() }
                    BotaoDeContorno(titulo: "Abrir o Piggu") { fechar() }
                }
            }
        }
        .task { await confirmar() }
    }

    private func confirmar() async {
        estado = .confirmando
        do {
            try await sessao.confirmarEmail(token: token)
            estado = .pronto
        } catch {
            estado = .erro(error.localizedDescription)
        }
    }
}
