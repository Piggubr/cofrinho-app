import SwiftUI

struct MaisView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @AppStorage(Tema.chave) private var tema = Tema.chiclete
    @State private var confirmandoSaida = false
    @State private var excluindo = false
    @State private var reenviando = false
    @State private var avisoDoEmail: String?

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

                if let usuario = sessao.usuario {
                    secaoDaConta(usuario)
                }

                Section("Termos e privacidade") {
                    NavigationLink("Termos de Uso") { TextoLegalView(documento: .termos) }
                    NavigationLink("Aviso de Privacidade") { TextoLegalView(documento: .privacidade) }
                }
                .listRowBackground(paleta.superficie)

                Section {
                    Button("Excluir minha conta", role: .destructive) { excluindo = true }
                } header: {
                    Text("Seus dados")
                } footer: {
                    Text("Excluir a conta é gratuito em qualquer plano e apaga tudo o que você lançou.")
                }
                .listRowBackground(paleta.superficie)

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
            .confirmarExclusaoDaConta(isPresented: $excluindo)
        }
    }

    /// O que falta na conta, como no Perfil do site: o e-mail sem confirmar e o CPF.
    @ViewBuilder
    private func secaoDaConta(_ usuario: Usuario) -> some View {
        if usuario.emailConfirmado == false || usuario.cpfInformado == false {
            Section("Sua conta") {
                if usuario.emailConfirmado == false {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Seu e-mail ainda não foi confirmado. Abra o link que mandamos para \(usuario.email); só então dá para aceitar convite de família.")
                            .font(.sora(.subheadline))
                        if let avisoDoEmail {
                            Text(avisoDoEmail)
                                .font(.sora(.footnote))
                                .foregroundStyle(paleta.textoSuave)
                        }
                        Button("Reenviar e-mail de confirmação") { Task { await reenviarConfirmacao() } }
                            .disabled(reenviando)
                    }
                    .padding(.vertical, 4)
                }
                if usuario.cpfInformado == false {
                    NavigationLink {
                        CpfView()
                    } label: {
                        LabeledContent("Documento") {
                            Text("Informar agora")
                        }
                    }
                }
            }
            .listRowBackground(paleta.superficie)
        }
    }

    private func reenviarConfirmacao() async {
        reenviando = true
        defer { reenviando = false }
        do {
            try await sessao.reenviarConfirmacao()
            avisoDoEmail = String(localized: "Mandamos outro link. O anterior deixou de valer.")
        } catch {
            avisoDoEmail = error.localizedDescription
        }
    }

    private func iniciais(_ nome: String) -> String {
        nome.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }
}
