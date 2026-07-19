package nl.vdzon.robberts_assistent.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Plant de opgeslagen alarms opnieuw in na een reboot of app-update (AlarmManager-planningen
 * overleven een herstart niet).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> AlarmScheduling.rescheduleFromStorage(context)
        }
    }
}
