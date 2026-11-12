import Foundation

/// Onde fica a API. Ordem: argumento de lancamento ou ajuste (`-PigguAPIURL http://...`),
/// depois o Info.plist (build setting `PIGGU_API_URL`), depois o padrao de producao.
enum ConfiguracaoDaAPI {
    static let padrao = URL(string: "https://piggu.com.br/api")!

    static var urlBase: URL {
        if let texto = UserDefaults.standard.string(forKey: "PigguAPIURL"), let url = URL(string: texto) {
            return url
        }
        if let texto = Bundle.main.object(forInfoDictionaryKey: "PigguAPIURL") as? String,
           !texto.isEmpty, !texto.hasPrefix("$("), let url = URL(string: texto) {
            return url
        }
        return padrao
    }

    /// Refresh no corpo e no Keychain em vez do cookie (D-072). Desligado ate a etapa 1 do
    /// backend chegar a producao: argumento `-PigguRefreshNoCorpo YES` ou o build setting
    /// `PIGGU_REFRESH_NO_CORPO`.
    static var refreshNoCorpo: Bool {
        if UserDefaults.standard.object(forKey: "PigguRefreshNoCorpo") != nil {
            return UserDefaults.standard.bool(forKey: "PigguRefreshNoCorpo")
        }
        return (Bundle.main.object(forInfoDictionaryKey: "PigguRefreshNoCorpo") as? String) == "YES"
    }
}

enum ErroDaAPI: LocalizedError, Equatable {
    /// Sem rede, tempo esgotado, servidor fora do ar antes de responder.
    case semConexao
    /// O Cloudflare Access desviou o pedido para a tela de login dele.
    case bloqueadoPeloAccess
    /// O 401 persistiu depois da renovacao: so entrando de novo.
    case sessaoEncerrada
    /// `codigo` e o do corpo de erro (TERMOS_NECESSARIOS, LINK_INVALIDO, EM_BREVE...).
    case servidor(status: Int, mensagem: String?, codigo: String? = nil)
    case respostaInvalida

    /// O codigo do backend, para as telas que tratam um erro especifico.
    var codigo: String? {
        if case let .servidor(_, _, codigo) = self { codigo } else { nil }
    }

    var errorDescription: String? {
        switch self {
        case .semConexao:
            return String(localized: "Sem conexão com o servidor. Confira a internet e tente de novo.")
        case .bloqueadoPeloAccess:
            return String(localized: "O acesso ao servidor está protegido e o app ainda não consegue passar por essa proteção.")
        case .sessaoEncerrada:
            return String(localized: "Sua sessão venceu. Entre novamente.")
        case let .servidor(status, mensagem, codigo):
            // Gateway com o servico fora do ar ou lento (D-191): o texto do contrato, com ou sem corpo.
            if status == 503 || status == 504 || codigo == "SERVICO_INDISPONIVEL" {
                return String(localized: "Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.")
            }
            if let mensagem, !mensagem.isEmpty {
                return mensagem
            }
            return status >= 500
                ? String(localized: "O Piggu não está respondendo agora (erro \(status)). Tente daqui a pouco.")
                : String(localized: "Não deu certo (erro \(status)).")
        case .respostaInvalida:
            return String(localized: "O Piggu respondeu algo que o app não entendeu.")
        }
    }
}

/// Quem leva o pedido ao servidor. Em producao e o URLSession; nos testes e na
/// demonstracao, um dublê que responde sem rede.
protocol Transporte: Sendable {
    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse)
}

struct TransporteURLSession: Transporte {
    /// A sessao padrao usa o HTTPCookieStorage compartilhado: e ali que fica o cookie
    /// HttpOnly do refresh, enviado sozinho nas chamadas para /api/auth.
    var sessao: URLSession = .shared

    /// O sistema grava esse cookie em Library/Cookies, que entra no backup do iCloud e do
    /// computador. Fora do backup, o refresh de 30 dias fica so neste iPhone, como no Keychain
    /// (`ThisDeviceOnly`). Roda uma vez por abertura, antes do primeiro pedido.
    static let cookiesForaDoBackup: Void = {
        var pasta = URL.libraryDirectory.appending(path: "Cookies", directoryHint: .isDirectory)
        var valores = URLResourceValues()
        valores.isExcludedFromBackup = true
        try? FileManager.default.createDirectory(at: pasta, withIntermediateDirectories: true)
        try? pasta.setResourceValues(valores)
    }()

    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        _ = Self.cookiesForaDoBackup
        let (dados, resposta) = try await sessao.data(for: pedido)
        guard let http = resposta as? HTTPURLResponse else {
            throw ErroDaAPI.respostaInvalida
        }
        return (dados, http)
    }
}

private struct PedidoDeSenha: Codable, Sendable {
    let senhaAtual: String?
    let novaSenha: String
    let refreshToken: String?
}

/// Resposta sem corpo (204, logout).
struct SemCorpo: Codable, Sendable {}

/// Cliente unico da API do Piggu.
///
/// O backend trabalha com dois tokens: o access, curto, que vai no header de cada
/// chamada e fica so em memoria; e o refresh, longo. O refresh vem num cookie HttpOnly com
/// Path=/api/auth ou, com o `cofre`, no corpo, e fica no Keychain (D-072). Um 401 dispara
/// uma unica renovacao e a chamada e repetida uma vez, como o interceptor do app web.
actor ClienteDaAPI {
    nonisolated let urlBase: URL
    private let transporte: Transporte
    private var accessToken: String?
    /// Com o cofre, o refresh fica aqui e no Keychain (so com "continuar conectado").
    private let cofre: CofreDoRefresh?
    private var refresh: String?
    private var lembrarRefresh = false
    private var renovacaoEmCurso: Task<ParDeTokens, Error>?
    /// Sobe a cada saida: renovacao que termina depois de sair e descartada (o actor e reentrante).
    private var geracao = 0
    private var aoRenovar: (@Sendable (Usuario) async -> Void)?
    private var aoPerderSessao: (@Sendable () async -> Void)?

    private let decodificador = JSONDecoder()
    private let codificador = JSONEncoder()

    init(
        urlBase: URL = ConfiguracaoDaAPI.urlBase,
        transporte: Transporte = TransporteURLSession(),
        cofre: CofreDoRefresh? = ConfiguracaoDaAPI.refreshNoCorpo ? Keychain() : nil
    ) {
        self.urlBase = urlBase
        self.transporte = transporte
        self.cofre = cofre
        refresh = cofre?.ler()
        lembrarRefresh = refresh != nil
    }

    /// O login pede o refresh no corpo (`refreshNoCorpo`) so quando ha onde guarda-lo.
    nonisolated var pedeRefreshNoCorpo: Bool { cofre != nil }

    func configurar(
        aoRenovar: @escaping @Sendable (Usuario) async -> Void,
        aoPerderSessao: @escaping @Sendable () async -> Void
    ) {
        self.aoRenovar = aoRenovar
        self.aoPerderSessao = aoPerderSessao
    }

    func guardar(_ tokens: ParDeTokens, lembrar: Bool = true) {
        accessToken = tokens.accessToken
        lembrarRefresh = lembrar
        guardarRefresh(tokens.refreshToken)
    }

    /// Avisa o servidor (que encerra a sessao e apaga o cookie) e esquece os tokens. Falhar
    /// aqui nao pode impedir a pessoa de sair deste aparelho.
    func sair() async {
        // A renovacao em curso termina descartada, e o logout leva o refresh que ela trouxe.
        geracao += 1
        let renovado = try? await renovacaoEmCurso?.value
        let corpo = (try? codificador.encode(PedidoDeRefresh(refreshToken: renovado?.refreshToken ?? refresh)))
            ?? Data("{}".utf8)
        _ = try? await executar(metodo: "POST", caminho: "/auth/logout", query: [:], corpo: corpo, aberta: true)
        esquecerTokens()
    }

    func esquecerTokens() {
        geracao += 1
        accessToken = nil
        refresh = nil
        cofre?.apagar()
    }

    private func guardarRefresh(_ novo: String?) {
        guard let cofre, let novo else { return }
        refresh = novo
        if lembrarRefresh {
            cofre.guardar(novo)
        } else {
            cofre.apagar()
        }
    }

    var temToken: Bool { accessToken != nil }

    /// Cria a senha (conta so do Google) ou troca a atual. A sessao deste aparelho continua: o
    /// refresh vai junto (no corpo, com o Keychain; senao no cookie) e o backend so derruba as outras.
    func definirSenha(_ novaSenha: String, senhaAtual: String?) async throws -> Usuario {
        try await put("/auth/me/senha", corpo: PedidoDeSenha(senhaAtual: senhaAtual, novaSenha: novaSenha,
                                                              refreshToken: refresh))
    }

    // MARK: - Verbos

    /// - Parameter aberta: rotas que servem para obter um token nao levam o header.
    func get<Resposta: Decodable & Sendable>(
        _ caminho: String,
        query: [String: String?] = [:],
        aberta: Bool = false
    ) async throws -> Resposta {
        let dados = try await executar(metodo: "GET", caminho: caminho, query: query, corpo: nil, aberta: aberta)
        return try decodificar(dados)
    }

    func post<Corpo: Encodable & Sendable, Resposta: Decodable & Sendable>(
        _ caminho: String,
        corpo: Corpo,
        query: [String: String?] = [:],
        aberta: Bool = false
    ) async throws -> Resposta {
        try await enviar("POST", caminho, corpo: corpo, query: query, aberta: aberta)
    }

    func patch<Corpo: Encodable & Sendable, Resposta: Decodable & Sendable>(
        _ caminho: String,
        corpo: Corpo
    ) async throws -> Resposta {
        try await enviar("PATCH", caminho, corpo: corpo, query: [:], aberta: false)
    }

    /// PATCH sem corpo (marcar comprado, assistido): o que importa vai na query.
    func patch<Resposta: Decodable & Sendable>(_ caminho: String, query: [String: String?]) async throws -> Resposta {
        let dados = try await executar(metodo: "PATCH", caminho: caminho, query: query, corpo: nil, aberta: false)
        return try decodificar(dados)
    }

    /// PUT sem corpo (marcar a fatura como paga): o que importa vai na query.
    func put(_ caminho: String, query: [String: String?]) async throws {
        _ = try await executar(metodo: "PUT", caminho: caminho, query: query, corpo: nil, aberta: false)
    }

    /// A resposta como veio (o CSV dos gastos), sem decodificar.
    func dados(_ caminho: String, query: [String: String?] = [:]) async throws -> Data {
        try await executar(metodo: "GET", caminho: caminho, query: query, corpo: nil, aberta: false)
    }

    func put<Corpo: Encodable & Sendable, Resposta: Decodable & Sendable>(
        _ caminho: String,
        corpo: Corpo
    ) async throws -> Resposta {
        try await enviar("PUT", caminho, corpo: corpo, query: [:], aberta: false)
    }

    func delete(_ caminho: String, query: [String: String?] = [:]) async throws {
        _ = try await executar(metodo: "DELETE", caminho: caminho, query: query, corpo: nil, aberta: false)
    }

    /// DELETE que devolve o recurso atualizado (desconectar banco devolve as contas que sobram).
    func delete<Resposta: Decodable & Sendable>(_ caminho: String) async throws -> Resposta {
        let dados = try await executar(metodo: "DELETE", caminho: caminho, query: [:], corpo: nil, aberta: false)
        return try decodificar(dados)
    }

    private func enviar<Corpo: Encodable & Sendable, Resposta: Decodable & Sendable>(
        _ metodo: String,
        _ caminho: String,
        corpo: Corpo,
        query: [String: String?],
        aberta: Bool
    ) async throws -> Resposta {
        let json = try codificador.encode(corpo)
        let dados = try await executar(metodo: metodo, caminho: caminho, query: query, corpo: json, aberta: aberta)
        return try decodificar(dados)
    }

    // MARK: - Renovacao

    /// Renova o access pelo refresh (cookie ou corpo). Chamadas simultaneas esperam a mesma renovacao:
    /// o backend rotaciona o refresh a cada uso, e duas renovacoes paralelas
    /// invalidariam uma a outra.
    @discardableResult
    func renovar() async throws -> ParDeTokens {
        if let renovacaoEmCurso {
            return try await renovacaoEmCurso.value
        }
        let tarefa = Task { try await self.pedirRenovacao() }
        renovacaoEmCurso = tarefa
        defer { renovacaoEmCurso = nil }
        let geracaoDoPedido = geracao

        let tokens: ParDeTokens
        do {
            tokens = try await tarefa.value
        } catch let ErroDaAPI.servidor(status, _, _) where status == 401 || status == 403 {
            // Refresh recusado (vencido, revogado ou conta desativada): nao serve mais.
            esquecerTokens()
            throw ErroDaAPI.servidor(status: status, mensagem: nil)
        }
        // Saiu enquanto a renovacao ia e voltava: a sessao nao volta.
        guard geracao == geracaoDoPedido else { throw ErroDaAPI.sessaoEncerrada }
        accessToken = tokens.accessToken
        guardarRefresh(tokens.refreshToken)
        await aoRenovar?(tokens.usuario)
        return tokens
    }

    private func pedirRenovacao() async throws -> ParDeTokens {
        let corpo = try refresh.map { try codificador.encode(PedidoDeRefresh(refreshToken: $0)) } ?? Data("{}".utf8)
        let dados = try await executar(metodo: "POST", caminho: "/auth/refresh", query: [:], corpo: corpo, aberta: true)
        return try decodificar(dados)
    }

    // MARK: - Execucao

    private func executar(
        metodo: String,
        caminho: String,
        query: [String: String?],
        corpo: Data?,
        aberta: Bool
    ) async throws -> Data {
        // Abrindo o app, as telas pedem antes de o token chegar: esperam a renovacao em curso
        // em vez de levar um 401 cada uma.
        var token = accessToken
        if !aberta, token == nil, let renovacaoEmCurso {
            token = try? await renovacaoEmCurso.value.accessToken
        }
        let pedido = try montar(metodo: metodo, caminho: caminho, query: query, corpo: corpo,
                                token: aberta ? nil : token)
        let (dados, resposta) = try await chamar(pedido)

        guard resposta.statusCode == 401, !aberta else {
            return try validar(dados, resposta)
        }

        let tokens: ParDeTokens
        do {
            tokens = try await renovar()
        } catch let ErroDaAPI.servidor(status, _, _) where status == 401 || status == 403 {
            // So o refresh recusado encerra a sessao (D-075). Sem rede, servidor fora ou 429 so
            // repassam o erro: o refresh ainda vale para a proxima vez.
            accessToken = nil
            await aoPerderSessao?()
            throw ErroDaAPI.sessaoEncerrada
        }

        let repetido = try montar(metodo: metodo, caminho: caminho, query: query, corpo: corpo,
                                  token: tokens.accessToken)
        let (novosDados, novaResposta) = try await chamar(repetido)
        if novaResposta.statusCode == 401 {
            accessToken = nil
            await aoPerderSessao?()
            throw ErroDaAPI.sessaoEncerrada
        }
        return try validar(novosDados, novaResposta)
    }

    private func montar(
        metodo: String,
        caminho: String,
        query: [String: String?],
        corpo: Data?,
        token: String?
    ) throws -> URLRequest {
        var base = urlBase.absoluteString
        while base.hasSuffix("/") {
            base.removeLast()
        }
        guard var componentes = URLComponents(string: base + caminho) else {
            throw ErroDaAPI.respostaInvalida
        }
        let itens = query
            .compactMap { chave, valor in valor.flatMap { $0.isEmpty ? nil : URLQueryItem(name: chave, value: $0) } }
            .sorted { $0.name < $1.name }
        if !itens.isEmpty {
            componentes.queryItems = itens
        }
        guard let url = componentes.url else {
            throw ErroDaAPI.respostaInvalida
        }

        var pedido = URLRequest(url: url)
        pedido.httpMethod = metodo
        pedido.setValue("application/json", forHTTPHeaderField: "Accept")
        // Mesmo site que a API: o cookie SameSite=Strict do refresh pode ir junto.
        pedido.mainDocumentURL = urlBase
        if let corpo {
            pedido.httpBody = corpo
            pedido.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token {
            pedido.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return pedido
    }

    private func chamar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let resultado: (Data, HTTPURLResponse)
        do {
            resultado = try await transporte.enviar(pedido)
        } catch let erro as ErroDaAPI {
            throw erro
        } catch {
            throw ErroDaAPI.semConexao
        }
        // O URLSession segue redirects: se o Access desviou, a resposta final e a tela dele.
        if let host = resultado.1.url?.host, host.hasSuffix("cloudflareaccess.com") {
            throw ErroDaAPI.bloqueadoPeloAccess
        }
        return resultado
    }

    private func validar(_ dados: Data, _ resposta: HTTPURLResponse) throws -> Data {
        guard (200..<300).contains(resposta.statusCode) else {
            let corpo = try? decodificador.decode(CorpoDeErro.self, from: dados)
            throw ErroDaAPI.servidor(status: resposta.statusCode, mensagem: corpo?.erro, codigo: corpo?.codigo)
        }
        return dados
    }

    private func decodificar<Resposta: Decodable>(_ dados: Data) throws -> Resposta {
        if Resposta.self == SemCorpo.self, let vazio = SemCorpo() as? Resposta {
            return vazio
        }
        do {
            return try decodificador.decode(Resposta.self, from: dados)
        } catch {
            throw ErroDaAPI.respostaInvalida
        }
    }
}
