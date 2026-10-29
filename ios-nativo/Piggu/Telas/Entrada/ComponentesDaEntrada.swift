import SwiftUI

/// O cartao das telas de entrada do site (.tela-de-login): o porquinho, o titulo, a frase e o
/// formulario, centrado e com largura de leitura. Sem internet, avisa no topo.
struct TelaDeEntrada<Conteudo: View>: View {
    let titulo: LocalizedStringKey
    var subtitulo: LocalizedStringKey?
    @ViewBuilder let conteudo: Conteudo

    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                VStack(spacing: 10) {
                    Image("Logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 72, height: 72)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .accessibilityHidden(true)
                    Text(titulo)
                        .font(.sora(.title, peso: .bold))
                        .foregroundStyle(paleta.primaria)
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                    if let subtitulo {
                        Text(subtitulo)
                            .foregroundStyle(paleta.textoSuave)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(.top, 24)

                if !rede.conectado {
                    AvisoDaEntrada(texto: String(localized: "Você está sem internet. Confira o Wi-Fi ou os dados móveis."),
                                   tipo: .alerta)
                }

                conteudo
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(paleta.fundo)
    }
}

/// Faixa de erro, sucesso ou aviso (.aviso do site), lida pelo VoiceOver quando aparece.
struct AvisoDaEntrada: View {
    enum Tipo {
        case erro, sucesso, alerta
    }

    let texto: String
    var tipo = Tipo.erro

    @Environment(\.paleta) private var paleta

    private var cores: (frente: Color, fundo: Color) {
        switch tipo {
        case .erro: (paleta.perigo, paleta.perigoSuave)
        case .sucesso: (paleta.sucesso, paleta.sucessoSuave)
        case .alerta: (paleta.alerta, paleta.alertaSuave)
        }
    }

    var body: some View {
        Text(texto)
            .font(.sora(.callout))
            .foregroundStyle(cores.frente)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(12)
            .background(cores.fundo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .onAppear {
                AccessibilityNotification.Announcement(texto).post()
            }
    }
}

/// Botao largo do formulario, com o carregando no lugar do texto.
struct BotaoDeEnviar: View {
    let titulo: LocalizedStringKey
    var carregando = false
    let acao: () async -> Void

    @Environment(\.paleta) private var paleta

    var body: some View {
        Button {
            Task { await acao() }
        } label: {
            Group {
                if carregando {
                    ProgressView().tint(paleta.primariaContraste)
                } else {
                    Text(titulo)
                }
            }
            .font(.sora(.headline, peso: .semibold))
            .frame(maxWidth: .infinity, minHeight: 28)
        }
        .botaoPrincipal()
        .controlSize(.large)
        .disabled(carregando)
    }
}

/// Rotulo em cima do campo, com a dica embaixo, como o .campo do site.
struct CampoDaEntrada<Campo: View>: View {
    let rotulo: LocalizedStringKey
    var dica: LocalizedStringKey?
    var comProblema = false
    @ViewBuilder let campo: Campo

    @Environment(\.paleta) private var paleta

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(rotulo)
                .font(.sora(.subheadline, peso: .medium))
            campo
                .padding(14)
                .background(paleta.campo, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(comProblema ? paleta.perigo : paleta.borda, lineWidth: comProblema ? 2 : 1))
            if let dica {
                Text(dica)
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Data de nascimento: o botao mostra a escolhida e abre a roda de datas.
struct CampoDeNascimento: View {
    @Binding var data: Date?
    var comProblema = false

    @State private var escolhendo = false
    @State private var rascunho = Calendar.current.date(from: DateComponents(year: 2000, month: 1, day: 1)) ?? .now
    @Environment(\.paleta) private var paleta

    var body: some View {
        CampoDaEntrada(rotulo: "Data de nascimento", comProblema: comProblema) {
            Button {
                rascunho = data ?? rascunho
                escolhendo = true
            } label: {
                HStack {
                    if let data {
                        Text(data.formatted(.dateTime.day().month(.wide).year().locale(Formatos.localidade)))
                            .foregroundStyle(paleta.texto)
                    } else {
                        Text("Escolher").foregroundStyle(paleta.textoSuave)
                    }
                    Spacer()
                    Image(systemName: "calendar").foregroundStyle(paleta.primaria)
                }
            }
            .accessibilityLabel("Data de nascimento")
            .accessibilityValue(data.map { $0.formatted(date: .long, time: .omitted) } ?? String(localized: "Não escolhida"))
        }
        .sheet(isPresented: $escolhendo) {
            NavigationStack {
                DatePicker("Data de nascimento", selection: $rascunho, in: ...Date.now, displayedComponents: .date)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .environment(\.locale, Formatos.localidade)
                    .padding()
                    .navigationTitle("Data de nascimento")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancelar") { escolhendo = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Pronto") {
                                data = rascunho
                                escolhendo = false
                            }
                        }
                    }
            }
            .presentationDetents([.medium])
        }
    }
}

/// "Li e aceito os Termos de Uso e o Aviso de Privacidade.", com os dois links.
struct AceiteDosTermos: View {
    @Binding var aceitou: Bool
    var comProblema = false

    @Environment(\.paleta) private var paleta

    var body: some View {
        Toggle(isOn: $aceitou) {
            Text("Li e aceito os [Termos de Uso](piggu://termos) e o [Aviso de Privacidade](piggu://privacidade).")
                .font(.sora(.callout))
                .foregroundStyle(comProblema ? paleta.perigo : paleta.texto)
        }
        .toggleStyle(CaixaDeMarcar())
    }
}

/// Caixa de marcar do site: o quadrado a esquerda e o texto (com links) ao lado.
struct CaixaDeMarcar: ToggleStyle {
    @Environment(\.paleta) private var paleta

    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Button {
                configuration.isOn.toggle()
            } label: {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(configuration.isOn ? paleta.primaria : paleta.textoSuave)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Aceito os Termos de Uso e o Aviso de Privacidade"))
            .accessibilityValue(configuration.isOn ? Text("Marcado") : Text("Desmarcado"))
            configuration.label
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "Termos de Uso · Aviso de Privacidade", no pe das telas de entrada.
struct LinksLegais: View {
    @Environment(\.paleta) private var paleta

    var body: some View {
        Text("[Termos de Uso](piggu://termos) · [Aviso de Privacidade](piggu://privacidade)")
            .font(.sora(.footnote))
            .foregroundStyle(paleta.textoSuave)
    }
}

/// Botao de contorno, pequeno (.botao.contorno.pequeno do site).
struct BotaoDeContorno: View {
    let titulo: LocalizedStringKey
    var papel: ButtonRole?
    let acao: () -> Void

    var body: some View {
        Button(titulo, role: papel, action: acao)
            .buttonStyle(.bordered)
            .font(.sora(.subheadline, peso: .medium))
    }
}
