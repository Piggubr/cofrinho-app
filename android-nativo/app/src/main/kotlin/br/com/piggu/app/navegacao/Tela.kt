package br.com.piggu.app.navegacao

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Uma rota tipada para cada rota de frontend/src/app/app.routes.ts, com o mesmo caminho.
 *
 * <p>[nativa] diz se a tela já existe no app. As que ainda não existem abrem a página do site
 * num Custom Tab (como o iOS fez na etapa 4, D-090 de lá); cada etapa troca o link pela tela.</p>
 */
@Serializable
sealed interface Tela : NavKey {
    val caminho: String
    val nativa: Boolean get() = false

    // Entrada, fora da moldura (A3): nativas.
    @Serializable data object Entrar : Tela { override val caminho = "/entrar"; override val nativa = true }
    @Serializable data object Privacidade : Tela { override val caminho = "/privacidade"; override val nativa = true }
    @Serializable data object Termos : Tela { override val caminho = "/termos"; override val nativa = true }
    @Serializable data class ConfirmarEmail(val token: String) : Tela {
        override val caminho get() = "/confirmar-email?token=$token"
        override val nativa get() = true
    }
    @Serializable data object EsqueciSenha : Tela { override val caminho = "/esqueci-senha"; override val nativa = true }
    @Serializable data class RedefinirSenha(val token: String) : Tela {
        override val caminho get() = "/redefinir-senha?token=$token"
        override val nativa get() = true
    }
    @Serializable data object Aceite : Tela { override val caminho = "/aceite"; override val nativa = true }
    @Serializable data object SobreVoce : Tela { override val caminho = "/sobre-voce"; override val nativa = true }
    @Serializable data object Cpf : Tela { override val caminho = "/cpf"; override val nativa = true }

    // Abas da barra (abas-nativas.ts e shell.ts): nativas, com o conteúdo chegando na A4.
    @Serializable data object Painel : Tela { override val caminho = "/painel"; override val nativa = true }
    @Serializable data object Gastos : Tela { override val caminho = "/gastos"; override val nativa = true }
    @Serializable data object Receitas : Tela { override val caminho = "/receitas"; override val nativa = true }
    @Serializable data object Relatorios : Tela { override val caminho = "/relatorios"; override val nativa = true }

    /** Aba do celular com o resto do menu; não existe no site, que abre uma folha. */
    @Serializable data object Mais : Tela { override val caminho = "/mais"; override val nativa = true }

    // Telas do menu (shell.ts): as da A5 já nativas, as outras no site por enquanto.
    @Serializable data object Perfil : Tela { override val caminho = "/perfil"; override val nativa = true }
    @Serializable data object Familia : Tela { override val caminho = "/familia"; override val nativa = true }
    @Serializable data object Plano : Tela { override val caminho = "/plano"; override val nativa = true }
    @Serializable data object ContasECartoes : Tela { override val caminho = "/contas-e-cartoes"; override val nativa = true }
    @Serializable data object ContasFixas : Tela { override val caminho = "/contas-fixas"; override val nativa = true }
    @Serializable data object Orcamentos : Tela { override val caminho = "/orcamentos"; override val nativa = true }
    @Serializable data object Calendario : Tela { override val caminho = "/calendario"; override val nativa = true }
    @Serializable data object Compras : Tela { override val caminho = "/compras" }
    @Serializable data object Lugares : Tela { override val caminho = "/lugares" }
    @Serializable data object Filmes : Tela { override val caminho = "/filmes" }
    @Serializable data object Feed : Tela { override val caminho = "/feed" }
    @Serializable data object Premios : Tela { override val caminho = "/premios"; override val nativa = true }
    @Serializable data object Planejar : Tela { override val caminho = "/planejar"; override val nativa = true }
    @Serializable data object Metas : Tela { override val caminho = "/metas"; override val nativa = true }
    @Serializable data object GirlsMath : Tela { override val caminho = "/girls-math"; override val nativa = true }

    // Erros (features/erros): /erro é a 500, e qualquer caminho desconhecido é a 404.
    @Serializable data object Erro : Tela { override val caminho = "/erro"; override val nativa = true }
    @Serializable data object NaoEncontrada : Tela { override val caminho = "/nao-encontrada"; override val nativa = true }

    companion object {
        /**
         * As abas, na ordem da barra do celular. Getter, e não campo: carregar uma das telas antes
         * inicializa a interface (métodos default do Kotlin 2), e o campo veria a tela ainda nula.
         */
        val abas: List<Tela> get() = listOf(Painel, Gastos, Receitas, Relatorios, Mais)

        /** Abrem sem sessão (as rotas do site sem o autenticadoGuard). */
        fun aberta(tela: Tela) = tela in setOf(Entrar, Privacidade, Termos, EsqueciSenha) ||
            tela is ConfirmarEmail || tela is RedefinirSenha
    }
}
