import SwiftUI

/// Destino do link de senha nova (features/redefinir-senha). Funciona sem sessao: quem
/// esqueceu a senha nao esta logado. Depois de trocar, todas as sessoes da conta caem.
struct RedefinirSenhaView: View {
    enum Estado {
        case formulario, pronto, linkRuim
    }

    let token: String

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar

    @State private var estado: Estado
    @State private var senha = ""
    @State private var confirmacao = ""
    @State private var salvando = false
    @State private var erro: String?

    init(token: String) {
        self.token = token
        _estado = State(initialValue: token.isEmpty ? .linkRuim : .formulario)
    }

    var body: some View {
        switch estado {
        case .formulario:
            TelaDeEntrada(titulo: "Senha nova", subtitulo: "Escolha a senha que você vai usar para entrar no Piggu.") {
                if let erro {
                    AvisoDaEntrada(texto: erro)
                }
                CampoDaEntrada(rotulo: "Senha nova", dica: "Pelo menos 8 caracteres, com letras e números.") {
                    SecureField("Senha nova", text: $senha, prompt: Text(verbatim: ""))
                        .textContentType(.newPassword)
                }
                CampoDaEntrada(rotulo: "Repita a senha") {
                    SecureField("Repita a senha", text: $confirmacao, prompt: Text(verbatim: ""))
                        .textContentType(.newPassword)
                }
                BotaoDeEnviar(titulo: "Salvar a senha nova", carregando: salvando) { await salvar() }
            }
        case .pronto:
            TelaDeEntrada(titulo: "Senha nova salva",
                          subtitulo: "Pronto. Por segurança, saímos da sua conta em todos os aparelhos; entre com a senha nova.") {
                BotaoDeEnviar(titulo: "Entrar") { fechar() }
            }
        case .linkRuim:
            TelaDeEntrada(titulo: "Link vencido") {
                AvisoDaEntrada(texto: String(localized: "Este link venceu ou já foi usado. Peça outro e use em até 1 hora."))
                NavigationLink {
                    EsqueciSenhaView()
                } label: {
                    Text("Pedir outro link")
                        .font(.sora(.headline, peso: .semibold))
                        .frame(maxWidth: .infinity, minHeight: 28)
                }
                .botaoPrincipal()
                .controlSize(.large)
            }
        }
    }

    private func salvar() async {
        if let problema = Conta.problemaDaSenha(senha) {
            erro = problema
            return
        }
        guard senha == confirmacao else {
            erro = String(localized: "As duas senhas não são iguais.")
            return
        }
        erro = nil
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.redefinirSenha(token: token, novaSenha: senha)
            estado = .pronto
        } catch let falha as ErroDaAPI where falha.codigo == "LINK_INVALIDO" {
            estado = .linkRuim
        } catch {
            erro = error.localizedDescription
        }
    }
}
