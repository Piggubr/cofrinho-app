import SwiftUI

/// Criar conta por e-mail e senha (o modo "cadastro" de features/login): nome, nascimento,
/// e-mail, senha duas vezes, celular, pais onde mora, CPF e o aceite dos termos. Nao abre
/// sessao: a pessoa confirma o e-mail e entra com a senha.
struct CadastroView: View {
    enum Campo: Hashable {
        case nome, nascimento, email, senha, confirmacao, telefone, documento, aceite
    }

    @Binding var email: String
    /// Recebe o e-mail para onde foi o link.
    let aoCriar: (String) -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta

    @State private var nome = ""
    @State private var nascimento: Date?
    @State private var senha = ""
    @State private var confirmacao = ""
    @State private var pais = Pais.brasil
    /// Pais do DDI do celular; segue o pais onde mora ate a pessoa escolher outro.
    @State private var paisDoDdi = Pais.brasil
    @State private var telefone = ""
    @State private var documento = ""
    @State private var aceitou = false
    @State private var enviando = false
    @State private var erro: String?
    @State private var campoComProblema: Campo?

    var body: some View {
        VStack(spacing: 14) {
            if let erro {
                AvisoDaEntrada(texto: erro)
            }

            CampoDaEntrada(rotulo: "Como quer ser chamado?", comProblema: campoComProblema == .nome) {
                TextField("Como quer ser chamado?", text: $nome, prompt: Text(verbatim: ""))
                    .textContentType(.nickname)
            }
            CampoDeNascimento(data: $nascimento, comProblema: campoComProblema == .nascimento)
            CampoDaEntrada(rotulo: "E-mail", comProblema: campoComProblema == .email) {
                TextField("E-mail", text: $email, prompt: Text(verbatim: ""))
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            CampoDaEntrada(rotulo: "Senha", dica: "Pelo menos 8 caracteres, com letras e números.",
                           comProblema: campoComProblema == .senha) {
                SecureField("Senha", text: $senha, prompt: Text(verbatim: ""))
                    .textContentType(.newPassword)
            }
            CampoDaEntrada(rotulo: "Repita a senha", comProblema: campoComProblema == .confirmacao) {
                SecureField("Repita a senha", text: $confirmacao, prompt: Text(verbatim: ""))
                    .textContentType(.newPassword)
            }
            CampoDaEntrada(rotulo: "Celular", comProblema: campoComProblema == .telefone) {
                CampoDeCelular(paisDoDdi: $paisDoDdi, telefone: $telefone)
            }
            CampoDaEntrada(rotulo: "País onde você mora") {
                SeletorDePais(pais: $pais)
            }
            .onChange(of: pais) { _, novo in paisDoDdi = novo }
            CampoDaEntrada(rotulo: "CPF", dica: "Fica guardado cifrado e não aparece para a sua família.",
                           comProblema: campoComProblema == .documento) {
                CampoDeCpf(documento: $documento)
            }
            AceiteDosTermos(aceitou: $aceitou, comProblema: campoComProblema == .aceite)

            BotaoDeEnviar(titulo: "Criar minha conta", carregando: enviando) { await cadastrar() }
        }
    }

    private func cadastrar() async {
        if let (campo, problema) = campoEProblema() {
            campoComProblema = campo
            erro = problema
            return
        }
        campoComProblema = nil
        erro = nil
        enviando = true
        defer { enviando = false }
        let enviadoPara = email.trimmingCharacters(in: .whitespaces)
        do {
            try await sessao.cadastrar(PedidoDeCadastro(
                nome: nome.trimmingCharacters(in: .whitespaces),
                email: enviadoPara,
                senha: senha,
                pais: pais,
                documento: CPF.digitos(documento),
                telefone: CampoDeCelular.completo(paisDoDdi, telefone),
                nascimento: nascimento.map(Formatos.dia) ?? ""))
            // O backend responde igual se o e-mail ja tem conta: a tela nao entra sozinha.
            aoCriar(enviadoPara)
        } catch {
            erro = error.localizedDescription
        }
    }

    /// O primeiro campo que falta ou esta errado e o aviso dele; o backend confere de novo.
    private func campoEProblema() -> (Campo, String)? {
        if nome.trimmingCharacters(in: .whitespaces).isEmpty {
            return (.nome, String(localized: "Conte como podemos te chamar."))
        }
        if let problema = Conta.problemaDoNascimento(nascimento) {
            return (.nascimento, problema)
        }
        if !Conta.pareceEmail(email.trimmingCharacters(in: .whitespaces)) {
            return (.email, String(localized: "Confira o e-mail."))
        }
        if let problema = Conta.problemaDaSenha(senha, email: email) {
            return (.senha, problema)
        }
        if senha != confirmacao {
            return (.confirmacao, String(localized: "As duas senhas não são iguais."))
        }
        if telefone.filter(\.isNumber).count < 6 {
            return (.telefone, String(localized: "Informe seu celular."))
        }
        if !CPF.valido(documento) {
            return (.documento, documento.trimmingCharacters(in: .whitespaces).isEmpty
                ? String(localized: "Informe o número do seu documento (CPF).")
                : String(localized: "CPF inválido. Confira o número."))
        }
        if !aceitou {
            return (.aceite, String(localized: "Para criar a conta, aceite os Termos de Uso e o Aviso de Privacidade."))
        }
        return nil
    }
}

/// Os paises pelo nome, com o Brasil primeiro.
struct SeletorDePais: View {
    @Binding var pais: String

    var body: some View {
        Picker("País onde você mora", selection: $pais) {
            ForEach(Pais.todos) { opcao in
                Text(verbatim: opcao.nome).tag(opcao.codigo)
            }
        }
        .labelsHidden()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// CPF com a mascara enquanto digita (000.000.000-00).
/// DDI e numero, como no cadastro do site; o backend junta os dois em E.164.
struct CampoDeCelular: View {
    @Binding var paisDoDdi: String
    @Binding var telefone: String

    var body: some View {
        HStack {
            Picker("Código do país (DDI)", selection: $paisDoDdi) {
                ForEach(Pais.todos) { opcao in
                    Text(verbatim: "\(opcao.nome) +\(opcao.ddi)").tag(opcao.codigo)
                }
            }
            .labelsHidden()
            .fixedSize()
            Divider()
            TextField("Celular", text: $telefone, prompt: Text(verbatim: "(11) 99999-8888"))
                .textContentType(.telephoneNumber)
                .keyboardType(.phonePad)
        }
    }

    /// O que vai em `telefone`: "+55 (11) 99999-8888".
    static func completo(_ paisDoDdi: String, _ telefone: String) -> String {
        "+\(Pais.ddi(de: paisDoDdi)) \(telefone.trimmingCharacters(in: .whitespaces))"
    }
}

struct CampoDeCpf: View {
    @Binding var documento: String

    var body: some View {
        TextField("CPF", text: $documento, prompt: Text(verbatim: CPF.exemplo))
            .keyboardType(.numberPad)
            .onChange(of: documento) { _, novo in
                let formatado = CPF.formatar(novo)
                if formatado != novo {
                    documento = formatado
                }
            }
    }
}
