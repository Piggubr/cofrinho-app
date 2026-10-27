import SwiftUI

/// Completa o cadastro (features/sobre-voce): como a pessoa quer ser chamada, quando nasceu, o
/// CPF e o celular, o mesmo que o cadastro por senha pede (D-088). Aparece uma vez para quem
/// entrou pela Apple, pelo Google ou tem conta de antes desses campos, e so com o que falta.
/// A data decide se o titular ve os dados da pessoa (13 a 17 anos) e, como o CPF, nao muda depois.
struct SobreVoceView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta

    @State private var apelido = ""
    @State private var nascimento: Date?
    @State private var documento = ""
    @State private var paisDoDdi = Pais.brasil
    @State private var telefone = ""
    @State private var salvando = false
    @State private var erro: String?

    private var pedeNascimento: Bool { (sessao.usuario?.nascimento ?? "").isEmpty }
    private var pedeCpf: Bool { sessao.usuario?.cpfInformado == false }
    private var pedeCelular: Bool { sessao.usuario?.celularInformado == false }

    var body: some View {
        TelaDeEntrada(titulo: "Sobre você",
                      subtitulo: pedeCpf || pedeCelular
                          ? "Antes de começar, complete seu cadastro."
                          : "Antes de começar, conte como quer ser chamado.") {
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
            if pedeCelular {
                CampoDaEntrada(rotulo: "Celular") {
                    CampoDeCelular(paisDoDdi: $paisDoDdi, telefone: $telefone)
                }
            }
            if pedeCpf {
                CampoDaEntrada(rotulo: "CPF", dica: "Fica guardado cifrado e não aparece para a sua família. Não muda depois.") {
                    CampoDeCpf(documento: $documento)
                }
            }
            BotaoDeEnviar(titulo: "Continuar", carregando: salvando) { await salvar() }
            BotaoDeContorno(titulo: "Sair") { Task { await sessao.sair() } }
                .disabled(salvando)
        }
        .onAppear {
            if apelido.isEmpty, let usuario = sessao.usuario {
                apelido = usuario.apelido ?? usuario.primeiroNome
                paisDoDdi = usuario.pais ?? Pais.brasil
            }
        }
    }

    private var problema: String? {
        if apelido.trimmingCharacters(in: .whitespaces).isEmpty {
            return String(localized: "Conte como podemos te chamar.")
        }
        if pedeNascimento, let problema = Conta.problemaDoNascimento(nascimento) {
            return problema
        }
        if pedeCpf, !CPF.valido(documento) {
            return documento.trimmingCharacters(in: .whitespaces).isEmpty
                ? String(localized: "Informe seu CPF.")
                : String(localized: "CPF inválido. Confira o número.")
        }
        if pedeCelular, telefone.filter(\.isNumber).count < 6 {
            return String(localized: "Informe seu celular.")
        }
        return nil
    }

    private func salvar() async {
        if let problema {
            erro = problema
            return
        }
        erro = nil
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.sobreVoce(apelido: apelido.trimmingCharacters(in: .whitespaces),
                                       nascimento: pedeNascimento ? nascimento.map(Formatos.dia) : nil,
                                       cpf: pedeCpf ? CPF.digitos(documento) : nil,
                                       telefone: pedeCelular ? CampoDeCelular.completo(paisDoDdi, telefone) : nil)
        } catch {
            erro = error.localizedDescription
        }
    }
}
