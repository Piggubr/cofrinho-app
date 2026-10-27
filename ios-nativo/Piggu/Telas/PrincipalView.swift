import SwiftUI

/// Abas do app logado (cada uma com o seu NavigationStack), o + flutuante, a folha de
/// lancar gasto, compartilhada pelas abas, e o aviso curto depois de lancar.
///
/// Os gastos guardados sem internet sobem quando a rede volta (e ao abrir o app).
struct PrincipalView: View {
    /// Link a abrir (piggu://, atalho, widget); volta a nil depois de aberto.
    @Binding var rota: Rota?

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @State private var aba = PrincipalView.abaInicial
    @State private var lancamento: PedidoDeLancamento? = PrincipalView.lancarAoAbrir ? .init(valor: nil) : nil
    /// Sobe a cada gasto lancado: Painel, Gastos, Receitas e Relatorios recarregam.
    @State private var versaoDosDados = 0
    @State private var aviso: String?
    /// A pilha do Mais, para os links abrirem orcamento, contas do mes, cartoes e calendario.
    @State private var caminhoDoMais: [TelaDoMais] = []

    var body: some View {
        abas
            .sheet(item: $lancamento) { pedido in
                LancarGastoView(valorInicial: pedido.valor) { mensagem in
                    versaoDosDados += 1
                    avisar(mensagem)
                }
            }
            .overlay(alignment: .top) {
                if let aviso {
                    AvisoFlutuante(texto: aviso)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .onTapGesture { self.aviso = nil }
                }
            }
            .onChange(of: rota, initial: true) { _, nova in
                abrir(nova)
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
                Tab("Gastos", systemImage: "list.bullet.rectangle.fill", value: Aba.gastos) { gastos }
                Tab("Receitas", systemImage: "arrow.down.circle.fill", value: Aba.receitas) { receitas }
                Tab("Relatórios", systemImage: "chart.bar.xaxis", value: Aba.relatorios) { relatorios }
                Tab("Mais", systemImage: "ellipsis.circle.fill", value: Aba.mais) { MaisView(caminho: $caminhoDoMais) }
            }
            .tabViewStyle(.sidebarAdaptable)
            .minimizarAbasAoRolar()
        } else {
            TabView(selection: $aba) {
                painel
                    .tabItem { Label("Painel", systemImage: "chart.pie.fill") }
                    .tag(Aba.painel)
                gastos
                    .tabItem { Label("Gastos", systemImage: "list.bullet.rectangle.fill") }
                    .tag(Aba.gastos)
                receitas
                    .tabItem { Label("Receitas", systemImage: "arrow.down.circle.fill") }
                    .tag(Aba.receitas)
                relatorios
                    .tabItem { Label("Relatórios", systemImage: "chart.bar.xaxis") }
                    .tag(Aba.relatorios)
                MaisView(caminho: $caminhoDoMais)
                    .tabItem { Label("Mais", systemImage: "ellipsis.circle.fill") }
                    .tag(Aba.mais)
            }
        }
    }

    private var painel: some View {
        PainelView(versaoDosDados: versaoDosDados, aoLancar: lancar) { aba = $0 }.comBotaoDeLancar(lancar)
    }

    private var gastos: some View {
        GastosView(versaoDosDados: versaoDosDados, aoLancar: lancar) { versaoDosDados += 1 }
            .comBotaoDeLancar(lancar)
    }

    private var receitas: some View {
        ReceitasView(versaoDosDados: versaoDosDados) { versaoDosDados += 1 }.comBotaoDeLancar(lancar)
    }

    private var relatorios: some View {
        RelatoriosView(versaoDosDados: versaoDosDados, aoLancar: lancar).comBotaoDeLancar(lancar)
    }

    private func lancar() {
        lancamento = PedidoDeLancamento(valor: nil)
    }

    private func abrir(_ rota: Rota?) {
        switch rota {
        case let .aba(destino):
            aba = destino
        case let .tela(tela):
            aba = .mais
            caminhoDoMais = [tela]
        case let .lancarGasto(valor):
            lancamento = PedidoDeLancamento(valor: valor)
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
    func comBotaoDeLancar(_ acao: @escaping () -> Void) -> some View {
        contentMargins(.bottom, 88, for: .scrollContent)
            .overlay(alignment: .bottomTrailing) {
                BotaoDeLancar(acao: acao)
            }
    }

    @ViewBuilder
    func minimizarAbasAoRolar() -> some View {
        if #available(iOS 26, *) {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}
