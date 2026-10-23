import SwiftUI

struct LoginView: View {
    @Environment(Sessao.self) private var sessao

    @State private var email = ""
    @State private var senha = ""
    @State private var lembrar = true
    @State private var entrando = false
    @State private var erro: String?

    private var podeEntrar: Bool {
        !email.trimmingCharacters(in: .whitespaces).isEmpty && !senha.isEmpty && !entrando
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                VStack(spacing: 12) {
                    Image("Logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 88, height: 88)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    Text("Piggu")
                        .font(.largeTitle.bold())
                    Text("Seu dinheiro, organizado.")
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 48)

                VStack(spacing: 12) {
                    TextField("E-mail", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.next)
                        .campoDoLogin()
                    SecureField("Senha", text: $senha)
                        .textContentType(.password)
                        .submitLabel(.go)
                        .onSubmit { Task { await entrar() } }
                        .campoDoLogin()
                    Toggle("Continuar conectado", isOn: $lembrar)
                        .padding(.horizontal, 4)
                }

                if let erro {
                    Text(erro)
                        .font(.callout)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }

                Button {
                    Task { await entrar() }
                } label: {
                    Group {
                        if entrando {
                            ProgressView().tint(.white)
                        } else {
                            Text("Entrar")
                        }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 28)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(!podeEntrar)

                HStack {
                    Rectangle().frame(height: 1).foregroundStyle(.quaternary)
                    Text("ou").font(.footnote).foregroundStyle(.secondary)
                    Rectangle().frame(height: 1).foregroundStyle(.quaternary)
                }

                VStack(spacing: 10) {
                    // TODO: Google Sign-In (POST /auth/google com o ID token).
                    BotaoEmBreve(titulo: "Entrar com Google", icone: "g.circle.fill")
                    // TODO: Sign in with Apple (o backend ainda nao tem a rota).
                    BotaoEmBreve(titulo: "Entrar com Apple", icone: "apple.logo")
                    Text("Em breve no app. Por enquanto, entre com e-mail e senha.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func entrar() async {
        guard podeEntrar else { return }
        entrando = true
        erro = nil
        defer { entrando = false }
        do {
            try await sessao.entrarComSenha(email: email, senha: senha, lembrar: lembrar)
        } catch {
            self.erro = error.localizedDescription
        }
    }
}

private struct BotaoEmBreve: View {
    let titulo: String
    let icone: String

    var body: some View {
        Button {} label: {
            Label(titulo, systemImage: icone)
                .frame(maxWidth: .infinity, minHeight: 28)
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
        .disabled(true)
    }
}

private extension View {
    func campoDoLogin() -> some View {
        padding(14)
            .background(.fill.tertiary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

#if DEBUG
#Preview {
    LoginView()
        .environment(Sessao(cliente: ClienteDaAPI(transporte: TransporteDeDemonstracao())))
}
#endif
