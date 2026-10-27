import SwiftUI

/// Lancamento rapido de gasto, o mesmo formulario do + e da tela de Gastos do site
/// (formulario-de-gasto.ts): valor, categoria e descricao; como pagou, parcelado ou
/// recorrente no cartao; e em "Mais opcoes" a data, a moeda da compra e a divisao.
///
/// Sem internet o gasto fica na fila deste aparelho e sobe quando a rede voltar.
struct LancarGastoView: View {
    /// Vem preenchido pelo link ou atalho (piggu://gasto?valor=12.50).
    var valorInicial: Decimal?
    /// Lancado (ou guardado sem rede), com a mensagem para o aviso.
    let aoSalvar: (String) -> Void

    enum Forma: Hashable { case cartao, debito }
    enum Modo: Hashable { case vista, parcelado, recorrente }
    enum Campo: Hashable { case valor, item }

    static let chaveDaContaPadrao = "piggu_conta_padrao"

    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @Environment(\.dismiss) private var fechar

    @State private var valorDigitado = ""
    @State private var categoria = ""
    @State private var categorias: [String] = []
    @State private var item = ""
    @State private var contas: [ContaOuCartao] = []
    @State private var forma = Forma.debito
    @State private var contaId = ""
    @State private var modo = Modo.vista
    @State private var parcelasDigitadas = "2"
    @State private var data = Date()
    @State private var moedaDaCompra = Formatos.codigoDaMoeda
    @State private var cotacao: Decimal?
    @State private var membros: [MembroDaFamilia] = []
    @State private var dividirCom: [String] = []
    /// Escolha da pessoa; nil segue o cartao (o do casal compartilha) e o padrao do perfil.
    @State private var compartilhado: Bool?
    @State private var precoAcima: ConferenciaDePreco?
    @State private var maisOpcoes = false
    @State private var salvando = false
    @State private var erro: String?
    @State private var faltou: String?
    @FocusState private var focado: Campo?

    private var valor: Decimal? { Formatos.lerValor(valorDigitado) }
    private var cartoes: [ContaOuCartao] { contas.filter { $0.tipo == .cartao } }
    private var bancos: [ContaOuCartao] { contas.filter { $0.tipo == .conta } }
    private var outraMoeda: Bool { moedaDaCompra != Formatos.codigoDaMoeda }
    private var parcelas: Int? { Int(parcelasDigitadas) }

    private var convertido: Decimal? {
        guard let cotacao, let valor else { return nil }
        var resultado = valor * cotacao
        var arredondado = Decimal()
        NSDecimalRound(&arredondado, &resultado, 2, .plain)
        return arredondado
    }

    private var compartilhadoEfetivo: Bool {
        compartilhado ?? (contas.first { $0.id == contaId }?.compartilhado == true
            || sessao.usuario?.compartilhamento?.compartilharPorPadrao == true)
    }

    /// Fatura em que a compra cai, pela data e pelo fechamento do cartao.
    private var fatura: (mes: String, vencimento: String)? {
        guard forma == .cartao, let cartao = cartoes.first(where: { $0.id == contaId }),
              let fechamento = cartao.fechamento, let vencimento = cartao.vencimento else { return nil }
        return FaturaDoCartao.daCompra(fechamento: fechamento, vencimento: vencimento, compra: Formatos.dia(data))
    }

    private var valorDaParcela: Decimal? {
        guard let valor, let parcelas, parcelas >= 2 else { return nil }
        var resultado = valor / Decimal(parcelas)
        var truncado = Decimal()
        NSDecimalRound(&truncado, &resultado, 2, .down)
        return truncado
    }

    /// O que esta escolhido em "Mais opcoes", para conferir sem abrir.
    private var resumoDasOpcoes: String {
        var partes = [Calendar.current.isDateInToday(data) ? String(localized: "Hoje") : Formatos.dataCurta(Formatos.dia(data))]
        if outraMoeda { partes.append(moedaDaCompra) }
        if !dividirCom.isEmpty { partes.append(String(localized: "dividido")) }
        if membros.count > 1 && compartilhadoEfetivo { partes.append(String(localized: "compartilhado")) }
        return partes.joined(separator: " · ")
    }

    var body: some View {
        NavigationStack {
            Form {
                principal
                comoPagou
                if forma == .cartao { comoFoiACompra }
                opcoes
                avisos
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Lançar gasto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { fechar() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if salvando {
                        ProgressView()
                    } else {
                        Button("Lançar") { Task { await lancar() } }
                    }
                }
            }
            .comTelasDoMais()
            .task { await carregar() }
            .onAppear {
                if let valorInicial, valorDigitado.isEmpty {
                    valorDigitado = Formatos.valorParaDigitar(valorInicial)
                }
                focado = .valor
            }
            .onChange(of: focado) { anterior, _ in
                if anterior == .valor {
                    if let valor { valorDigitado = Formatos.valorParaDigitar(valor) }
                    Task { await conferirPreco() }
                }
            }
            .onChange(of: moedaDaCompra) { _, _ in Task { await buscarCotacao() } }
        }
    }

    // MARK: - Secoes

    private var principal: some View {
        Section {
            LabeledContent("Valor (\(Formatos.simboloDaMoeda))") {
                TextField("Valor", text: $valorDigitado, prompt: Text(verbatim: "0,00"))
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.sora(.title3, peso: .semibold).monospacedDigit())
                    .focused($focado, equals: .valor)
                    .accessibilityLabel("Valor")
            }
            CampoDeCategoria(categoria: $categoria, categorias: $categorias)
            LabeledContent("Descrição") {
                TextField("Descrição", text: $item, prompt: Text("Ex.: Leite"))
                    .multilineTextAlignment(.trailing)
                    .focused($focado, equals: .item)
                    .textInputAutocapitalization(.sentences)
                    .onChange(of: item) { _, novo in if novo.count > 200 { item = String(novo.prefix(200)) } }
            }
        } footer: {
            if !valorDigitado.isEmpty, valor == nil {
                Text("Valor inválido. Use, por exemplo, 12,50.").foregroundStyle(paleta.perigo)
            } else if let precoAcima {
                Label("\(Formatos.porcentagem(precoAcima.percentual)) acima da média deste item (\(Formatos.moeda(precoAcima.media)))",
                      systemImage: "exclamationmark.triangle")
                    .foregroundStyle(paleta.alerta)
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private var comoPagou: some View {
        Section("Como pagou") {
            Picker("Como pagou", selection: Binding(get: { forma }, set: { trocarForma($0) })) {
                Text("Cartão de crédito").tag(Forma.cartao)
                Text("Débito/Pix").tag(Forma.debito)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            if forma == .cartao {
                if cartoes.isEmpty {
                    Text("Nenhum cartão cadastrado ainda.").foregroundStyle(paleta.textoSuave)
                    NavigationLink("Cadastrar cartão", value: TelaDoMais.cartoes)
                } else {
                    Picker("Cartão", selection: $contaId) {
                        Text("Escolha o cartão").tag("")
                        ForEach(cartoes) { Text($0.nome).tag($0.id) }
                    }
                    .listRowBackground(faltou == "conta" ? paleta.perigoSuave : paleta.superficie)
                    if let fatura {
                        Text("Vai para a fatura de \(Formatos.nomeDoMes(chave: fatura.mes)), que vence em \(Formatos.dataCurta(fatura.vencimento)).")
                            .font(.sora(.footnote))
                            .foregroundStyle(paleta.textoSuave)
                    }
                }
            } else if !bancos.isEmpty {
                Picker("Saiu de qual conta", selection: $contaId) {
                    Text("Escolha a conta").tag("")
                    ForEach(bancos) { Text($0.nome).tag($0.id) }
                }
                .listRowBackground(faltou == "conta" ? paleta.perigoSuave : paleta.superficie)
            }
        }
        .listRowBackground(paleta.superficie)
    }

    /// Debito/Pix e sempre a vista: o jeito da compra so aparece no cartao.
    private var comoFoiACompra: some View {
        Section("Como foi a compra") {
            Picker("Como foi a compra", selection: Binding(get: { modo }, set: { trocarModo($0) })) {
                Text("À vista").tag(Modo.vista)
                Text("Parcelado").tag(Modo.parcelado)
                Text("Recorrente").tag(Modo.recorrente)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            if modo == .parcelado {
                LabeledContent("Quantas parcelas") {
                    TextField("Parcelas", text: $parcelasDigitadas)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .accessibilityLabel("Quantas parcelas")
                }
                .listRowBackground(faltou == "parcelas" ? paleta.perigoSuave : paleta.superficie)
                if let valorDaParcela, let parcelas {
                    Text("\(parcelas)x de \(Formatos.moeda(valorDaParcela)), uma em cada mês")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
            } else if modo == .recorrente {
                Text("Lança sozinho todo dia \(Calendar.current.component(.day, from: data)), até você desligar em Contas do mês.")
                    .font(.sora(.footnote))
                    .foregroundStyle(paleta.textoSuave)
            }
        }
        .listRowBackground(paleta.superficie)
    }

    private var opcoes: some View {
        Section {
            DisclosureGroup(isExpanded: $maisOpcoes) {
                DatePicker("Data", selection: $data, displayedComponents: .date)
                Picker("Moeda da compra", selection: $moedaDaCompra) {
                    ForEach(moedasDaCompra, id: \.self) { Text($0).tag($0) }
                }
                if outraMoeda, let convertido {
                    Text("≈ \(Formatos.moeda(convertido)) (cotação de hoje)")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
                if membros.count > 1 {
                    ForEach(membros) { membro in
                        Toggle(isOn: Binding(
                            get: { dividirCom.contains(membro.id) },
                            set: { marcado in
                                dividirCom.removeAll { $0 == membro.id }
                                if marcado { dividirCom.append(membro.id) }
                            })) {
                            Text("Dividir com \(membro.nome)")
                        }
                    }
                    Toggle("Compartilhado: aparece para quem mora com você",
                           isOn: Binding(get: { compartilhadoEfetivo }, set: { compartilhado = $0 }))
                }
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mais opções")
                    Text(resumoDasOpcoes).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
            }
        }
        .listRowBackground(paleta.superficie)
    }

    @ViewBuilder
    private var avisos: some View {
        if !rede.conectado {
            Section {
                Label("Você está sem internet. O gasto fica guardado neste aparelho e vai assim que a conexão voltar.",
                      systemImage: "wifi.slash")
                    .foregroundStyle(paleta.alerta)
            }
            .listRowBackground(paleta.alertaSuave)
        }
        if let erro {
            Section {
                Text(erro).foregroundStyle(paleta.perigo)
            }
            .listRowBackground(paleta.perigoSuave)
        }
    }

    private var moedasDaCompra: [String] {
        var vistas = Set<String>()
        return ([Formatos.codigoDaMoeda] + ["BRL", "USD", "EUR", "GBP", "ARS", "CLP", "UYU", "JPY"])
            .filter { vistas.insert($0).inserted }
    }

    // MARK: - Escolhas

    /// Troca cartao por debito/Pix; com uma conta so daquele tipo, ela ja vem escolhida.
    private func trocarForma(_ nova: Forma, preferida: String? = nil) {
        let opcoes = nova == .cartao ? cartoes : bancos
        let escolhida = opcoes.first { $0.id == (preferida ?? contaId) }
        forma = nova
        contaId = escolhida?.id ?? (opcoes.count == 1 ? opcoes[0].id : "")
        if nova == .debito { trocarModo(.vista) }
        faltou = nil
    }

    /// Parcelado comeca em 2x.
    private func trocarModo(_ novo: Modo) {
        modo = novo
        parcelasDigitadas = novo == .parcelado ? "2" : "1"
        faltou = nil
    }

    // MARK: - API

    private func carregar() async {
        let cliente = sessao.cliente
        async let categorias = try? cliente.listarCategorias()
        async let contas = try? cliente.contas()
        async let membros = try? cliente.verFamilia().membros
        self.categorias = await categorias ?? []
        self.contas = await contas ?? []
        self.membros = await membros ?? []
        let padrao = self.contas.first { $0.id == UserDefaults.standard.string(forKey: Self.chaveDaContaPadrao) }
        trocarForma(padrao?.tipo == .cartao ? .cartao : .debito, preferida: padrao?.id)
    }

    private func buscarCotacao() async {
        cotacao = nil
        guard outraMoeda else { return }
        do {
            cotacao = try await sessao.cliente.consultarCotacao(de: moedaDaCompra, para: Formatos.codigoDaMoeda).taxa
        } catch {
            erro = error.localizedDescription
        }
    }

    /// Ao sair do valor: avisa se o preco passou da media do item (so na moeda da pessoa).
    private func conferirPreco() async {
        let item = item.trimmingCharacters(in: .whitespaces)
        guard !item.isEmpty, let valor, valor > 0, !outraMoeda, rede.conectado else {
            precoAcima = nil
            return
        }
        let conferencia = await sessao.cliente.conferirPreco(item: item, valor: valor)
        precoAcima = conferencia?.acima == true ? conferencia : nil
    }

    private func lancar() async {
        let item = item.trimmingCharacters(in: .whitespaces)
        faltou = nil
        guard !item.isEmpty else {
            erro = String(localized: "Digite o nome do item.")
            return
        }
        guard let valor, valor >= 0, valor <= Rota.valorMaximo else {
            erro = String(localized: "Digite um valor válido.")
            return
        }
        if outraMoeda && convertido == nil {
            erro = String(localized: "Aguarde a cotação da moeda da compra.")
            return
        }
        if forma == .cartao && cartoes.isEmpty {
            erro = String(localized: "Cadastre o cartão em Cartões e bancos, ou escolha Débito/Pix.")
            return
        }
        if contaId.isEmpty && (forma == .cartao || !bancos.isEmpty) {
            faltou = "conta"
            erro = forma == .cartao
                ? String(localized: "Escolha em qual cartão foi a compra.")
                : String(localized: "Escolha de qual conta saiu o dinheiro.")
            return
        }
        let quantas = modo == .parcelado ? (parcelas ?? 0) : 1
        guard (1...48).contains(quantas), modo != .parcelado || quantas >= 2 else {
            faltou = "parcelas"
            erro = String(localized: "Parcelado: de 2 a 48 parcelas.")
            return
        }

        salvando = true
        erro = nil
        defer { salvando = false }
        let pedido = NovoLancamento(
            data: Formatos.dia(data),
            origem: "Manual",
            itens: [ItemDeGasto(
                item: item,
                categoria: categoria.isEmpty ? nil : categoria,
                valor: outraMoeda ? convertido! : valor,
                // Sem o campo Tipo na tela: o recorrente vira conta fixa, o resto e variavel.
                tipo: modo == .recorrente ? "Fixo" : "Variavel",
                moedaOriginal: outraMoeda ? moedaDaCompra : nil,
                valorOriginal: outraMoeda ? valor : nil)],
            contaId: contaId.isEmpty ? nil : contaId,
            parcelas: quantas > 1 ? quantas : nil,
            dividirCom: dividirCom.isEmpty ? nil : dividirCom,
            recorrente: modo == .recorrente ? true : nil,
            compartilhado: compartilhadoEfetivo,
            idDoEnvio: UUID().uuidString.lowercased())
        do {
            let salvos = try await sessao.cliente.lancarGastos(pedido)
            lembrarConta()
            aoSalvar(await mensagemDoOrcamento(salvos.first?.categoria, mes: String(pedido.data.prefix(7))))
            fechar()
        } catch ErroDaAPI.semConexao {
            // Na fila do mercado, sem sinal: guarda e manda quando a conexao voltar.
            sessao.fila.guardar(pedido)
            lembrarConta()
            aoSalvar(String(localized: "Sem internet: o gasto ficou guardado neste aparelho e vai assim que a conexão voltar."))
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }

    /// A ultima conta usada vira a padrao do proximo lancamento, neste aparelho.
    private func lembrarConta() {
        UserDefaults.standard.set(contaId.isEmpty ? nil : contaId, forKey: Self.chaveDaContaPadrao)
    }

    /// Depois de lancar: se a categoria passou de 80% do orcamento do mes, a mensagem avisa.
    private func mensagemDoOrcamento(_ categoria: String?, mes: String) async -> String {
        guard let categoria,
              let orcamento = (try? await sessao.cliente.orcamentos(mes))?
                .first(where: { $0.categoria == categoria && $0.alerta != .ok }) else {
            return String(localized: "Gasto lançado.")
        }
        let percentual = Formatos.porcentagem(orcamento.percentual)
        return orcamento.alerta == .estourou
            ? String(localized: "Gasto lançado. \(categoria) passou do limite do mês (\(percentual)).")
            : String(localized: "Gasto lançado. \(categoria) já usou \(percentual) do limite do mês.")
    }
}
