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
import io.flutter.plugin.common.MethodChannel

/**
 * Host-activity voor het handmatig te openen Flutter-scherm. Los van de
 * trampoline-activities die de spraak-/notificatie-flow afhandelen.
 *
 * Stelt een MethodChannel beschikbaar zodat de Flutter-knoppen dezelfde
 * trampoline-activities kunnen starten als de (toekomstige) "Hey Google"-flow
 * — zo test je met de hand exact hetzelfde pad (TTS + notificatie).
 *
 * `FlutterActivity` erft van `android.app.Activity`, niet van AndroidX's
 * `ComponentActivity` — dus gebruiken we hier de klassieke
 * `ActivityCompat.requestPermissions`-route i.p.v. `registerForActivityResult`.
 */
class MainActivity : FlutterActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ (Tiramisu) vereist een runtime-toestemming om
        // notificaties te posten. Zonder dit verzoek blijft de permissie
        // geweigerd en slaat AnswerTrampolineActivity het posten stil over —
        // TTS werkt dan wel, maar er komt nooit een notificatie.
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
    }

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
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
