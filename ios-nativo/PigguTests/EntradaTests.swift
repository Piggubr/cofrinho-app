import Foundation
import Testing
@testable import Piggu

/// O fluxo de entrada contra a API falsa da demonstracao (TransporteDeDemonstracao), e o
/// contrato de cada pedido contra o dublê roteirizado.
@MainActor
struct EntradaTests {
    private let base = URL(string: "https://piggu.com.br/api")!

    private func sessao(conta: String? = nil, logado: Bool = false) -> Sessao {
        let transporte = TransporteDeDemonstracao(logado: logado, falha: nil, conta: conta)
        return Sessao(cliente: ClienteDaAPI(urlBase: base, transporte: transporte, cofre: nil), cache: Cache(soNaMemoria: true))
    }

    private func roteirizada(_ roteiro: [(status: Int, corpo: String)]) -> (Sessao, TransporteRoteirizado) {
        let transporte = TransporteRoteirizado(roteiro)
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte, cofre: nil)
        return (Sessao(cliente: cliente, cache: Cache(soNaMemoria: true)), transporte)
    }

    private func corpo(_ pedido: URLRequest) -> [String: Any] {
        (pedido.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: Any]) ?? [:]
    }

    // MARK: - Apple e Google

    @Test func contaNovaDaAppleSoNasceComOAceite() async throws {
        let sessao = sessao()

        await #expect(throws: ErroDaAPI.self) {
            try await sessao.entrarComApple(idToken: "t", nonce: "n", nome: "Ana Souza", lembrar: true)
        }
        do {
            try await sessao.entrarComApple(idToken: "t", nonce: "n", nome: "Ana Souza", lembrar: true)
        } catch let erro as ErroDaAPI {
            #expect(erro.codigo == "TERMOS_NECESSARIOS")
        }
        #expect(sessao.usuario == nil)

        try await sessao.entrarComApple(idToken: "t", nonce: "n", nome: "Ana Souza", lembrar: true,
                                        versaoDosTermos: Conta.versaoDosTermos)

        let usuario = try #require(sessao.usuario)
        #expect(usuario.apelido == "Ana Souza")
        // Conta nova pela Apple: falta a data de nascimento, entao a proxima tela e "Sobre voce".
        #expect(usuario.sobreVocePendente)
        try await sessao.sobreVoce(apelido: "Ana", nascimento: "1990-05-01")
        #expect(sessao.usuario?.sobreVocePendente == false)
    }

    @Test func appleMandaONonceOriginalEONome() async throws {
        let (sessao, transporte) = roteirizada([(200, tokensNovos)])

        try await sessao.entrarComApple(idToken: "id", nonce: "abc", nome: "Ana", lembrar: false,
                                        versaoDosTermos: "2026-10-16")

        let pedido = try #require(await transporte.recebidos.first)
        #expect(pedido.url?.path == "/api/auth/apple")
        #expect(pedido.value(forHTTPHeaderField: "Authorization") == nil)
        let enviado = corpo(pedido)
        #expect(enviado["idToken"] as? String == "id")
        #expect(enviado["nonce"] as? String == "abc")
        #expect(enviado["nome"] as? String == "Ana")
        #expect(enviado["versaoDosTermos"] as? String == "2026-10-16")
        #expect(enviado["lembrar"] as? Bool == false)
        // Sem o Keychain ligado, o refresh continua no cookie.
        #expect(enviado["refreshNoCorpo"] == nil)
    }

    @Test func googleMandaOIdTokenSemVersaoNaPrimeiraTentativa() async throws {
        let (sessao, transporte) = roteirizada([(200, tokensNovos)])

        try await sessao.entrarComGoogle(idToken: "g", lembrar: true)

        let pedido = try #require(await transporte.recebidos.first)
        #expect(pedido.url?.path == "/api/auth/google")
        #expect(corpo(pedido).keys.sorted() == ["idToken", "lembrar"])
        #expect(sessao.usuario?.email == "ana@example.com")
    }

    @Test func appleForaDoArMostraOAvisoDoServidor() async throws {
        let (sessao, _) = roteirizada([
            (501, #"{"erro":"O login com a Apple ainda nao esta disponivel.","codigo":"EM_BREVE"}"#),
        ])

        await #expect(throws: ErroDaAPI.servidor(status: 501, mensagem: "O login com a Apple ainda nao esta disponivel.",
                                                 codigo: "EM_BREVE")) {
            try await sessao.entrarComApple(idToken: "t", nonce: "n", nome: nil, lembrar: true)
        }
    }

    @Test func nonceVaiComHashSha256EmHexadecimal() {
        // SHA-256 de "abc" (FIPS 180-2).
        #expect(EntradaComApple(nonce: "abc").nonceComHash
            == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        #expect(EntradaComApple().nonce != EntradaComApple().nonce)
    }

    // MARK: - Cadastro, senha e e-mail

    @Test func cadastroMandaOContratoDoBackend() async throws {
        let (sessao, transporte) = roteirizada([(202, "")])

        try await sessao.cadastrar(PedidoDeCadastro(
            nome: "Ana", email: "ana@example.com", senha: "porquinho1", pais: "BR",
            documento: CPF.digitos("529.982.247-25"), telefone: "+55 (11) 99999-8888", nascimento: "1990-05-01"))

        let pedido = try #require(await transporte.recebidos.first)
        #expect(pedido.url?.path == "/api/auth/cadastro")
        let enviado = corpo(pedido)
        #expect(enviado["documento"] as? String == "52998224725")
        #expect(enviado["versaoDosTermos"] as? String == Conta.versaoDosTermos)
        #expect(enviado["telefone"] as? String == "+55 (11) 99999-8888")
        #expect(enviado["nascimento"] as? String == "1990-05-01")
        // Nao abre sessao: a pessoa confirma o e-mail e entra com a senha.
        #expect(sessao.usuario == nil)
    }

    @Test func linkVencidoDeSenhaVoltaComLinkInvalido() async throws {
        let sessao = sessao()

        try await sessao.esqueciSenha(email: "ana@example.com")
        try await sessao.redefinirSenha(token: "bom", novaSenha: "porquinho456")
        do {
            try await sessao.redefinirSenha(token: "vencido", novaSenha: "porquinho456")
            Issue.record("esperava LINK_INVALIDO")
        } catch let erro as ErroDaAPI {
            #expect(erro.codigo == "LINK_INVALIDO")
        }
    }

    @Test func redefinirComSessaoAbertaSaiDoAparelho() async throws {
        let sessao = sessao(logado: true)
        await sessao.restaurar()
        #expect(sessao.usuario != nil)

        try await sessao.redefinirSenha(token: "bom", novaSenha: "porquinho456")

        #expect(sessao.estado == .deslogado)
    }

    @Test func confirmarEmailComSessaoAtualizaOPerfil() async throws {
        let sessao = sessao(conta: "email", logado: true)
        await sessao.restaurar()
        #expect(sessao.usuario?.emailConfirmado == false)

        try await sessao.confirmarEmail(token: "bom")

        #expect(sessao.usuario?.emailConfirmado == true)
    }

    // MARK: - Termos, CPF e excluir conta

    @Test func termosMudadosPedemOAceiteDeNovo() async throws {
        let sessao = sessao(conta: "termos", logado: true)
        await sessao.restaurar()
        #expect(sessao.usuario?.termosPendentes == true)

        try await sessao.aceitarTermos()

        #expect(sessao.usuario?.termosAceitos == Conta.versaoDosTermos)
        #expect(sessao.usuario?.termosPendentes == false)
    }

    @Test func cpfInformadoRenovaOToken() async throws {
        let (sessao, transporte) = roteirizada([(200, tokensNovos.usuarioDeTokens), (200, tokensNovos)])

        try await sessao.informarPais("BR", documento: "52998224725")

        let caminhos = await transporte.recebidos.map { "\($0.httpMethod ?? "") \($0.url?.path ?? "")" }
        #expect(caminhos == ["PUT /api/me/pais", "POST /api/auth/refresh"])
        #expect(corpo(await transporte.recebidos[0]) as? [String: String] == ["pais": "BR", "documento": "52998224725"])
    }

    @Test func excluirContaApagaNoServidorESai() async throws {
        let sessao = sessao(logado: true)
        await sessao.restaurar()

        try await sessao.excluirConta()

        #expect(sessao.estado == .deslogado)
    }

    @Test func excluirContaComFalhaContinuaLogado() async throws {
        let (sessao, transporte) = roteirizada([(200, tokensNovos), (500, #"{"erro":"Erro interno."}"#)])
        await sessao.restaurar()

        await #expect(throws: ErroDaAPI.self) { try await sessao.excluirConta() }

        #expect(sessao.usuario != nil)
        #expect(await transporte.recebidos.last?.httpMethod == "DELETE")
    }
}

/// As regras conferidas no app antes de mandar (os mesmos casos dos testes do site).
struct RegrasDaContaTests {
    @Test func senhaBoa() {
        #expect(Conta.problemaDaSenha("cofrinho1") == nil)
        #expect(Conta.problemaDaSenha("ção12345") == nil)
    }

    @Test func senhaRuim() {
        #expect(Conta.problemaDaSenha("abc123")?.contains("pelo menos 8") == true)
        #expect(Conta.problemaDaSenha("12345678")?.contains("letras e números") == true)
        #expect(Conta.problemaDaSenha("abcdefgh")?.contains("letras e números") == true)
        #expect(Conta.problemaDaSenha("a1" + String(repeating: "x", count: 71))?.contains("no máximo") == true)
        #expect(Conta.problemaDaSenha("Ana1@piggu.test", email: "ana1@piggu.test")?.contains("e-mail") == true)
    }

    @Test(arguments: [
        ("ana@piggu.com.br", true),
        ("a@b.co", true),
        ("ana@piggu", false),
        ("ana@.com", false),
        ("@piggu.com", false),
        ("ana piggu@x.com", false),
        ("ana@@piggu.com", false),
    ])
    func email(texto: String, parece: Bool) {
        #expect(Conta.pareceEmail(texto) == parece)
    }

    @Test func cpf() {
        #expect(CPF.valido("529.982.247-25"))
        #expect(CPF.valido("52998224725"))
        #expect(!CPF.valido("529.982.247-24"))
        #expect(!CPF.valido("111.111.111-11"))
        #expect(!CPF.valido("5299822472"))
        #expect(CPF.formatar("52998224725") == "529.982.247-25")
        #expect(CPF.formatar("5299") == "529.9")
        #expect(CPF.formatar("529.982.247-2599") == "529.982.247-25")
    }

    @Test func nascimento() throws {
        let calendario = Calendar(identifier: .gregorian)
        let hoje = try #require(calendario.date(from: DateComponents(year: 2026, month: 10, day: 26)))
        func data(_ ano: Int, _ mes: Int, _ dia: Int) -> Date {
            calendario.date(from: DateComponents(year: ano, month: mes, day: dia))!
        }

        #expect(Conta.problemaDoNascimento(nil, hoje: hoje, calendario: calendario) == "Informe sua data de nascimento.")
        #expect(Conta.problemaDoNascimento(data(2013, 10, 26), hoje: hoje, calendario: calendario) == nil)
        #expect(Conta.problemaDoNascimento(data(2013, 10, 27), hoje: hoje, calendario: calendario)?.contains("13 anos") == true)
        #expect(Conta.problemaDoNascimento(data(2027, 1, 1), hoje: hoje, calendario: calendario) == "Confira a data de nascimento.")
        #expect(Conta.problemaDoNascimento(data(1900, 1, 1), hoje: hoje, calendario: calendario) == "Confira a data de nascimento.")
    }

    @Test func paisesComOBrasilPrimeiroENomeEmPortugues() {
        #expect(Pais.todos.first?.codigo == "BR")
        #expect(Pais.todos.count == 242)
        #expect(Pais.ddi(de: "BR") == "55")
        #expect(Pais.ddi(de: "US") == "1")
        #expect(Pais.todos.contains { $0.codigo == "DE" && $0.nome == "Alemanha" })
    }

    @Test func pendenciasDoPerfil() throws {
        let json = #"{"id":"u1","email":"a@b.co","nome":"Ana","primeiroNome":"Ana","apelido":"Ana","foto":null,"role":"TITULAR","ativo":true,"plano":"GRATUITO","premiumAte":null,"familia":"f1","nascimento":"1990-01-01","termosAceitos":"2026-10-16"}"#
        let usuario = try JSONDecoder().decode(Usuario.self, from: Data(json.utf8))
        #expect(!usuario.termosPendentes)
        #expect(!usuario.sobreVocePendente)

        var antigo = usuario
        antigo.termosAceitos = nil
        #expect(antigo.termosPendentes)
    }
}

/// Os textos legais do app sao os do site, na versao vigente.
struct TextosLegaisTests {
    @Test(arguments: [TextoLegalView.Documento.termos, .privacidade])
    func textoEstaNoAppNaVersaoVigente(documento: TextoLegalView.Documento) throws {
        let blocos = try #require(TextoLegalView.blocos(documento))
        #expect(blocos.count > 20)
        #expect(blocos.contains { bloco in
            if case let .paragrafo(texto) = bloco { String(texto.characters) == "Versão \(Conta.versaoDosTermos)" } else { false }
        })
    }

    @Test func tabelaViraUmaLinhaComOCabecalho() {
        let blocos = TextoLegalView.blocos(de: "|#Dado | Para quê\n\n|CPF | Identificar **você**")
        guard case let .linhaDaTabela(colunas, cabecalho) = blocos.first else {
            Issue.record("esperava uma linha de tabela, veio \(blocos)")
            return
        }
        #expect(String(colunas[0].characters) == "CPF")
        #expect(String(colunas[1].characters) == "Identificar você")
        #expect(String(cabecalho[1].characters) == "Para quê")
    }
}

private extension String {
    /// So o perfil de dentro do par de tokens, como /me/pais devolve.
    var usuarioDeTokens: String {
        let dados = try! JSONSerialization.jsonObject(with: Data(utf8)) as! [String: Any]
        return String(data: try! JSONSerialization.data(withJSONObject: dados["usuario"]!), encoding: .utf8)!
    }
}
