import SwiftUI
import TipKit

/// Abas do app logado (cada uma com o seu NavigationStack), o + flutuante, as folhas de
/// lancar gasto e receita, compartilhadas pelas abas, e o aviso curto depois de lancar.
///
/// Os gastos guardados sem internet sobem quando a rede volta (e ao abrir o app).
struct PrincipalView: View {
    /// Link a abrir (piggu://, atalho, widget); volta a nil depois de aberto.
    @Binding var rota: Rota?

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    /// Aba e pilhas ficam por janela: duas janelas do Piggu, cada uma no seu lugar.
    @SceneStorage("piggu_aba") private var aba = PrincipalView.abaInicial
    @State private var lancamento: PedidoDeLancamento? = PrincipalView.lancarAoAbrir ? .init(valor: nil) : nil
    @State private var lancandoReceita = false
    /// Sobe a cada gasto lancado: Painel, Gastos, Receitas e Relatorios recarregam.
    @State private var versaoDosDados = 0
    @State private var aviso: String?
    /// Gasto achado no Spotlight, para a aba Gastos abrir.
    @State private var gastoParaAbrir: GastoPedido?
    @Environment(\.scenePhase) private var fase
    @Environment(\.accessibilityReduceMotion) private var reduzirMovimento
    /// A pilha do Mais, para os links abrirem orcamento, contas do mes, cartoes e calendario.
    @SceneStorage("piggu_caminho_do_mais") private var caminhoDoMais = ""
    @AppStorage(PrincipalView.chaveEsconderAbas) private var esconderAbas = false
    /// Gastos ou Receitas, dentro da aba Lançamentos (D-164).
    @SceneStorage("piggu_lancamentos") private var lancamentos = Aba.gastos
    @SceneStorage("piggu_caminho_do_planejar") private var caminhoDoPlanejar = ""
    /// Os Relatórios abrem dentro do Planejar.
    @State private var relatoriosAbertos = false

    /// Ajuste do Perfil: a barra de abas encolhe ao rolar a lista. Desligado, ela fica sempre inteira.
    static let chaveEsconderAbas = "piggu_esconder_abas"

    var body: some View {
        abas
            // A busca e o assistente do topo abrem telas por aqui.
            .environment(\.abrirRota, AbrirRota { abrir($0) })
            .sheet(item: $lancamento) { pedido in
                LancarGastoView(valorInicial: pedido.valor) { mensagem in
                    versaoDosDados += 1
                    avisar(mensagem)
                    Task { await AtualizadorDoSistema.atualizar(sessao) }
                }
            }
            .sheet(isPresented: $lancandoReceita) {
                NovaReceitaView { mensagem, _ in
                    versaoDosDados += 1
                    avisar(mensagem)
                }
            }
            .overlay(alignment: .top) {
                if let aviso {
                    AvisoFlutuante(texto: aviso)
                        .padding(.top, 8)
                        .transition(reduzirMovimento ? .opacity : .move(edge: .top).combined(with: .opacity))
                        .onTapGesture { self.aviso = nil }
                }
            }
            .onChange(of: rota, initial: true) { _, nova in
                abrir(nova)
            }
            // A aba guardada antes do D-164 pode ser receitas ou relatorios, que não estão mais na barra.
            .onChange(of: aba, initial: true) { _, nova in
                if nova == .receitas || nova == .relatorios { selecionar(nova) }
            }
            // Widgets, Siri e Spotlight ao abrir e ao voltar ao app; o aparelho para os avisos.
            .task(id: fase) {
                guard fase == .active else { return }
                await AtualizadorDoSistema.atualizar(sessao)
                if sessao.usuario?.avisos?.push == true { await Notificacoes.renovar() }
            }
            .task(id: sessao.usuario?.plano) {
                // O Premium acabou: o icone do Premium volta ao padrao, como a cor (Classico fica).
                if await IconeDoApp.voltarAoPadraoSemPremium(sessao.usuario?.plano == .premium) {
                    avisar(String(localized: "O Premium acabou, então a cor e o ícone voltaram para o padrão."))
                }
            }
            .task(id: rede.conectado) {
                guard rede.conectado else { return }
                let enviados = await sessao.fila.enviar()
                if enviados > 0 {
                    versaoDosDados += 1
                    avisar(String(localized: "Enviados \(enviados) gastos guardados sem conexão."))
                }
            }
    }

    /// Aviso discreto no topo, que some sozinho.
    private func avisar(_ texto: String) {
        withAnimation { aviso = texto }
        Task {
            try? await Task.sleep(for: .seconds(4))
            if aviso == texto { withAnimation { aviso = nil } }
        }
    }

    @ViewBuilder
    private var abas: some View {
        if #available(iOS 18, *) {
            TabView(selection: $aba) {
                Tab("Painel", systemImage: "chart.pie.fill", value: Aba.painel) { painel }
                Tab("Lançamentos", systemImage: "list.bullet.rectangle.fill", value: Aba.gastos) { abaDeLancamentos }
                Tab("Planejar", systemImage: "target", value: Aba.planejar) { planejar }
                Tab("Mais", systemImage: "ellipsis.circle.fill", value: Aba.mais) { MaisView(caminho: .guardada($caminhoDoMais)) }
            }
            .tabViewStyle(.sidebarAdaptable)
            .minimizarAbasAoRolar(esconderAbas)
        } else {
            TabView(selection: $aba) {
                painel
                    .tabItem { Label("Painel", systemImage: "chart.pie.fill") }
                    .tag(Aba.painel)
                abaDeLancamentos
                    .tabItem { Label("Lançamentos", systemImage: "list.bullet.rectangle.fill") }
                    .tag(Aba.gastos)
                planejar
                    .tabItem { Label("Planejar", systemImage: "target") }
                    .tag(Aba.planejar)
                MaisView(caminho: .guardada($caminhoDoMais))
                    .tabItem { Label("Mais", systemImage: "ellipsis.circle.fill") }
                    .tag(Aba.mais)
            }
        }
    }

    private var painel: some View {
        PainelView(versaoDosDados: versaoDosDados, aoLancar: lancar, aoMudar: { versaoDosDados += 1 }) { selecionar($0) }
            .comBotaoDeLancar(gasto: lancar, receita: lancarReceita)
    }

    private var gastos: some View {
        GastosView(versaoDosDados: versaoDosDados, aoLancar: lancar, abrirGasto: $gastoParaAbrir, lancamentos: $lancamentos) { versaoDosDados += 1 }
            .comBotaoDeLancar(gasto: lancar, receita: lancarReceita)
    }

    private var receitas: some View {
        ReceitasView(versaoDosDados: versaoDosDados, lancamentos: $lancamentos) { versaoDosDados += 1 }
            .comBotaoDeLancar(gasto: lancar, receita: lancarReceita)
    }

    /// Gastos e Receitas, cada uma com a sua tela, trocadas pelo seletor no topo (D-164).
    private var abaDeLancamentos: some View {
        Group { if lancamentos == .receitas { receitas } else { gastos } }
    }

    /// Planejar, com os Relatórios dentro (D-164).
    private var planejar: some View {
        NavigationStack(path: .guardada($caminhoDoPlanejar)) {
            PlanejarView { relatoriosAbertos = true }
                .topoDoPiggu()
                .comTelasDoMais()
                .navigationDestination(isPresented: $relatoriosAbertos) {
                    RelatoriosView(versaoDosDados: versaoDosDados, aoLancar: lancar, comPilha: false)
                }
        }
        .comBotaoDeLancar(gasto: lancar, receita: lancarReceita)
    }

    /// Vai para a aba do destino; receitas e relatorios abrem dentro de Lançamentos e do Planejar.
    private func selecionar(_ destino: Aba) {
        switch destino {
        case .gastos, .receitas:
            lancamentos = destino
            aba = .gastos
        case .relatorios:
            aba = .planejar
            relatoriosAbertos = true
        case .painel, .planejar, .mais:
            aba = destino
        }
    }

    private func lancar() {
        lancamento = PedidoDeLancamento(valor: nil)
    }

    private func lancarReceita() {
        lancandoReceita = true
    }

    private func abrir(_ rota: Rota?) {
        switch rota {
        case let .aba(destino):
            selecionar(destino)
        case .tela(.planejar):
            aba = .planejar
        case let .tela(tela):
            aba = .mais
            caminhoDoMais = tela.rawValue
        case let .lancarGasto(valor):
            lancamento = PedidoDeLancamento(valor: valor)
        case let .verGasto(id, mes):
            selecionar(.gastos)
            gastoParaAbrir = GastoPedido(id: id, mes: mes)
        case .confirmarEmail, .redefinirSenha, .textoLegal, .esqueciSenha, .cpf, nil:
            // Os da conta abrem na RaizView.
            return
        }
        self.rota = nil
    }

    // Em Debug, `-PigguAba gastos` e `-PigguLancar YES` abrem direto numa tela (prints).
    #if DEBUG
    private static var abaInicial: Aba {
        UserDefaults.standard.string(forKey: "PigguAba").flatMap(Aba.init(rawValue:)) ?? .painel
    }
    private static var lancarAoAbrir: Bool { UserDefaults.standard.bool(forKey: "PigguLancar") }
    #else
    private static let abaInicial = Aba.painel
    private static let lancarAoAbrir = false
    #endif
}

/// Cada vez que a folha de lancar abre; o valor vem do link ou do atalho.
struct PedidoDeLancamento: Identifiable {
    let id = UUID()
    let valor: Decimal?
}

private extension View {
    /// O + flutua sobre a lista, acima da barra de abas; a lista ganha folga embaixo para o
    /// ultimo item nao ficar escondido atras dele.
    func comBotaoDeLancar(gasto: @escaping () -> Void, receita: @escaping () -> Void) -> some View {
        contentMargins(.bottom, 88, for: .scrollContent)
            .overlay(alignment: .bottomTrailing) {
                BotaoDeLancar(gasto: gasto, receita: receita)
                    .popoverTip(Dicas.Lancar(), arrowEdge: .bottom)
            }
    }

    @ViewBuilder
    func minimizarAbasAoRolar(_ ligado: Bool) -> some View {
        if #available(iOS 26, *), ligado {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}
