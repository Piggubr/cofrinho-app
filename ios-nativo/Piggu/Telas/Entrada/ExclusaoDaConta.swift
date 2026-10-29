import SwiftUI

extension View {
    /// Pergunta, pede "EXCLUIR" e apaga a conta (DELETE /me), como o Perfil do site. A Apple
    /// exige que quem cria conta no app possa excluir pelo app.
    func confirmarExclusaoDaConta(isPresented: Binding<Bool>) -> some View {
        modifier(ExclusaoDaConta(perguntando: isPresented))
    }
}

private struct ExclusaoDaConta: ViewModifier {
    @Binding var perguntando: Bool

    @Environment(Sessao.self) private var sessao
    @State private var digitado = ""
    @State private var erro: String?

    func body(content: Content) -> some View {
        content
            .alert("Excluir minha conta", isPresented: $perguntando) {
                TextField("EXCLUIR", text: $digitado)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                Button("Excluir", role: .destructive) {
                    let confirmou = digitado.trimmingCharacters(in: .whitespaces).uppercased() == "EXCLUIR"
                    digitado = ""
                    if confirmou {
                        Task { await excluir() }
                    }
                }
                Button("Cancelar", role: .cancel) { digitado = "" }
            } message: {
                Text("Excluir sua conta apaga tudo o que você lançou. Se você for a última pessoa da família, tudo da família é apagado. Se outras pessoas ficarem, as categorias que você criou ficam com elas, sem o seu nome. Uma assinatura Premium feita por você é cancelada. Isso não tem volta. Digite EXCLUIR para confirmar.")
            }
            .alert("Não deu para excluir a conta", isPresented: Binding(get: { erro != nil }, set: { if !$0 { erro = nil } })) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(erro ?? "")
            }
    }

    private func excluir() async {
        do {
            try await sessao.excluirConta()
        } catch {
            erro = error.localizedDescription
        }
    }
}
