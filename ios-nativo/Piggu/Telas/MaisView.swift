import SwiftUI

struct MaisView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @AppStorage(Tema.chave) private var tema = Tema.chiclete
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
                                .font(.sora(.headline, peso: .semibold))
                                .foregroundStyle(paleta.primariaContraste)
                                .frame(width: 48, height: 48)
                                .background(paleta.primaria, in: Circle())
                                .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(usuario.nome).font(.sora(.headline, peso: .semibold))
                                Text(usuario.email)
                                    .font(.sora(.subheadline))
                                    .foregroundStyle(paleta.textoSuave)
                            }
                        }
                        .padding(.vertical, 4)
                        .accessibilityElement(children: .combine)
                        LabeledContent("Plano", value: usuario.plano == .premium ? "Premium" : "Gratuito")
                    }
                    .listRowBackground(paleta.superficie)

                    Section {
                        Picker("Cor do Piggu", selection: $tema) {
                            ForEach(Tema.allCases) { opcao in
                                Group {
                                    if opcao.premium {
                                        Text("\(Text(opcao.nome)) · Premium")
                                    } else {
                                        Text(opcao.nome)
                                    }
                                }
                                .tag(opcao)
                                    .selectionDisabled(opcao.premium && usuario.plano != .premium)
                            }
                        }
                    } header: {
                        Text("Aparência")
                    } footer: {
                        Text("A cor fica neste aparelho.")
                    }
                    .listRowBackground(paleta.superficie)
                }

                Section {
                    Button("Sair", role: .destructive) { confirmandoSaida = true }
                } footer: {
                    Text("Piggu \(versao)")
                }
                .listRowBackground(paleta.superficie)
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
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
