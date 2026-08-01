package nl.vdzon.robberts_assistent

import android.app.Activity
import android.content.Intent

/** Waar een app-start vandaan kwam, afgeleid uit het referrer-package van de startende app. */
enum class LaunchSourceType { ASSISTANT, LAUNCHER, OTHER, UNKNOWN }

/**
 * Ruwe gegevens van één app-start. Bewust ruw: nog niet zeker is wát Google Assistent/Gemini
 * meestuurt, dus alles gaat naar de backend-log zodat [LaunchSource.classify] later scherper
 * gezet kan worden.
 */
data class LaunchInfo(
    val source: LaunchSourceType,
    val referrer: String?,
    val action: String?,
    val categories: List<String>,
    val extras: Map<String, String>,
) {
    /** Map-representatie voor het MethodChannel naar Flutter. */
    fun toMap(): Map<String, Any?> = mapOf(
        "source" to source.name,
        "referrer" to referrer,
        "action" to action,
        "categories" to categories,
        "extras" to extras,
    )
}

object LaunchSource {
    /**
     * Packages die we als "gestart vanuit Google Assistent/Gemini" beschouwen. Bewust een losse,
     * uitbreidbare lijst: zodra de echte logs (`grep APP_LAUNCH`) laten zien welk package Gemini
     * op Robberts toestel gebruikt, wordt die hier toegevoegd.
     */
    val ASSISTANT_PACKAGES = setOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.googleassistant",
        "com.google.android.apps.bard",
        "com.google.android.apps.gemini",
    )

    /** Bekende launchers; alles wat op `.launcher` eindigt telt daarnaast ook als launcher. */
    val LAUNCHER_PACKAGES = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
    )

    private const val LAUNCHER_SUFFIX = ".launcher"

    /** Maximaal aantal extras dat we meesturen; voorkomt een onwerkbaar lange logregel. */
    const val MAX_EXTRAS = 50

    /** Maximale lengte per extra-waarde. */
    const val MAX_EXTRA_VALUE_LENGTH = 200

    /**
     * Pure classificatie op basis van het referrer-package: geen Android-classes, dus los testbaar
     * in een gewone JVM-unittest.
     */
    fun classify(referrer: String?): LaunchSourceType {
        val packageName = referrer?.trim().orEmpty()
        if (packageName.isEmpty()) return LaunchSourceType.UNKNOWN
        if (packageName in ASSISTANT_PACKAGES) return LaunchSourceType.ASSISTANT
        if (packageName in LAUNCHER_PACKAGES || packageName.endsWith(LAUNCHER_SUFFIX)) {
            return LaunchSourceType.LAUNCHER
        }
        return LaunchSourceType.OTHER
    }

    /**
     * Bouwt de volledige [LaunchInfo] uit de activity (referrer) en het intent (action/categories/
     * extras). Alles is defensief: een rare of niet-uitleesbare extra mag de app nooit laten
     * crashen, dus elke stap zit in een `runCatching`.
     */
    fun from(activity: Activity, intent: Intent?): LaunchInfo {
        val referrer = runCatching { activity.referrer?.let { it.host ?: it.toString() } }.getOrNull()
        return LaunchInfo(
            source = classify(referrer),
            referrer = referrer,
            action = runCatching { intent?.action }.getOrNull(),
            categories = runCatching { intent?.categories?.toList().orEmpty() }.getOrDefault(emptyList()),
            extras = readExtras(intent),
        )
    }

    private fun readExtras(intent: Intent?): Map<String, String> {
        val bundle = runCatching { intent?.extras }.getOrNull() ?: return emptyMap()
        val keys = runCatching { bundle.keySet().toList() }.getOrDefault(emptyList())
        val extras = LinkedHashMap<String, String>()
        for (key in keys.take(MAX_EXTRAS)) {
            // Per key apart: een enkele onleesbare/onbekende extra mag de rest niet weggooien.
            val value = runCatching { bundle.get(key)?.toString() }.getOrNull() ?: continue
            extras[oneLine(key)] = oneLine(value).take(MAX_EXTRA_VALUE_LENGTH)
        }
        return extras
    }

    private fun oneLine(value: String): String = value.replace('\n', ' ').replace('\r', ' ')
}
