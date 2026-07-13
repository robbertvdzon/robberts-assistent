package nl.vdzon.wind

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stille achtergrondcheck of er een nieuwe Wind-versie is, met een notificatie i.p.v. een
 * blokkerend schermpje — [AnswerTrampolineActivity] moet instant blijven ("Hey Google, open
 * Wind" mag niet ineens een dialoogje tonen). Tikken op de notificatie opent de GitHub-
 * release-pagina in de browser; Wind heeft (bewust, geen extra permissie-machinerie in deze
 * headless app) geen eigen download+installeer-flow zoals robberts_assistent/notities.
 *
 * Gethrottled (1x per 12 uur) zodat niet elke trampoline-launch een GitHub-API-call doet.
 */
object WindUpdateChecker {
    private const val REPO = "robbertvdzon/robberts-assistent"
    private const val TAG = "wind-latest"
    private const val PACKAGE_NAME = "nl.vdzon.wind"
    private const val PREFS_NAME = "wind_update_checker"
    private const val KEY_LAST_CHECK_MILLIS = "last_check_millis"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    private const val CHECK_INTERVAL_MILLIS = 12 * 60 * 60 * 1000L

    /** Fire-and-forget vanuit een achtergrond-coroutine/-thread — doet zelf geen UI-werk. */
    fun checkAndNotifyIfDue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MILLIS, 0L)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MILLIS) return
        prefs.edit().putLong(KEY_LAST_CHECK_MILLIS, System.currentTimeMillis()).apply()

        val installed = installedVersionCode(context)
        val release = runCatching { fetchLatestRelease() }.getOrNull() ?: return
        if (release.buildNumber <= installed) return
        // Meld dezelfde nieuwe versie niet elke 12 uur opnieuw als de gebruiker 'm negeert.
        if (prefs.getLong(KEY_LAST_NOTIFIED_VERSION, -1L) == release.buildNumber) return
        prefs.edit().putLong(KEY_LAST_NOTIFIED_VERSION, release.buildNumber).apply()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationHelper.postUpdateAvailable(
            context,
            title = "Nieuwe Wind-versie beschikbaar",
            text = "Tik om build ${release.buildNumber} te downloaden (je hebt build $installed).",
            pendingIntent = pendingIntent,
        )
    }

    private fun installedVersionCode(context: Context): Long =
        runCatching {
            val info = context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        }.getOrDefault(-1L)

    private data class Release(val htmlUrl: String, val buildNumber: Long)

    private fun fetchLatestRelease(): Release? {
        val connection = URL("https://api.github.com/repos/$REPO/releases/tags/$TAG").openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val buildNumber = Regex("build (\\d+)").find(json.optString("body", ""))
                ?.groupValues?.get(1)?.toLongOrNull() ?: return null
            Release(json.optString("html_url", "https://github.com/$REPO/releases/tag/$TAG"), buildNumber)
        } finally {
            connection.disconnect()
        }
    }
}
