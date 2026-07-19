package nl.vdzon.robberts_assistent.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Ontvangt het AlarmManager-broadcast als een alarm afgaat en start [AlarmService] (foreground),
 * die het geluid laat afgaan en de full-screen [AlarmActivity] toont. Een door een exact alarm
 * getriggerde receiver mag een foreground-service starten, ook vanuit de achtergrond.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduling.ACTION_FIRE) return

        val id = intent.getIntExtra(AlarmScheduling.EXTRA_ID, 0)
        val message = intent.getStringExtra(AlarmScheduling.EXTRA_MESSAGE) ?: "Alarm"
        AlarmScheduling.markFired(context, id)

        val service = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmScheduling.EXTRA_ID, id)
            putExtra(AlarmScheduling.EXTRA_MESSAGE, message)
        }
        ContextCompat.startForegroundService(context, service)
    }
}
