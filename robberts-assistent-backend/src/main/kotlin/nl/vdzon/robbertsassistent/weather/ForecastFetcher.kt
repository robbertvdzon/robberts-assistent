package nl.vdzon.robbertsassistent.weather

import org.slf4j.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Gedeelde ophaalstrategie voor de twee Open-Meteo-clients ([OpenMeteoWeatherClient],
 * [OpenMeteoWindForecastClient]). Beide halen één vaste URL op en kappen het resultaat client-side
 * af op `hours`, dus kan de volledige, ruwe respons hier gecachet en hergebruikt worden:
 *
 * 1. **TTL-cache** ([ttl], standaard 10 minuten): een tweede aanroep binnen de TTL doet geen
 *    HTTP-call. Zo veroorzaakt één briefing-opbouw (weerkaart + kiten + strandfietsen) nog maar
 *    één call per URL i.p.v. drie. Thread-veilig via double-checked locking (zelfde stijl als de
 *    basiskaart-cache in `briefing.OsmCoastMapImageBuilder`), zodat de uurlijkse scheduler en de
 *    reload-knop elkaar niet dubbel laten ophalen.
 * 2. **Retry** ([MAX_ATTEMPTS] pogingen met [retryDelaysMs] pauze ertussen) bij een netwerk-/
 *    IO-fout, HTTP 5xx en HTTP 429 — precies het geval waar Open-Meteo's incidentele 503 onder
 *    valt. Bij overige 4xx wordt direct gestopt (opnieuw proberen helpt daar niet).
 * 3. **Last-known-good**: falen alle pogingen, dan wordt de laatst succesvol opgehaalde respons
 *    teruggegeven — mits die niet ouder is dan [maxStaleAge] — met een verouderd-markering, zodat
 *    de briefing bruikbare (zij het oudere) data toont i.p.v. een foutmelding.
 *
 * De cache bewaart de ruwe respons-body en niet de geparste voorspelling, zodat het "vanaf nu"-
 * filter in de parse-stap ook bij een cachehit tegen de actuele tijd gebeurt.
 *
 * [now] en [sleeper] zijn injecteerbaar zodat TTL, de 12-uursgrens en de retry-pauzes in tests
 * bestuurbaar zijn zonder echte wachttijd (geen `Clock`-bean in de module).
 */
internal class ForecastFetcher(
    private val httpClient: HttpClient,
    private val url: String,
    private val logger: Logger,
    private val statusError: (Int) -> String,
    private val exceptionError: (Throwable) -> String,
    private val now: () -> Instant = { Instant.now() },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val ttl: Duration = DEFAULT_TTL,
    private val maxStaleAge: Duration = DEFAULT_MAX_STALE_AGE,
) {

    /** Uitkomst van een ophaalpoging: een bruikbare body (vers of verouderd), of een foutmelding. */
    sealed interface Result {
        data class Body(val body: String, val fetchedAt: Instant, val stale: Boolean) : Result
        data class Failure(val message: String) : Result
    }

    private data class Cached(val body: String, val fetchedAt: Instant)

    @Volatile
    private var cached: Cached? = null
    private val lock = Any()

    fun fetch(): Result {
        freshFromCache(now())?.let { return it }
        synchronized(lock) {
            // Tweede check binnen de lock: een parallelle aanroep kan de cache net gevuld hebben.
            freshFromCache(now())?.let { return it }

            var lastError: String? = null
            for (attempt in 0 until MAX_ATTEMPTS) {
                if (attempt > 0) sleeper(retryDelaysMs[(attempt - 1).coerceAtMost(retryDelaysMs.lastIndex)])
                when (val attempted = attemptFetch()) {
                    is Attempt.Success -> {
                        val fetchedAt = now()
                        cached = Cached(attempted.body, fetchedAt)
                        return Result.Body(attempted.body, fetchedAt, stale = false)
                    }
                    is Attempt.Retryable -> lastError = attempted.message
                    is Attempt.Fatal -> {
                        lastError = attempted.message
                        break
                    }
                }
            }

            val message = lastError ?: statusError(0)
            logger.warn("Ophalen van {} definitief mislukt: {}", url, message)

            val fallback = cached
            if (fallback != null && Duration.between(fallback.fetchedAt, now()) <= maxStaleAge) {
                return Result.Body(fallback.body, fallback.fetchedAt, stale = true)
            }
            return Result.Failure(message)
        }
    }

    private fun freshFromCache(at: Instant): Result.Body? {
        val snapshot = cached ?: return null
        if (Duration.between(snapshot.fetchedAt, at) >= ttl) return null
        return Result.Body(snapshot.body, snapshot.fetchedAt, stale = false)
    }

    private sealed interface Attempt {
        data class Success(val body: String) : Attempt
        data class Retryable(val message: String) : Attempt
        data class Fatal(val message: String) : Attempt
    }

    private fun attemptFetch(): Attempt =
        try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val status = response.statusCode()
            when {
                status in 200..299 -> Attempt.Success(response.body())
                status >= 500 || status == 429 -> Attempt.Retryable(statusError(status))
                else -> Attempt.Fatal(statusError(status))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Attempt.Fatal(exceptionError(e))
        } catch (e: Exception) {
            Attempt.Retryable(exceptionError(e))
        }

    internal companion object {
        /** Eén eerste poging plus twee retries. */
        const val MAX_ATTEMPTS = 3
        val DEFAULT_RETRY_DELAYS_MS = listOf(500L, 2_000L)
        val DEFAULT_TTL: Duration = Duration.ofMinutes(10)
        val DEFAULT_MAX_STALE_AGE: Duration = Duration.ofHours(12)
    }
}
