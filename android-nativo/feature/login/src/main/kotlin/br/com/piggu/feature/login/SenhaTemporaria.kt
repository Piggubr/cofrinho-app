package br.com.piggu.feature.login

// Substituído pelas regras de regras-da-senha.ts quando o porte chegar.
internal fun problemaDaSenhaNova(senha: String): Int? =
    if (senha.length < 8 || senha.none { it.isLetter() } || senha.none { it.isDigit() }) R.string.redefinir_dica else null
