import SwiftUI

/// Pedido do link de senha nova (features/redefinir-senha/esqueci-senha). A resposta e a
/// mesma com ou sem conta, entao a tela tambem nao diz se o e-mail existe.
struct EsqueciSenhaView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar

    @State private var email = ""
    @State private var enviando = false
    @State private var enviadoPara: String?
    @State private var erro: String?

    var body: some View {
        TelaDeEntrada(titulo: "Esqueci minha senha",
                      subtitulo: enviadoPara == nil ? "Informe o e-mail da sua conta e mandamos um link para criar uma senha nova." : nil) {
            if let enviadoPara {
                AvisoDaEntrada(
                    texto: String(localized: "Se \(enviadoPara) tem conta no Piggu, mandamos um link para criar uma senha nova. Ele vale por 1 hora. Confira também a caixa de spam."),
                    tipo: .sucesso)
                BotaoDeEnviar(titulo: "Voltar para entrar") { fechar() }
            } else {
                if let erro {
                    AvisoDaEntrada(texto: erro)
                }
                CampoDaEntrada(rotulo: "E-mail") {
                    TextField("E-mail", text: $email, prompt: Text(verbatim: ""))
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.send)
                        .onSubmit { Task { await pedir() } }
                }
                BotaoDeEnviar(titulo: "Mandar o link", carregando: enviando) { await pedir() }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private func pedir() async {
        let email = email.trimmingCharacters(in: .whitespaces)
        guard Conta.pareceEmail(email) else {
            erro = String(localized: "Confira o e-mail.")
            return
        }
        erro = nil
        enviando = true
        defer { enviando = false }
        do {
            try await sessao.esqueciSenha(email: email)
            enviadoPara = email
        } catch {
            erro = error.localizedDescription
        }
    }
}
