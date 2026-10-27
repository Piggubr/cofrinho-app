import SwiftUI
import TipKit

/// O tour de boas-vindas do site (core/ui/tutorial.ts) em TipKit: as mesmas dicas, cada uma
/// perto do que ela explica, e cada uma some quando a pessoa fecha. A do assistente fica de fora:
/// o assistente ainda nao existe no app.
enum Dicas {
    static func configurar(demo: Bool) {
        // Na demonstracao (e nos prints) as dicas so aparecem com -PigguDicas YES.
        if demo && !UserDefaults.standard.bool(forKey: "PigguDicas") {
            Tips.hideAllTipsForTesting()
        }
        try? Tips.configure([.displayFrequency(.immediate)])
    }

    struct BoasVindas: Tip {
        var title: Text { Text("Bem-vindo ao Piggu") }
        var message: Text? {
            Text("O resumo do mês: quanto entrou, quanto saiu, quanto ainda dá para gastar e as contas que vencem.")
        }
        var image: Image? { Image(systemName: "house") }
    }

    struct Lancar: Tip {
        var title: Text { Text("Lance pelo +") }
        var message: Text? {
            Text("Gasto de qualquer tela, sem sair de onde está. Dá para ler a nota pela câmera e o Piggu preenche para você.")
        }
        var image: Image? { Image(systemName: "plus.circle") }
    }

    struct Planejar: Tip {
        var title: Text { Text("Planejar") }
        var message: Text? {
            Text("Defina quanto quer gastar por categoria, cadastre as contas do mês e acompanhe seus objetivos.")
        }
        var image: Image? { Image(systemName: "target") }
    }

    struct Relatorios: Tip {
        var title: Text { Text("Relatórios") }
        var message: Text? { Text("Veja para onde o dinheiro foi, mês a mês e por categoria.") }
        var image: Image? { Image(systemName: "chart.bar") }
    }

    struct Familia: Tip {
        var title: Text { Text("Família") }
        var message: Text? {
            Text("Convide quem mora com você. Cada um lança do seu celular e vê o que é seu; titular e parceiro veem também o um do outro e o de quem tem menos de 18 anos.")
        }
        var image: Image? { Image(systemName: "person.2") }
    }

    struct Busca: Tip {
        var title: Text { Text("Busca") }
        var message: Text? { Text("Encontre qualquer gasto pela busca, neste mês e nos outros.") }
        var image: Image? { Image(systemName: "magnifyingglass") }
    }
}
