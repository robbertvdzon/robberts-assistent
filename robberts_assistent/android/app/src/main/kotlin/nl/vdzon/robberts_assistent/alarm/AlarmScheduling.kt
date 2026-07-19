package nl.vdzon.robberts_assistent.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import nl.vdzon.robberts_assistent.MainActivity
import org.json.JSONArray
import org.json.JSONObject

/** Eén ingepland alarm-voorkomen. */
data class AlarmEntry(val id: Int, val message: String, val triggerAtMillis: Long)

/**
 * Plant alarms in via [AlarmManager.setAlarmClock] — altijd exact (ook in Doze) en zónder
 * SCHEDULE_EXACT_ALARM-permissie. Het afgaan gaat via een broadcast naar [AlarmReceiver].
 *
 * De ingeplande set wordt in SharedPreferences bewaard, zodat [BootReceiver] alles na een reboot
 * opnieuw kan inplannen (AlarmManager-planningen overleven een herstart niet).
 */
object AlarmScheduling {

    const val ACTION_FIRE = "nl.vdzon.robberts_assistent.ALARM_FIRE"
    const val ACTION_DISMISS = "nl.vdzon.robberts_assistent.ALARM_DISMISS"
    const val ACTION_SNOOZE = "nl.vdzon.robberts_assistent.ALARM_SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_MESSAGE = "message"

    const val SNOOZE_MINUTES = 5

    private const val PREFS = "ra_alarms"
    private const val KEY_SCHEDULED = "scheduled"

    /** Wist alle vorige planningen en plant de meegegeven (toekomstige) voorkomens opnieuw in. */
    fun scheduleAll(context: Context, entries: List<AlarmEntry>) {
        readStore(context).forEach { cancel(context, it.id) }
        val now = System.currentTimeMillis()
        val future = entries.filter { it.triggerAtMillis > now }
        future.forEach { scheduleOne(context, it) }
        writeStore(context, future)
    }

    /** Wist alle planningen (gebruikt als er geen actieve alarms zijn). */
    fun cancelAll(context: Context) {
        readStore(context).forEach { cancel(context, it.id) }
        writeStore(context, emptyList())
    }

    /** Plant de persistente set opnieuw in na een reboot; verlopen voorkomens worden overgeslagen. */
    fun rescheduleFromStorage(context: Context) {
        val now = System.currentTimeMillis()
        val future = readStore(context).filter { it.triggerAtMillis > now }
        future.forEach { scheduleOne(context, it) }
        writeStore(context, future)
    }

    /** Snoozt: plant hetzelfde alarm [SNOOZE_MINUTES] minuten later opnieuw in. */
    fun snooze(context: Context, id: Int, message: String) {
        val entry = AlarmEntry(id, message, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
        scheduleOne(context, entry)
        upsertStore(context, entry)
    }

    /** Markeert een afgegaan alarm als verwerkt (uit de persistente set). */
    fun markFired(context: Context, id: Int) = removeFromStore(context, id)

    private fun scheduleOne(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fire = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, entry.id)
            putExtra(EXTRA_MESSAGE, entry.message)
        }
        val operation = PendingIntent.getBroadcast(context, entry.id, fire, piFlags())
        // showIntent: opent de app als de gebruiker op het wekker-icoon in de statusbalk tikt.
        val show = PendingIntent.getActivity(
            context, entry.id,
            Intent(context, MainActivity::class.java),
            piFlags(),
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(entry.triggerAtMillis, show), operation)
    }

    private fun cancel(context: Context, id: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fire = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_FIRE }
        val operation = PendingIntent.getBroadcast(context, id, fire, piFlags())
        am.cancel(operation)
    }

    private fun piFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // --- persistentie (SharedPreferences als JSON-array) ---

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readStore(context: Context): List<AlarmEntry> {
        val raw = prefs(context).getString(KEY_SCHEDULED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AlarmEntry(o.getInt("id"), o.getString("message"), o.getLong("triggerAtMillis"))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeStore(context: Context, entries: List<AlarmEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("message", it.message)
                    .put("triggerAtMillis", it.triggerAtMillis),
            )
        }
        prefs(context).edit().putString(KEY_SCHEDULED, arr.toString()).apply()
    }

    private fun upsertStore(context: Context, entry: AlarmEntry) {
        val next = readStore(context).filter { it.id != entry.id } + entry
        writeStore(context, next)
    }

    private fun removeFromStore(context: Context, id: Int) {
        writeStore(context, readStore(context).filter { it.id != id })
    }
}
