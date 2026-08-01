package nl.vdzon.robbertsassistent.applaunch

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * De app stuurt hier bij elke start de ruwe launch-gegevens naartoe. Een onbekende of ontbrekende
 * `source` is bewust géén fout (400): juist bij een nog onbekende bron wil je de logregel hebben.
 */
data class AppLaunchRequest(
    val source: String? = null,
    val platform: String? = null,
    val referrer: String? = null,
    val action: String? = null,
    val categories: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    val appVersion: String? = null,
)

data class AppLaunchResponse(
    val id: String,
    val at: Instant,
    val source: AppLaunchSource,
    val platform: String,
    val referrer: String?,
    val action: String?,
    val categories: List<String>,
    val extras: Map<String, String>,
    val appVersion: String?,
)

data class AppLaunchesResponse(val launches: List<AppLaunchResponse>)

@RestController
class AppLaunchController(
    private val authService: AuthService,
    private val appLaunchService: AppLaunchService,
) {
    @PostMapping("/api/v1/app-launches")
    fun record(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: AppLaunchRequest,
    ): AppLaunchResponse {
        authService.requireAuthorization(authorization)
        return appLaunchService.record(
            source = parseSource(request.source),
            platform = request.platform?.takeIf { it.isNotBlank() } ?: "onbekend",
            referrer = request.referrer,
            action = request.action,
            categories = request.categories,
            extras = request.extras,
            appVersion = request.appVersion,
        ).toResponse()
    }

    @GetMapping("/api/v1/app-launches")
    fun list(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam(required = false) limit: Int?,
    ): AppLaunchesResponse {
        authService.requireAuthorization(authorization)
        val launches = appLaunchService.recent(limit ?: AppLaunchService.DEFAULT_LIMIT)
        return AppLaunchesResponse(launches.map { it.toResponse() })
    }

    private fun parseSource(raw: String?): AppLaunchSource =
        runCatching { AppLaunchSource.valueOf(raw.orEmpty().trim().uppercase()) }
            .getOrDefault(AppLaunchSource.UNKNOWN)
}

private fun AppLaunch.toResponse() = AppLaunchResponse(
    id = id,
    at = at,
    source = source,
    platform = platform,
    referrer = referrer,
    action = action,
    categories = categories,
    extras = extras,
    appVersion = appVersion,
)
