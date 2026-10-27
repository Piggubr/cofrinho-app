import SwiftUI

/// Abas do app logado e a folha de lancar gasto, compartilhada pelas abas.
struct PrincipalView: View {
    enum Aba: String {
        case painel, gastos, mais
    }

    @State private var aba = PrincipalView.abaInicial
    @State private var lancando = PrincipalView.lancarAoAbrir
    /// Sobe a cada gasto lancado: Painel e Gastos recarregam.
    @State private var versaoDosDados = 0

    var body: some View {
        TabView(selection: $aba) {
            PainelView(versaoDosDados: versaoDosDados, aoLancar: { lancando = true })
                .tabItem { Label("Painel", systemImage: "chart.pie.fill") }
                .tag(Aba.painel)
            GastosView(versaoDosDados: versaoDosDados, aoLancar: { lancando = true })
                .tabItem { Label("Gastos", systemImage: "list.bullet.rectangle.fill") }
                .tag(Aba.gastos)
            MaisView()
                .tabItem { Label("Mais", systemImage: "ellipsis.circle.fill") }
                .tag(Aba.mais)
        }
        .sheet(isPresented: $lancando) {
            LancarGastoView { versaoDosDados += 1 }
        }
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

/// O "+" da barra de navegacao.
struct BotaoDeLancar: ToolbarContent {
    let acao: () -> Void

    var body: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Button(action: acao) {
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
            }
            .accessibilityLabel("Lançar gasto")
        }
    }
}

/// Setas para trocar de mes, com o nome do mes no meio.
struct SeletorDeMes: View {
    @Binding var mes: Date

    private var ehMesAtual: Bool { Formatos.mes(mes) == Formatos.mes(Date()) }

    var body: some View {
        HStack {
            Button {
                mes = Formatos.somarMeses(-1, a: mes)
            } label: {
                Image(systemName: "chevron.left")
            }
            .accessibilityLabel("Mês anterior")
            Spacer()
            Text(Formatos.inicialMaiuscula(Formatos.nomeDoMes(mes)))
                .font(.headline)
            Spacer()
            Button {
                mes = Formatos.somarMeses(1, a: mes)
            } label: {
                Image(systemName: "chevron.right")
            }
            .disabled(ehMesAtual)
            .accessibilityLabel("Próximo mês")
        }
        .buttonStyle(.borderless)
        .padding(.vertical, 4)
    }
}
