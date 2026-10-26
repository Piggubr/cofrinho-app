import SwiftUI

/// Como a pessoa quer ser chamada e quando nasceu (features/sobre-voce). Aparece uma vez
/// para quem entrou pela Apple, pelo Google ou tem conta de antes desses campos. A data
/// decide se o titular ve os dados da pessoa (13 a 17 anos) e nao muda depois.
struct SobreVoceView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta

    @State private var apelido = ""
    @State private var nascimento: Date?
    @State private var salvando = false
    @State private var erro: String?

    private var pedeNascimento: Bool { (sessao.usuario?.nascimento ?? "").isEmpty }

    var body: some View {
        TelaDeEntrada(titulo: "Sobre você", subtitulo: "Antes de começar, conte como quer ser chamado.") {
            if let erro {
                AvisoDaEntrada(texto: erro)
            }
            CampoDaEntrada(rotulo: "Como quer ser chamado?") {
                TextField("Como quer ser chamado?", text: $apelido, prompt: Text(verbatim: ""))
                    .textContentType(.nickname)
            }
            if pedeNascimento {
                CampoDeNascimento(data: $nascimento)
                Text("A data não muda depois. Quem tem entre 13 e 17 anos tem os lançamentos visíveis para o titular e o parceiro da família.")
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
            }
            BotaoDeEnviar(titulo: "Continuar", carregando: salvando) { await salvar() }
            BotaoDeContorno(titulo: "Sair") { Task { await sessao.sair() } }
                .disabled(salvando)
        }
        .onAppear {
            if apelido.isEmpty, let usuario = sessao.usuario {
                apelido = usuario.apelido ?? usuario.primeiroNome
            }
        }
    }

    private func salvar() async {
        let problema = apelido.trimmingCharacters(in: .whitespaces).isEmpty
            ? String(localized: "Conte como podemos te chamar.")
            : pedeNascimento ? Conta.problemaDoNascimento(nascimento) : nil
        if let problema {
            erro = problema
            return
        }
        erro = nil
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.sobreVoce(apelido: apelido.trimmingCharacters(in: .whitespaces),
                                       nascimento: pedeNascimento ? nascimento.map(Formatos.dia) : nil)
        } catch {
            erro = error.localizedDescription
        }
    }
}
