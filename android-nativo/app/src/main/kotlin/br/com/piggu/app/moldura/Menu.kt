package br.com.piggu.app.moldura

import androidx.annotation.StringRes
import br.com.piggu.app.R
import br.com.piggu.app.navegacao.Tela

/** Os grupos do menu do site (GRUPOS de shell.ts), na mesma ordem. */
enum class GrupoDoMenu(@StringRes val titulo: Int) {
    DiaADia(R.string.grupo_dia_a_dia),
    Planejar(R.string.grupo_planejar),
    Contas(R.string.grupo_contas),
    Estilo(R.string.grupo_estilo),
    Conta(R.string.grupo_conta),
}

/** Um item do menu, como o ItemDeMenu de shell.ts; [icone] é o nome de core/ui/icone.ts. */
data class ItemDoMenu(
    val tela: Tela,
    @StringRes val rotulo: Int,
    val icone: String,
    val grupo: GrupoDoMenu,
    /** Assinar e cancelar o Premium é só do titular; os outros não veem o item. */
    val somenteTitular: Boolean = false,
    /** Módulo de estilo de vida que liga ou desliga o item no Perfil (D-092 do iOS). */
    val modulo: String? = null,
)

/** Os itens de shell.ts, na mesma ordem. Todo papel vê o menu inteiro; só o Premium é do titular. */
val ITENS_DO_MENU: List<ItemDoMenu> = listOf(
    ItemDoMenu(Tela.Painel, R.string.tela_painel, "casa", GrupoDoMenu.DiaADia),
    ItemDoMenu(Tela.Gastos, R.string.tela_gastos, "carteira", GrupoDoMenu.DiaADia),
    ItemDoMenu(Tela.Receitas, R.string.tela_receitas, "entrada", GrupoDoMenu.DiaADia),
    ItemDoMenu(Tela.Relatorios, R.string.tela_relatorios, "grafico", GrupoDoMenu.DiaADia),
    ItemDoMenu(Tela.Planejar, R.string.tela_planejar, "alvo", GrupoDoMenu.Planejar),
    ItemDoMenu(Tela.Metas, R.string.tela_metas, "bandeira", GrupoDoMenu.Planejar),
    ItemDoMenu(Tela.Orcamentos, R.string.tela_orcamentos, "pizza", GrupoDoMenu.Planejar),
    ItemDoMenu(Tela.ContasFixas, R.string.tela_contas_fixas, "recibo", GrupoDoMenu.Planejar),
    ItemDoMenu(Tela.ContasECartoes, R.string.tela_contas_e_cartoes, "cartao", GrupoDoMenu.Contas),
    ItemDoMenu(Tela.Calendario, R.string.tela_calendario, "calendario", GrupoDoMenu.Contas),
    ItemDoMenu(Tela.Compras, R.string.tela_compras, "carrinho", GrupoDoMenu.Estilo, modulo = "compras"),
    ItemDoMenu(Tela.Lugares, R.string.tela_lugares, "local", GrupoDoMenu.Estilo, modulo = "lugares"),
    ItemDoMenu(Tela.Filmes, R.string.tela_filmes, "filme", GrupoDoMenu.Estilo, modulo = "filmes"),
    ItemDoMenu(Tela.Feed, R.string.tela_fotos, "imagem", GrupoDoMenu.Estilo, modulo = "fotos"),
    ItemDoMenu(Tela.Premios, R.string.tela_premios, "trofeu", GrupoDoMenu.Estilo, modulo = "premios"),
    ItemDoMenu(Tela.GirlsMath, R.string.tela_girls_math, "sacola", GrupoDoMenu.Estilo, modulo = "girls-math"),
    ItemDoMenu(Tela.Familia, R.string.tela_familia, "pessoas", GrupoDoMenu.Conta),
    ItemDoMenu(Tela.Plano, R.string.tela_premium, "estrela", GrupoDoMenu.Conta, somenteTitular = true),
)

/** Abas do celular (atalhos() de shell.ts), com o Mais no fim. */
val ITENS_DAS_ABAS: List<ItemDoMenu> = ITENS_DO_MENU.filter { it.grupo == GrupoDoMenu.DiaADia }

/**
 * Os grupos que a pessoa vê, sem grupo vazio (gruposVisiveis de shell.ts). [modulos] nulo (conta
 * antiga, demo) liga todos; com a lista, a tela de estilo de vida fora dela some (D-092 do iOS).
 */
fun gruposVisiveis(
    ehTitular: Boolean,
    modulos: Collection<String>?,
    semAsAbas: Boolean = false,
): List<Pair<GrupoDoMenu, List<ItemDoMenu>>> =
    ITENS_DO_MENU
        .filter { ehTitular || !it.somenteTitular }
        .filter { item -> item.modulo == null || modulos == null || item.modulo in modulos }
        .filter { !semAsAbas || it !in ITENS_DAS_ABAS }
        .groupBy { it.grupo }
        .toSortedMap()
        .map { (grupo, itens) -> grupo to itens }

/** Item do menu da tela, para o título do topo; nulo nas abas e no Perfil. */
fun itemDa(tela: Tela): ItemDoMenu? = ITENS_DO_MENU.firstOrNull { it.tela == tela }
