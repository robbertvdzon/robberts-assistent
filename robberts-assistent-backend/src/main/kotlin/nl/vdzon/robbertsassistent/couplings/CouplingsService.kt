package nl.vdzon.robbertsassistent.couplings

import com.google.firebase.messaging.Message
import nl.vdzon.robbertsassistent.airquality.AirQualityClient
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.news.NewsClient
import nl.vdzon.robbertsassistent.push.FcmTokenStore
import nl.vdzon.robbertsassistent.tides.TideClient
import nl.vdzon.robbertsassistent.waste.WasteClient
import nl.vdzon.robbertsassistent.weather.WeatherClient
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Rapporteert de status van alle externe koppelingen en kan ze live testen — voedt het
 * "Koppelingen"-scherm in de app.
 *
 * [statuses] leest alleen de secrets (of iets geconfigureerd is en of de echte impl of de fallback
 * actief is) en doet géén netwerk-calls. [testAll] draait per koppeling een lichte, niet-destructieve
 * live-test (parallel), zodat je kunt zien of een koppeling nog werkt.
 */
@Service
class CouplingsService(
    private val secrets: AppSecrets,
    private val firebase: FirebaseProvider,
    private val calendarClient: CalendarClient,
    private val tokenStore: FcmTokenStore,
    private val weatherClient: WeatherClient,
    private val tideClient: TideClient,
    private val airQualityClient: AirQualityClient,
    private val newsClient: NewsClient,
    private val wasteClient: WasteClient,
) {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    private val googleOAuthConfigured: Boolean
        get() = !secrets.googleOAuthClientId.isNullOrBlank() &&
            !secrets.googleOAuthClientSecret.isNullOrBlank() &&
            !secrets.googleOAuthRefreshToken.isNullOrBlank()

    private val telegramConfigured: Boolean
        get() = !secrets.telegramBotToken.isNullOrBlank() && !secrets.telegramChatId.isNullOrBlank()

    private val storageConfigured: Boolean
        get() = secrets.firebaseConfigured && !secrets.firebaseStorageBucket.isNullOrBlank()

    /** Statuslijst zonder live-test (snel, geen netwerk). */
    fun statuses(): List<CouplingStatus> = listOf(
        CouplingStatus(
            id = "openai",
            name = "OpenAI (chat + vision)",
            description = "De AI-assistent en de moestuin-foto-chat.",
            configured = !secrets.openAiApiKey.isNullOrBlank(),
            mode = if (secrets.effectiveMockAi) "fallback" else "echt",
        ),
        CouplingStatus(
            id = "telegram",
            name = "Telegram",
            description = "Uitgaande meldingen naar je Telegram.",
            configured = telegramConfigured,
            mode = if (telegramConfigured) "echt" else "fallback",
        ),
        CouplingStatus(
            id = "firestore",
            name = "Firestore",
            description = "Notities, reminders, alarms, chat-historie, FCM-tokens.",
            configured = secrets.firebaseConfigured,
            mode = if (secrets.firebaseConfigured) "echt" else "fallback",
        ),
        CouplingStatus(
            id = "storage",
            name = "Firebase Storage",
            description = "Opslag van de moestuin-foto's.",
            configured = storageConfigured,
            mode = if (storageConfigured) "echt" else "fallback",
        ),
        CouplingStatus(
            id = "google",
            name = "Google Agenda + Docs",
            description = "Read-only agenda en documenten (OAuth refresh-token).",
            configured = googleOAuthConfigured,
            mode = if (googleOAuthConfigured) "echt" else "fallback",
        ),
        CouplingStatus(
            id = "fcm",
            name = "FCM push",
            description = "Push-notificaties naar je telefoon.",
            configured = secrets.firebaseConfigured,
            mode = if (secrets.firebaseConfigured) "echt" else "fallback",
        ),
        // Keyless koppelingen: geen secret nodig, dus altijd geconfigureerd + echt.
        CouplingStatus(
            id = "weather",
            name = "Weer/regen",
            description = "Regen-/weersvoorspelling bij de moestuin (Open-Meteo).",
            configured = true,
            mode = "echt",
        ),
        CouplingStatus(
            id = "tides",
            name = "Getijden",
            description = "Hoog-/laagwater en waterhoogte bij IJmuiden (Rijkswaterstaat).",
            configured = true,
            mode = "echt",
        ),
        CouplingStatus(
            id = "airquality",
            name = "Luchtkwaliteit/UV/pollen",
            description = "Luchtkwaliteitsindex, UV-index en pollen bij de moestuin (Open-Meteo).",
            configured = true,
            mode = "echt",
        ),
        CouplingStatus(
            id = "news",
            name = "Nieuws",
            description = "Laatste nieuwskoppen (NOS, RSS).",
            configured = true,
            mode = "echt",
        ),
        CouplingStatus(
            id = "waste",
            name = "Afvalkalender",
            description = "Afvalophaaldata voor Robberts huisadres (HVC Groep).",
            configured = true,
            mode = "echt",
        ),
    )

    /** Statuslijst mét live-test per koppeling; tests draaien parallel. */
    fun testAll(): List<CouplingStatus> =
        statuses().parallelStream()
            .map { it.copy(test = probe(it.id)) }
            .toList()

    private fun probe(id: String): TestResult = timed {
        when (id) {
            "openai" -> testOpenAi()
            "telegram" -> testTelegram()
            "firestore" -> testFirestore()
            "storage" -> testStorage()
            "google" -> testGoogle()
            "fcm" -> testFcm()
            "weather" -> testWeather()
            "tides" -> testTides()
            "airquality" -> testAirQuality()
            "news" -> testNews()
            "waste" -> testWaste()
            else -> false to "onbekende koppeling"
        }
    }

    private fun testOpenAi(): Pair<Boolean, String> {
        val key = secrets.openAiApiKey
        if (key.isNullOrBlank()) return false to "geen API-key (mock actief)"
        val request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
            .header("Authorization", "Bearer $key")
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return if (response.statusCode() in 200..299) true to "API-key geldig" else false to "HTTP ${response.statusCode()}"
    }

    private fun testTelegram(): Pair<Boolean, String> {
        val token = secrets.telegramBotToken
        if (token.isNullOrBlank()) return false to "geen bot-token"
        val request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot$token/getMe"))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return if (response.statusCode() in 200..299) true to "bot bereikbaar" else false to "HTTP ${response.statusCode()}"
    }

    private fun testFirestore(): Pair<Boolean, String> {
        if (!secrets.firebaseConfigured) return false to "niet geconfigureerd (in-memory)"
        val snapshot = firebase.firestore().collection("notes").document("note").get().get(8, TimeUnit.SECONDS)
        return true to if (snapshot.exists()) "gelezen (notitie aanwezig)" else "bereikbaar (geen notitie)"
    }

    private fun testStorage(): Pair<Boolean, String> {
        if (!storageConfigured) return false to "niet geconfigureerd"
        val bucket = firebase.bucket()
        return true to "bucket ${bucket.name} bereikbaar"
    }

    private fun testGoogle(): Pair<Boolean, String> {
        if (!googleOAuthConfigured) return false to "niet geconfigureerd (stub)"
        val events = calendarClient.upcoming(1)
        return true to "agenda gelezen (${events.size} afspraak vooruit)"
    }

    private fun testFcm(): Pair<Boolean, String> {
        if (!secrets.firebaseConfigured) return false to "niet geconfigureerd"
        val tokens = tokenStore.all()
        if (tokens.isEmpty()) return true to "Firebase bereikbaar; geen toestel geregistreerd"
        // Dry-run: valideert token + pad zonder een echte push af te leveren.
        firebase.messaging().send(Message.builder().setToken(tokens.first()).putData("ping", "1").build(), true)
        return true to "push-pad gevalideerd (dry-run, ${tokens.size} toestel)"
    }

    private fun testWeather(): Pair<Boolean, String> {
        val forecast = weatherClient.hourlyForecast(1)
        return forecast.error?.let { false to it } ?: (true to "voorspelling opgehaald (${forecast.hours.size} uur)")
    }

    private fun testTides(): Pair<Boolean, String> {
        val forecast = tideClient.forecast(1)
        return forecast.error?.let { false to it } ?: (true to "getijdata opgehaald (${forecast.levels.size} punt(en))")
    }

    private fun testAirQuality(): Pair<Boolean, String> {
        val forecast = airQualityClient.hourlyForecast(1)
        return forecast.error?.let { false to it } ?: (true to "luchtkwaliteitsdata opgehaald (${forecast.hours.size} uur)")
    }

    private fun testNews(): Pair<Boolean, String> {
        val feed = newsClient.latestHeadlines(1)
        return feed.error?.let { false to it } ?: (true to "${feed.items.size} nieuwsitem(s) opgehaald")
    }

    private fun testWaste(): Pair<Boolean, String> {
        val schedule = wasteClient.upcomingPickups()
        return schedule.error?.let { false to it } ?: (true to "${schedule.pickups.size} ophaalmoment(en) gevonden")
    }

    private inline fun timed(block: () -> Pair<Boolean, String>): TestResult {
        val start = System.nanoTime()
        val (ok, detail) = try {
            block()
        } catch (e: Exception) {
            false to (e.message ?: e.javaClass.simpleName)
        }
        return TestResult(ok, detail, (System.nanoTime() - start) / 1_000_000)
    }
}
