package nl.vdzon.wind

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Gedeelde notificatielogica voor wind-antwoorden. Gebruikt door zowel de
 * trampoline-activities ([AnswerTrampolineActivity]) als de doorlopende
 * spraakassistent ([VoiceAssistant]), zodat beide dezelfde notificatie-stijl
 * en dezelfde runtime-permissiecheck delen.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "wind_answers"

    fun post(context: Context, title: String, text: String) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Op Android 13+ is POST_NOTIFICATIONS een runtime-permissie. Zonder
        // toestemming slaan we het posten over (crasht anders niet de PoC).
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            NotificationManagerCompat.from(context).notify(text.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wind-antwoorden",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notificaties met wind-antwoorden."
            }
            manager.createNotificationChannel(channel)
        }
    }
}
