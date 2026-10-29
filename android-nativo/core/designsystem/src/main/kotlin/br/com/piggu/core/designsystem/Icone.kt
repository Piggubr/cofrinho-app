package br.com.piggu.core.designsystem

import androidx.annotation.DrawableRes

/**
 * Os ícones do site (frontend/src/app/core/ui/icone.ts), com a mesma chave, desenhados com o
 * Material Symbols Outlined (gerar-icones.mjs). O "mais" do site é o + de adicionar; o "Mais"
 * da barra de abas é o ic_mais.
 */
enum class Icone(val chave: String, @DrawableRes val desenho: Int) {
    Casa("casa", R.drawable.ic_casa),
    Carteira("carteira", R.drawable.ic_carteira),
    Entrada("entrada", R.drawable.ic_entrada),
    Grafico("grafico", R.drawable.ic_grafico),
    Cartao("cartao", R.drawable.ic_cartao),
    Banco("banco", R.drawable.ic_banco),
    Recibo("recibo", R.drawable.ic_recibo),
    Pizza("pizza", R.drawable.ic_pizza),
    Calendario("calendario", R.drawable.ic_calendario),
    Carrinho("carrinho", R.drawable.ic_carrinho),
    Local("local", R.drawable.ic_local),
    Filme("filme", R.drawable.ic_filme),
    Imagem("imagem", R.drawable.ic_imagem),
    Camera("camera", R.drawable.ic_camera),
    Trofeu("trofeu", R.drawable.ic_trofeu),
    Alvo("alvo", R.drawable.ic_alvo),
    Pessoas("pessoas", R.drawable.ic_pessoas),
    Pessoa("pessoa", R.drawable.ic_pessoa),
    Estrela("estrela", R.drawable.ic_estrela),
    Sair("sair", R.drawable.ic_sair),
    Busca("busca", R.drawable.ic_busca),
    Mais("mais", R.drawable.ic_adicionar),
    Fechar("fechar", R.drawable.ic_fechar),
    Menu("menu", R.drawable.ic_menu),
    Grade("grade", R.drawable.ic_grade),
    Esquerda("esquerda", R.drawable.ic_esquerda),
    Direita("direita", R.drawable.ic_direita),
    Lixeira("lixeira", R.drawable.ic_lixeira),
    Lapis("lapis", R.drawable.ic_lapis),
    Check("check", R.drawable.ic_check),
    Bandeira("bandeira", R.drawable.ic_bandeira),
    Sacola("sacola", R.drawable.ic_sacola),
    Nota("nota", R.drawable.ic_nota),
    Compartilhar("compartilhar", R.drawable.ic_compartilhar),
    Subir("subir", R.drawable.ic_subir),
    Descer("descer", R.drawable.ic_descer),
    Arrastar("arrastar", R.drawable.ic_arrastar),
    Brilho("brilho", R.drawable.ic_brilho),
    Enviar("enviar", R.drawable.ic_enviar),
    Alerta("alerta", R.drawable.ic_alerta),
    Cofrinho("cofrinho", R.drawable.ic_cofrinho),
}
