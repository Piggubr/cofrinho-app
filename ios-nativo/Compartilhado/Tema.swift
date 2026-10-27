import SwiftUI

/// Cor do Piggu (Perfil > Cor do Piggu no site, frontend/src/app/core/ui/tema.ts). Fica no
/// aparelho, na mesma chave do site (`piggu_tema`), e vale antes do login tambem.
enum Tema: String, CaseIterable, Identifiable, Sendable {
    case chiclete, rosa, menta, azul, lilas

    static let chave = "piggu_tema"

    var id: String { rawValue }

    var nome: LocalizedStringKey {
        switch self {
        case .chiclete: "Chiclete"
        case .rosa: "Rosa Piggu"
        case .menta: "Menta"
        case .azul: "Azul"
        case .lilas: "Lilás"
        }
    }

    /// Chiclete, Rosa, Menta e Azul sao gratis; as outras cores sao do Premium.
    var premium: Bool { self == .lilas }

    var paleta: Paleta {
        switch self {
        case .chiclete: .chiclete
        case .rosa: .rosa
        case .menta: .menta
        case .azul: .azul
        case .lilas: .lilas
        }
    }
}

/// Os tokens do frontend/src/styles.scss, cada um com o valor do claro e do escuro.
///
/// So os papeis que o app usa. Texto, sucesso, alerta e perigo sao iguais em todos os temas;
/// o resto muda com a cor escolhida. O `--muted` do site ficou de fora: no branco ele nao
/// chega ao contraste minimo para texto, entao o secundario e o `--ink-soft`.
struct Paleta: Sendable {
    /// --primaria: botoes, links, o + e a cor de destaque do sistema (tint).
    let primaria: Color
    /// --primaria-contraste: texto sobre a primaria.
    let primariaContraste: Color
    /// --site-bg: fundo das telas.
    let fundo: Color
    /// --surface: cartoes e linhas de lista.
    let superficie: Color
    /// --surface-sunken
    let superficieFunda: Color
    /// --border
    let borda: Color
    /// --field-bg: fundo dos campos de texto.
    let campo: Color
    /// --track: trilho das barras.
    let trilho: Color
    /// --rose-soft: selo dos estados vazios.
    let suave: Color
    /// --serie-gasto e --serie-receita: barras e icones dos graficos.
    let serieGasto: Color
    let serieReceita: Color

    /// --ink e --ink-soft
    let texto = Color(claro: 0x2B1E32, escuro: 0xF3E9EE)
    let textoSuave = Color(claro: 0x5C4C58, escuro: 0xCDBCC5)
    /// --ok-ink, --ouro-ink e --danger: legiveis como texto nos dois modos.
    let sucesso = Color(claro: 0x46664A, escuro: 0xA9D4A9)
    let alerta = Color(claro: 0x82601A, escuro: 0xF0CD85)
    let perigo = Color(claro: 0xC0436E, escuro: 0xEF6B95)
    /// --ok-soft, --ouro-soft e --danger-soft: fundo dos avisos.
    let sucessoSuave = Color(claro: 0xECF3EC, escuro: 0x1F3324)
    let alertaSuave = Color(claro: 0xFBF1DE, escuro: 0x3A2E17)
    let perigoSuave = Color(claro: 0xFCEAF0, escuro: 0x3D1A27)
}

extension Paleta {
    static let chiclete = Paleta(
        primaria: Color(claro: 0xD1006B, escuro: 0xFF5CAE),
        primariaContraste: Color(claro: 0xFFFFFF, escuro: 0x1F0012),
        fundo: Color(claro: 0xFFF4F9, escuro: 0x14060D),
        superficie: Color(claro: 0xFFFFFF, escuro: 0x22101A),
        superficieFunda: Color(claro: 0xFFE9F3, escuro: 0x2D1523),
        borda: Color(claro: 0xFFD9EA, escuro: 0x3D1A2E),
        campo: Color(claro: 0xFFECF5, escuro: 0x2D1523),
        trilho: Color(claro: 0xFFD6E9, escuro: 0x3D1A2E),
        suave: Color(claro: 0xFFE0EF, escuro: 0x4A1430),
        serieGasto: Color(claro: 0xFF3D96, escuro: 0xFF5CAE),
        serieReceita: Color(claro: 0x22B573, escuro: 0x3EE08F))

    static let rosa = Paleta(
        primaria: Color(claro: 0x8E2F58, escuro: 0xE58FB2),
        primariaContraste: Color(claro: 0xFFFFFF, escuro: 0x2A0F1D),
        fundo: Color(claro: 0xFCF8F9, escuro: 0x161015),
        superficie: Color(claro: 0xFFFFFF, escuro: 0x221A20),
        superficieFunda: Color(claro: 0xF7F0F3, escuro: 0x2C2229),
        borda: Color(claro: 0xF0E4E9, escuro: 0x372B34),
        campo: Color(claro: 0xF7F0F3, escuro: 0x2C2229),
        trilho: Color(claro: 0xF3E6EC, escuro: 0x372B34),
        suave: Color(claro: 0xFBEFF3, escuro: 0x3A2431),
        serieGasto: Color(claro: 0xC27A97, escuro: 0xE58FB2),
        serieReceita: Color(claro: 0x7FA07F, escuro: 0x8FBF8F))

    /// No Menta a receita vira cinza-azulado para nao confundir com o gasto verde.
    static let menta = Paleta(
        primaria: Color(claro: 0x1F6B5A, escuro: 0x8FD6BD),
        primariaContraste: Color(claro: 0xFFFFFF, escuro: 0x0D2A22),
        fundo: Color(claro: 0xF7FBFA, escuro: 0x0F1513),
        superficie: Color(claro: 0xFFFFFF, escuro: 0x1A2220),
        superficieFunda: Color(claro: 0xEEF6F3, escuro: 0x222C29),
        borda: Color(claro: 0xE1EFE9, escuro: 0x2C3834),
        campo: Color(claro: 0xEEF6F3, escuro: 0x222C29),
        trilho: Color(claro: 0xDDEEE7, escuro: 0x2C3834),
        suave: Color(claro: 0xEAF6F1, escuro: 0x20352E),
        serieGasto: Color(claro: 0x3F9A82, escuro: 0x8FD6BD),
        serieReceita: Color(claro: 0x8A9BB8, escuro: 0xA7B6CF))

    static let azul = Paleta(
        primaria: Color(claro: 0x22608F, escuro: 0x96C4E8),
        primariaContraste: Color(claro: 0xFFFFFF, escuro: 0x0B2033),
        fundo: Color(claro: 0xF7FAFC, escuro: 0x0F1317),
        superficie: Color(claro: 0xFFFFFF, escuro: 0x1A1F24),
        superficieFunda: Color(claro: 0xEEF4F8, escuro: 0x22292F),
        borda: Color(claro: 0xE2ECF3, escuro: 0x2C343B),
        campo: Color(claro: 0xEEF4F8, escuro: 0x22292F),
        trilho: Color(claro: 0xDDE9F2, escuro: 0x2C343B),
        suave: Color(claro: 0xEAF3FA, escuro: 0x1F2F3C),
        serieGasto: Color(claro: 0x5A95C4, escuro: 0x96C4E8),
        serieReceita: Color(claro: 0x7FA07F, escuro: 0x8FBF8F))

    static let lilas = Paleta(
        primaria: Color(claro: 0x6A3D94, escuro: 0xC4A5EC),
        primariaContraste: Color(claro: 0xFFFFFF, escuro: 0x1E1030),
        fundo: Color(claro: 0xFAF8FC, escuro: 0x131017),
        superficie: Color(claro: 0xFFFFFF, escuro: 0x1E1A24),
        superficieFunda: Color(claro: 0xF3EFF8, escuro: 0x27222E),
        borda: Color(claro: 0xEBE4F2, escuro: 0x332C3B),
        campo: Color(claro: 0xF3EFF8, escuro: 0x27222E),
        trilho: Color(claro: 0xE9E0F3, escuro: 0x332C3B),
        suave: Color(claro: 0xF3ECFB, escuro: 0x2C2238),
        serieGasto: Color(claro: 0xA07CC5, escuro: 0xC4A5EC),
        serieReceita: Color(claro: 0x7FA07F, escuro: 0x8FBF8F))
}

extension EnvironmentValues {
    @Entry var paleta: Paleta = .chiclete
}

extension Color {
    /// Cor que troca sozinha com o modo claro e escuro do aparelho.
    init(claro: UInt32, escuro: UInt32) {
        self.init(uiColor: UIColor { tracos in
            UIColor(hex: tracos.userInterfaceStyle == .dark ? escuro : claro)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1)
    }
}
