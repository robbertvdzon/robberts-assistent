package nl.vdzon.wind

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Host-activity voor het handmatig te openen Flutter-scherm. Los van de
 * trampoline-activities die de spraak-/notificatie-flow afhandelen.
 *
 * Stelt een MethodChannel beschikbaar zodat de Flutter-knoppen dezelfde
 * trampoline-activities kunnen starten als de (toekomstige) "Hey Google"-flow
 * — zo test je met de hand exact hetzelfde pad (TTS + notificatie).
 */
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
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
    }

    private companion object {
        const val CHANNEL = "nl.vdzon.wind/answers"
    }
}
