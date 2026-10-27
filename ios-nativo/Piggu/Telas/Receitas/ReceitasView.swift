import SwiftUI

/// Receitas do mes (features/incomes): com elas o Painel mostra a sobra e a taxa de poupanca.
/// Tambem as que entram todo mes e o dinheiro a receber, que so vira receita no "Recebi".
struct ReceitasView: View {
    let versaoDosDados: Int
    /// Lancou, recebeu ou apagou: as outras abas recarregam.
    let aoMudar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<[Receita]> = .carregando
    @State private var resumo: ResumoDoMes?
    @State private var recorrentes: [ContaFixa] = []
    @State private var aReceber: [AReceber] = []
    @State private var membros: [MembroDaFamilia] = []
    @State private var aviso: String?
    @State private var erro: String?
    @State private var lancando = false
    @State private var guardandoAReceber = false
    @State private var apagando: Receita?
    @State private var parando: ContaFixa?
    @State private var apagandoAReceber: AReceber?

    private var eu: String? { sessao.usuario?.id }
    private var hoje: String { Formatos.dia(Date()) }

    var body: some View {
        NavigationStack {
            List {
                if let aviso {
                    Faixa(texto: aviso, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                if let erro {
                    Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                if let resumo {
                    CartaoDoResumo(resumo: resumo)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                }
                Section {
                    Button { lancando = true } label: { Label("Nova receita", systemImage: "plus.circle.fill") }
                }
                .listRowBackground(paleta.superficie)
                if !recorrentes.isEmpty { entramTodoMes }
                secaoAReceber
                switch carga {
                case .carregando:
                    Section { EsqueletoDeLista(linhas: 3) }
                        .listRowBackground(paleta.superficie)
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await carregar() } }
                        .listRowBackground(Color.clear)
                case let .pronto(receitas, guardadoEm):
                    if let guardadoEm {
                        AvisoDeGuardado(guardadoEm: guardadoEm)
                    }
                    doMes(receitas)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
            .navigationTitle("Receitas")
            .topoDoPiggu()
            .refreshable { await carregar() }
            .task(id: "\(Formatos.mes(mes))#\(versaoDosDados)#\(rede.conectado)") { await carregar() }
            .sheet(isPresented: $lancando) {
                NovaReceitaView(temCompanhia: membros.count > 1) { mensagem, data in
                    erro = nil
                    aviso = mensagem
                    // Mostra o mes em que a receita entrou.
                    mes = data
                    aoMudar()
                }
            }
            .sheet(isPresented: $guardandoAReceber) {
                NovoAReceberView {
                    erro = nil
                    aviso = String(localized: "Guardado. Quando receber, toque em Recebi.")
                    aoMudar()
                }
            }
            .confirmationDialog(apagando.map { String(localized: "Apagar a receita \"\($0.descricao)\"?") } ?? "",
                                isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                                titleVisibility: .visible, presenting: apagando) { receita in
                Button("Apagar", role: .destructive) {
                    Task { await executar { try await $0.excluirReceita(receita.id) } }
                }
            }
            .confirmationDialog(parando.map { String(localized: "Parar de lançar \"\($0.descricao)\" todo mês?") } ?? "",
                                isPresented: Binding(get: { parando != nil }, set: { if !$0 { parando = nil } }),
                                titleVisibility: .visible, presenting: parando) { fixa in
                Button("Parar de repetir", role: .destructive) {
                    Task { await executar { try await $0.excluirContaFixa(fixa.id) } }
                }
            } message: { _ in
                Text("As receitas já lançadas continuam.")
            }
            .confirmationDialog(apagandoAReceber.map { String(localized: "Apagar o que \($0.quem) te deve?") } ?? "",
                                isPresented: Binding(get: { apagandoAReceber != nil },
                                                     set: { if !$0 { apagandoAReceber = nil } }),
                                titleVisibility: .visible, presenting: apagandoAReceber) { conta in
                Button("Apagar", role: .destructive) {
                    Task { await executar { try await $0.excluirAReceber(conta.id) } }
                }
            } message: { _ in
                Text("Nada entra como receita.")
            }
        }
    }

    // MARK: - Secoes

    private var entramTodoMes: some View {
        Section("Entram todo mês") {
            ForEach(recorrentes) { fixa in
                LinhaDoCartao(titulo: fixa.descricao, detalhe: detalhe(fixa)) {
                    Text(Formatos.moeda(fixa.valor + (fixa.segundoValor ?? 0)))
                        .font(.sora(.body, peso: .semibold)).monospacedDigit()
                }
                .swipeActions {
                    Button(role: .destructive) { parando = fixa } label: {
                        Label("Parar de repetir", systemImage: "stop.circle")
                    }
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private var secaoAReceber: some View {
        Section {
            ForEach(aReceber) { conta in
                let atrasada = conta.data < hoje
                VStack(alignment: .leading, spacing: 6) {
                    LinhaDoCartao(
                        titulo: [conta.quem, conta.descricao ?? ""].filter { !$0.isEmpty }.joined(separator: " · "),
                        detalhe: nil) {
                        Text(Formatos.moeda(conta.valor)).font(.sora(.body, peso: .semibold)).monospacedDigit()
                    }
                    HStack {
                        Text(atrasada
                             ? String(localized: "Está te devendo desde \(Formatos.dataCurta(conta.data))")
                             : String(localized: "Combinado para \(Formatos.dataCurta(conta.data))"))
                            .font(.sora(.footnote))
                            .foregroundStyle(atrasada ? paleta.perigo : paleta.textoSuave)
                        Spacer()
                        Button("Recebi") { Task { await receber(conta) } }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                    }
                }
                .swipeActions(edge: .leading) {
                    Button { Task { await receber(conta) } } label: { Label("Recebi", systemImage: "checkmark") }
                        .tint(paleta.sucesso)
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) { apagandoAReceber = conta } label: { Label("Apagar", systemImage: "trash") }
                }
            }
            if aReceber.isEmpty {
                Text("Ninguém te deve nada agora.").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            Button { guardandoAReceber = true } label: { Label("Guardar a receber", systemImage: "plus") }
        } header: {
            Text("A receber")
        } footer: {
            Text("Dinheiro que alguém te deve. Só conta como receita quando você toca em Recebi; passou da data combinada, o Piggu te avisa.")
        }
        .listRowBackground(paleta.superficie)
    }

    @ViewBuilder
    private func doMes(_ receitas: [Receita]) -> some View {
        Section {
            if receitas.isEmpty {
                EstadoVazio(icone: "arrow.down.circle", titulo: "Nenhuma receita neste mês",
                            texto: "Salário, freela, um pix de volta: tudo que entra conta para a sobra do mês.",
                            acao: ("Lançar uma receita", { lancando = true }))
            }
            ForEach(receitas) { receita in
                LinhaDoCartao(titulo: receita.descricao, detalhe: detalhe(receita)) {
                    Text(Formatos.moeda(receita.valor)).font(.sora(.body, peso: .semibold)).monospacedDigit()
                }
                .swipeActions {
                    if receita.compartilhado != true || receita.usuario == eu {
                        Button(role: .destructive) { apagando = receita } label: { Label("Apagar", systemImage: "trash") }
                    }
                }
                .accessibilityElement(children: .combine)
            }
        } header: {
            HStack {
                Text(Formatos.inicialMaiuscula(Formatos.nomeDoMes(mes)))
                Spacer()
                Text("Total: \(Formatos.moeda(receitas.reduce(0) { $0 + $1.valor }))").monospacedDigit()
            }
        }
        .listRowBackground(receitas.isEmpty ? Color.clear : paleta.superficie)
    }

    /// Em duas parcelas (D-087), os dois dias com o valor de cada um.
    private func detalhe(_ fixa: ContaFixa) -> String {
        if let segundoDia = fixa.segundoDia, let segundoValor = fixa.segundoValor {
            return String(localized: "Dia \(fixa.dia): \(Formatos.moeda(fixa.valor)) · dia \(segundoDia): \(Formatos.moeda(segundoValor)) · \(fixa.categoria)")
        }
        return String(localized: "Todo dia \(fixa.dia) · \(fixa.categoria)")
    }

    private func detalhe(_ receita: Receita) -> String {
        var partes = [Formatos.dataCurta(receita.data), receita.categoria]
        if receita.compartilhado == true { partes.append(String(localized: "compartilhada")) }
        if receita.usuario != eu && membros.count > 1 {
            partes.append(String(localized: "de \(PessoasDaFamilia.nome(receita.usuario, em: membros))"))
        }
        return partes.joined(separator: " · ")
    }

    // MARK: - API

    private func carregar() async {
        let mesDaTela = Formatos.mes(mes)
        let cliente = sessao.cliente
        async let resumo = try? cliente.resumoDoMes(mesDaTela)
        async let recorrentes = try? cliente.receitasRecorrentes(mesDaTela)
        async let aReceber = try? cliente.aReceber()
        async let membros = try? cliente.verFamilia().membros
        await sessao.cache.carregar("receitas/\(mesDaTela)", mostrar: { carga = $0 }) {
            try await cliente.listarReceitas(mesDaTela)
        }
        // O resumo e informativo: sem ele a tela segue.
        self.resumo = await resumo ?? sessao.cache.ler("resumo/\(mesDaTela)", como: ResumoDoMes.self)?.valor
        self.recorrentes = await recorrentes ?? []
        self.aReceber = await aReceber ?? self.aReceber
        self.membros = await membros ?? self.membros
    }

    /// "Recebi": vira receita de hoje, no mes em que o dinheiro entrou.
    private func receber(_ conta: AReceber) async {
        await executar { try await $0.receber(conta.id) }
        if erro == nil {
            aviso = String(localized: "\(conta.quem) pagou. Entrou como receita de hoje.")
            mes = Date()
        }
    }

    private func executar(_ acao: (ClienteDaAPI) async throws -> Void) async {
        do {
            try await acao(sessao.cliente)
            erro = nil
            aoMudar()
        } catch {
            erro = error.localizedDescription
        }
    }
}
