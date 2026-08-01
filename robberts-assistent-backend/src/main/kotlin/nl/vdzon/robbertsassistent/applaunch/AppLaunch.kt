package nl.vdzon.robbertsassistent.applaunch

import java.time.Instant

/**
 * Waar een app-start vandaan kwam. De app bepaalt dit zelf (Android: op basis van de
 * referrer van de startende app); een onbekende of ontbrekende waarde wordt [UNKNOWN].
 */
enum class AppLaunchSource { ASSISTANT, LAUNCHER, OTHER, UNKNOWN }

/**
 * Eén gelogde app-start. [id] en [at] worden door de server bepaald (de clientklok wordt niet
 * vertrouwd); de overige velden komen van de app en zijn bewust ruw — nog niet zeker is wat
 * Google Assistent/Gemini precies meestuurt, en juist die ruwe gegevens maken de herkenning
 * later scherper.
 */
data class AppLaunch(
    val id: String,
    val at: Instant,
    val source: AppLaunchSource,
    val platform: String,
    val referrer: String? = null,
    val action: String? = null,
    val categories: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    val appVersion: String? = null,
)
