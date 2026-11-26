package br.com.piggu.core.data

import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.Usuario
import java.time.LocalDate

/** A versão dos Termos e do Aviso em vigor (VERSAO_DO_AVISO de aviso.ts e do backend). */
const val VERSAO_DOS_TERMOS = "2026-11-08"

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

/**
 * O banner do plano gratuito (D-180): só com conta gratuita (nem Premium nem ADMIN), fora da
 * demonstração e com 18 anos ou mais pelo `nascimento`, contados no fuso de Brasília. Sessão
 * abrindo, plano desconhecido e nascimento ausente ou ilegível não mostram. Derivada do estado
 * da sessão: assinou o Premium ou trocou de conta, o banner segue na hora.
 */
fun Usuario?.deveMostrarAnuncios(demo: Boolean, hoje: LocalDate = LocalDate.now(FUSO_DA_CONTA)): Boolean =
    this != null && !demo && !ehPremium && plano == Plano.GRATUITO && maiorDeIdade(hoje)

private fun Usuario.maiorDeIdade(hoje: LocalDate): Boolean {
    val nasceu = nascimento?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return !nasceu.plusYears(18).isAfter(hoje)
}
