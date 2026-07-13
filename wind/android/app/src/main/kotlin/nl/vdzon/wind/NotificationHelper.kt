package nl.vdzon.wind

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

/**
 * Gedeelde notificatielogica voor wind-antwoorden, gebruikt door de
 * trampoline-activities ([AnswerTrampolineActivity]).
 */
object NotificationHelper {

    private const val CHANNEL_ID = "wind_answers"
    private const val UPDATE_CHANNEL_ID = "wind_updates"

    fun post(context: Context, title: String, text: String) {
        ensureChannel(context, CHANNEL_ID, "Wind-antwoorden", "Notificaties met wind-antwoorden.")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        postIfPermitted(context, text.hashCode(), notification)
    }

    /**
     * Melding dat er een nieuwe Wind-versie is (zie [WindUpdateChecker]) — apart kanaal van de
     * wind-antwoorden, met een tik-actie die [pendingIntent] opent (de GitHub-release-pagina).
     */
    fun postUpdateAvailable(context: Context, title: String, text: String, pendingIntent: PendingIntent) {
        ensureChannel(context, UPDATE_CHANNEL_ID, "Wind-updates", "Melding als er een nieuwe versie van Wind is.")

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        postIfPermitted(context, UPDATE_CHANNEL_ID.hashCode(), notification)
    }

    private fun postIfPermitted(context: Context, id: Int, notification: android.app.Notification) {
        // Op Android 13+ is POST_NOTIFICATIONS een runtime-permissie. Zonder
        // toestemming slaan we het posten over (crasht anders niet de PoC).
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun ensureChannel(context: Context, channelId: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                this.description = description
            }
            manager.createNotificationChannel(channel)
        }
    }
}
