package br.com.piggu.core.data

import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.Usuario

/** A versão dos Termos e do Aviso em vigor (VERSAO_DO_AVISO de aviso.ts e do backend). */
const val VERSAO_DOS_TERMOS = "2026-10-16"

// As regras de auth.service.ts e auth.guard.ts. Conveniência de navegação: quem decide é o backend.

val Usuario.ehAdmin: Boolean get() = role == PigguRole.ADMIN

/** Espelha o backend: o ADMIN opera a instalação e usa tudo. */
val Usuario.ehPremium: Boolean get() = ehAdmin || plano == Plano.PREMIUM

/** Titular da família; o ADMIN vale como titular dentro da própria família. */
val Usuario.ehTitular: Boolean get() = role == PigguRole.TITULAR || ehAdmin

/** Cria e edita os prêmios da família: titular (ou ADMIN) e parceiro. */
val Usuario.cuidaDosPremios: Boolean get() = ehTitular || role == PigguRole.PARCEIRO

/** Os Termos mudaram desde o último aceite: o app só abre o aceite. Ausente conta como aceito. */
val Usuario.termosPendentes: Boolean get() = termosAceitos != null && termosAceitos != VERSAO_DOS_TERMOS

/** Falta apelido, nascimento, CPF ou celular (D-088): o app só abre "Sobre você". Ausente não trava. */
val Usuario.sobreVocePendente: Boolean get() = cadastroCompleto == false

/** Os módulos de estilo de vida ligados, com o id do site ("girls-math"); nulo (conta antiga, demo) liga todos. */
val Usuario.modulosLigados: List<String>?
    get() = preferencias?.modulos
        ?.filter { it != ModuloDeEstiloDeVida.DESCONHECIDO }
        ?.map { it.name.lowercase().replace('_', '-') }

/** O que a pessoa precisa fazer antes do resto, na ordem do termosGuard. */
enum class Pendencia { Termos, SobreVoce }

val Usuario.pendencia: Pendencia?
    get() = when {
        termosPendentes -> Pendencia.Termos
        sobreVocePendente -> Pendencia.SobreVoce
        else -> null
    }
