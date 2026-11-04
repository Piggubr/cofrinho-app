package br.com.piggu.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.piggu.core.data.AparelhoDoPush
import br.com.piggu.core.data.FonteDoTokenDoPush
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** O canal dos avisos do dia (fatura, orçamento, conta do mês); o FCM usa o mesmo com o app fechado. */
const val CANAL_DOS_AVISOS = "avisos"

/**
 * Liga o Firebase só para o push (D-166), sem o plugin google-services: as chaves vêm do build
 * (PIGGU_GOOGLE_SERVICES). Sem elas, como na demo, o app segue sem push.
 */
fun ligarPush(contexto: Context): Boolean {
    criarCanalDosAvisos(contexto)
    if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return false
    if (FirebaseApp.getApps(contexto).isEmpty()) {
        val opcoes = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJETO)
            .setGcmSenderId(BuildConfig.FIREBASE_REMETENTE)
            .build()
        FirebaseApp.initializeApp(contexto, opcoes)
    }
    return true
}

private fun criarCanalDosAvisos(contexto: Context) {
    val canal = NotificationChannel(CANAL_DOS_AVISOS, contexto.getString(R.string.canal_avisos), NotificationManager.IMPORTANCE_DEFAULT)
        .apply { description = contexto.getString(R.string.canal_avisos_descricao) }
    contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoPush {
    /** Sem Firebase ou com as notificações desligadas no Android, não há o que registrar. */
    @Provides
    fun fonteDoToken(@ApplicationContext contexto: Context) = FonteDoTokenDoPush {
        if (FirebaseApp.getApps(contexto).isEmpty() || !NotificationManagerCompat.from(contexto).areNotificationsEnabled()) {
            null
        } else {
            runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        }
    }
}

/** O token novo vai ao servidor; com o app aberto, o FCM não mostra a notificação sozinho. */
@AndroidEntryPoint
class ServicoDePush : FirebaseMessagingService() {
    @Inject lateinit var aparelho: AparelhoDoPush

    override fun onNewToken(token: String) = aparelho.tokenNovo(token)

    override fun onMessageReceived(mensagem: RemoteMessage) {
        val aviso = mensagem.notification ?: return
        val semPermissao = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (semPermissao) return
        // O ícone escolhido no Perfil é um activity-alias: o launcher diz qual está ligado.
        val abrir = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notificacao = NotificationCompat.Builder(this, CANAL_DOS_AVISOS)
            .setSmallIcon(br.com.piggu.core.designsystem.R.drawable.ic_cofrinho)
            .setContentTitle(aviso.title)
            .setContentText(aviso.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.body))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(mensagem.messageId.hashCode(), notificacao)
    }
}
