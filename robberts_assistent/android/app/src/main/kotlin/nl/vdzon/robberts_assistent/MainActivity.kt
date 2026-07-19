package nl.vdzon.robberts_assistent

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread
import nl.vdzon.robberts_assistent.alarm.AlarmChannel

/**
 * Registreert het update-MethodChannel voor de "update alle drie de apps"-knop (zie [UpdateInstaller])
 * en het alarm-MethodChannel voor de native wekker (zie [AlarmChannel]).
 */
class MainActivity : FlutterActivity() {
    private val installer by lazy { UpdateInstaller(applicationContext) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        AlarmChannel.register(applicationContext, flutterEngine.dartExecutor.binaryMessenger)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "installedVersionCode" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(packageName?.let { installer.installedVersionCode(it) } ?: -1L)
                }
                "canInstallPackages" -> result.success(installer.canInstallPackages())
                "requestInstallPermission" -> {
                    installer.requestInstallPermission()
                    result.success(null)
                }
                "downloadAndInstall" -> {
                    val url = call.argument<String>("url")
                    val fileName = call.argument<String>("fileName")
                    if (url == null || fileName == null) {
                        result.error("bad-args", "url/fileName ontbreken", null)
                        return@setMethodCallHandler
                    }
                    // Blokkeert de main thread niet: netwerk-IO in downloadAndInstall.
                    thread {
                        runCatching { installer.downloadAndInstall(url, fileName) }
                            .onSuccess { runOnUiThread { result.success(null) } }
                            .onFailure { e -> runOnUiThread { result.error("download-failed", e.message, null) } }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private companion object {
        const val CHANNEL = "nl.vdzon.robberts_assistent/updater"
    }
}
