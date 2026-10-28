package br.com.piggu.core.network

import br.com.piggu.core.model.Avisos
import br.com.piggu.core.model.ConviteDaFamilia
import br.com.piggu.core.model.EnderecoDePagamento
import br.com.piggu.core.model.ErroDoApp
import br.com.piggu.core.model.EventoDeAuditoria
import br.com.piggu.core.model.Familia
import br.com.piggu.core.model.InfoDoPlano
import br.com.piggu.core.model.ParDeTokens
import br.com.piggu.core.model.PedidoComToken
import br.com.piggu.core.model.PedidoDeAlteracaoDeUsuario
import br.com.piggu.core.model.PedidoDeAparelho
import br.com.piggu.core.model.PedidoDeCadastro
import br.com.piggu.core.model.PedidoDeCheckout
import br.com.piggu.core.model.PedidoDeCompartilhamento
import br.com.piggu.core.model.PedidoDeEmail
import br.com.piggu.core.model.PedidoDeFoto
import br.com.piggu.core.model.PedidoDeLogin
import br.com.piggu.core.model.PedidoDeModulos
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.PedidoDePais
import br.com.piggu.core.model.PedidoDePapel
import br.com.piggu.core.model.PedidoDePreferencias
import br.com.piggu.core.model.PedidoDeRefresh
import br.com.piggu.core.model.PedidoDeSenha
import br.com.piggu.core.model.PedidoDeSenhaNova
import br.com.piggu.core.model.PedidoDeTermos
import br.com.piggu.core.model.PedidoDoGoogle
import br.com.piggu.core.model.PedidoSobreVoce
import br.com.piggu.core.model.Reembolso
import br.com.piggu.core.model.Usuario
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * piggu-identity: entrar e sair (auth.service.ts), a conta (users.service.ts), a família
 * (family.service.ts), o Premium (billing.service.ts) e o histórico das pessoas.
 * Rotas relativas à [URL_DA_API]. O `vazio` é o `{}` que o site manda nos POST sem dados.
 */
interface ServicoDeIdentidade {
    // Entrar e manter a sessão (API_SPEC): o refresh vai e volta no corpo.

    @POST("auth/entrar")
    suspend fun entrarComSenha(@Body pedido: PedidoDeLogin): ParDeTokens

    @POST("auth/google")
    suspend fun entrarComGoogle(@Body pedido: PedidoDoGoogle): ParDeTokens

    /** Cria a conta sem abrir sessão (202): a pessoa confirma o e-mail e entra com a senha. */
    @POST("auth/cadastro")
    suspend fun cadastrar(@Body pedido: PedidoDeCadastro)

    @POST("auth/refresh")
    suspend fun renovar(@Body pedido: PedidoDeRefresh): ParDeTokens

    /** Encerra a sessão no servidor (204). */
    @POST("auth/logout")
    suspend fun sair(@Body pedido: PedidoDeRefresh)

    @PUT("auth/me/senha")
    suspend fun definirSenha(@Body pedido: PedidoDeSenha): Usuario

    @POST("auth/confirmar-email")
    suspend fun confirmarEmail(@Body pedido: PedidoComToken)

    /** O backend responde igual com ou sem conta. */
    @POST("auth/senha/esqueci")
    suspend fun esqueciSenha(@Body pedido: PedidoDeEmail)

    @POST("auth/senha/redefinir")
    suspend fun redefinirSenha(@Body pedido: PedidoDeSenhaNova)

    @POST("auth/me/email/reenviar")
    suspend fun reenviarConfirmacao(@Body vazio: JsonObject = JsonObject(emptyMap()))

    // A conta.

    @GET("auth/me")
    suspend fun meuPerfil(): Usuario

    @PUT("auth/me/preferences")
    suspend fun salvarPreferencias(@Body pedido: PedidoDePreferencias): Usuario

    @PUT("auth/me/alerts")
    suspend fun salvarAvisos(@Body avisos: Avisos): Usuario

    @PUT("auth/me/sharing")
    suspend fun salvarCompartilhamento(@Body pedido: PedidoDeCompartilhamento): Usuario

    @POST("auth/me/devices")
    suspend fun registrarAparelho(@Body pedido: PedidoDeAparelho)

    @DELETE("auth/me/devices/{token}")
    suspend fun removerAparelho(@Path("token") token: String)

    @PUT("auth/me/modules")
    suspend fun salvarModulos(@Body pedido: PedidoDeModulos): Usuario

    @PUT("me/pais")
    suspend fun informarPais(@Body pedido: PedidoDePais): Usuario

    @PUT("me/sobre-voce")
    suspend fun sobreVoce(@Body pedido: PedidoSobreVoce): Usuario

    @PUT("me/termos")
    suspend fun aceitarTermos(@Body pedido: PedidoDeTermos): Usuario

    @PUT("me/foto")
    suspend fun trocarFoto(@Body pedido: PedidoDeFoto): Usuario

    /** Sem foto: ficam as iniciais, nem a do Google aparece. */
    @DELETE("me/foto")
    suspend fun removerFoto(): Usuario

    @PUT("me/foto/google")
    suspend fun usarFotoDoGoogle(@Body vazio: JsonObject = JsonObject(emptyMap())): Usuario

    /** Cópia de tudo o que o Piggu guarda da pessoa (LGPD art. 18), em JSON. */
    @Streaming
    @GET("me/export")
    suspend fun exportarMeusDados(): ResponseBody

    /** Exclui a conta e os dados; se era a última da família, a família sai junto. */
    @DELETE("me")
    suspend fun excluirConta()

    @GET("users")
    suspend fun listarUsuarios(): List<Usuario>

    @PATCH("users/{id}")
    suspend fun alterarUsuario(@Path("id") id: String, @Body mudancas: PedidoDeAlteracaoDeUsuario): Usuario

    /** Erro que aconteceu no aparelho, já passado por [semDadosPessoais]. Rota aberta. */
    @POST("app/erros")
    suspend fun registrarErroDoApp(@Body erro: ErroDoApp)

    // A família. O backend só deixa o titular convidar e remover.

    @GET("family")
    suspend fun verFamilia(): Familia

    @PUT("family")
    suspend fun renomearFamilia(@Body pedido: PedidoDeNome): Familia

    @POST("family/invites")
    suspend fun convidar(@Body pedido: PedidoDeEmail): Familia

    @DELETE("family/invites/{id}")
    suspend fun cancelarConvite(@Path("id") id: String): Familia

    @DELETE("family/members/{id}")
    suspend fun removerMembro(@Path("id") id: String): Familia

    /** PARCEIRO convida (vale quando a pessoa aceita); MEMBRO volta a membro. */
    @PUT("family/members/{id}/role")
    suspend fun mudarPapel(@Path("id") id: String, @Body pedido: PedidoDePapel): Familia

    /** É preciso entrar de novo depois. */
    @POST("family/partnership/accept")
    suspend fun aceitarParceria(@Body vazio: JsonObject = JsonObject(emptyMap()))

    @POST("family/partnership/decline")
    suspend fun recusarParceria(@Body vazio: JsonObject = JsonObject(emptyMap())): Familia

    /** Convites de outras famílias para o meu e-mail. */
    @GET("family/invites/mine")
    suspend fun convitesParaMim(): List<ConviteDaFamilia>

    @POST("family/invites/{id}/accept")
    suspend fun aceitarConvite(@Path("id") id: String, @Body vazio: JsonObject = JsonObject(emptyMap()))

    @POST("family/leave")
    suspend fun sairDaFamilia(@Body vazio: JsonObject = JsonObject(emptyMap()))

    /** Quem entrou, saiu ou mudou de papel. Junte com o do dinheiro por [historicoDaFamilia]. */
    @GET("family/history")
    suspend fun historicoDasPessoas(@Query("limite") limite: Int = 50): List<EventoDeAuditoria>

    // Premium. O pagamento acontece na página do provedor.

    /** @param campanha código de promoção do link, que pode liberar o teste de novo */
    @GET("billing/plan")
    suspend fun plano(@Query("campanha") campanha: String? = null): InfoDoPlano

    @POST("billing/checkout")
    suspend fun checkout(@Body pedido: PedidoDeCheckout): EnderecoDePagamento

    /** Desistir em até 7 dias da primeira cobrança: cancela na hora e devolve o valor. */
    @POST("billing/refund")
    suspend fun reembolso(@Body vazio: JsonObject = JsonObject(emptyMap())): Reembolso

    /** Trocar cartão, ver recibos e cancelar. */
    @POST("billing/portal")
    suspend fun portal(@Body vazio: JsonObject = JsonObject(emptyMap())): EnderecoDePagamento
}
