import SwiftUI

/// As telas do Mais, na ordem e nos grupos do menu do site (shell.ts). Abrem no Mais e tambem
/// pelos atalhos do Painel e do lancamento (NavigationLink(value:) na pilha de cada aba).
enum TelaDoMais: String, CaseIterable, Hashable {
    case planejar, metas, orcamento, contasDoMes, cartoes, calendario, compras, lugares, filmes, fotos, premios, girlsMath,
         familia, perfil, premium

    var titulo: LocalizedStringKey {
        switch self {
        case .planejar: "Planejar"
        case .metas: "Minhas metas"
        case .orcamento: "Orçamento"
        case .contasDoMes: "Contas do mês"
        case .cartoes: "Cartões e bancos"
        case .calendario: "Calendário"
        case .compras: "Compras"
        case .lugares: "Lugares"
        case .filmes: "Filmes"
        case .fotos: "Fotos"
        case .premios: "Prêmios"
        case .girlsMath: "Girls Math"
        case .familia: "Família"
        case .perfil: "Perfil"
        case .premium: "Premium"
        }
    }

    /// Os simbolos do menu do site (icone.ts): alvo, bandeira, pizza, recibo, cartao...
    var simbolo: String {
        switch self {
        case .planejar: "target"
        case .metas: "flag"
        case .orcamento: "chart.pie"
        case .contasDoMes: "doc.text"
        case .cartoes: "creditcard"
        case .calendario: "calendar"
        case .compras: "cart"
        case .lugares: "mappin.and.ellipse"
        case .filmes: "film"
        case .fotos: "photo"
        case .premios: "trophy"
        case .girlsMath: "bag"
        case .familia: "person.2"
        case .perfil: "person.crop.circle"
        case .premium: "star"
        }
    }

    /// Modulo de estilo de vida que liga ou desliga a tela no menu (Perfil); nil, sempre aparece.
    var modulo: String? {
        switch self {
        case .compras: "compras"
        case .lugares: "lugares"
        case .filmes: "filmes"
        case .fotos: "fotos"
        case .premios: "premios"
        case .girlsMath: "girls-math"
        default: nil
        }
    }

    @ViewBuilder
    var tela: some View {
        switch self {
        case .planejar: PlanejarView()
        case .metas: MinhasMetasView()
        case .orcamento: OrcamentoView()
        case .contasDoMes: ContasDoMesView()
        case .cartoes: ContasECartoesView()
        case .calendario: CalendarioView()
        case .compras: ComprasView()
        case .lugares: LugaresView()
        case .filmes: FilmesView()
        case .fotos: FotosView()
        case .premios: PremiosView()
        case .girlsMath: GirlsMathView()
        case .familia: FamiliaView()
        case .perfil: PerfilView()
        case .premium: PremiumView()
        }
    }
}

extension View {
    /// A pilha da aba sabe abrir as telas do Mais.
    func comTelasDoMais() -> some View {
        navigationDestination(for: TelaDoMais.self) { $0.tela }
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
