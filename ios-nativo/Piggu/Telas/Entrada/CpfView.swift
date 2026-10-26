import SwiftUI

/// Informar o CPF (features/cpf), opcional; o Perfil oferece esta tela a quem ainda nao
/// informou. O CPF prende a conta a uma pessoa.
struct CpfView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @Environment(\.dismiss) private var fechar

    @State private var pais = Pais.brasil
    @State private var documento = ""
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        TelaDeEntrada(titulo: "Seu documento",
                      subtitulo: "Cada pessoa tem uma conta no Piggu. O documento garante isso: ele fica preso à sua conta e não pode ser usado em outra.") {
            if let erro {
                AvisoDaEntrada(texto: erro)
            }
            CampoDaEntrada(rotulo: "País onde você mora") {
                SeletorDePais(pais: $pais)
            }
            CampoDaEntrada(rotulo: "CPF") {
                CampoDeCpf(documento: $documento)
            }
            BotaoDeEnviar(titulo: "Continuar", carregando: salvando) { await salvar() }
            Text("Guardamos o documento cifrado e usamos só para isso. Ele não aparece para a sua família e sai inteiro apenas quando você baixa os seus dados. Detalhes no [Aviso de Privacidade](piggu://privacidade). Já tem conta com este documento ou quer corrigir? Escreva para \(Conta.contatoDePrivacidade).")
                .font(.sora(.footnote))
                .foregroundStyle(paleta.textoSuave)
            BotaoDeContorno(titulo: "Agora não") { fechar() }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let atual = sessao.usuario?.pais, !atual.isEmpty {
                pais = atual
            }
        }
    }

    private func salvar() async {
        guard CPF.valido(documento) else {
            erro = documento.trimmingCharacters(in: .whitespaces).isEmpty
                ? String(localized: "Informe o número do seu documento (CPF).")
                : String(localized: "CPF inválido. Confira o número.")
            return
        }
        erro = nil
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.informarPais(pais, documento: CPF.digitos(documento))
            fechar()
        } catch {
            erro = error.localizedDescription
        }
    }
}
