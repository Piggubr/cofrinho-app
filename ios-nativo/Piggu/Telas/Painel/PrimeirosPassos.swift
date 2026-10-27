import SwiftUI

/// Guia de primeiro uso: moeda, limite do mes e primeiro gasto (primeiros-passos.ts).
///
/// O limite e o gasto contam como feitos pelo que ja existe na conta; quem ja tem os dois nao
/// ve o guia. A moeda e o "pular" ficam neste aparelho, na mesma chave do site.
struct PrimeirosPassos: View {
    let modelo: ModeloDoPainel
    let temLimite: Bool
    let temGasto: Bool
    let aoLancar: () -> Void

    /// As moedas mais comuns de quem usa o Piggu; a lista inteira fica no Perfil.
    private static let moedas = ["BRL", "EUR", "USD", "GBP", "ARS", "CLP", "UYU", "JPY"]

    @State private var estado = Estado()
    @State private var moedaEscolhida = Formatos.codigoDaMoeda
    @State private var salvando = false
    @Environment(\.paleta) private var paleta

    struct Estado: Codable {
        var moeda: Bool?
        var fechado: Bool?
    }

    private var chave: String { "piggu_guia_\(modelo.eu ?? "")" }
    private var moedaFeita: Bool { estado.moeda == true }
    private var feitos: Int { [moedaFeita, temLimite, temGasto].filter { $0 }.count }

    var body: some View {
        Group {
            if estado.fechado != true && !(temLimite && temGasto) {
                Cartao("Primeiros passos") {
                    Etiqueta(texto: String(localized: "\(feitos) de 3"))
                } conteudo: {
                    passo("1. Em que moeda você gasta?", feito: moedaFeita) {
                        if moedaFeita {
                            Text(Formatos.nomeDaMoeda(Formatos.codigoDaMoeda)).font(.sora(.footnote))
                        } else {
                            HStack {
                                Picker("Moeda", selection: $moedaEscolhida) {
                                    ForEach(Array(Set([Formatos.codigoDaMoeda] + Self.moedas)).sorted(), id: \.self) {
                                        Text(Formatos.nomeDaMoeda($0)).tag($0)
                                    }
                                }
                                .labelsHidden()
                                Spacer()
                                Button("Usar esta") {
                                    Task {
                                        salvando = true
                                        if await modelo.usarMoeda(moedaEscolhida) { guardar { $0.moeda = true } }
                                        salvando = false
                                    }
                                }
                                .botaoPrincipal()
                                .disabled(salvando)
                            }
                        }
                    }
                    passo("2. Quanto quer gastar por mês?", feito: temLimite) {
                        if !temLimite { NavigationLink("Definir o limite", value: TelaDeOrganizacao.orcamento) }
                    }
                    passo("3. Lance o primeiro gasto ou importe o extrato", feito: temGasto) {
                        if !temGasto {
                            HStack {
                                Button("Lançar gasto", action: aoLancar).buttonStyle(.bordered)
                                LinkDoSite(titulo: "Importar extrato", caminho: "/gastos#importar")
                            }
                        }
                    }
                    if let erro = modelo.erros[.resumo] {
                        Faixa(texto: erro, tipo: .erro)
                    }
                    Button("Pular o guia") { guardar { $0.fechado = true } }
                        .font(.sora(.footnote))
                }
            }
        }
        .onAppear {
            estado = UserDefaults.standard.data(forKey: chave)
                .flatMap { try? JSONDecoder().decode(Estado.self, from: $0) } ?? Estado()
        }
    }

    private func passo<Conteudo: View>(_ titulo: LocalizedStringKey, feito: Bool,
                                       @ViewBuilder conteudo: () -> Conteudo) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label {
                Text(titulo).strikethrough(feito).foregroundStyle(feito ? paleta.textoSuave : paleta.texto)
            } icon: {
                Image(systemName: feito ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(feito ? paleta.sucesso : paleta.textoSuave)
            }
            .accessibilityValue(feito ? Text("feito") : Text(""))
            conteudo()
        }
        .padding(.vertical, 2)
    }

    private func guardar(_ mudanca: (inout Estado) -> Void) {
        mudanca(&estado)
        UserDefaults.standard.set(try? JSONEncoder().encode(estado), forKey: chave)
    }
}
