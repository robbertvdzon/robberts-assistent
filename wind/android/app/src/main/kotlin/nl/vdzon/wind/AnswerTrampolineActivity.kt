package nl.vdzon.wind

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Basis voor de "trampoline"-activities. Een trampoline-activity toont geen
 * zichtbaar scherm (zie `Theme.Translucent.NoDisplay` in het manifest): ze
 * spreekt het antwoord uit via [TextToSpeech], post een notificatie met exact
 * dezelfde tekst en sluit zichzelf daarna direct af.
 *
 * Subklassen leveren alleen de [answer] (de tekst) en de [notificationTitle].
 */
abstract class AnswerTrampolineActivity : Activity(), TextToSpeech.OnInitListener {

    /** De uit te spreken / te notificeren tekst. */
    abstract val answer: String

    /** Korte titel voor de notificatie. */
    abstract val notificationTitle: String

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notificatie meteen posten; die is niet afhankelijk van TTS-init.
        postNotification()

        // TTS initialiseren; we sluiten pas af nadat het uitspreken klaar is.
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (status == TextToSpeech.SUCCESS && engine != null) {
            engine.language = Locale("nl", "NL")
            engine.setOnUtteranceProgressListener(
                object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        finishAndCleanup()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishAndCleanup()
                    }
                },
            )
            engine.speak(answer, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } else {
            // TTS niet beschikbaar: de notificatie staat al; gewoon afsluiten.
            finishAndCleanup()
        }
    }

    private fun finishAndCleanup() {
        // Kan vanuit een TTS-callbackthread komen; UI-werk op de main-thread.
        runOnUiThread {
            tts?.stop()
            tts?.shutdown()
            tts = null
            finish()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun postNotification() {
        ensureChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notificationTitle)
            .setContentText(answer)
            .setStyle(NotificationCompat.BigTextStyle().bigText(answer))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Op Android 13+ is POST_NOTIFICATIONS een runtime-permissie. Zonder
        // toestemming slaan we het posten over (crasht anders niet de PoC).
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            NotificationManagerCompat.from(this)
                .notify(answer.hashCode(), notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE)
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

    private companion object {
        const val CHANNEL_ID = "wind_answers"
        const val UTTERANCE_ID = "wind_answer"
    }
}
