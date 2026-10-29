package br.com.piggu.feature.perfil

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.designsystem.VarianteDeCor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Os ícones do app na tela inicial (ICONES_DO_APP de icone-do-app.ts), na mesma ordem. O Neon
 * (padrão) e o Clássico são grátis; os outros, do Premium. A prévia é a mesma arte do site.
 */
enum class IconeDoApp(val id: String, @StringRes val nome: Int, @DrawableRes val previa: Int, val premium: Boolean) {
    Padrao("padrao", R.string.perfil_icone_padrao, R.drawable.icone_do_app_padrao, premium = false),
    Classico("classico", R.string.perfil_icone_classico, R.drawable.icone_do_app_classico, premium = false),
    Monograma("monograma", R.string.perfil_icone_monograma, R.drawable.icone_do_app_monograma, premium = true),
    Silhueta("silhueta", R.string.perfil_icone_silhueta, R.drawable.icone_do_app_silhueta, premium = true),
    Azul("azul", R.string.perfil_icone_azul, R.drawable.icone_do_app_azul, premium = true),
    Menta("menta", R.string.perfil_icone_menta, R.drawable.icone_do_app_menta, premium = true),
    Lilas("lilas", R.string.perfil_icone_lilas, R.drawable.icone_do_app_lilas, premium = true),
    Coral("coral", R.string.perfil_icone_coral, R.drawable.icone_do_app_coral, premium = true),
    Mel("mel", R.string.perfil_icone_mel, R.drawable.icone_do_app_mel, premium = true),
    Vinho("vinho", R.string.perfil_icone_vinho, R.drawable.icone_do_app_vinho, premium = true),
    Grafite("grafite", R.string.perfil_icone_grafite, R.drawable.icone_do_app_grafite, premium = true),
    Namorados("namorados", R.string.perfil_icone_namorados, R.drawable.icone_do_app_namorados, premium = true),
    ;

    /**
     * O activity-alias do manifesto do app (.IconePadrao, .IconeAzul...), os nomes do app
     * Capacitor. A classe fica no pacote do código do app, que não muda com a variante demo.
     */
    val classe: String get() = "br.com.piggu.app.Icone" + id.replaceFirstChar(Char::uppercase)
}

/**
 * Troca o ícone na tela inicial (IconeDoAppPlugin.java): liga o activity-alias escolhido e
 * desliga os outros. Desligar o atalho que abriu a tarefa faz o Android fechar o app na hora
 * (o Capacitor fechava), então a escolha fica guardada e só vale quando o app sai da frente
 * ([aplicarPendente], chamado pelo app); a tela já mostra o escolhido (D-119).
 */
@Singleton
class IconesDoApp @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val preferencias: PreferenciasDoAparelho,
) {
    private val pacotes get() = contexto.packageManager
    private val guardado get() = contexto.getSharedPreferences("piggu_icone_do_app", Context.MODE_PRIVATE)

    /** O escolhido: o que espera o app sair da frente ou, sem nada esperando, o que está ligado. */
    fun atual(): IconeDoApp = pendente() ?: ligado()

    private val escolhidoAgora by lazy { MutableStateFlow(atual()) }

    /** [atual] que muda na hora da escolha: a marca do topo acompanha (D-097 do iOS). */
    val escolhido: StateFlow<IconeDoApp> get() = escolhidoAgora

    /** O padrão vem ligado no manifesto e os outros desligados: o ligado é o outro que estiver ligado. */
    private fun ligado(): IconeDoApp = IconeDoApp.entries.firstOrNull {
        it != IconeDoApp.Padrao && pacotes.getComponentEnabledSetting(componente(it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: IconeDoApp.Padrao

    private fun pendente(): IconeDoApp? =
        guardado.getString(CHAVE, null)?.let { id -> IconeDoApp.entries.firstOrNull { it.id == id } }

    /** Quem chama confere o Premium. Vale quando o app sai da frente. */
    fun escolher(icone: IconeDoApp) {
        if (icone == ligado()) guardado.edit().remove(CHAVE).apply()
        else guardado.edit().putString(CHAVE, icone.id).apply()
        escolhidoAgora.value = icone
    }

    /** O app chama ao sair da frente (ProcessLifecycleOwner): troca o atalho do lançador. */
    fun aplicarPendente() {
        val icone = pendente() ?: return
        guardado.edit().remove(CHAVE).apply()
        // Liga o novo antes de desligar os outros: nunca fica um instante sem ícone.
        pacotes.setComponentEnabledSetting(componente(icone), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        IconeDoApp.entries.filter { it != icone }.forEach {
            pacotes.setComponentEnabledSetting(componente(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /**
     * O app chama quando o plano da pessoa logada muda (o effect do IconesDoApp do site): sem
     * Premium, um ícone do Premium volta ao Neon, o Clássico fica, e a marca do aviso de "voltou
     * ao padrão" é gravada sem mexer numa cor grátis. Devolve se voltou.
     */
    suspend fun voltarAoPadraoSemPremium(premium: Boolean): Boolean {
        if (premium || !atual().premium) return false
        escolher(IconeDoApp.Padrao)
        preferencias.voltarAoPadrao(VarianteDeCor.deId(preferencias.cor.first()).efetiva(premium = false).id)
        return true
    }

    private fun componente(icone: IconeDoApp) = ComponentName(contexto, icone.classe)

    private companion object {
        const val CHAVE = "pendente"
    }
}
