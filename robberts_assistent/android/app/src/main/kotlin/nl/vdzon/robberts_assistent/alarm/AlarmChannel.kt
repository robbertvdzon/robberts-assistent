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
                "scheduleNap" -> {
                    val at = (call.argument<Number>("triggerAtMillis"))?.toLong()
                    if (at == null) {
                        result.error("bad-args", "triggerAtMillis ontbreekt", null)
                    } else {
                        AlarmScheduling.scheduleNap(context.applicationContext, at)
                        result.success(null)
                    }
                }
                "cancelNap" -> {
                    AlarmScheduling.cancelNap(context.applicationContext)
                    result.success(null)
                }
                "takeNapDismissedAt" -> result.success(AlarmScheduling.takeNapDismissedAt(context.applicationContext))
                else -> result.notImplemented()
            }
        }
    }
}
