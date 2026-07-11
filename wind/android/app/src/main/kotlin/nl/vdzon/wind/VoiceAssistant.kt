package nl.vdzon.wind

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Doorlopende spraakassistent voor de Wind-app: luistert net zo lang tot je
 * "stop met luisteren" zegt, herkent drie vaste zinnen en spreekt/notificeert
 * het antwoord (via dezelfde [NotificationHelper] als de trampoline-flow).
 *
 * Gebruikt Android's `SpeechRecognizer` headless (geen Google-mic-popup, in
 * tegenstelling tot `startActivityForResult` met `RecognizerIntent`) — na elk
 * resultaat/timeout start de lus zichzelf opnieuw, totdat [stop] wordt
 * aangeroepen.
 */
class VoiceAssistant(
    private val activity: Activity,
    private val onStatus: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false

    fun start() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onStatus("Spraakherkenning is niet beschikbaar op dit toestel.")
            return
        }
        listening = true
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(activity) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale("nl", "NL")
                listenOnce()
            }
        } else {
            listenOnce()
        }
    }

    fun stop() {
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        onStatus("Gestopt met luisteren.")
    }

    private fun listenOnce() {
        if (!listening) return
        onStatus("Ik luister…")

        val r = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL")
        }
        r.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(bundle: Bundle) {
                    val heard = bundle
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    handleQuery(heard)
                }

                override fun onError(error: Int) {
                    // Bv. stilte/timeout (ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH):
                    // gewoon opnieuw luisteren, geen foutmelding nodig.
                    if (listening) listenOnce()
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            },
        )
        r.startListening(intent)
    }

    private fun handleQuery(heard: String) {
        val text = heard.lowercase(Locale("nl", "NL"))
        onStatus("Ik hoorde: \"$heard\"")
        when {
            "stop" in text -> speak("Ik stop met luisteren.") { stop() }
            "voorspelling" in text -> answerAndContinue(WindAnswers.FORECAST, "Windvoorspelling")
            "wind" in text -> answerAndContinue(WindAnswers.WIND_SPEED, "Huidige windsnelheid")
            else -> speak("Dat begrijp ik niet.") { if (listening) listenOnce() }
        }
    }

    private fun answerAndContinue(answer: String, title: String) {
        NotificationHelper.post(activity, title, answer)
        speak(answer) { if (listening) listenOnce() }
    }

    private fun speak(text: String, onDone: () -> Unit) {
        onStatus(text)
        val engine = tts
        if (engine == null || !ttsReady) {
            onDone()
            return
        }
        val utteranceId = "assistant_${text.hashCode()}"
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    activity.runOnUiThread(onDone)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    activity.runOnUiThread(onDone)
                }
            },
        )
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Op te ruimen wanneer de host-activity wordt vernietigd. */
    fun destroy() {
        listening = false
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
