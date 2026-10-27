import SwiftUI

/// Mais: as telas que nao cabem nas abas, nos grupos e na ordem do menu do site (shell.ts), com o
/// Perfil no topo. O Premium e so do titular, como no site; as telas de estilo de vida seguem os
/// modulos ligados no Perfil.
struct MaisView: View {
    @Binding var caminho: [TelaDoMais]

    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @State private var confirmandoSaida = false

    private var versao: String {
        let info = Bundle.main.infoDictionary
        let marketing = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(marketing) (\(build))"
    }

    /// Ausente (conta antiga, demo) liga todas, como no site.
    private func ligado(_ modulo: String) -> Bool {
        sessao.usuario?.preferencias?.modulos?.contains(modulo) ?? true
    }

    private var ehTitular: Bool { [.titular, .admin].contains(sessao.usuario?.role) }

    var body: some View {
        NavigationStack(path: $caminho) {
            List {
                if let usuario = sessao.usuario {
                    Section {
                        NavigationLink(value: TelaDoMais.perfil) {
                            HStack(spacing: 14) {
                                FotoDaPessoa(foto: usuario.foto, nome: usuario.nome, lado: 48)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(usuario.nome).font(.sora(.headline, peso: .semibold))
                                    Text(usuario.email).font(.sora(.subheadline)).foregroundStyle(paleta.textoSuave)
                                }
                            }
                            .padding(.vertical, 4)
                            .accessibilityElement(children: .combine)
                            .accessibilityHint("Abre o Perfil")
                        }
                        LabeledContent("Plano", value: usuario.plano == .premium ? "Premium" : "Gratuito")
                    }
                    .listRowBackground(paleta.superficie)
                }

                grupo("Planejar", [.planejar, .metas, .orcamento, .contasDoMes])
                grupo("Contas", [.cartoes, .calendario])
                Section("Estilo de vida") {
                    ForEach([("compras", "Compras", "cart", "/compras"), ("lugares", "Lugares", "mappin.and.ellipse", "/lugares"),
                             ("filmes", "Filmes", "film", "/filmes"), ("fotos", "Fotos", "photo", "/feed")], id: \.0) { item in
                        if ligado(item.0) {
                            LinkDoSite(titulo: LocalizedStringKey(item.1), caminho: item.3)
                        }
                    }
                    ForEach([TelaDoMais.premios, .girlsMath], id: \.self) { tela in
                        if tela.modulo.map(ligado) ?? true { link(tela) }
                    }
                }
                .listRowBackground(paleta.superficie)
                Section("Conta") {
                    link(.familia)
                    if ehTitular {
                        LinkDoSite(titulo: "Premium", caminho: "/plano")
                    }
                }
                .listRowBackground(paleta.superficie)

                Section("Termos e privacidade") {
                    NavigationLink("Termos de Uso") { TextoLegalView(documento: .termos) }
                    NavigationLink("Aviso de Privacidade") { TextoLegalView(documento: .privacidade) }
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
            .comTelasDoMais()
            .confirmationDialog("Sair da conta neste aparelho?", isPresented: $confirmandoSaida,
                                titleVisibility: .visible) {
                Button("Sair", role: .destructive) { Task { await sessao.sair() } }
                Button("Cancelar", role: .cancel) {}
            }
        }
    }

    private func grupo(_ titulo: LocalizedStringKey, _ telas: [TelaDoMais]) -> some View {
        Section(titulo) {
            ForEach(telas, id: \.self) { link($0) }
        }
        .listRowBackground(paleta.superficie)
    }

    private func link(_ tela: TelaDoMais) -> some View {
        NavigationLink(value: tela) { Label(tela.titulo, systemImage: tela.simbolo) }
    }
}
