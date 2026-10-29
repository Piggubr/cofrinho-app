import StoreKit

/// Premium no iPhone pela compra da Apple (StoreKit 2), D-094. A compra leva o id da conta Piggu
/// (appAccountToken) e a transacao assinada vai ao backend, que confere com a Apple e atualiza o
/// mesmo plano que a Stripe atualiza no site. Renovacao, cancelamento e reembolso chegam ao
/// backend pelos avisos da App Store, sem depender do app aberto.
enum LojaDaApple {
    /// Os produtos do grupo "Piggu Premium" no App Store Connect (e no Piggu.storekit).
    static let produtos = ["br.com.piggu.premium.mensal", "br.com.piggu.premium.anual"]

    /// So finaliza depois que o backend aceitou: se a rede cair, a Apple entrega de novo em
    /// `Transaction.updates`. Recusa definitiva (outra conta, nao e do Piggu) tambem finaliza,
    /// senao a mesma transacao voltaria a cada abertura.
    static func confirmar(_ verificacao: VerificationResult<Transaction>, cliente: ClienteDaAPI) async throws -> PlanoDaConta {
        guard case let .verified(transacao) = verificacao else {
            throw ErroDaAPI.servidor(status: 422, mensagem: String(localized: "A Apple não confirmou esta compra."))
        }
        do {
            let plano = try await cliente.confirmarCompraDaApple(verificacao.jwsRepresentation)
            await transacao.finish()
            return plano
        } catch let erro as ErroDaAPI {
            if case let .servidor(status, _, _) = erro, (400..<500).contains(status), status != 401 {
                await transacao.finish()
            }
            throw erro
        }
    }

    /// Restaurar compras: sincroniza com a Apple e manda ao backend as assinaturas vigentes.
    /// - Returns: o plano depois disso; nil quando esta conta da Apple nao tem assinatura do Piggu.
    static func restaurar(cliente: ClienteDaAPI) async throws -> PlanoDaConta? {
        try await AppStore.sync()
        var plano: PlanoDaConta?
        for await verificacao in Transaction.currentEntitlements
        where produtos.contains(verificacao.unsafePayloadValue.productID) {
            plano = try await confirmar(verificacao, cliente: cliente)
        }
        return plano
    }

    /// Renovacao, compra em outro aparelho, "Pedir para comprar" aprovado: chega aqui enquanto o
    /// app esta aberto. Sem ninguem logado fica sem finalizar e volta na proxima abertura.
    @MainActor
    static func acompanhar(_ sessao: Sessao) async {
        for await verificacao in Transaction.updates {
            guard sessao.usuario != nil else { continue }
            if (try? await confirmar(verificacao, cliente: sessao.cliente)) != nil {
                // O plano vai no token: renovar leva o Premium aos outros servicos.
                _ = try? await sessao.cliente.renovar()
            }
        }
    }
}
