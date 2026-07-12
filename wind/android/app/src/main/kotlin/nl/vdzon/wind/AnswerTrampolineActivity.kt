package nl.vdzon.wind

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Basis voor de "trampoline"-activities. Een trampoline-activity toont geen
 * zichtbaar scherm (zie `@style/TrampolineTheme` in het manifest — bewust
 * geen `Theme.NoDisplay`, want dat eist een synchrone `finish()` in
 * `onCreate`, terwijl wij asynchroon op TTS wachten): ze probeert eerst een
 * actueel antwoord op te halen bij de backend/AI ([AssistantClient] — alleen
 * stil, geen inlog-UI), valt bij falen terug op de statische [WindAnswers]-
 * tekst, spreekt het resultaat uit via [TextToSpeech], post een notificatie
 * met exact dezelfde tekst (via [NotificationHelper]) en sluit zichzelf
 * daarna direct af.
 *
 * Subklassen leveren de [question] (voor de AI), de [fallbackAnswer]
 * (offline/niet-ingelogd) en de [notificationTitle].
 */
abstract class AnswerTrampolineActivity : Activity(), TextToSpeech.OnInitListener {

    /** De vraag die naar de chat-assistent gestuurd wordt. */
    abstract val question: String

    /** Terugvaltekst als de assistent niet bereikbaar is (geen sessie, netwerkfout, timeout). */
    abstract val fallbackAnswer: String

    /** Korte titel voor de notificatie. */
    abstract val notificationTitle: String

    private var tts: TextToSpeech? = null

    /** `null` = nog niet geïnitialiseerd, anders het resultaat van [TextToSpeech.OnInitListener]. */
    private var ttsInitSucceeded: Boolean? = null
    private var resolvedAnswer: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notificatie-permissie (Android 13+) alvast aanvragen; de notificatie zelf posten we pas
        // zodra het antwoord bekend is (proceedIfReady), dus hier hoeft na afhandeling niets meer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }

        tts = TextToSpeech(this, this)

        scope.launch {
            resolvedAnswer = AssistantClient.tryFetchAnswer(this@AnswerTrampolineActivity, question)
                ?: fallbackAnswer
            proceedIfReady()
        }
    }

    override fun onInit(status: Int) {
        val succeeded = status == TextToSpeech.SUCCESS
        ttsInitSucceeded = succeeded
        if (succeeded) {
            tts?.language = Locale("nl", "NL")
        }
        proceedIfReady()
    }

    /** Wacht tot zowel het antwoord als TTS-init klaar zijn (in willekeurige volgorde). */
    private fun proceedIfReady() {
        val answer = resolvedAnswer ?: return
        val ttsOk = ttsInitSucceeded ?: return

        NotificationHelper.post(this, notificationTitle, answer)

        val engine = tts
        if (ttsOk && engine != null) {
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
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private companion object {
        const val UTTERANCE_ID = "wind_answer"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
