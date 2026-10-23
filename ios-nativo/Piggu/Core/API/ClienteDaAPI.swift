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
}

enum ErroDaAPI: LocalizedError, Equatable {
    /// Sem rede, tempo esgotado, servidor fora do ar antes de responder.
    case semConexao
    /// O Cloudflare Access desviou o pedido para a tela de login dele.
    case bloqueadoPeloAccess
    /// O 401 persistiu depois da renovacao: so entrando de novo.
    case sessaoEncerrada
    case servidor(status: Int, mensagem: String?)
    case respostaInvalida

    var errorDescription: String? {
        switch self {
        case .semConexao:
            return "Sem conexão com o Piggu. Confira a internet e tente de novo."
        case .bloqueadoPeloAccess:
            return "O acesso ao servidor está protegido e o app ainda não consegue passar por essa proteção."
        case .sessaoEncerrada:
            return "Sua sessão venceu. Entre novamente."
        case let .servidor(status, mensagem):
            if let mensagem, !mensagem.isEmpty {
                return mensagem
            }
            return status >= 500
                ? "O Piggu não está respondendo agora (erro \(status)). Tente daqui a pouco."
                : "Não deu certo (erro \(status))."
        case .respostaInvalida:
            return "O Piggu respondeu algo que o app não entendeu."
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

    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (dados, resposta) = try await sessao.data(for: pedido)
        guard let http = resposta as? HTTPURLResponse else {
            throw ErroDaAPI.respostaInvalida
        }
        return (dados, http)
    }
}

/// Resposta sem corpo (204, logout).
struct SemCorpo: Codable, Sendable {}

/// Cliente unico da API do Piggu.
///
/// O backend trabalha com dois tokens: o access, curto, que vai no header de cada
/// chamada e fica so em memoria; e o refresh, longo, num cookie HttpOnly com
/// Path=/api/auth. Um 401 dispara uma unica renovacao e a chamada e repetida uma vez,
/// como o interceptor do app web.
actor ClienteDaAPI {
    nonisolated let urlBase: URL
    private let transporte: Transporte
    private var accessToken: String?
    private var renovacaoEmCurso: Task<ParDeTokens, Error>?
    private var aoRenovar: (@Sendable (Usuario) async -> Void)?
    private var aoPerderSessao: (@Sendable () async -> Void)?

    private let decodificador = JSONDecoder()
    private let codificador = JSONEncoder()

    init(urlBase: URL = ConfiguracaoDaAPI.urlBase, transporte: Transporte = TransporteURLSession()) {
        self.urlBase = urlBase
        self.transporte = transporte
    }

    func configurar(
        aoRenovar: @escaping @Sendable (Usuario) async -> Void,
        aoPerderSessao: @escaping @Sendable () async -> Void
    ) {
        self.aoRenovar = aoRenovar
        self.aoPerderSessao = aoPerderSessao
    }

    func guardar(_ tokens: ParDeTokens) {
        accessToken = tokens.accessToken
    }

    func esquecerToken() {
        accessToken = nil
    }

    var temToken: Bool { accessToken != nil }

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
        aberta: Bool = false
    ) async throws -> Resposta {
        let json = try codificador.encode(corpo)
        let dados = try await executar(metodo: "POST", caminho: caminho, query: [:], corpo: json, aberta: aberta)
        return try decodificar(dados)
    }

    // MARK: - Renovacao

    /// Renova o access pelo cookie. Chamadas simultaneas esperam a mesma renovacao:
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

        let tokens = try await tarefa.value
        accessToken = tokens.accessToken
        await aoRenovar?(tokens.usuario)
        return tokens
    }

    private func pedirRenovacao() async throws -> ParDeTokens {
        let dados = try await executar(
            metodo: "POST", caminho: "/auth/refresh", query: [:], corpo: Data("{}".utf8), aberta: true)
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
        let pedido = try montar(metodo: metodo, caminho: caminho, query: query, corpo: corpo,
                                token: aberta ? nil : accessToken)
        let (dados, resposta) = try await chamar(pedido)

        guard resposta.statusCode == 401, !aberta else {
            return try validar(dados, resposta)
        }

        let tokens: ParDeTokens
        do {
            tokens = try await renovar()
        } catch {
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
            throw ErroDaAPI.servidor(status: resposta.statusCode, mensagem: corpo?.erro)
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
