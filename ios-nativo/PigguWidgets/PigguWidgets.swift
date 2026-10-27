import AppIntents
import SwiftUI
import WidgetKit

/// Os widgets do Piggu: gasto do mes contra o limite (tela inicial e de bloqueio), o progresso de
/// uma meta e o "Lancar gasto" da Central de Controle. Os dados vem do resumo que o app grava no
/// App Group (ResumoDoWidget); o widget nunca chama a API nem guarda token.
@main
struct PigguWidgets: WidgetBundle {
    var body: some Widget {
        GastoDoMesWidget()
        MetaWidget()
        if #available(iOS 18, *) {
            LancarGastoControle()
        }
    }
}

struct EntradaDoPiggu: TimelineEntry {
    let date: Date
    let resumo: ResumoDoWidget?
}

struct FornecedorDoPiggu: TimelineProvider {
    func placeholder(in _: Context) -> EntradaDoPiggu {
        EntradaDoPiggu(date: .now, resumo: .exemplo)
    }

    func getSnapshot(in context: Context, completion: @escaping (EntradaDoPiggu) -> Void) {
        completion(EntradaDoPiggu(date: .now, resumo: context.isPreview ? .exemplo : ResumoDoWidget.ler() ?? .exemplo))
    }

    /// O app recarrega ao abrir e depois de lancar; a hora a mais so cobre a virada do mes.
    func getTimeline(in _: Context, completion: @escaping (Timeline<EntradaDoPiggu>) -> Void) {
        let entrada = EntradaDoPiggu(date: .now, resumo: ResumoDoWidget.ler())
        completion(Timeline(entries: [entrada], policy: .after(.now.addingTimeInterval(3600))))
    }
}

private extension ResumoDoWidget {
    var paleta: Paleta { (Tema(rawValue: tema) ?? .chiclete).paleta }
    /// O resumo e de outro mes (o app nao abriu desde a virada): melhor nao mostrar numero velho.
    var doMesAtual: Bool {
        let partes = Calendar.current.dateComponents([.year, .month], from: .now)
        return mes == String(format: "%04d-%02d", partes.year ?? 0, partes.month ?? 0)
    }
}

// MARK: - Gasto do mes

struct GastoDoMesWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "GastoDoMes", provider: FornecedorDoPiggu()) { entrada in
            VistaDoGasto(resumo: entrada.resumo.flatMap { $0.doMesAtual ? $0 : nil })
        }
        .configurationDisplayName("Gasto do mês")
        .description("Quanto você gastou no mês contra o limite do orçamento.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

private struct VistaDoGasto: View {
    let resumo: ResumoDoWidget?

    @Environment(\.widgetFamily) private var familia

    var body: some View {
        let paleta = resumo?.paleta ?? Tema.chiclete.paleta
        Group {
            if let resumo {
                switch familia {
                case .accessoryCircular: circular(resumo)
                case .accessoryRectangular: retangular(resumo)
                case .accessoryInline: Text("Gastei \(resumo.moeda(resumo.gasto)) este mês")
                case .systemMedium: medio(resumo, paleta)
                default: pequeno(resumo, paleta)
                }
            } else {
                Text(familia == .accessoryInline ? "Abra o Piggu" : "Abra o Piggu para ver o gasto do mês.")
                    .font(.footnote)
            }
        }
        .widgetURL(URL(string: "piggu://gastos"))
        .containerBackground(for: .widget) { paleta.superficie }
    }

    private func pequeno(_ resumo: ResumoDoWidget, _ paleta: Paleta) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Gasto do mês", systemImage: "wallet.pass").font(.caption.weight(.semibold))
                .foregroundStyle(paleta.primaria)
            Text(resumo.moeda(resumo.gasto)).font(.title3.weight(.bold)).monospacedDigit()
                .minimumScaleFactor(0.6).lineLimit(1)
            Spacer(minLength: 0)
            limite(resumo, paleta)
        }
    }

    private func medio(_ resumo: ResumoDoWidget, _ paleta: Paleta) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Label("Gasto do mês", systemImage: "wallet.pass").font(.caption.weight(.semibold))
                    .foregroundStyle(paleta.primaria)
                Text(resumo.moeda(resumo.gasto)).font(.title2.weight(.bold)).monospacedDigit()
                    .minimumScaleFactor(0.6).lineLimit(1)
                Spacer(minLength: 0)
                limite(resumo, paleta)
                Text("Atualizado \(resumo.atualizadoEm, style: .relative) atrás").font(.caption2)
                    .foregroundStyle(paleta.textoSuave)
            }
            if let fracao = resumo.fracaoDoLimite {
                Gauge(value: min(fracao, 1)) {
                    Text("Limite")
                } currentValueLabel: {
                    Text(fracao.formatted(.percent.precision(.fractionLength(0)))).font(.caption.weight(.bold))
                }
                .gaugeStyle(.accessoryCircularCapacity)
                .tint(fracao > 1 ? paleta.perigo : paleta.primaria)
                .scaleEffect(1.4)
                .frame(width: 80)
            }
        }
    }

    @ViewBuilder
    private func limite(_ resumo: ResumoDoWidget, _ paleta: Paleta) -> some View {
        if let fracao = resumo.fracaoDoLimite, let limite = resumo.limite, let restante = resumo.restante {
            VStack(alignment: .leading, spacing: 4) {
                ProgressView(value: min(fracao, 1)).tint(fracao > 1 ? paleta.perigo : paleta.primaria)
                Text(restante >= 0 ? "Resta \(resumo.moeda(restante)) de \(resumo.moeda(limite))"
                                   : "Passou \(resumo.moeda(-restante)) do limite")
                    .font(.caption2).foregroundStyle(fracao > 1 ? paleta.perigo : paleta.textoSuave)
                    .lineLimit(2).minimumScaleFactor(0.8)
            }
        } else {
            Text("Sem limite para este mês").font(.caption2).foregroundStyle(paleta.textoSuave)
        }
    }

    private func circular(_ resumo: ResumoDoWidget) -> some View {
        Gauge(value: min(resumo.fracaoDoLimite ?? 0, 1)) {
            Image(systemName: "wallet.pass")
        } currentValueLabel: {
            Text(resumo.fracaoDoLimite.map { $0.formatted(.percent.precision(.fractionLength(0))) } ?? "–")
        }
        .gaugeStyle(.accessoryCircular)
        .widgetAccentable()
    }

    private func retangular(_ resumo: ResumoDoWidget) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Gasto do mês").font(.caption2.weight(.semibold)).widgetAccentable()
            Text(resumo.moeda(resumo.gasto)).font(.headline).monospacedDigit()
            if let limite = resumo.limite {
                Text("de \(resumo.moeda(limite))").font(.caption2)
            }
        }
    }
}

// MARK: - Meta

struct MetaWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MetaDoMes", provider: FornecedorDoPiggu()) { entrada in
            VistaDaMeta(resumo: entrada.resumo)
        }
        .configurationDisplayName("Meta do mês")
        .description("O progresso da sua próxima meta do mês.")
        .supportedFamilies([.systemSmall, .accessoryCircular])
    }
}

private struct VistaDaMeta: View {
    let resumo: ResumoDoWidget?

    @Environment(\.widgetFamily) private var familia

    var body: some View {
        let paleta = resumo?.paleta ?? Tema.chiclete.paleta
        Group {
            if let meta = resumo?.meta, let resumo {
                let fracao = Double(meta.atual) / Double(max(meta.alvo, 1))
                if familia == .accessoryCircular {
                    Gauge(value: min(fracao, 1)) { Image(systemName: "flag") } currentValueLabel: {
                        Text("\(meta.atual)/\(meta.alvo)")
                    }
                    .gaugeStyle(.accessoryCircular)
                    .widgetAccentable()
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Meta do mês", systemImage: "flag").font(.caption.weight(.semibold))
                            .foregroundStyle(paleta.primaria)
                        Text(meta.titulo).font(.headline).lineLimit(2)
                        Spacer(minLength: 0)
                        Gauge(value: min(fracao, 1)) { EmptyView() }
                            .gaugeStyle(.accessoryLinearCapacity).tint(paleta.primaria)
                        Text("\(meta.atual) de \(meta.alvo) · \(resumo.metasConcluidas) de \(resumo.metasTotal) concluídas")
                            .font(.caption2).foregroundStyle(paleta.textoSuave)
                    }
                }
            } else {
                Text("Crie uma meta no Piggu para acompanhar aqui.").font(.footnote)
            }
        }
        .widgetURL(URL(string: "piggu://metas"))
        .containerBackground(for: .widget) { paleta.superficie }
    }
}

// MARK: - Central de Controle

@available(iOS 18, *)
struct LancarGastoControle: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "LancarGasto") {
            ControlWidgetButton(action: LancarGastoIntent()) {
                Label("Lançar gasto", systemImage: "plus.circle.fill")
            }
        }
        .displayName("Lançar gasto")
        .description("Abre o Piggu pronto para lançar um gasto.")
    }
}
