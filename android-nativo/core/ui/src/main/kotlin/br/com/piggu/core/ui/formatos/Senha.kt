package br.com.piggu.core.ui.formatos

// As regras da senha (regras-da-senha.ts, espelho de Senhas.java): o servidor confere de novo.

const val MINIMO_DA_SENHA = 8
const val MAXIMO_DA_SENHA_EM_BYTES = 72

/** O que há de errado com a senha; a feature escolhe o texto. */
enum class ProblemaDaSenha { Curta, Longa, SemLetraOuNumero, IgualAoEmail }

/** O problema da senha, ou nulo quando ela serve. Como o site: o mínimo conta em UTF-16, o teto em bytes. */
fun problemaDaSenha(senha: String, email: String = ""): ProblemaDaSenha? = when {
    senha.length < MINIMO_DA_SENHA -> ProblemaDaSenha.Curta
    senha.toByteArray(Charsets.UTF_8).size > MAXIMO_DA_SENHA_EM_BYTES -> ProblemaDaSenha.Longa
    // \p{L} e \p{Nd} do site.
    senha.codePoints().noneMatch(Character::isLetter) || senha.codePoints().noneMatch(Character::isDigit) ->
        ProblemaDaSenha.SemLetraOuNumero
    email.isNotEmpty() && senha.trim().lowercase() == email.trim().lowercase() -> ProblemaDaSenha.IgualAoEmail
    else -> null
}
