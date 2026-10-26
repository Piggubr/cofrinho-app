import SwiftUI

struct LoginView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta

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
                        .font(.sora(.largeTitle, peso: .bold))
                        .foregroundStyle(paleta.primaria)
                    Text("Seu dinheiro, organizado.")
                        .foregroundStyle(paleta.textoSuave)
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

                if !rede.conectado {
                    Label("Você está sem internet. Confira o Wi-Fi ou os dados móveis.", systemImage: "wifi.slash")
                        .font(.sora(.callout))
                        .foregroundStyle(paleta.alerta)
                        .frame(maxWidth: .infinity)
                }

                if let erro {
                    Text(erro)
                        .font(.sora(.callout))
                        .foregroundStyle(paleta.perigo)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }

                Button {
                    Task { await entrar() }
                } label: {
                    Group {
                        if entrando {
                            ProgressView().tint(paleta.primariaContraste)
                        } else {
                            Text("Entrar")
                        }
                    }
                    .font(.sora(.headline, peso: .semibold))
                    .frame(maxWidth: .infinity, minHeight: 28)
                }
                .botaoPrincipal()
                .controlSize(.large)
                .disabled(!podeEntrar)

                HStack {
                    Rectangle().frame(height: 1).foregroundStyle(paleta.borda)
                    Text("ou").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    Rectangle().frame(height: 1).foregroundStyle(paleta.borda)
                }

                VStack(spacing: 10) {
                    // TODO: Google Sign-In (POST /auth/google com o ID token).
                    BotaoEmBreve(titulo: "Entrar com Google", icone: "g.circle.fill")
                    // TODO: Sign in with Apple (POST /auth/apple, etapa 3).
                    BotaoEmBreve(titulo: "Entrar com Apple", icone: "apple.logo")
                    Text("Em breve no app. Por enquanto, entre com e-mail e senha.")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(paleta.fundo)
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

private struct CampoDoLogin: ViewModifier {
    @Environment(\.paleta) private var paleta

    func body(content: Content) -> some View {
        content
            .padding(14)
            .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(paleta.borda))
    }
}

private extension View {
    func campoDoLogin() -> some View {
        modifier(CampoDoLogin())
    }
}

#if DEBUG
#Preview {
    LoginView()
        .environment(Sessao(cliente: ClienteDaAPI(transporte: TransporteDeDemonstracao()), cache: Cache(soNaMemoria: true)))
        .environment(MonitorDeRede())
}
#endif
