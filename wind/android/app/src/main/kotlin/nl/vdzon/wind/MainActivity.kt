package nl.vdzon.wind

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * Host-activity voor het handmatig te openen Flutter-scherm. Los van de
 * trampoline-activities die de losse spraak-/notificatie-flow afhandelen.
 *
 * Stelt twee kanalen beschikbaar aan Flutter:
 *  - [ANSWERS_CHANNEL]: eenmalige antwoorden starten via de trampoline-
 *    activities (zelfde pad als de toekomstige "Hey Google"-flow).
 *  - [ASSISTANT_CHANNEL] + [ASSISTANT_STATUS_CHANNEL]: de doorlopende
 *    spraakassistent ([VoiceAssistant]) starten/stoppen en de status ervan
 *    (wat 'ie hoort/zegt) live tonen in het Flutter-scherm.
 *
 * `FlutterActivity` erft van `android.app.Activity`, niet van AndroidX's
 * `ComponentActivity` — dus gebruiken we hier de klassieke
 * `ActivityCompat.requestPermissions`-route i.p.v. `registerForActivityResult`.
 */
class MainActivity : FlutterActivity() {

    private var voiceAssistant: VoiceAssistant? = null
    private var statusEventSink: EventChannel.EventSink? = null
    private var pendingAssistantStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Meteen beginnen met luisteren zodra de app opent — geen knop nodig.
        // Eerst de notificatie-permissie afhandelen (indien nodig): Android
        // staat geen twee gelijktijdige permissie-dialogen toe, dus pas ná
        // dat resultaat (of meteen als 'ie niet nodig was) de microfoon-
        // permissie/assistent starten.
        if (!requestNotificationPermissionIfNeeded()) {
            startAssistant()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, ANSWERS_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "openWindSpeed" -> {
                    startActivity(Intent(this, WindSpeedActivity::class.java))
                    result.success(null)
                }
                "openForecast" -> {
                    startActivity(Intent(this, ForecastActivity::class.java))
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, ASSISTANT_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    startAssistant()
                    result.success(null)
                }
                "stop" -> {
                    voiceAssistant?.stop()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, ASSISTANT_STATUS_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    statusEventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    statusEventSink = null
                }
            },
        )
    }

    private fun startAssistant() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingAssistantStart = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE,
            )
            return
        }
        val assistant = voiceAssistant ?: VoiceAssistant(this) { status ->
            runOnUiThread { statusEventSink?.success(status) }
        }.also { voiceAssistant = it }
        assistant.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                // Ongeacht toegestaan/geweigerd: nu pas de assistent starten
                // (zie onCreate) om niet twee permissie-dialogen te overlappen.
                startAssistant()
            }
            RECORD_AUDIO_REQUEST_CODE -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (granted && pendingAssistantStart) {
                    startAssistant()
                } else if (!granted) {
                    statusEventSink?.success("Geen toestemming voor de microfoon.")
                }
                pendingAssistantStart = false
            }
        }
    }

    /** @return true als er een permissie-dialoog is getoond (resultaat komt async binnen). */
    private fun requestNotificationPermissionIfNeeded(): Boolean {
        // Android 13+ (Tiramisu) vereist een runtime-toestemming om
        // notificaties te posten. Zonder dit verzoek blijft de permissie
        // geweigerd en slaat NotificationHelper het posten stil over — TTS
        // werkt dan wel, maar er komt nooit een notificatie.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
            return true
        }
        return false
    }

    override fun onDestroy() {
        voiceAssistant?.destroy()
        voiceAssistant = null
        super.onDestroy()
    }

    private companion object {
        const val ANSWERS_CHANNEL = "nl.vdzon.wind/answers"
        const val ASSISTANT_CHANNEL = "nl.vdzon.wind/assistant"
        const val ASSISTANT_STATUS_CHANNEL = "nl.vdzon.wind/assistant_status"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        const val RECORD_AUDIO_REQUEST_CODE = 1002
    }
}
