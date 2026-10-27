import StoreKit
import SwiftUI

/// Premium (features/plan): o plano de /billing/plan e, para o titular sem assinatura na casa, a
/// assinatura pela compra da Apple (D-094). Quem ja e Premium pelo site ve o plano ativo e nao
/// encontra a loja, entao nunca e cobrado de novo. Quem assinou pelo iPhone troca de plano ou
/// cancela nas assinaturas da Apple.
struct PremiumView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<PlanoDaConta> = .carregando
    @State private var aviso: String?
    @State private var erro: String?
    @State private var restaurando = false
    @State private var gerenciando = false

    private var titular: Bool { sessao.usuario?.role == .titular }

    /// So o titular assina, e so quando ninguem da casa assinou (uma assinatura por casa).
    nonisolated static func podeAssinar(_ plano: PlanoDaConta, titular: Bool) -> Bool {
        titular && plano.plano == .gratuito && !plano.casaTemAssinatura
    }

    nonisolated static func nome(_ plano: PlanoDaConta) -> String {
        plano.planoFamilia ? String(localized: "Premium família")
            : plano.planoCasal ? String(localized: "Premium casal") : String(localized: "Premium")
    }

    /// O que a pessoa le sobre a assinatura que ja existe, como na tela de planos do site.
    nonisolated static func detalhe(_ plano: PlanoDaConta) -> String? {
        guard plano.plano == .premium else { return nil }
        if !plano.voceAssinou {
            return plano.planoCasal || plano.planoFamilia
                ? String(localized: "Quem assinou o plano da sua casa cuida da assinatura.")
                : nil
        }
        return switch plano.origem {
        case "WEB": String(localized: "Assinado pelo site. Para trocar o cartão, ver os recibos ou cancelar, entre no Piggu pelo navegador.")
        case "APP_STORE": String(localized: "Assinado pelo iPhone. Troque de plano ou cancele nas assinaturas da App Store.")
        case "PLAY_STORE": String(localized: "Assinado pelo Android. Para cancelar, use as assinaturas do Google Play.")
        default: nil
        }
    }

    var body: some View {
        Group {
            if case let .pronto(plano, guardadoEm) = carga, guardadoEm == nil, rede.conectado,
               Self.podeAssinar(plano, titular: titular) {
                loja(plano)
            } else {
                resumo
            }
        }
        .navigationTitle("Premium")
        .navigationBarTitleDisplayMode(.inline)
        .manageSubscriptionsSheet(isPresented: $gerenciando)
        .task(id: rede.conectado) { await carregar() }
    }

    // MARK: - Plano atual

    private var resumo: some View {
        List {
            if let aviso {
                Faixa(texto: aviso, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 4) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(plano, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                situacao(plano, guardado: guardadoEm != nil)
                itens("O que o Premium tem", plano.premium)
                itens("Gratuito", plano.gratuito)
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .refreshable { await carregar() }
    }

    @ViewBuilder
    private func situacao(_ plano: PlanoDaConta, guardado: Bool) -> some View {
        Section {
            if plano.plano == .premium {
                Label("Você tem o \(Self.nome(plano))", systemImage: "star.fill")
                    .font(.sora(.headline, peso: .semibold))
                    .foregroundStyle(paleta.primaria)
                if let ate = plano.premiumAte {
                    LabeledContent("Até", value: Formatos.dataCurta(String(ate.prefix(10))))
                }
                if let detalhe = Self.detalhe(plano) {
                    Text(detalhe).font(.sora(.subheadline)).foregroundStyle(paleta.textoSuave)
                }
                if plano.voceAssinou, plano.origem == "APP_STORE" {
                    Button("Gerenciar assinatura") { gerenciando = true }
                }
            } else if sessao.usuario?.role == .admin {
                Text("Como administrador, você já usa tudo do Premium.")
            } else if plano.casaTemAssinatura {
                Text(plano.planoCasal
                     ? "Sua casa tem o Premium casal, que vale para o titular e o parceiro. Quem assinou pode trocar para o plano família."
                     : "Alguém da sua família assinou o Premium individual, que vale só para essa pessoa. Ela pode trocar para o plano família.")
            } else if !titular {
                Text("Quem cuida da assinatura é o titular da família.")
            } else if guardado || !rede.conectado {
                Text("Sem internet. Para assinar, conecte-se.")
            }
            if titular { botaoDeRestaurar }
        }
        .listRowBackground(paleta.superficie)
    }

    /// Fora da barra: no iPad a barra de abas fica no topo e cobriria o botao.
    private var botaoDeRestaurar: some View {
        Button("Restaurar compras") { Task { await restaurar() } }
            .disabled(restaurando || !rede.conectado)
    }

    private func itens(_ titulo: LocalizedStringKey, _ itens: [String]) -> some View {
        Section(titulo) {
            ForEach(itens, id: \.self) { item in
                Label(item, systemImage: "checkmark").labelStyle(.titleAndIcon)
            }
        }
        .listRowBackground(paleta.superficie)
    }

    // MARK: - Loja da Apple

    private func loja(_ plano: PlanoDaConta) -> some View {
        SubscriptionStoreView(productIDs: LojaDaApple.produtos) {
            VStack(spacing: 14) {
                Image(systemName: "star.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(paleta.primaria)
                    .accessibilityHidden(true)
                Text("Piggu Premium").font(.sora(.title, peso: .bold))
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(plano.premium.filter { $0 != "Tudo do gratuito" }, id: \.self) { item in
                        Label(item, systemImage: "checkmark.circle.fill")
                            .font(.sora(.subheadline))
                            .symbolRenderingMode(.hierarchical)
                    }
                }
                botaoDeRestaurar.font(.sora(.subheadline, peso: .medium))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .subscriptionStoreButtonLabel(.multiline)
        // Restaurar fica no cabecalho (manda ao backend); fechar nao cabe numa tela empurrada.
        .storeButton(.hidden, for: .restorePurchases, .cancellation)
        .subscriptionStorePolicyDestination(for: .termsOfService) { TextoLegalView(documento: .termos) }
        .subscriptionStorePolicyDestination(for: .privacyPolicy) { TextoLegalView(documento: .privacidade) }
        .inAppPurchaseOptions { _ in
            // O backend so da o Premium para a conta marcada na compra.
            sessao.usuario.flatMap { UUID(uuidString: $0.id) }.map { [.appAccountToken($0)] } ?? []
        }
        .onInAppPurchaseCompletion { _, resultado in await concluir(resultado) }
        .tint(paleta.primaria)
        .background(paleta.fundo)
        .overlay(alignment: .top) {
            if let texto = erro ?? aviso { AvisoFlutuante(texto: texto) }
        }
    }

    private func concluir(_ resultado: Result<Product.PurchaseResult, any Error>) async {
        aviso = nil
        switch resultado {
        case let .success(.success(verificacao)):
            if await aplicar({ try await LojaDaApple.confirmar(verificacao, cliente: $0) }) {
                aviso = String(localized: "Pronto! Você agora é Premium.")
            }
        case .success(.pending):
            aviso = String(localized: "A compra está esperando aprovação. O Premium chega assim que ela for aprovada.")
        case .success(.userCancelled):
            break
        case let .failure(falha):
            erro = falha.localizedDescription
        @unknown default:
            break
        }
    }

    private func restaurar() async {
        restaurando = true
        defer { restaurando = false }
        aviso = nil
        if await aplicar({ try await LojaDaApple.restaurar(cliente: $0) }) {
            aviso = carga.valor?.plano == .premium
                ? String(localized: "Compras restauradas.")
                : String(localized: "Esta conta da Apple não tem assinatura do Piggu.")
        }
    }

    /// Manda ao backend e renova o token: o plano vai nele para os outros servicos.
    private func aplicar(_ acao: (ClienteDaAPI) async throws -> PlanoDaConta?) async -> Bool {
        erro = nil
        do {
            if let novo = try await acao(sessao.cliente) {
                carga = .pronto(novo)
            }
            _ = try? await sessao.cliente.renovar()
            return true
        } catch {
            self.erro = error.localizedDescription
            return false
        }
    }

    private func carregar() async {
        let cliente = sessao.cliente
        await sessao.cache.carregar("plano", mostrar: { carga = $0 }) {
            try await cliente.verPlano()
        }
    }
}
