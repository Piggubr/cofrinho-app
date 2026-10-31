import SwiftUI

/// Cartao do Painel (o .cartao do site): superficie solida, nunca vidro.
struct Cartao<Acessorio: View, Conteudo: View>: View {
    let titulo: Text?
    @ViewBuilder var acessorio: Acessorio
    @ViewBuilder var conteudo: Conteudo

    @Environment(\.paleta) private var paleta

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let titulo {
                HStack(alignment: .firstTextBaseline) {
                    titulo
                        .font(.sora(.headline, peso: .semibold))
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 8)
                    acessorio
                }
            }
            conteudo
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(paleta.superficie, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(paleta.borda.opacity(0.6)))
    }
}

extension Cartao where Acessorio == EmptyView {
    init(_ titulo: LocalizedStringKey?, @ViewBuilder conteudo: () -> Conteudo) {
        self.titulo = titulo.map { Text($0) }
        acessorio = EmptyView()
        self.conteudo = conteudo()
    }
}

extension Cartao where Acessorio == EmptyView {
    /// Titulo que ja vem pronto (o nome do mes).
    init(verbatim titulo: String, @ViewBuilder conteudo: () -> Conteudo) {
        self.titulo = Text(verbatim: titulo)
        acessorio = EmptyView()
        self.conteudo = conteudo()
    }
}

extension Cartao {
    init(_ titulo: LocalizedStringKey, @ViewBuilder acessorio: () -> Acessorio, @ViewBuilder conteudo: () -> Conteudo) {
        self.titulo = Text(titulo)
        self.acessorio = acessorio()
        self.conteudo = conteudo()
    }
}

/// Etiqueta arredondada ao lado do titulo (o .etiqueta do site).
struct Etiqueta: View {
    let texto: String

    @Environment(\.paleta) private var paleta

    var body: some View {
        Text(texto)
            .font(.sora(.caption, peso: .semibold))
            .monospacedDigit()
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(paleta.suave, in: Capsule())
    }
}

/// Linha de lista de cartao: titulo, detalhe e o que vier a direita.
struct LinhaDoCartao<Direita: View>: View {
    let titulo: String
    var detalhe: String?
    @ViewBuilder var direita: Direita

    @Environment(\.paleta) private var paleta

    var body: some View {
        LadoALado(espaco: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(titulo)
                if let detalhe, !detalhe.isEmpty {
                    Text(detalhe)
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
            }
            Spacer(minLength: 8)
            direita
        }
        .padding(.vertical, 4)
    }
}

/// Campo de dinheiro que aceita virgula e ponto (12,50, 1.234,56), como o appValor do site.
struct CampoDeValor: View {
    let rotulo: LocalizedStringKey
    @Binding var texto: String
    var prompt: String = "0,00"

    var body: some View {
        TextField(rotulo, text: $texto, prompt: Text(verbatim: prompt))
            .keyboardType(.decimalPad)
            .monospacedDigit()
            .onSubmit { formatar() }
            .accessibilityLabel(rotulo)
    }

    /// Ao sair do campo, o valor fica no padrao brasileiro (12,50).
    func formatar() {
        if let valor = Formatos.lerValor(texto) {
            texto = Formatos.valorParaDigitar(valor)
        }
    }
}

/// Faixa de aviso dentro de uma lista ou cartao: sucesso, erro ou informacao.
struct Faixa: View {
    enum Tipo {
        case sucesso, erro, info
    }

    let texto: String
    var tipo: Tipo = .info

    @Environment(\.paleta) private var paleta

    var body: some View {
        Label {
            Text(texto)
        } icon: {
            Image(systemName: tipo == .erro ? "exclamationmark.circle.fill"
                  : tipo == .sucesso ? "checkmark.circle.fill" : "info.circle.fill")
        }
        .font(.sora(.footnote))
        .foregroundStyle(tipo == .erro ? paleta.perigo : tipo == .sucesso ? paleta.sucesso : paleta.texto)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(fundo, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var fundo: Color {
        tipo == .erro ? paleta.perigoSuave : tipo == .sucesso ? paleta.sucessoSuave : paleta.alertaSuave
    }
}

/// Barra de progresso do site (.progresso): cheia na cor do tema, vermelha quando estoura.
/// Quanto cada categoria levou: o nome e o valor numa linha, e a barra embaixo, contra a maior.
/// O mesmo desenho no Painel e nos Relatorios; `anterior` vira o traco do mes anterior.
struct BarrasPorCategoria: View {
    struct Linha: Identifiable {
        let categoria: String
        let total: Decimal
        var anterior: Decimal = 0
        var id: String { categoria }
    }

    let linhas: [Linha]

    var body: some View {
        let maior = linhas.map { max($0.total, $0.anterior) }.max()?.paraDesenho ?? 0
        VStack(spacing: 14) {
            ForEach(linhas) { linha in
                VStack(spacing: 6) {
                    LadoALado(alinhamento: .firstTextBaseline, espaco: 12) {
                        Text(linha.categoria).font(.sora(.subheadline))
                        Spacer(minLength: 0)
                        Text(Formatos.moeda(linha.total)).font(.sora(.subheadline, peso: .semibold)).monospacedDigit()
                    }
                    BarraDeValor(fracao: maior > 0 ? linha.total.paraDesenho / maior : 0,
                                 marca: linha.anterior > 0 && maior > 0 ? linha.anterior.paraDesenho / maior : nil)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(linha.anterior > 0
                    ? Text("\(linha.categoria): \(Formatos.moeda(linha.total)). Mês anterior: \(Formatos.moeda(linha.anterior))")
                    : Text("\(linha.categoria): \(Formatos.moeda(linha.total))"))
            }
        }
        .padding(.vertical, 4)
    }
}

/// O vazio dentro de um cartao do Painel: o icone com o titulo, um texto curto e o botao do
/// primeiro passo, tudo no centro, como o EstadoVazio das listas.
struct VazioDoCartao: View {
    let icone: String
    let titulo: LocalizedStringKey
    var texto: LocalizedStringKey?
    let acao: LocalizedStringKey
    let executar: () -> Void

    @Environment(\.paleta) private var paleta

    var body: some View {
        VStack(spacing: 8) {
            Label(titulo, systemImage: icone)
                .font(.sora(.subheadline, peso: .medium))
            if let texto {
                Text(texto)
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
                    .multilineTextAlignment(.center)
            }
            Button(acao, action: executar)
                .buttonStyle(.bordered)
                .controlSize(.small)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
    }
}

struct BarraDeProgresso: View {
    /// 0 a 100.
    let percentual: Double
    var estourado = false
    var concluido = false

    @State private var mostrado: Double = 0
    @Environment(\.accessibilityReduceMotion) private var reduzirMovimento
    @Environment(\.paleta) private var paleta

    var body: some View {
        Capsule()
            .fill(paleta.trilho)
            .frame(height: 8)
            .overlay(alignment: .leading) {
                GeometryReader { espaco in
                    Capsule()
                        .fill(estourado ? paleta.perigo : concluido ? paleta.sucesso : paleta.primaria)
                        .frame(width: espaco.size.width * min(max(mostrado, 0), 100) / 100)
                }
            }
            .accessibilityHidden(true)
            // A barra enche ao aparecer e acompanha a mudanca, como o .progresso do site.
            .onAppear { animar(percentual) }
            .onChange(of: percentual) { _, novo in animar(novo) }
    }

    private func animar(_ valor: Double) {
        withAnimation(reduzirMovimento ? nil : .easeOut(duration: 0.7)) { mostrado = valor }
    }
}

/// Convite ao Premium: abre a tela de planos com a compra da Apple, nunca o site (D-094).
struct LinkDoPremium: View {
    let titulo: LocalizedStringKey

    var body: some View {
        NavigationLink { PremiumView() } label: {
            Label(titulo, systemImage: "star")
        }
    }
}

/// Aviso curto depois de uma acao ("Gasto lançado."), flutuando no topo: vidro, porque fica
/// por cima do conteudo.
struct AvisoFlutuante: View {
    let texto: String

    var body: some View {
        Text(texto)
            .font(.sora(.subheadline, peso: .medium))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .pigguVidro(em: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .padding(.horizontal, 16)
            .accessibilityAddTraits(.updatesFrequently)
            // Some sozinho em segundos: o VoiceOver le na hora, sem a pessoa ir ate ele.
            .onChange(of: texto, initial: true) { _, novo in AccessibilityNotification.Announcement(novo).post() }
    }
}

/// O nome de quem lancou, a partir do id (nomeNaFamilia do site).
enum PessoasDaFamilia {
    static let sistema = "00000000-0000-0000-0000-000000000000"

    static func nome(_ id: String?, em membros: [MembroDaFamilia]) -> String {
        if id == sistema { return "Piggu" }
        return membros.first { $0.id == id }?.nome ?? String(localized: "Ex-membro")
    }
}
