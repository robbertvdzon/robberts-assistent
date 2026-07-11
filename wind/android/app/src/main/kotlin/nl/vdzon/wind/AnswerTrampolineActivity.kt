package nl.vdzon.wind

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Basis voor de "trampoline"-activities. Een trampoline-activity toont geen
 * zichtbaar scherm (zie `@style/TrampolineTheme` in het manifest — bewust
 * geen `Theme.NoDisplay`, want dat eist een synchrone `finish()` in
 * `onCreate`, terwijl wij asynchroon op TTS wachten): ze spreekt het antwoord
 * uit via [TextToSpeech], post een notificatie met exact dezelfde tekst (via
 * [NotificationHelper]) en sluit zichzelf daarna direct af.
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
        NotificationHelper.post(this, notificationTitle, answer)

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

    private companion object {
        const val UTTERANCE_ID = "wind_answer"
    }
}
