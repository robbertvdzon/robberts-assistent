package nl.vdzon.robbertsassistent.insights

import java.time.LocalDate
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.briefing.BriefingSection
import nl.vdzon.robbertsassistent.briefing.BriefingSectionProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class InsightsDto(val windowDays: Long, val insights: List<Insight>)

@RestController
class InsightsController(
    private val authService: AuthService,
    private val insightService: InsightService,
) {
    /** Zeven dagen tot en met `end` (standaard vandaag, Europe/Amsterdam). */
    @GetMapping("/api/v1/insights/week")
    fun week(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("end", required = false) end: String?,
    ): WeekOverview {
        authService.requireAuthorization(authorization)
        val date = runCatching { end?.let(LocalDate::parse) ?: LocalDate.now(InsightService.ZONE) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige datum") }
        return insightService.week(date)
    }

    @GetMapping("/api/v1/insights")
    fun insights(@RequestHeader("Authorization", required = false) authorization: String?): InsightsDto {
        authService.requireAuthorization(authorization)
        return InsightsDto(InsightService.WINDOW_DAYS, insightService.insights())
    }
}

/** Eén regel "Eten en bewegen" in de dagelijkse briefing (Vandaag-tab). */
@Component
class NutritionSectionProvider(private val insightService: InsightService) : BriefingSectionProvider {
    override val order = 18

    override fun section(): BriefingSection = BriefingSection(
        key = "eten-bewegen",
        title = "Eten en bewegen",
        text = runCatching { insightService.todayLine() }.getOrNull() ?: "Vandaag nog niets vastgelegd.",
    )
}
