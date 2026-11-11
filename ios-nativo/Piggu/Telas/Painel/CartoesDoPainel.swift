import AuthenticationServices
import SwiftUI

// Os cartoes do Painel, um para cada bloco de frontend/src/app/features/dashboard, com os
// mesmos textos e as mesmas regras de mostrar e esconder.

/// Sobra do mes: receitas - gastos, e quanto disso foi poupado (resumo-do-mes.ts).
struct CartaoDoResumo: View {
    let resumo: ResumoDoMes
    /// No Painel, Receitas e Gastos abrem a tela deles.
    var aoAbrir: ((Aba) -> Void)?

    @Environment(\.paleta) private var paleta

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            LadoALado {
                // Sem transparencia: no escuro o texto e escuro sobre o rosa e perderia o contraste.
                Text("Sobra do mês")
                    .font(.sora(.subheadline, peso: .medium))
                Spacer()
                if let taxa = resumo.taxaDePoupanca {
                    Text("\(Formatos.porcentagem(taxa)) poupado")
                        .font(.sora(.caption, peso: .semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(.white.opacity(0.18), in: Capsule())
                }
            }
            Text(Formatos.moeda(resumo.sobra))
                .font(.sora(.largeTitle, peso: .bold))
                .monospacedDigit()
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            // Diferente do "Ainda dá", que tira tambem as contas a pagar (BUG-002).
            Text("Receitas menos gastos")
                .font(.sora(.footnote, peso: .medium))
            LadoALado(espaco: 10) {
                parcela("Receitas", resumo.receitas, icone: "chart.line.uptrend.xyaxis", abre: .receitas)
                parcela("Gastos", resumo.gastos, icone: "wallet.pass", abre: .gastos)
            }
            if resumo.receitas == 0 {
                Button {
                    aoAbrir?(.receitas)
                } label: {
                    Text("Lance as **receitas do mês** para ver quanto sobrou e quanto você poupou.")
                        .font(.sora(.footnote))
                        .multilineTextAlignment(.leading)
                }
                .buttonStyle(.plain)
            }
        }
        .foregroundStyle(paleta.primariaContraste)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        // Cor solida: o branco sobre a ponta clara do degrade (serieGasto) ficava abaixo de 4,5:1 (BUG-015).
        .background(paleta.primaria, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: aoAbrir == nil ? .combine : .contain)
    }

    @ViewBuilder
    private func parcela(_ rotulo: LocalizedStringKey, _ valor: Decimal, icone: String, abre aba: Aba) -> some View {
        if let aoAbrir {
            Button { aoAbrir(aba) } label: { conteudoDaParcela(rotulo, valor, icone: icone) }
                .buttonStyle(.plain)
                .accessibilityHint("Toque para ver a lista")
        } else {
            conteudoDaParcela(rotulo, valor, icone: icone)
        }
    }

    private func conteudoDaParcela(_ rotulo: LocalizedStringKey, _ valor: Decimal, icone: String) -> some View {
        LadoALado(espaco: 8) {
            Image(systemName: icone)
                .font(.footnote.weight(.semibold))
                .frame(width: 30, height: 30)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 0) {
                Text(rotulo).font(.sora(.caption))
                Text(Formatos.moeda(valor)).font(.sora(.subheadline, peso: .semibold)).monospacedDigit()
                    .minimumScaleFactor(0.7).lineLimit(1)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

/// Para ficar de olho: orcamentos, faturas, contas do mes e quem esta devendo (avisos-do-mes.ts).
struct CartaoDeAvisos: View {
    let modelo: ModeloDoPainel
    let irPara: (Aba) -> Void


    var body: some View {
        let orcamentos = modelo.orcamentosEmAlerta
        let faturas = modelo.faturasAVencer
        let contas = modelo.contasEmAberto
        let devendo = modelo.devendo
        if !(orcamentos.isEmpty && faturas.isEmpty && contas.isEmpty && devendo.isEmpty) {
            Cartao("Para ficar de olho") {
                ForEach(orcamentos) { orcamento in
                    linha(icone: "chart.pie", titulo: String(localized: "Orçamento"),
                          detalhe: orcamento.alerta == .estourou
                              ? String(localized: "\(orcamento.categoria) passou do limite: \(Formatos.moeda(orcamento.gasto)) de \(Formatos.moeda(orcamento.limite))")
                              : String(localized: "\(orcamento.categoria) já usou \(Formatos.porcentagem(orcamento.percentual)) do limite"),
                          atrasada: orcamento.alerta == .estourou, tela: .orcamento)
                }
                ForEach(faturas) { cartao in
                    if let fatura = cartao.faturaAPagar {
                        linha(icone: "creditcard", titulo: String(localized: "Fatura do cartão"),
                              detalhe: String(localized: "\(cartao.nome) vence em \(Formatos.dataCurta(fatura.vencimento)): \(Formatos.moeda(fatura.total))"),
                              tela: .cartoes)
                    }
                }
                ForEach(contas) { conta in
                    linha(icone: "doc.text", titulo: "\(conta.descricao) · \(Formatos.moeda(conta.valor))",
                          detalhe: conta.situacao == .vencida
                              ? String(localized: "Venceu em \(Formatos.dataCurta(conta.vencimento))")
                              : String(localized: "Vence em \(Formatos.dataCurta(conta.vencimento))"),
                          atrasada: conta.situacao == .vencida, tela: .contasDoMes)
                }
                ForEach(devendo) { conta in
                    linha(icone: "arrow.down.left", titulo: "\(conta.quem) · \(Formatos.moeda(conta.valor))",
                          detalhe: String(localized: "Está te devendo desde \(Formatos.dataCurta(conta.data))"),
                          atrasada: true, aba: .receitas)
                }
            }
        }
    }

    /// Receitas abre a aba; orcamento, fatura e contas do mes abrem a tela na pilha do Painel.
    @ViewBuilder
    private func linha(icone: String, titulo: String, detalhe: String, atrasada: Bool = false,
                       tela: TelaDoMais? = nil, aba: Aba? = nil) -> some View {
        let conteudo = LinhaDeAviso(icone: icone, titulo: titulo, detalhe: detalhe, atrasada: atrasada)
        if let tela {
            NavigationLink(value: tela) { conteudo }.buttonStyle(.plain)
        } else {
            Button { if let aba { irPara(aba) } } label: { conteudo }.buttonStyle(.plain)
        }
    }
}

private struct LinhaDeAviso: View {
    let icone: String
    let titulo: String
    let detalhe: String
    let atrasada: Bool

    @Environment(\.paleta) private var paleta

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icone)
                .frame(width: 38, height: 38)
                .foregroundStyle(atrasada ? paleta.perigo : paleta.primaria)
                .background(atrasada ? paleta.perigoSuave : paleta.suave,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(titulo).font(.sora(.subheadline, peso: .medium)).foregroundStyle(paleta.texto)
                Text(detalhe).font(.sora(.footnote))
                    .foregroundStyle(atrasada ? paleta.perigo : paleta.textoSuave)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right").font(.footnote).foregroundStyle(paleta.textoSuave)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// Ainda dá para gastar (D-164): fixo no topo do Painel, fora do Personalizar. Fica carregando até
/// as contas e os membros chegarem e mostra o erro se as contas falharem (BUG-002, D-195).
struct CartaoAindaDaParaGastar: View {
    let mes: Date
    let estado: ModeloDoPainel.EstadoDoAindaDa
    /// O que está na fila do aparelho e já entrou no gasto (BUG-010).
    var pendentes: Decimal = 0
    var tentarDeNovo: () -> Void = {}
    let irPara: (Aba) -> Void

    @Environment(\.paleta) private var paleta

    private var passado: Bool { Formatos.mes(mes) < Formatos.mes(Date()) }
    private var nomeDoMes: String { Formatos.nomeDoMes(mes) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(passado ? "Sobrou em \(nomeDoMes)" : "Ainda dá para gastar em \(nomeDoMes)")
                .font(.sora(.headline, peso: .semibold))
                .accessibilityAddTraits(.isHeader)
            switch estado {
            case .carregando:
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .accessibilityLabel("Carregando as contas do mês")
            case let .falhou(erro):
                Text("Não deu para ver as contas do mês, então este número ainda não está certo. \(erro.localizedDescription)")
                    .font(.sora(.subheadline))
                Button("Tentar de novo", action: tentarDeNovo)
                    .buttonStyle(.pilula)
                    .font(.sora(.subheadline, peso: .medium))
            case let .pronto(conta?):
                numero(conta)
            case .pronto(nil):
                Text("Defina um limite para o mês ou lance suas receitas para ver quanto ainda dá para gastar.")
                    .font(.sora(.subheadline))
                ViewThatFits {
                    HStack { botoesSemTeto }
                    VStack(alignment: .leading, spacing: 8) { botoesSemTeto }
                }
                .font(.sora(.subheadline, peso: .medium))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(paleta.suave, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var botoesSemTeto: some View {
        NavigationLink("Definir limite", value: TelaDoMais.orcamento)
        Spacer(minLength: 8)
        Button("Lançar receitas") { irPara(.receitas) }
    }

    @ViewBuilder
    private func numero(_ conta: AindaDaParaGastar) -> some View {
        let passou = conta.resta < 0
        Text(Formatos.moeda(conta.resta))
            .font(.sora(.largeTitle, peso: .bold))
            .monospacedDigit()
            .minimumScaleFactor(0.6)
            .lineLimit(1)
            .foregroundStyle(passou ? paleta.perigo : paleta.primaria)
        if passou {
            Text(passado ? "Passou do que dava para gastar em \(nomeDoMes)." : "Já passou do que dava para gastar este mês.")
                .font(.sora(.subheadline)).foregroundStyle(paleta.perigo)
        } else if let porDia = conta.porDia {
            Text("Cerca de \(Formatos.moeda(porDia)) por dia até o fim do mês").font(.sora(.subheadline))
        }
        // De onde vem o número, em uma linha (contrato da auditoria), e a conta à vista embaixo.
        Text(conta.tetoEhOLimite ? "Limite do mês menos o que já foi gasto e as contas a pagar"
                                 : "Receitas do mês menos o que já foi gasto e as contas a pagar")
            .font(.sora(.footnote, peso: .medium))
        // "Receitas de" e "já gastos" sao links: abrem Receitas e Gastos no mes do Painel.
        Group {
            if conta.tetoEhOLimite {
                Text("Limite de \(Formatos.moeda(conta.teto)), menos [\(Formatos.moeda(conta.gasto)) já gastos](piggu://gastos) e \(Formatos.moeda(conta.contasAPagar)) de contas a pagar")
            } else {
                Text("[Receitas de \(Formatos.moeda(conta.teto))](piggu://receitas), menos [\(Formatos.moeda(conta.gasto)) já gastos](piggu://gastos) e \(Formatos.moeda(conta.contasAPagar)) de contas a pagar")
            }
            if pendentes > 0 {
                Text("Os gastos incluem \(Formatos.moeda(pendentes)) pendentes, esperando conexão.")
            }
        }
        .font(.sora(.footnote))
        .foregroundStyle(paleta.textoSuave)
        .tint(paleta.primaria)
        .environment(\.openURL, OpenURLAction { url in
            irPara(url.host() == "receitas" ? .receitas : .gastos)
            return .handled
        })
    }
}

/// O que falta na conta (CPF, e-mail sem confirmar), no topo do Painel: antes só aparecia no Perfil.
struct AvisosDaConta: View {
    let usuario: Usuario

    @Environment(\.paleta) private var paleta

    var body: some View {
        if usuario.emailConfirmado == false || usuario.cpfInformado == false {
            VStack(alignment: .leading, spacing: 10) {
                if usuario.emailConfirmado == false {
                    NavigationLink(value: TelaDoMais.perfil) {
                        Label("Confirme seu e-mail: abra o link que mandamos para \(usuario.email). Sem isso, não dá para aceitar convite da família.",
                              systemImage: "envelope.badge")
                    }
                }
                if usuario.cpfInformado == false {
                    Link(destination: URL(string: "piggu://cpf")!) {
                        Label("Informe seu CPF para deixar a conta completa.", systemImage: "person.text.rectangle")
                    }
                }
            }
            .font(.sora(.footnote, peso: .medium))
            .foregroundStyle(paleta.texto)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(paleta.alertaSuave, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }
}

/// Conta de membro (D-164): o "Ainda dá" é de quem cuida do orçamento da casa.
struct CartaoSemAindaDaDoMembro: View {
    @Environment(\.paleta) private var paleta

    var body: some View {
        Label("Quem cuida do limite e das contas da casa vê aqui quanto ainda dá para gastar. Na conta de membro, você lança e acompanha os seus gastos.",
              systemImage: "info.circle")
            .font(.sora(.footnote))
            .foregroundStyle(paleta.textoSuave)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(paleta.superficie, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Gasto e limite do mes, com a barra do limite.
struct CartaoDoMes: View {
    let mes: Date
    let gasto: Decimal
    let limite: Decimal
    let irPara: (Aba) -> Void

    @Environment(\.paleta) private var paleta

    private var progresso: Int {
        limite > 0 ? min(100, Int((gasto / limite * 100).paraDesenho.rounded())) : 0
    }
    private var estourou: Bool { limite > 0 && gasto > limite }

    var body: some View {
        Cartao(verbatim: Formatos.inicialMaiuscula(Formatos.nomeDoMes(mes))) {
            HStack(alignment: .top) {
                numero("Gasto no mês", Formatos.moeda(gasto))
                numero("Limite do mês", limite > 0 ? Formatos.moeda(limite) : "—")
            }
            if limite > 0 {
                BarraDeProgresso(percentual: Double(progresso), estourado: estourou)
                Group {
                    if estourou {
                        Text("\(progresso)% do limite · **limite ultrapassado**")
                    } else {
                        Text("\(progresso)% do limite")
                    }
                }
                .font(.sora(.footnote))
                .foregroundStyle(estourou ? paleta.perigo : paleta.textoSuave)
            } else {
                Text("Sem limite definido para este mês.")
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
            }
            HStack {
                NavigationLink("Definir limite", value: TelaDoMais.orcamento)
                Spacer()
                Button("Ver gastos") { irPara(.gastos) }
            }
            .font(.sora(.subheadline, peso: .medium))
        }
    }

    private func numero(_ rotulo: LocalizedStringKey, _ valor: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(rotulo).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            Text(valor).font(.sora(.title3, peso: .semibold)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Contas bancarias do Open Finance: saldos, atualizar, desconectar e conectar um banco
/// (no navegador do sistema, D-091). No Painel e em Cartoes e bancos.
struct CartaoDosBancos: View {
    let openFinance: OpenFinance
    let ehPremium: Bool

    @State private var desconectando: (conexaoId: String, instituicao: String)?
    @State private var pedindoAutorizacao = false
    @Environment(\.webAuthenticationSession) private var sessaoWeb
    @Environment(\.paleta) private var paleta

    var body: some View {
        Cartao("Contas bancárias") {
            HStack(spacing: 6) {
                ForEach(openFinance.saldoPorMoeda, id: \.moeda) { total in
                    Etiqueta(texto: Formatos.moeda(total.total, codigo: total.moeda))
                }
            }
        } conteudo: {
            if let erro = openFinance.erro {
                Faixa(texto: erro, tipo: .erro)
            }
            if openFinance.contas.isEmpty {
                Text("Nenhum banco conectado.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            ForEach(openFinance.contas) { conta in
                LinhaDoCartao(titulo: conta.nome,
                              detalhe: [conta.instituicao, conta.numero ?? ""].filter { !$0.isEmpty }.joined(separator: " · ")) {
                    Text(Formatos.moeda(conta.saldo, codigo: conta.moeda)).monospacedDigit()
                }
            }
            ForEach(openFinance.bancos, id: \.conexaoId) { banco in
                HStack {
                    Text("\(banco.instituicao) conectado").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    Spacer()
                    Button("Desconectar") { desconectando = banco }
                        .buttonStyle(.pilula)
                        .controlSize(.small)
                        .disabled(openFinance.ocupado)
                }
            }
            if pedindoAutorizacao {
                autorizacao
            }
            HStack {
                if ehPremium {
                    Button(openFinance.ocupado ? "Aguarde..." : "Conectar banco") { pedindoAutorizacao = true }
                        .buttonStyle(.pilula)
                        .disabled(openFinance.ocupado)
                    if !openFinance.contas.isEmpty {
                        Spacer()
                        Button("Atualizar saldos") { Task { await openFinance.sincronizar() } }
                            .disabled(openFinance.ocupado)
                    }
                } else {
                    LinkDoPremium(titulo: "Conectar banco com o Premium")
                }
            }
            .font(.sora(.subheadline, peso: .medium))
        }
        .confirmationDialog(
            desconectando.map { String(localized: "Desconectar \($0.instituicao)?") } ?? "",
            isPresented: Binding(get: { desconectando != nil }, set: { if !$0 { desconectando = nil } }),
            titleVisibility: .visible
        ) {
            Button("Desconectar", role: .destructive) {
                if let banco = desconectando { Task { await openFinance.desconectar(banco.conexaoId) } }
            }
        } message: {
            Text("O Piggu para de ler este banco e apaga os saldos guardados.")
        }
    }

    /// Cada banco e um compartilhamento novo: a autorizacao e pedida toda vez, com o texto do site.
    private var autorizacao: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Para mostrar seus saldos, o Piggu usa a Pluggy, empresa autorizada de Open Finance. Você entra no seu banco pela tela da Pluggy (sua senha nunca passa pelo Piggu) e autoriza o compartilhamento das contas e saldos. Você pode desconectar quando quiser, de graça. Detalhes no [Aviso de Privacidade](piggu://privacidade).")
                .font(.sora(.footnote))
            HStack {
                Button("Autorizo, continuar") {
                    pedindoAutorizacao = false
                    Task {
                        await openFinance.conectar { pagina in
                            try await sessaoWeb.authenticate(using: pagina, callbackURLScheme: "piggu",
                                                             preferredBrowserSession: .ephemeral)
                        }
                    }
                }
                .botaoPrincipal()
                Button("Cancelar") { pedindoAutorizacao = false }
                    .buttonStyle(.pilula)
            }
        }
        .padding(12)
        .background(paleta.alertaSuave, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Autorizar conexão com o banco")
    }
}

/// Cofrinho: saldo, depositos e o campo de depositar.
struct CartaoDoCofrinho: View {
    let modelo: ModeloDoPainel
    let cofrinho: Cofrinho
    let mes: String
    let visao: Visao

    @State private var valor = ""
    @State private var salvando = false
    @State private var apagando: Deposito?
    @FocusState private var focado: Bool
    @Environment(\.paleta) private var paleta

    var body: some View {
        Cartao("Cofrinho") {
            Etiqueta(texto: Formatos.moeda(cofrinho.saldo))
        } conteudo: {
            HStack(alignment: .top) {
                numero("Depositado", cofrinho.totalDepositos)
                numero("Gasto desde o início", cofrinho.totalGastos)
            }
            HStack(alignment: .bottom, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Novo depósito (\(Formatos.simboloDaMoeda))").font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                    CampoDeValor(rotulo: "Novo depósito", texto: $valor)
                        .focused($focado)
                        .padding(10)
                        .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                Button(salvando ? "Guardando..." : "Depositar") { Task { await depositar() } }
                    .botaoPrincipal()
                    .disabled(salvando)
            }
            if let erro = modelo.erros[.cofrinho] {
                Faixa(texto: erro, tipo: .erro)
            }
            if cofrinho.depositos.isEmpty {
                VazioDoCartao(icone: "banknote", titulo: "Nenhum depósito ainda",
                              acao: "Fazer o primeiro depósito") { focado = true }
            } else {
                ForEach(cofrinho.depositos) { deposito in
                    LinhaDoCartao(titulo: Formatos.moeda(deposito.valor),
                                  detalhe: "\(Formatos.dataCurta(deposito.data)) · \(modelo.nome(deposito.usuario))") {
                        Button(role: .destructive) { apagando = deposito } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Apagar")
                    }
                }
            }
        }
        .confirmationDialog("Apagar este depósito?", isPresented: Binding(
            get: { apagando != nil }, set: { if !$0 { apagando = nil } }), titleVisibility: .visible
        ) {
            Button("Apagar", role: .destructive) {
                if let deposito = apagando { Task { await modelo.excluirDeposito(deposito.id) } }
            }
        }
    }

    private func numero(_ rotulo: LocalizedStringKey, _ valor: Decimal) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(rotulo).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            Text(Formatos.moeda(valor)).font(.sora(.title3, peso: .semibold)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func depositar() async {
        salvando = true
        defer { salvando = false }
        if await modelo.depositar(Formatos.lerValor(valor) ?? 0, mes: mes, visao: visao) {
            valor = ""
            focado = false
        }
    }
}

/// Cofrinhos por objetivo: quanto juntar, ate quando e quanto guardar por mes (objetivos.ts).
struct CartaoDosObjetivos: View {
    let modelo: ModeloDoPainel
    let mes: String
    let visao: Visao

    @State private var valores: [String: String] = [:]
    @State private var formulario: FormularioDeObjetivo?
    @State private var apagando: ObjetivoDoCofrinho?
    @Environment(\.paleta) private var paleta

    var body: some View {
        Cartao("Cofrinhos por objetivo") {
            if let erro = modelo.erros[.objetivos] {
                Faixa(texto: erro, tipo: .erro)
            }
            if let objetivos = modelo.objetivos {
                ForEach(objetivos) { objetivo in
                    linha(objetivo)
                    Divider()
                }
                if objetivos.isEmpty && modelo.podeCriarObjetivo {
                    VazioDoCartao(icone: "target", titulo: "Nenhum objetivo ainda",
                                  texto: "Juntando para uma viagem, um sofá ou a reserva? Dê um nome, quanto e até quando, e o Piggu diz quanto guardar por mês.",
                                  acao: "Criar o primeiro objetivo") { formulario = FormularioDeObjetivo(objetivo: nil) }
                } else if modelo.podeCriarObjetivo {
                    Button("Novo objetivo") { formulario = FormularioDeObjetivo(objetivo: nil) }
                        .buttonStyle(.pilula)
                } else {
                    Text("No gratuito, um objetivo por vez. Com o Piggu Premium, quantos quiser.")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                    LinkDoPremium(titulo: "Piggu Premium").font(.sora(.footnote))
                }
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .sheet(item: $formulario) { formulario in
            ObjetivoView(modelo: modelo, objetivo: formulario.objetivo)
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar \"\($0.nome)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible) {
            Button("Apagar", role: .destructive) {
                if let objetivo = apagando { Task { await modelo.excluirObjetivo(objetivo) } }
            }
        } message: {
            Text("O que foi guardado nele volta para o cofrinho geral.")
        }
    }

    @ViewBuilder
    private func linha(_ objetivo: ObjetivoDoCofrinho) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(objetivo.nome).font(.sora(.body, peso: .medium))
                        if objetivo.compartilhado == true {
                            Etiqueta(texto: String(localized: "a dois"))
                        }
                    }
                    Text("\(Formatos.moeda(objetivo.guardado)) de \(Formatos.moeda(objetivo.alvo))")
                        .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    if !modelo.ehMeu(objetivo.usuario) && modelo.temCompanhia {
                        Text("Criado por \(modelo.nome(objetivo.usuario))")
                            .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                }
                Spacer()
                Button { formulario = FormularioDeObjetivo(objetivo: objetivo) } label: {
                    Image(systemName: "pencil")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Editar")
                if modelo.ehMeu(objetivo.usuario) {
                    Button(role: .destructive) { apagando = objetivo } label: { Image(systemName: "trash") }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Apagar")
                }
            }
            BarraDeProgresso(percentual: objetivo.percentual, concluido: objetivo.concluido)
            Text(situacao(objetivo)).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            if !objetivo.concluido {
                HStack(spacing: 10) {
                    CampoDeValor(rotulo: "Guardar em \(objetivo.nome)", texto: Binding(
                        get: { valores[objetivo.id] ?? "" }, set: { valores[objetivo.id] = $0 }))
                        .padding(10)
                        .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    Button("Guardar") {
                        Task {
                            let valor = Formatos.lerValor(valores[objetivo.id] ?? "") ?? 0
                            if await modelo.depositar(valor, objetivo: objetivo.id, mes: mes, visao: visao) {
                                valores[objetivo.id] = nil
                            }
                        }
                    }
                    .buttonStyle(.pilula)
                }
            }
        }
    }

    private func situacao(_ objetivo: ObjetivoDoCofrinho) -> String {
        if objetivo.concluido {
            return String(localized: "Objetivo alcançado!")
        }
        if objetivo.atrasado {
            return String(localized: "O prazo passou e faltam \(Formatos.moeda(objetivo.falta)).")
        }
        if let porMes = objetivo.porMes, let prazo = objetivo.prazo {
            return String(localized: "Guarde \(Formatos.moeda(porMes)) por mês até \(Formatos.dataCurta(prazo)).")
        }
        return String(localized: "Faltam \(Formatos.moeda(objetivo.falta)), sem prazo.")
    }
}

struct FormularioDeObjetivo: Identifiable {
    let id = UUID()
    let objetivo: ObjetivoDoCofrinho?
}

/// Minhas metas do mes: o anel e "x de y concluidas" (metas-do-painel.ts).
struct CartaoDasMetas: View {
    let modelo: ModeloDoPainel

    @Environment(\.paleta) private var paleta

    var body: some View {
        let metas = modelo.metasDoMes ?? []
        let concluidas = metas.filter(\.concluida).count
        Cartao(nil) {
            HStack(spacing: 18) {
                AnelDasMetas(percentual: MinhasMetasView.percentualConcluido(metas), pequeno: true)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Seu ritmo").font(.sora(.caption, peso: .semibold)).textCase(.uppercase)
                        .foregroundStyle(paleta.primaria)
                    Text("Metas do mês").font(.sora(.headline, peso: .semibold)).accessibilityAddTraits(.isHeader)
                    Group {
                        if let erro = modelo.erros[.metas] {
                            Text(erro)
                        } else if metas.isEmpty {
                            Text("Crie sua primeira meta para começar a jornada.")
                        } else {
                            Text("\(concluidas) de \(metas.count) concluídas")
                        }
                    }
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
                    NavigationLink("Ver metas", value: TelaDoMais.metas)
                        .buttonStyle(.pilula).controlSize(.small)
                }
            }
        }
    }
}

/// Quanto a pessoa deixou de gastar este mes no Girls Math.
struct CartaoDoGirlsMath: View {
    let modelo: ModeloDoPainel

    @Environment(\.paleta) private var paleta

    var body: some View {
        let mes = Formatos.mes(Date())
        let noMes = (modelo.girlsMath ?? []).filter { $0.data.hasPrefix(mes) }.reduce(Decimal(0)) { $0 + $1.valor }
        Cartao("Girls Math") {
            Image(systemName: "bag").foregroundStyle(paleta.primaria)
        } conteudo: {
            if let erro = modelo.erros[.girlsMath] {
                Faixa(texto: erro, tipo: .erro)
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Você deixou de gastar este mês").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    Text(Formatos.moeda(noMes)).font(.sora(.title, peso: .bold)).foregroundStyle(paleta.primaria)
                        .monospacedDigit()
                }
                .accessibilityElement(children: .combine)
            }
            NavigationLink("Registrar o que não comprei", value: TelaDoMais.girlsMath)
                .font(.sora(.footnote, peso: .medium))
        }
    }
}

/// Suas notas: as 5 mais recentes e um jeito rapido de anotar (notas-do-painel.ts).
struct CartaoDasNotas: View {
    let modelo: ModeloDoPainel

    @State private var escrevendo = false
    @State private var titulo = ""
    @State private var texto = ""
    @State private var salvando = false
    @State private var apagando: Nota?
    @Environment(\.paleta) private var paleta

    var body: some View {
        let recentes = (modelo.notas ?? []).sorted { $0.criadoEm > $1.criadoEm }.prefix(5)
        Cartao("Suas notas") {
            NavigationLink("Ver todas", value: TelaDoMais.calendario).font(.sora(.footnote))
        } conteudo: {
            if let erro = modelo.erros[.notas] {
                Faixa(texto: erro, tipo: .erro)
            }
            ForEach(Array(recentes)) { nota in
                LinhaDoCartao(titulo: nota.titulo, detalhe: detalhe(nota)) {
                    Button(role: .destructive) { apagando = nota } label: { Image(systemName: "trash") }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Apagar")
                }
            }
            if recentes.isEmpty && modelo.notas != nil {
                Text("Nenhuma nota ainda.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            if escrevendo {
                TextField("Título", text: $titulo)
                    .padding(10)
                    .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                TextField("Escreve aqui...", text: $texto, axis: .vertical)
                    .lineLimit(3...6)
                    .padding(10)
                    .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .accessibilityLabel("Nota")
                HStack {
                    Button(salvando ? "Guardando..." : "Salvar nota") {
                        Task {
                            salvando = true
                            if await modelo.criarNota(titulo: String(titulo.prefix(150)), texto: String(texto.prefix(1500))) {
                                titulo = ""
                                texto = ""
                                escrevendo = false
                            }
                            salvando = false
                        }
                    }
                    .botaoPrincipal()
                    .disabled(salvando)
                    Button("Cancelar") { escrevendo = false }
                        .buttonStyle(.pilula)
                }
            } else {
                Button("+ Criar outra") { escrevendo = true }
                    .buttonStyle(.pilula)
            }
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar a nota \"\($0.titulo)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible) {
            Button("Apagar", role: .destructive) {
                if let nota = apagando { Task { await modelo.excluirNota(nota) } }
            }
        }
    }

    private func detalhe(_ nota: Nota) -> String {
        [nota.data.map(Formatos.dataCurta), nota.texto].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

/// Por categoria: quanto cada uma levou no mes, num grafico de barras.
struct CartaoDasCategorias: View {
    let totais: [(categoria: String, total: Decimal)]
    let aoLancar: () -> Void

    var body: some View {
        Cartao("Por categoria") {
            if totais.isEmpty {
                VazioDoCartao(icone: "doc.text", titulo: "Nenhum gasto lançado neste mês",
                              acao: "Lançar um gasto", executar: aoLancar)
            } else {
                BarrasPorCategoria(linhas: totais.map { .init(categoria: $0.categoria, total: $0.total) })
            }
        }
    }
}

/// Comeco do mes (D-165): o mes de hoje ainda sem gasto mostra o anterior, apagado, para o
/// Painel nao parecer zerado. Some no primeiro gasto do mes.
struct CartaoDoMesPassado: View {
    let modelo: ModeloDoPainel
    let mes: Date
    let aoLancar: () -> Void
    let irPara: (Aba) -> Void

    @Environment(\.paleta) private var paleta

    var body: some View {
        Cartao(verbatim: String(localized: "Como foi \(Formatos.nomeDoMes(Formatos.somarMeses(-1, a: mes)))")) {
            Text("Nenhum gasto neste mês ainda. Enquanto isso, o mês passado para comparar.")
                .font(.sora(.footnote))
                .foregroundStyle(paleta.textoSuave)
            VStack(alignment: .leading, spacing: 10) {
                BarrasPorCategoria(linhas: ModeloDoPainel.porCategoria(modelo.gastosDoMesPassado)
                    .map { .init(categoria: $0.categoria, total: $0.total) })
                Text("Últimos gastos")
                    .font(.sora(.subheadline, peso: .semibold))
                    .foregroundStyle(paleta.textoSuave)
                    .padding(.top, 6)
                ForEach(modelo.recentesDoMesPassado) { gasto in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(gasto.estabelecimento.flatMap { $0.isEmpty ? nil : $0 } ?? gasto.item)
                                .font(.sora(.subheadline, peso: .medium))
                                .foregroundStyle(paleta.texto)
                            Text("\(Formatos.dataCurta(gasto.data)) · \(gasto.categoria)")
                                .font(.sora(.footnote))
                                .foregroundStyle(paleta.textoSuave)
                        }
                        Spacer(minLength: 4)
                        Text(Formatos.moeda(gasto.valor))
                            .font(.sora(.subheadline, peso: .medium))
                            .monospacedDigit()
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            .opacity(0.75)
            HStack {
                Button("Lançar um gasto", action: aoLancar)
                Spacer()
                Button("Ver o ano") { irPara(.relatorios) }
            }
            .font(.sora(.subheadline, peso: .medium))
        }
    }
}
