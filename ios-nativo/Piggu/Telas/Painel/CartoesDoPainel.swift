import AuthenticationServices
import Charts
import SwiftUI

// Os cartoes do Painel, um para cada bloco de frontend/src/app/features/dashboard, com os
// mesmos textos e as mesmas regras de mostrar e esconder.

/// Sobra do mes: receitas - gastos, e quanto disso foi poupado (resumo-do-mes.ts).
struct CartaoDoResumo: View {
    let resumo: ResumoDoMes
    var aoVerReceitas: (() -> Void)?

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
            LadoALado(espaco: 10) {
                parcela("Receitas", resumo.receitas, icone: "chart.line.uptrend.xyaxis")
                parcela("Gastos", resumo.gastos, icone: "wallet.pass")
            }
            if resumo.receitas == 0 {
                Button {
                    aoVerReceitas?()
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
        .background(
            LinearGradient(colors: [paleta.primaria, paleta.serieGasto], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func parcela(_ rotulo: LocalizedStringKey, _ valor: Decimal, icone: String) -> some View {
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
                        .buttonStyle(.bordered)
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
                        .buttonStyle(.bordered)
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
                    .buttonStyle(.bordered)
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
                VStack(spacing: 8) {
                    Label("Nenhum depósito ainda", systemImage: "banknote")
                        .font(.sora(.subheadline, peso: .medium))
                    Button("Fazer o primeiro depósito") { focado = true }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
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
                if objetivos.isEmpty {
                    Text("Juntando para uma viagem, um sofá ou a reserva? Dê um nome, quanto e até quando, e o Piggu diz quanto guardar por mês.")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
                ForEach(objetivos) { objetivo in
                    linha(objetivo)
                    Divider()
                }
                if modelo.podeCriarObjetivo {
                    Button(objetivos.isEmpty ? "Criar o primeiro objetivo" : "Novo objetivo") {
                        formulario = FormularioDeObjetivo(objetivo: nil)
                    }
                    .buttonStyle(.bordered)
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
                    .buttonStyle(.bordered)
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
                        .buttonStyle(.bordered).controlSize(.small)
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
                        .buttonStyle(.bordered)
                }
            } else {
                Button("+ Criar outra") { escrevendo = true }
                    .buttonStyle(.bordered)
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

    @Environment(\.paleta) private var paleta

    var body: some View {
        Cartao("Por categoria") {
            if totais.isEmpty {
                VStack(spacing: 8) {
                    Label("Nenhum gasto lançado neste mês", systemImage: "doc.text")
                        .font(.sora(.subheadline, peso: .medium))
                    Button("Lançar um gasto", action: aoLancar)
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
            } else {
                Chart(totais, id: \.categoria) { linha in
                    BarMark(x: .value("Total", linha.total.paraDesenho), y: .value("Categoria", linha.categoria))
                        .foregroundStyle(paleta.serieGasto)
                        .cornerRadius(6)
                        .annotation(position: .trailing) {
                            Text(Formatos.moeda(linha.total)).font(.sora(.caption)).monospacedDigit()
                                .foregroundStyle(paleta.textoSuave)
                        }
                        .accessibilityLabel(linha.categoria)
                        .accessibilityValue(Formatos.moeda(linha.total))
                }
                .chartXAxis(.hidden)
                .chartXScale(domain: 0...((totais.first?.total.paraDesenho ?? 1) * 1.45))
                .chartYAxis {
                    AxisMarks { _ in AxisValueLabel().font(.sora(.footnote)) }
                }
                .frame(height: CGFloat(totais.count) * 34 + 8)
            }
        }
    }
}
