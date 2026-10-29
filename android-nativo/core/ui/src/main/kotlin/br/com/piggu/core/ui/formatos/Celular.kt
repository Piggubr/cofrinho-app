package br.com.piggu.core.ui.formatos

// O celular do cadastro (login.ts e sobre-voce.ts): o DDI escolhido e o número como a pessoa
// digitou. O backend junta os dois em E.164 e confere o número de verdade.

/** Como o site: pelo menos 6 dígitos. */
fun celularValido(telefone: String): Boolean = telefone.count { it in '0'..'9' } >= 6

/** O que vai em `telefone`: "+55 (11) 99999-8888". */
fun celularCompleto(paisDoDdi: String, telefone: String): String = "+${ddiDoPais(paisDoDdi)} ${telefone.trim()}"

/** Exemplo do celular no campo vazio (o placeholder do site). */
const val EXEMPLO_DO_CELULAR = "(11) 99999-8888"
