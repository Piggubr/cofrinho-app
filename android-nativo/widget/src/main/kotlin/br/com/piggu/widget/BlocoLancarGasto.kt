package br.com.piggu.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * O bloco "Lançar gasto" das Configurações rápidas (o controle da Central de Controle do iOS):
 * abre o formulário de gasto do app pelo piggu://gasto. Com a tela travada, pede o desbloqueio antes.
 */
class BlocoLancarGasto : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        // Um botão, não um liga e desliga.
        qsTile?.apply { state = Tile.STATE_INACTIVE }?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::abrir) else abrir()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun abrir() {
        val intent = Intent(Intent.ACTION_VIEW, "piggu://gasto".toUri()).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
