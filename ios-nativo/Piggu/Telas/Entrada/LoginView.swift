import AuthenticationServices
import SwiftUI

/// Entrada no app (features/login): e-mail e senha, a Apple ou o Google para a mesma conta,
/// e a aba de criar conta. Pela Apple ou pelo Google, na primeira vez o backend so cria a
/// conta com o aceite dos termos (TERMOS_NECESSARIOS), pedido aqui depois que eles confirmam.
struct LoginView: View {
    enum Modo: Hashable {
        case entrar, cadastro
    }

    /// Conta nova pela Apple ou pelo Google: falta o aceite para criar. O token ainda vale e e
    /// reaproveitado, sem pedir de novo.
    enum ContaNova {
        case google(idToken: String)
        case apple(idToken: String, nonce: String, nome: String?)
    }

    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @Environment(\.colorScheme) private var esquema

    @State private var modo = LoginView.modoInicial
    @State private var email = ""
    @State private var senha = ""
    @State private var lembrar = true
    @State private var entrando = false
    @State private var erro: String?
    @State private var informacao: String?
    @State private var contaNova: ContaNova? = LoginView.contaNovaInicial
    @State private var aceitou = false
    @State private var apple = EntradaComApple()

    var body: some View {
        NavigationStack {
            TelaDeEntrada(titulo: "Piggu", subtitulo: "O cofrinho dos fofos.") {
                if let erro {
                    AvisoDaEntrada(texto: erro)
                }
                if let informacao {
                    AvisoDaEntrada(texto: informacao, tipo: .sucesso)
                }

                if contaNova != nil {
                    aceiteDaContaNova
                } else {
                    Picker("Entrar ou criar conta", selection: $modo) {
                        Text("Entrar").tag(Modo.entrar)
                        Text("Criar conta").tag(Modo.cadastro)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: modo) { trocouDeModo() }

                    switch modo {
                    case .entrar:
                        formularioDeEntrar
                    case .cadastro:
                        CadastroView(email: $email) { enviadoPara in
                            modo = .entrar
                            informacao = String(localized: "Pronto! Mandamos um link para \(enviadoPara). Confirme seu e-mail e entre com a senha que você criou.")
                        }
                    }

                    HStack {
                        Rectangle().frame(height: 1).foregroundStyle(paleta.borda)
                        Text("ou").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                        Rectangle().frame(height: 1).foregroundStyle(paleta.borda)
                    }
                    .accessibilityHidden(true)

                    botoesDosProvedores
                }

                Toggle("Continuar conectado neste aparelho", isOn: $lembrar)
                    .font(.sora(.callout))
                    .tint(paleta.primaria)

                LinksLegais()
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: String.self) { _ in EsqueciSenhaView() }
        }
    }

    private var formularioDeEntrar: some View {
        VStack(spacing: 14) {
            CampoDaEntrada(rotulo: "E-mail") {
                TextField("E-mail", text: $email, prompt: Text(verbatim: ""))
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.next)
            }
            CampoDaEntrada(rotulo: "Senha") {
                SecureField("Senha", text: $senha, prompt: Text(verbatim: ""))
                    .textContentType(.password)
                    .submitLabel(.go)
                    .onSubmit { Task { await entrarComSenha() } }
            }
            BotaoDeEnviar(titulo: "Entrar", carregando: entrando) { await entrarComSenha() }
            NavigationLink("Esqueci minha senha", value: "esqueci-senha")
                .font(.sora(.callout, peso: .medium))
        }
    }

    @ViewBuilder
    private var botoesDosProvedores: some View {
        VStack(spacing: 12) {
            SignInWithAppleButton(.signIn) { pedido in
                apple = EntradaComApple()
                apple.preparar(pedido)
            } onCompletion: { resultado in
                Task { await voltouDaApple(resultado) }
            }
            .signInWithAppleButtonStyle(esquema == .dark ? .white : .black)
            .frame(height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .disabled(entrando)
            // O estilo e lido so na criacao: recria ao trocar de modo claro/escuro.
            .id(esquema)

            if EntradaComGoogle.disponivel {
                Button {
                    Task { await entrarComGoogle() }
                } label: {
                    HStack(spacing: 10) {
                        Image("Google").resizable().frame(width: 18, height: 18).accessibilityHidden(true)
                        Text("Fazer login com o Google")
                    }
                    .font(.sora(.headline, peso: .medium))
                    .foregroundStyle(paleta.texto)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(paleta.superficie, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(paleta.borda))
                }
                .buttonStyle(.plain)
                .disabled(entrando)
            }
        }
    }

    private var aceiteDaContaNova: some View {
        VStack(spacing: 16) {
            Text("Falta pouco para criar sua conta.")
                .font(.sora(.headline, peso: .semibold))
            AceiteDosTermos(aceitou: $aceitou)
            BotaoDeEnviar(titulo: "Criar minha conta", carregando: entrando) { await criarContaNova() }
                .disabled(!aceitou)
            Button("Voltar") {
                contaNova = nil
                aceitou = false
            }
            .font(.sora(.callout, peso: .medium))
        }
    }

    // Em Debug, `-PigguEntrada cadastro|conta-nova` abre direto na aba ou no aceite (prints).
    #if DEBUG
    private static var modoInicial: Modo {
        UserDefaults.standard.string(forKey: "PigguEntrada") == "cadastro" ? .cadastro : .entrar
    }
    private static var contaNovaInicial: ContaNova? {
        UserDefaults.standard.string(forKey: "PigguEntrada") == "conta-nova" ? .google(idToken: "demo") : nil
    }
    #else
    private static let modoInicial = Modo.entrar
    private static let contaNovaInicial: ContaNova? = nil
    #endif

    // MARK: - Acoes

    /// Troca entre entrar e criar conta; o e-mail digitado continua la.
    private func trocouDeModo() {
        erro = nil
        informacao = nil
        senha = ""
    }

    private func entrarComSenha() async {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty, !senha.isEmpty else {
            erro = String(localized: "Informe seu e-mail e sua senha.")
            return
        }
        await tentar { try await sessao.entrarComSenha(email: email, senha: senha, lembrar: lembrar) }
    }

    private func entrarComGoogle() async {
        erro = nil
        let idToken: String?
        do {
            idToken = try await EntradaComGoogle.entrar()
        } catch {
            erro = String(localized: "Não consegui abrir o login do Google. Entre com e-mail e senha.")
            return
        }
        guard let idToken else { return }
        await tentar(contaNovaSePedir: .google(idToken: idToken)) {
            try await sessao.entrarComGoogle(idToken: idToken, lembrar: lembrar)
        }
    }

    private func voltouDaApple(_ resultado: Result<ASAuthorization, Error>) async {
        erro = nil
        switch resultado {
        case let .success(autorizacao):
            guard let credencial = EntradaComApple.credencial(de: autorizacao) else {
                erro = String(localized: "Não foi possível entrar.")
                return
            }
            let nonce = apple.nonce
            await tentar(contaNovaSePedir: .apple(idToken: credencial.idToken, nonce: nonce, nome: credencial.nome)) {
                try await sessao.entrarComApple(idToken: credencial.idToken, nonce: nonce, nome: credencial.nome,
                                                lembrar: lembrar)
            }
        case let .failure(falha):
            if (falha as? ASAuthorizationError)?.code != .canceled {
                erro = String(localized: "Não foi possível entrar com a Apple. Tente de novo ou entre com e-mail e senha.")
            }
        }
    }

    /// Cria a conta da Apple ou do Google com o aceite, reaproveitando o token que ja chegou.
    private func criarContaNova() async {
        guard aceitou, let contaNova else { return }
        let versao = Conta.versaoDosTermos
        await tentar {
            switch contaNova {
            case let .google(idToken):
                try await sessao.entrarComGoogle(idToken: idToken, lembrar: lembrar, versaoDosTermos: versao)
            case let .apple(idToken, nonce, nome):
                try await sessao.entrarComApple(idToken: idToken, nonce: nonce, nome: nome, lembrar: lembrar,
                                                versaoDosTermos: versao)
            }
        }
    }

    /// - Parameter contaNovaSePedir: o que guardar quando o backend pede o aceite da conta nova.
    private func tentar(contaNovaSePedir: ContaNova? = nil, _ acao: () async throws -> Void) async {
        erro = nil
        informacao = nil
        entrando = true
        defer { entrando = false }
        do {
            try await acao()
        } catch let falha as ErroDaAPI where falha.codigo == "TERMOS_NECESSARIOS" && contaNovaSePedir != nil {
            contaNova = contaNovaSePedir
            aceitou = false
        } catch {
            erro = error.localizedDescription
        }
    }
}

#if DEBUG
#Preview {
    LoginView()
        .environment(Sessao(cliente: ClienteDaAPI(transporte: TransporteDeDemonstracao()), cache: Cache(soNaMemoria: true)))
        .environment(MonitorDeRede())
}
#endif
