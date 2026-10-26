import SwiftUI

/// O que uma tela tem para mostrar.
enum Carga<Valor: Sendable>: Sendable {
    case carregando
    /// `guardadoEm` vem preenchido quando a atualizacao falhou e o valor e o do cache.
    case pronto(Valor, guardadoEm: Date? = nil)
    case falhou(ErroDaAPI)

    var valor: Valor? {
        if case let .pronto(valor, _) = self { valor } else { nil }
    }
}

/// Carregando: o formato do que vem, em blocos apagados, para a tela nao pular quando os
/// dados chegam (o app-carregando do site).
struct EsqueletoDeLista: View {
    var linhas = 6

    var body: some View {
        ForEach(0..<linhas, id: \.self) { indice in
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    // So o formato: o texto fica apagado e nunca e lido.
                    Text(verbatim: indice.isMultiple(of: 2) ? "Mercado da semana" : "Almoço")
                    Text(verbatim: "Categoria · Lugar").font(.sora(.footnote))
                }
                Spacer()
                Text(verbatim: "R$ 000,00")
            }
        }
        .redacted(reason: .placeholder)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Carregando…")
    }
}

/// Lista vazia: icone, titulo curto e o que fazer para sair dele (o app-vazio do site).
struct EstadoVazio: View {
    let icone: String
    let titulo: LocalizedStringKey
    let texto: LocalizedStringKey
    var acao: (titulo: LocalizedStringKey, executar: () -> Void)?

    @Environment(\.paleta) private var paleta

    var body: some View {
        ContentUnavailableView {
            Label {
                Text(titulo).font(.sora(.headline, peso: .semibold))
            } icon: {
                Image(systemName: icone)
                    .foregroundStyle(paleta.primaria)
                    .padding(14)
                    .background(paleta.suave, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
        } description: {
            Text(texto).font(.sora(.subheadline))
        } actions: {
            if let acao {
                Button(acao.titulo, action: acao.executar)
                    .botaoPrincipal()
            }
        }
    }
}

/// Erro ao carregar uma tela, com os textos da tela de erro do site
/// (frontend/src/app/features/erros) e do mensagemDeErro.
struct EstadoDeErro: View {
    let erro: ErroDaAPI
    let tentarDeNovo: () -> Void

    @Environment(MonitorDeRede.self) private var rede

    private var conteudo: (icone: String, titulo: LocalizedStringKey, texto: Text) {
        if erro == .semConexao || !rede.conectado {
            return ("wifi.slash", "Você está sem internet",
                    Text("Não deu para abrir esta tela agora. Confira o Wi-Fi ou os dados móveis e tente de novo."))
        }
        switch erro {
        case .respostaInvalida, .servidor(status: 500..., mensagem: _):
            return ("exclamationmark.triangle", "O Piggu tropeçou",
                    Text("Algo deu errado do nosso lado e esta tela não abriu. Nada do que você já salvou se perdeu; tente de novo em instantes."))
        default:
            return ("exclamationmark.circle", "Não deu para carregar", Text(erro.localizedDescription))
        }
    }

    var body: some View {
        ContentUnavailableView {
            Label {
                Text(conteudo.titulo).font(.sora(.headline, peso: .semibold))
            } icon: {
                Image(systemName: conteudo.icone)
            }
        } description: {
            conteudo.texto.font(.sora(.subheadline))
        } actions: {
            Button("Tentar de novo", action: tentarDeNovo)
                .botaoPrincipal()
        }
    }
}

/// Faixa no topo da lista quando o que aparece e o guardado no aparelho.
struct AvisoDeGuardado: View {
    let guardadoEm: Date

    @Environment(\.paleta) private var paleta
    @Environment(MonitorDeRede.self) private var rede

    private var quando: String {
        guardadoEm.formatted(.dateTime.day().month(.abbreviated).hour().minute().locale(Formatos.localidade))
    }

    var body: some View {
        Label {
            Text(rede.conectado
                 ? "Não deu para atualizar. Mostrando o que estava guardado de \(quando)."
                 : "Você está sem internet. Mostrando o que estava guardado de \(quando).")
        } icon: {
            Image(systemName: rede.conectado ? "exclamationmark.icloud" : "icloud.slash")
        }
        .font(.sora(.footnote))
        .foregroundStyle(paleta.alerta)
        .listRowBackground(paleta.alertaSuave)
    }
}
