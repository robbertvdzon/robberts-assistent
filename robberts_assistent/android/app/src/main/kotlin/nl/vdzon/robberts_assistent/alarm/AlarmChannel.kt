package nl.vdzon.robberts_assistent.alarm

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel-brug tussen de Flutter-laag (`AlarmScheduler`) en de native alarm-planning.
 * Wordt geregistreerd vanuit [nl.vdzon.robberts_assistent.MainActivity].
 */
object AlarmChannel {
    private const val CHANNEL = "nl.vdzon.robberts_assistent/alarm"

    fun register(context: Context, messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "scheduleAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val raw = call.argument<List<Map<String, Any>>>("alarms") ?: emptyList()
                    val entries = raw.map {
                        AlarmEntry(
                            id = (it["id"] as Number).toInt(),
                            message = it["message"] as String,
                            triggerAtMillis = (it["triggerAtMillis"] as Number).toLong(),
                        )
                    }
                    AlarmScheduling.scheduleAll(context.applicationContext, entries)
                    result.success(null)
                }
                "cancelAll" -> {
                    AlarmScheduling.cancelAll(context.applicationContext)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
