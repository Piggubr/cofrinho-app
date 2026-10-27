import SwiftUI

/// Personalizar o Painel: o que aparece e em que ordem (a tela de organizar do Perfil do site).
/// Arrastar pela alca muda a ordem; o interruptor mostra ou esconde o cartao.
struct OrganizarPainelView: View {
    let painel: PainelOrganizado

    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(painel.blocos) { bloco in
                        Toggle(bloco.id.nome, isOn: Binding(
                            get: { bloco.visivel }, set: { _ in painel.alternar(bloco.id) }))
                    }
                    .onMove { painel.mover(de: $0, para: $1) }
                } footer: {
                    Text("Fica neste aparelho: o celular pode ter uma ordem e o computador outra.")
                }
                .listRowBackground(paleta.superficie)

                Section {
                    Button("Voltar ao padrão") { painel.restaurar() }
                }
                .listRowBackground(paleta.superficie)
            }
            .environment(\.editMode, .constant(.active))
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Personalizar o Painel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Pronto") { fechar() } }
            }
        }
    }
}
