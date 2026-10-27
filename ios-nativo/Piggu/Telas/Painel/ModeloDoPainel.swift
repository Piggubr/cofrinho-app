import Foundation
import Observation

/// Tudo o que os cartoes do Painel mostram e fazem (dashboard.ts e os cartoes dele).
///
/// O nucleo (cofrinho, gastos do mes e limites) vem junto, como o forkJoin do site, e fica no
/// cache para abrir sem internet. O resto e informativo: cada parte que falha some sozinha e o
/// Painel segue, como no site.
@MainActor
@Observable
final class ModeloDoPainel {
    struct Nucleo: Codable, Equatable, Sendable {
        let cofrinho: Cofrinho
        let gastos: [Gasto]
        /// aaaa-mm para o limite daquele mes.
        let metas: [String: Decimal]
    }

    var carga: Carga<Nucleo> = .carregando
    var resumo: ResumoDoMes?
    var contasFixas: [ContaFixa] = []
    var orcamentos: [Orcamento] = []
    var cartoes: [ContaOuCartao] = []
    var aReceber: [AReceber] = []
    /// nil enquanto carrega.
    var objetivos: [ObjetivoDoCofrinho]?
    var notas: [Nota]?
    var metasDoMes: [MetaDoMes]?
    var girlsMath: [ItemDoGirlsMath]?
    var membros: [MembroDaFamilia] = []
    let openFinance: OpenFinance
    var cotacao: Cotacao?
    /// Erro de uma acao (depositar, apagar...), por cartao.
    var erros: [IdDoBloco: String] = [:]

    @ObservationIgnored let sessao: Sessao
    private var cliente: ClienteDaAPI { sessao.cliente }

    init(sessao: Sessao) {
        self.sessao = sessao
        openFinance = OpenFinance(cliente: sessao.cliente)
    }

    var eu: String? { sessao.usuario?.id }
    /// Mora mais alguem na casa: so assim faz sentido o "compartilhado".
    var temCompanhia: Bool { membros.count > 1 }
    var ehPremium: Bool { sessao.usuario?.plano == .premium }

    func ehMeu(_ usuario: String?) -> Bool { usuario == nil || usuario == eu }
    func nome(_ usuario: String?) -> String { PessoasDaFamilia.nome(usuario, em: membros) }

    // MARK: - Carregar

    func carregar(mes: String, visao: Visao) async {
        let sufixo = visao == .tudo ? "" : "/\(visao.rawValue)"
        let cliente = cliente
        let extras = Task { await carregarExtras(mes: mes) }
        let resumo = Task {
            await sessao.cache.carregar("resumo/\(mes)\(sufixo)", mostrar: { carga in
                if let valor = carga.valor { self.resumo = valor }
            }) { try await cliente.resumoDoMes(mes, visao: visao) }
        }
        await sessao.cache.carregar("painel/\(mes)\(sufixo)", mostrar: { nova in
            carga = nova
            if nova.valor != nil { Desempenho.painelPronto() }
        }) {
            async let cofrinho = cliente.consultarCofrinho()
            async let gastos = cliente.listarGastos(mes: mes, visao: visao)
            async let metas = cliente.listarMetas()
            return try await Nucleo(cofrinho: cofrinho, gastos: gastos, metas: metas)
        }
        await resumo.value
        await extras.value
    }

    private func carregarExtras(mes: String) async {
        let cliente = cliente
        async let contasFixas = try? cliente.contasFixas(mes)
        async let orcamentos = try? cliente.orcamentos(mes)
        async let cartoes = try? cliente.contas()
        async let aReceber = try? cliente.aReceber()
        async let membros = try? cliente.verFamilia().membros
        self.contasFixas = await contasFixas ?? []
        self.orcamentos = await orcamentos ?? []
        self.cartoes = await cartoes ?? []
        self.aReceber = await aReceber ?? []
        self.membros = await membros ?? []
        async let objetivos: Void = recarregarObjetivos()
        async let notas: Void = carregarNotas()
        async let metas: Void = carregarMetasDoMes()
        async let girls: Void = carregarGirlsMath()
        async let bancos: Void = openFinance.carregar()
        async let cotacao: Void = carregarCotacao()
        _ = await (objetivos, notas, metas, girls, bancos, cotacao)
    }

    private func carregarNotas() async {
        do {
            notas = try await cliente.listarNotas()
        } catch {
            notas = notas ?? []
            erros[.notas] = error.localizedDescription
        }
    }

    private func carregarMetasDoMes() async {
        do {
            metasDoMes = try await cliente.listarMetasDoMes()
            erros[.metas] = nil
        } catch {
            metasDoMes = []
            erros[.metas] = error.localizedDescription
        }
    }

    private func carregarGirlsMath() async {
        do {
            girlsMath = try await cliente.listarGirlsMath()
            erros[.girlsMath] = nil
        } catch {
            girlsMath = []
            erros[.girlsMath] = error.localizedDescription
        }
    }

    /// A cotacao do topo (shell.ts): so ligada no Perfil e com as duas pontas diferentes.
    private func carregarCotacao() async {
        let preferencias = sessao.usuario?.preferencias
        guard preferencias?.mostrarCotacao == true, let de = preferencias?.moedaCotacao ?? preferencias?.moeda,
              let para = preferencias?.moedaConversao, de != para else {
            cotacao = nil
            return
        }
        // A cotacao e informativa: sem ela o app segue normalmente.
        cotacao = try? await cliente.consultarCotacao(de: de, para: para)
    }

    // MARK: - Contas do nucleo

    func gastoNoMes(_ nucleo: Nucleo) -> Decimal {
        nucleo.gastos.reduce(0) { $0 + $1.valor }
    }

    /// Para o guia de primeiro uso: algum limite em qualquer mes, algum gasto ja lancado.
    func temLimite(_ nucleo: Nucleo) -> Bool { nucleo.metas.values.contains { $0 > 0 } }
    func temGasto(_ nucleo: Nucleo) -> Bool { !nucleo.gastos.isEmpty || nucleo.cofrinho.totalGastos > 0 }

    /// Total de cada categoria no mes, do maior para o menor.
    func porCategoria(_ nucleo: Nucleo) -> [(categoria: String, total: Decimal)] {
        Dictionary(grouping: nucleo.gastos, by: \.categoria)
            .map { ($0.key, $0.value.reduce(0) { $0 + $1.valor }) }
            .sorted { $0.1 > $1.1 }
    }

    // MARK: - Avisos (avisos-do-mes.ts)

    var orcamentosEmAlerta: [Orcamento] { orcamentos.filter { $0.alerta != .ok } }
    var contasEmAberto: [ContaFixa] { contasFixas.filter { $0.situacao != .paga } }
    /// Fatura fechada, com valor e ainda nao paga (D-085), que vence nos proximos 10 dias.
    var faturasAVencer: [ContaOuCartao] {
        let limite = Formatos.dia(Date().addingTimeInterval(10 * 86_400))
        return cartoes.filter {
            $0.faturaAPagar?.paga != true && ($0.faturaAPagar?.total ?? 0) > 0
                && ($0.faturaAPagar?.vencimento ?? "") <= limite
        }
    }
    /// Quem passou da data combinada e ainda nao pagou.
    var devendo: [AReceber] {
        let hoje = Formatos.dia(Date())
        return aReceber.filter { $0.data < hoje }
    }

    // MARK: - Cofrinho e objetivos

    func depositar(_ valor: Decimal, objetivo: String? = nil, mes: String, visao: Visao) async -> Bool {
        let bloco: IdDoBloco = objetivo == nil ? .cofrinho : .objetivos
        guard valor > 0 else {
            erros[bloco] = String(localized: "Digite um valor de depósito válido.")
            return false
        }
        erros[bloco] = nil
        do {
            try await cliente.depositar(NovoDeposito(valor: valor, data: Formatos.dia(Date()), objetivo: objetivo))
            await recarregarCofrinho()
            if objetivo != nil { await recarregarObjetivos() }
            return true
        } catch {
            erros[bloco] = error.localizedDescription
            return false
        }
    }

    func excluirDeposito(_ id: String) async {
        do {
            try await cliente.excluirDeposito(id)
            await recarregarCofrinho()
        } catch {
            erros[.cofrinho] = error.localizedDescription
        }
    }

    func recarregarCofrinho() async {
        guard case let .pronto(nucleo, guardadoEm) = carga else { return }
        do {
            let cofrinho = try await cliente.consultarCofrinho()
            carga = .pronto(Nucleo(cofrinho: cofrinho, gastos: nucleo.gastos, metas: nucleo.metas), guardadoEm: guardadoEm)
        } catch {
            erros[.cofrinho] = error.localizedDescription
        }
    }

    func recarregarObjetivos() async {
        do {
            objetivos = try await cliente.listarObjetivos()
        } catch {
            objetivos = objetivos ?? []
            erros[.objetivos] = error.localizedDescription
        }
    }

    /// Espelha o backend: o gratuito tem um objetivo (o que o parceiro compartilhou nao conta).
    var podeCriarObjetivo: Bool {
        ehPremium || (objetivos ?? []).filter { ehMeu($0.usuario) }.count < 1
    }

    func salvarObjetivo(_ pedido: PedidoDeObjetivo, id: String?) async throws {
        try await cliente.salvarObjetivo(pedido, id: id)
        await recarregarObjetivos()
    }

    func excluirObjetivo(_ objetivo: ObjetivoDoCofrinho) async {
        do {
            try await cliente.excluirObjetivo(objetivo.id)
            await recarregarObjetivos()
            await recarregarCofrinho()
        } catch {
            erros[.objetivos] = error.localizedDescription
        }
    }

    // MARK: - Notas

    func criarNota(titulo: String, texto: String) async -> Bool {
        let titulo = titulo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !titulo.isEmpty else {
            erros[.notas] = String(localized: "Digite o título da nota.")
            return false
        }
        erros[.notas] = nil
        do {
            let texto = texto.trimmingCharacters(in: .whitespacesAndNewlines)
            let nota = try await cliente.criarNota(NovaNota(titulo: titulo, texto: texto.isEmpty ? nil : texto))
            notas = [nota] + (notas ?? [])
            return true
        } catch {
            erros[.notas] = error.localizedDescription
            return false
        }
    }

    func excluirNota(_ nota: Nota) async {
        do {
            try await cliente.excluirNota(nota.id)
            notas?.removeAll { $0.id == nota.id }
        } catch {
            erros[.notas] = error.localizedDescription
        }
    }

    // MARK: - Primeiros passos

    func usarMoeda(_ codigo: String) async -> Bool {
        let atuais = sessao.usuario?.preferencias
        let preferencias = Preferencias(
            moeda: codigo, moedaCotacao: nil, moedaConversao: atuais?.moedaConversao ?? "BRL",
            mostrarCotacao: atuais?.mostrarCotacao ?? true)
        do {
            sessao.atualizar(try await cliente.salvarPreferencias(preferencias))
            erros[.resumo] = nil
            return true
        } catch {
            erros[.resumo] = error.localizedDescription
            return false
        }
    }
}
