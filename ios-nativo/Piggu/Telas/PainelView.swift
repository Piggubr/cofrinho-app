import SwiftUI
import TipKit

/// Painel inicial (features/dashboard): os cartoes na ordem e com a visibilidade escolhidas
/// em "Personalizar o Painel", o guia de primeiros passos e a cotacao da moeda.
///
/// No iOS 27 os cartoes se arrastam no proprio Painel; antes, pela tela de personalizar.
struct PainelView: View {
    let versaoDosDados: Int
    let aoLancar: () -> Void
    let irPara: (Aba) -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var visao: Visao = .tudo
    @State private var modelo: ModeloDoPainel?
    @State private var painel: PainelOrganizado?
    @State private var organizando = false

    var body: some View {
        NavigationStack {
            Group {
                if let modelo, let painel {
                    conteudo(modelo, painel)
                } else {
                    Color.clear
                }
            }
            .background(paleta.fundo)
            .comSeletorDeMes($mes)
            .navigationTitle(saudacao)
            .topoDoPiggu {
                if let cotacao = modelo?.cotacao {
                    Text(Formatos.cotacao(cotacao))
                        .font(.sora(.caption, peso: .medium))
                        .monospacedDigit()
                        .fixedSize()
                        .accessibilityHint(cotacao.desatualizada == true
                                           ? "Última cotação conhecida" : "Referência de bancos centrais")
                }
            }
            .comTelasDoMais()
            .sheet(isPresented: $organizando) {
                if let painel { OrganizarPainelView(painel: painel) }
            }
            .task(id: "\(Formatos.mes(mes))#\(visao)#\(versaoDosDados)#\(rede.conectado)") {
                if modelo == nil {
                    modelo = ModeloDoPainel(sessao: sessao)
                    painel = PainelOrganizado(usuario: sessao.usuario?.id ?? "anonimo")
                }
                await modelo?.carregar(mes: Formatos.mes(mes), visao: visao)
            }
        }
    }

    private var saudacao: String {
        sessao.usuario.map { String(localized: "Olá, \($0.comoChamar)") } ?? String(localized: "Painel")
    }

    private func conteudo(_ modelo: ModeloDoPainel, _ painel: PainelOrganizado) -> some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                switch modelo.carga {
                case .carregando:
                    Cartao(nil) { EsqueletoDeLista(linhas: 4) }
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await modelo.carregar(mes: Formatos.mes(mes), visao: visao) } }
                case let .pronto(nucleo, guardadoEm):
                    if let guardadoEm {
                        AvisoDeGuardado(guardadoEm: guardadoEm)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(paleta.alertaSuave, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    TipView(Dicas.BoasVindas())
                    PrimeirosPassos(modelo: modelo, temLimite: modelo.temLimite(nucleo),
                                    temGasto: modelo.temGasto(nucleo), aoLancar: aoLancar)
                    if modelo.temCompanhia {
                        Picker("O que ver", selection: $visao) {
                            ForEach(Visao.allCases, id: \.self) { Text($0.nome).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                    blocos(modelo, painel, nucleo)
                    Button("Personalizar o Painel") { organizando = true }
                        .font(.sora(.subheadline, peso: .medium))
                        .padding(.vertical, 8)
                }
            }
            .modifier(ReordenarCartoes(painel: painel))
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .refreshable { await modelo.carregar(mes: Formatos.mes(mes), visao: visao) }
    }

    @ViewBuilder
    private func blocos(_ modelo: ModeloDoPainel, _ painel: PainelOrganizado, _ nucleo: ModeloDoPainel.Nucleo) -> some View {
        let visiveis = painel.visiveis
        // O dos bancos vai junto do "mes", como no site (mesma posicao); sem ele, junto do primeiro.
        let bancosJuntoDe = visiveis.contains { $0.id == .mes } ? IdDoBloco.mes : visiveis.first?.id
        let cartoes = ForEach(visiveis) { bloco in
            VStack(spacing: 14) {
                cartao(bloco.id, modelo, nucleo)
                if modelo.openFinance.habilitado && bloco.id == bancosJuntoDe {
                    CartaoDosBancos(openFinance: modelo.openFinance, ehPremium: modelo.ehPremium)
                }
            }
        }
        if #available(iOS 27, *) {
            cartoes.reorderable()
        } else {
            cartoes
        }
    }

    @ViewBuilder
    private func cartao(_ id: IdDoBloco, _ modelo: ModeloDoPainel, _ nucleo: ModeloDoPainel.Nucleo) -> some View {
        let chaveDoMes = Formatos.mes(mes)
        switch id {
        case .resumo:
            if let resumo = modelo.resumo {
                CartaoDoResumo(resumo: resumo) { irPara(.receitas) }
            }
        case .avisos:
            CartaoDeAvisos(modelo: modelo, irPara: irPara)
        case .mes:
            CartaoDoMes(mes: mes, gasto: modelo.gastoNoMes(nucleo), limite: nucleo.metas[chaveDoMes] ?? 0, irPara: irPara)
        case .cofrinho:
            CartaoDoCofrinho(modelo: modelo, cofrinho: nucleo.cofrinho, mes: chaveDoMes, visao: visao)
        case .objetivos:
            CartaoDosObjetivos(modelo: modelo, mes: chaveDoMes, visao: visao)
        case .metas:
            CartaoDasMetas(modelo: modelo)
        case .categorias:
            CartaoDasCategorias(totais: modelo.porCategoria(nucleo), aoLancar: aoLancar)
        case .notas:
            CartaoDasNotas(modelo: modelo)
        case .girlsMath:
            CartaoDoGirlsMath(modelo: modelo)
        }
    }
}

/// No iOS 27, soltar um cartao arrastado grava a ordem nova, como o arrastar do site.
private struct ReordenarCartoes: ViewModifier {
    let painel: PainelOrganizado

    func body(content: Content) -> some View {
        if #available(iOS 27, *) {
            content.reorderContainer(for: BlocoDoPainel.self) { diferenca in
                let antesDe: IdDoBloco? = switch diferenca.destination.position {
                case let .before(id): id
                case .end: nil
                }
                withAnimation { painel.mover(diferenca.sources, antesDe: antesDe) }
            }
        } else {
            content
        }
    }
}
