import SwiftUI

/// Gastos e Receitas dividem a aba Lançamentos (D-164): a troca fica dentro de cada tela,
/// abaixo do topo com o logo e acima do mês.
struct TrocaDeLancamentos: View {
    @Binding var aba: Aba

    var body: some View {
        Picker("Lançamentos", selection: $aba) {
            Text("Gastos").tag(Aba.gastos)
            Text("Receitas").tag(Aba.receitas)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }
}

extension View {
    /// Sem a troca (tela aberta fora da aba), nao muda nada.
    func comTrocaDeLancamentos(_ aba: Binding<Aba>?) -> some View {
        safeAreaInset(edge: .top) {
            if let aba { TrocaDeLancamentos(aba: aba) }
        }
    }
}
