package br.com.piggu.core.network

import br.com.piggu.core.model.JsonDoPiggu
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/** A API de produção (API_SPEC, Endereços). A barra no fim mantém o `/api` das rotas relativas. */
const val URL_DA_API = "https://piggu.com.br/api/"

/** O que a sessão põe na rede: o Bearer em cada chamada e a renovação no 401. */
interface AutenticacaoDaRede {
    val interceptador: Interceptor
    val autenticador: Authenticator
}

/** Liga quando a API responde 426 com `ATUALIZE_O_APP`: esta versão do app não serve mais. */
@Singleton
class AvisoDeVersao @Inject constructor() {
    private val precisa = MutableStateFlow(false)
    val precisaAtualizar: StateFlow<Boolean> = precisa.asStateFlow()

    internal fun ligar() {
        precisa.value = true
    }
}

/** Manda a versão no User-Agent (fica na sessão) e escuta o 426 que pede para atualizar. */
internal class InterceptadorDeVersao(
    private val versaoDoApp: String,
    private val aviso: AvisoDeVersao,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request().newBuilder().header("User-Agent", "Piggu-Android/$versaoDoApp").build()
        val resposta = chain.proceed(pedido)
        if (resposta.code == 426 && lerCorpoDeErro(resposta.peekBody(64 * 1024L).string())?.codigo == "ATUALIZE_O_APP") {
            aviso.ligar()
        }
        return resposta
    }
}

/**
 * A rede para o Hilt. O app fornece `@Named("versaoDoApp")` (o versionName),
 * `@Named("urlDaApi")` ([URL_DA_API] em produção) e a sessão faz o `@Binds` de [AutenticacaoDaRede].
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDaRede {
    @Provides
    @Singleton
    fun json(): Json = JsonDoPiggu

    @Provides
    @Singleton
    fun okHttp(
        @Named("versaoDoApp") versaoDoApp: String,
        aviso: AvisoDeVersao,
        autenticacao: AutenticacaoDaRede,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(InterceptadorDeVersao(versaoDoApp, aviso))
        .addInterceptor(autenticacao.interceptador)
        .authenticator(autenticacao.autenticador)
        .build()

    @Provides
    @Singleton
    fun retrofit(@Named("urlDaApi") urlDaApi: String, cliente: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(urlDaApi)
            .client(cliente)
            .addCallAdapterFactory(AdaptadorDeErros)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun identidade(retrofit: Retrofit): ServicoDeIdentidade = retrofit.create()

    @Provides
    @Singleton
    fun financas(retrofit: Retrofit): ServicoDeFinancas = retrofit.create()

    @Provides
    @Singleton
    fun bancos(retrofit: Retrofit): ServicoDeBancos = retrofit.create()

    @Provides
    @Singleton
    fun estiloDeVida(retrofit: Retrofit): ServicoDeEstiloDeVida = retrofit.create()

    @Provides
    @Singleton
    fun midia(retrofit: Retrofit): ServicoDeMidia = retrofit.create()

    @Provides
    @Singleton
    fun recompensas(retrofit: Retrofit): ServicoDeRecompensas = retrofit.create()
}
