import SwiftUI

struct MaisView: View {
    @Environment(Sessao.self) private var sessao
    @State private var confirmandoSaida = false

    private var versao: String {
        let info = Bundle.main.infoDictionary
        let marketing = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(marketing) (\(build))"
    }

    var body: some View {
        NavigationStack {
            List {
                if let usuario = sessao.usuario {
                    Section {
                        HStack(spacing: 14) {
                            Text(iniciais(usuario.nome))
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(width: 48, height: 48)
                                .background(Color.accentColor, in: Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text(usuario.nome).font(.headline)
                                Text(usuario.email)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                        LabeledContent("Plano", value: usuario.plano == .premium ? "Premium" : "Gratuito")
                    }
                }

                Section {
                    Button("Sair", role: .destructive) { confirmandoSaida = true }
                } footer: {
                    Text("Piggu \(versao)")
                }
            }
            .navigationTitle("Mais")
            .confirmationDialog("Sair da conta neste aparelho?", isPresented: $confirmandoSaida,
                                titleVisibility: .visible) {
                Button("Sair", role: .destructive) { Task { await sessao.sair() } }
                Button("Cancelar", role: .cancel) {}
            }
        }
    }

    private func iniciais(_ nome: String) -> String {
        nome.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }
}
