import SwiftUI

/// Os Termos mudaram desde o ultimo aceite (features/aceite): o app so abre depois daqui.
/// Quem nao concorda pode sair ou excluir a conta.
struct AceiteView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta

    @State private var salvando = false
    @State private var erro: String?
    @State private var excluindo = false

    var body: some View {
        TelaDeEntrada(titulo: "Os Termos mudaram",
                      subtitulo: "Atualizamos os Termos de Uso e o Aviso de Privacidade. Para seguir usando o Piggu, leia e aceite a versão nova.") {
            if let erro {
                AvisoDaEntrada(texto: erro)
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("O que mudou")
                    .font(.sora(.headline, peso: .semibold))
                    .accessibilityAddTraits(.isHeader)
                ForEach(Self.mudancas.indices, id: \.self) { indice in
                    Label {
                        Text(Self.mudancas[indice])
                    } icon: {
                        Circle().fill(paleta.primaria).frame(width: 6, height: 6)
                    }
                    .labelStyle(.marcador)
                }
            }
            .font(.sora(.callout))
            .frame(maxWidth: .infinity, alignment: .leading)

            Text("Leia os [Termos de Uso](piggu://termos) e o [Aviso de Privacidade](piggu://privacidade) completos (versão \(Conta.versaoDosTermos)).")
                .font(.sora(.footnote))
                .foregroundStyle(paleta.textoSuave)

            BotaoDeEnviar(titulo: "Aceitar e continuar", carregando: salvando) { await aceitar() }
            BotaoDeContorno(titulo: "Sair") { Task { await sessao.sair() } }
                .disabled(salvando)
            Button("Não concordo: excluir minha conta", role: .destructive) { excluindo = true }
                .font(.sora(.footnote, peso: .medium))
                .disabled(salvando)
        }
        .confirmarExclusaoDaConta(isPresented: $excluindo)
    }

    private static let mudancas: [LocalizedStringKey] = [
        "Na família, cada pessoa usa todas as telas e vê só o que lançou e os gastos divididos com ela.",
        "O titular e o parceiro (o casal) veem tudo um do outro e o que lança quem tem de 13 a 17 anos. Por isso pedimos a data de nascimento, e é preciso ter pelo menos 13 anos para ter conta.",
        "Quem sai da família, ou é removido, leva o que lançou. Quem exclui a conta tem o que lançou apagado.",
    ]

    private func aceitar() async {
        erro = nil
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.aceitarTermos()
        } catch {
            erro = error.localizedDescription
        }
    }
}

/// Marcador alinhado com a primeira linha do texto.
private struct EstiloDeMarcador: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            configuration.icon.alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 4 }
            configuration.title
        }
    }
}

extension LabelStyle where Self == EstiloDeMarcador {
    fileprivate static var marcador: EstiloDeMarcador { EstiloDeMarcador() }
}
