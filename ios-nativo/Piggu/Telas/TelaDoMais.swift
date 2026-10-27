import SwiftUI

/// As telas de organizar, na ordem do menu do site: abrem no Mais e tambem pelos atalhos do
/// Painel e do lancamento (NavigationLink(value:) na pilha de cada aba).
enum TelaDeOrganizacao: String, CaseIterable, Hashable {
    case orcamento, contasDoMes, cartoes, calendario

    var titulo: LocalizedStringKey {
        switch self {
        case .orcamento: "Orçamento"
        case .contasDoMes: "Contas do mês"
        case .cartoes: "Cartões e bancos"
        case .calendario: "Calendário"
        }
    }

    /// Os simbolos do menu do site (icone.ts): pizza, recibo, cartao, calendario.
    var simbolo: String {
        switch self {
        case .orcamento: "chart.pie"
        case .contasDoMes: "doc.text"
        case .cartoes: "creditcard"
        case .calendario: "calendar"
        }
    }

    @ViewBuilder
    var tela: some View {
        switch self {
        case .orcamento: OrcamentoView()
        case .contasDoMes: ContasDoMesView()
        case .cartoes: ContasECartoesView()
        case .calendario: CalendarioView()
        }
    }
}

extension View {
    /// A pilha da aba sabe abrir as telas de organizar.
    func comTelasDeOrganizacao() -> some View {
        navigationDestination(for: TelaDeOrganizacao.self) { $0.tela }
    }
}

/// Seletor de mes simples, dentro de um formulario (o input type="month" do site).
struct EscolhaDeMes: View {
    let titulo: LocalizedStringKey
    /// aaaa-mm
    @Binding var mes: String

    var body: some View {
        LabeledContent(titulo) {
            HStack(spacing: 4) {
                Button { mes = DadosDoMes.somar(-1, a: mes) } label: { Image(systemName: "chevron.left") }
                    .accessibilityLabel("Mês anterior")
                Text(Formatos.nomeDoMes(chave: mes)).monospacedDigit().frame(minWidth: 130)
                Button { mes = DadosDoMes.somar(1, a: mes) } label: { Image(systemName: "chevron.right") }
                    .accessibilityLabel("Próximo mês")
            }
            .buttonStyle(.borderless)
        }
    }
}

enum DadosDoMes {
    /// "2026-10" mais `meses`.
    static func somar(_ meses: Int, a mes: String) -> String {
        let partes = mes.split(separator: "-").compactMap { Int($0) }
        guard partes.count == 2 else { return mes }
        let total = partes[0] * 12 + partes[1] - 1 + meses
        return String(format: "%04d-%02d", total / 12, total % 12 + 1)
    }
}
