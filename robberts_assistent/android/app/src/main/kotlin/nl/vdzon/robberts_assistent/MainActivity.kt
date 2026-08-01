package nl.vdzon.robberts_assistent

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread
import nl.vdzon.robberts_assistent.alarm.AlarmChannel

/**
 * Registreert het update-MethodChannel voor de "update alle drie de apps"-knop (zie [UpdateInstaller]),
 * het alarm-MethodChannel voor de native wekker (zie [AlarmChannel]) en het launch-MethodChannel dat
 * Flutter vertelt waar de app-start vandaan kwam (zie [LaunchSource]).
 */
class MainActivity : FlutterActivity() {
    private val installer by lazy { UpdateInstaller(applicationContext) }

    /** Laatst bepaalde launch; Flutter haalt 'm op via `launchInfo` (dekt de koude start). */
    private var lastLaunch: LaunchInfo? = null
    private var launchChannel: MethodChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Vóór super.onCreate: die zet de Flutter-engine op, waarna Dart de launch al kan opvragen.
        lastLaunch = LaunchSource.from(this, intent)
        super.onCreate(savedInstanceState)
    }

    /** De activity is `singleTop`, dus een tweede start komt hier binnen i.p.v. in [onCreate]. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val launch = LaunchSource.from(this, intent)
        lastLaunch = launch
        launchChannel?.invokeMethod("launchInfo", launch.toMap())
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        AlarmChannel.register(applicationContext, flutterEngine.dartExecutor.binaryMessenger)
        launchChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LAUNCH_CHANNEL).apply {
            setMethodCallHandler { call, result ->
                when (call.method) {
                    "launchInfo" -> result.success(lastLaunch?.toMap())
                    else -> result.notImplemented()
                }
            }
        }
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
        const val LAUNCH_CHANNEL = "nl.vdzon.robberts_assistent/launch"
    }
}
