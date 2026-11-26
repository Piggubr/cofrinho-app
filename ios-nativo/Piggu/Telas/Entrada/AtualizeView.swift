import SwiftUI

/// Cobre o app quando o gateway recusa esta versao (426 ATUALIZE_O_APP, D-201). Sem saida: com a
/// versao velha a API nao responde, e so a App Store resolve. O texto e o mesmo do Android.
struct AtualizeView: View {
    @Environment(\.paleta) private var paleta
    @Environment(\.openURL) private var abrirLink

    var body: some View {
        ContentUnavailableView {
            Label("Atualize o Piggu", systemImage: "arrow.down.app")
        } description: {
            Text("Esta versão do app não funciona mais. Instale a versão nova na App Store para continuar.")
        } actions: {
            Button("Abrir a App Store") { abrirLink(ConfiguracaoDaAPI.linkDaLoja) }
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(paleta.fundo)
    }
}

#Preview {
    AtualizeView()
}
