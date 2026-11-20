package nl.vdzon.robbertsassistent.assistant.ai

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.nutrition.Meal
import nl.vdzon.robbertsassistent.nutrition.MealService
import nl.vdzon.robbertsassistent.nutrition.MealStatus
import nl.vdzon.robbertsassistent.wellbeing.CheckInService
import nl.vdzon.robbertsassistent.wellbeing.Nap
import nl.vdzon.robbertsassistent.wellbeing.NapConflictException
import nl.vdzon.robbertsassistent.wellbeing.NapService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Laat de chat-assistent het dagboek bedienen: de tijdlijn van een dag opvragen, dutjes starten,
 * afmelden en achteraf invullen, en eetmomenten verbeteren, verzetten of verwijderen. Elke tool
 * geeft een korte Nederlandse zin terug en gooit nooit een fout naar buiten.
 */
@Component
class DiaryTools(
    private val meals: MealService,
    private val checkIns: CheckInService,
    private val naps: NapService,
    private val activity: ActivityService,
) {
    private val zone = MealService.ZONE

    @Tool(
        description = "De hele dag van Robbert op volgorde van tijd: nachtslaap, wandelingen en trainingen (van zijn " +
            "Garmin-horloge), dutjes, check-ins (honger en energie), eetmomenten en zijn gewicht. Eetmomenten en dutjes " +
            "staan er met een kort id bij, nodig voor de tools die iets aanpassen. Zonder datum vandaag.",
    )
    fun getDayTimeline(
        @ToolParam(description = "Datum als jjjj-mm-dd, of leeg voor vandaag", required = false) date: String?,
    ): String {
        val day = parseDate(date) ?: return INVALID_DATE
        val lines = mutableListOf<Pair<Instant, String>>()
        val a = activity.day(day)
        a?.sleep?.let { lines += it.start to "${hm(it.start)} geslapen tot ${hm(it.end)} (${duration(it.start, it.end)})" }
        a?.workouts.orEmpty().forEach {
            lines += it.start to "${hm(it.start)} ${workoutLabel(it.type)} tot ${hm(it.end)} (${duration(it.start, it.end)}" +
                (it.kcal?.let { kcal -> ", $kcal kcal" } ?: "") + ")"
        }
        naps.day(day).forEach { lines += it.start to "${describe(it)} [id ${it.id.take(8)}]" }
        checkIns.day(day).forEach { c ->
            val parts = listOfNotNull(
                c.hunger?.let { "honger $it (${HUNGER.getOrElse(it) { "?" }})" },
                c.energy?.let { "energie $it" },
                c.note.takeIf { it.isNotBlank() },
            )
            lines += c.at to "${hm(c.at)} check-in: ${parts.joinToString(", ")}"
        }
        meals.day(day).meals.forEach { m ->
            lines += m.eatenAt to "${hm(m.eatenAt)} ${m.title.ifBlank { "eetmoment" }}: ${mealSummary(m)} [id ${m.id.take(8)}]"
        }
        val header = listOfNotNull(
            a?.weightKg?.let { "gewicht $it kg" },
            a?.steps?.let { "$it stappen" },
            a?.totalKcal?.let { "verbruikt $it kcal" },
        ).joinToString(", ")
        if (lines.isEmpty() && header.isEmpty()) return "Op $day staat er niets in het dagboek."
        return buildString {
            appendLine("$day${if (header.isEmpty()) "" else ": $header"}")
            lines.sortedBy { it.first }.forEach { appendLine("- ${it.second}") }
            naps.open()?.takeIf { it.start.atZone(zone).toLocalDate() != day }?.let { appendLine("- nu loopt: ${describe(it)} [id ${it.id.take(8)}]") }
        }.trim()
    }

    @Tool(
        description = "Start een dutje: Robbert gaat nu slapen. Met `alarmMinutes` zet de app een wekker over zoveel " +
            "minuten (de wekker wordt gezet zodra de app het dutje ziet, dus alleen als hij de app open heeft). " +
            "Gebruik dit als hij zegt dat hij een dutje gaat doen.",
    )
    fun startNap(
        @ToolParam(description = "Wekker over zoveel minuten, of leeg voor geen wekker", required = false) alarmMinutes: Int?,
    ): String = guarded {
        if (alarmMinutes != null && alarmMinutes !in 1..600) return@guarded "Een wekker kan tussen 1 en 600 minuten."
        val now = Instant.now()
        val nap = naps.create(start = now, alarmAt = alarmMinutes?.let { now.plus(Duration.ofMinutes(it.toLong())) })
        "Dutje gestart om ${hm(nap.start)}" + (nap.alarmAt?.let { ", wekker om ${hm(it)}" } ?: ", zonder wekker") + ". Welterusten."
    }

    @Tool(
        description = "Meld het lopende dutje af: Robbert is wakker. Geef `minutesAgo` als hij al even wakker is " +
            "('een kwartier geleden'), of `time` als hij een tijdstip noemt; zonder beide is het nu.",
    )
    fun endNap(
        @ToolParam(description = "Hoeveel minuten geleden hij wakker werd, of leeg", required = false) minutesAgo: Int?,
        @ToolParam(description = "Tijdstip van wakker worden als uu:mm, of leeg", required = false) time: String?,
    ): String = guarded {
        val nap = naps.open() ?: return@guarded "Er loopt geen dutje."
        val now = ZonedDateTime.now(zone)
        val end = when {
            !time.isNullOrBlank() -> {
                val clock = parseTime(time) ?: return@guarded INVALID_TIME
                // Het laatste moment met dit kloktijdstip dat niet in de toekomst ligt.
                now.toLocalDate().atTime(clock).atZone(zone).let { if (it.isAfter(now)) it.minusDays(1) else it }.toInstant()
            }
            minutesAgo != null -> now.toInstant().minus(Duration.ofMinutes(minutesAgo.coerceAtLeast(0).toLong()))
            else -> now.toInstant()
        }
        val done = naps.update(nap.id, end = end, clearAlarm = true)
        "Dutje afgemeld: ${hm(done.start)} tot ${hm(end)} (${duration(done.start, end)}). Een wekker die nog stond gaat uit zodra de app opent."
    }

    @Tool(
        description = "Leg een dutje achteraf vast met begin en eind, bv. 'ik heb van kwart over één tot kwart voor " +
            "twee geslapen'. Zonder datum vandaag.",
    )
    fun logNap(
        @ToolParam(description = "Begin als uu:mm") start: String,
        @ToolParam(description = "Eind als uu:mm") end: String,
        @ToolParam(description = "Datum als jjjj-mm-dd, of leeg voor vandaag", required = false) date: String?,
    ): String = guarded {
        val day = parseDate(date) ?: return@guarded INVALID_DATE
        val from = parseTime(start) ?: return@guarded INVALID_TIME
        val to = parseTime(end) ?: return@guarded INVALID_TIME
        val startAt = day.atTime(from).atZone(zone).toInstant()
        // Een dutje over middernacht heen eindigt de volgende dag.
        val endAt = day.atTime(to).atZone(zone).let { if (to > from) it else it.plusDays(1) }.toInstant()
        val nap = naps.create(start = startAt, end = endAt)
        "Vastgelegd: ${describe(nap)} op $day."
    }

    @Tool(description = "Verwijder een dutje. Het id komt uit getDayTimeline. Alleen bij een duidelijk verzoek.")
    fun deleteNap(@ToolParam(description = "Het korte id van het dutje") id: String): String = guarded {
        val now = Instant.now()
        val nap = pick(naps.between(now.minus(LOOKBACK), now.plusSeconds(120)), id) { it.id } ?: return@guarded noMatch("dutje", id)
        naps.delete(nap.id)
        "Verwijderd: ${describe(nap)}."
    }

    @Tool(
        description = "Verbeter een eetmoment in woorden, bv. 'het was volkorenbrood en maar één plak kaas'. Het " +
            "eetdagboek rekent het daarna opnieuw uit. Het id komt uit getDayNutrition of getDayTimeline.",
    )
    fun correctMeal(
        @ToolParam(description = "Het korte id van het eetmoment") id: String,
        @ToolParam(description = "Wat er niet klopt, in Robberts woorden") correction: String,
    ): String = guarded {
        val meal = findMeal(id) ?: return@guarded noMatch("eetmoment", id)
        if (meal.status == MealStatus.ANALYSEREN) return@guarded "Dat eetmoment wordt nog uitgerekend; probeer het zo opnieuw."
        meals.correctByText(meal.id, correction)
        "Doorgegeven. ${meal.title.ifBlank { "Het eetmoment" }} van ${hm(meal.eatenAt)} wordt opnieuw uitgerekend; over een halve minuut staat het bijgewerkt in het dagboek."
    }

    @Tool(
        description = "Zet een eetmoment op een ander tijdstip of een andere dag, bv. als het op het verkeerde moment " +
            "is vastgelegd. Het id komt uit getDayNutrition of getDayTimeline.",
    )
    fun moveMeal(
        @ToolParam(description = "Het korte id van het eetmoment") id: String,
        @ToolParam(description = "Nieuw tijdstip als uu:mm") time: String,
        @ToolParam(description = "Nieuwe datum als jjjj-mm-dd, of leeg om de dag te houden", required = false) date: String?,
    ): String = guarded {
        val meal = findMeal(id) ?: return@guarded noMatch("eetmoment", id)
        val clock = parseTime(time) ?: return@guarded INVALID_TIME
        val day = if (date.isNullOrBlank()) meal.eatenAt.atZone(zone).toLocalDate() else parseDate(date) ?: return@guarded INVALID_DATE
        val moved = meals.move(meal.id, day.atTime(clock).atZone(zone).toInstant())
        "${meal.title.ifBlank { "Het eetmoment" }} staat nu op $day om ${hm(moved.eatenAt)}."
    }

    @Tool(
        description = "Leg vast dat Robbert een eetmoment niet helemaal heeft opgegeten. De gefotografeerde en gewogen " +
            "portie blijft staan (die is nodig om latere foto's mee te vergelijken); de dag telt alleen het opgegeten " +
            "deel. Gebruik dit in plaats van correctMeal als hij alleen minder at. Het id komt uit getDayNutrition of " +
            "getDayTimeline. 100 zet het terug naar alles opgegeten.",
    )
    fun setMealEaten(
        @ToolParam(description = "Het korte id van het eetmoment") id: String,
        @ToolParam(description = "Welk deel hij opat, in procenten van 1 tot 100; een kwart laten staan is 75") percent: Int,
    ): String = guarded {
        val meal = findMeal(id) ?: return@guarded noMatch("eetmoment", id)
        val saved = meals.setEaten(meal.id, percent)
        "${saved.title.ifBlank { "Het eetmoment" }} telt nu voor $percent% mee: ${saved.kcal} kcal."
    }

    @Tool(
        description = "Verwijder een eetmoment uit het dagboek. Het id komt uit getDayNutrition of getDayTimeline. " +
            "Alleen bij een duidelijk verzoek; dit is niet terug te draaien.",
    )
    fun deleteMeal(@ToolParam(description = "Het korte id van het eetmoment") id: String): String = guarded {
        val meal = findMeal(id) ?: return@guarded noMatch("eetmoment", id)
        meals.delete(meal.id)
        "Verwijderd: ${meal.title.ifBlank { "eetmoment" }} van ${meal.eatenAt.atZone(zone).toLocalDate()} ${hm(meal.eatenAt)} (${mealSummary(meal)})."
    }

    // ---- hulpjes --------------------------------------------------------------------------------

    private fun guarded(action: () -> String): String = try {
        action()
    } catch (e: NapConflictException) {
        "${e.message}. Meld dat eerst af."
    } catch (e: IllegalArgumentException) {
        e.message ?: "Dat kon ik niet vastleggen."
    } catch (e: IllegalStateException) {
        e.message ?: "Dat kan nu niet."
    } catch (e: RuntimeException) {
        "Dat is niet gelukt: ${e.message ?: "onbekende fout"}."
    }

    private fun findMeal(id: String): Meal? {
        val now = Instant.now()
        return pick(meals.between(now.minus(LOOKBACK), now.plusSeconds(600)), id) { it.id }
    }

    /** Het ene item waarvan het id met [id] begint; bij geen of meerdere treffers `null`. */
    private fun <T> pick(items: List<T>, id: String, idOf: (T) -> String): T? {
        val wanted = id.trim().removePrefix("id").trim()
        if (wanted.length < 4) return null
        return items.filter { idOf(it).startsWith(wanted) }.singleOrNull()
    }

    private fun noMatch(what: String, id: String) =
        "Geen $what gevonden met id $id in de laatste twee weken. Vraag de dag op met getDayTimeline voor de juiste id's."

    private fun parseDate(date: String?): LocalDate? =
        runCatching { date?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.trim()) } ?: LocalDate.now(zone) }.getOrNull()

    private fun parseTime(time: String): LocalTime? =
        runCatching { LocalTime.parse(time.trim().replace('.', ':').padStart(5, '0')) }.getOrNull()

    private fun hm(at: Instant): String = at.atZone(zone).toLocalTime().toString().take(5)

    private fun duration(from: Instant, to: Instant): String {
        val minutes = Duration.between(from, to).toMinutes()
        return if (minutes < 60) "$minutes min" else "${minutes / 60} u ${(minutes % 60).toString().padStart(2, '0')}"
    }

    private fun describe(nap: Nap): String {
        val end = nap.end ?: return "${hm(nap.start)} dutje, loopt nog" + (nap.alarmAt?.let { " (wekker ${hm(it)})" } ?: "")
        return "${hm(nap.start)} dutje tot ${hm(end)} (${duration(nap.start, end)}${if (nap.endEstimated) ", eind geschat" else ""})"
    }

    private fun mealSummary(meal: Meal): String = when (meal.status) {
        MealStatus.ANALYSEREN -> "wordt nog uitgerekend" + meal.note.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        MealStatus.MISLUKT -> "uitrekenen mislukt" + meal.note.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        else -> "${meal.kcal} kcal, ${meal.items.joinToString(", ") { it.name }}" +
            (if (meal.eatenPercent < 100) " (${meal.eatenPercent}% opgegeten)" else "") +
            if (meal.status == MealStatus.VOORSTEL) " (nog niet bevestigd)" else ""
    }

    private fun workoutLabel(type: String): String = when (type) {
        "WALKING", "HIKING" -> "wandelen"
        "RUNNING" -> "hardlopen"
        "BIKING" -> "fietsen"
        "SWIMMING" -> "zwemmen"
        "STRENGTH_TRAINING" -> "krachttraining"
        else -> type.lowercase().replace('_', ' ')
    }

    companion object {
        private val LOOKBACK: Duration = Duration.ofDays(14)
        private val HUNGER = listOf("geen", "trek", "beetje honger", "honger", "slap")
        private const val INVALID_DATE = "Ongeldige datum; gebruik jjjj-mm-dd."
        private const val INVALID_TIME = "Ongeldig tijdstip; gebruik uu:mm."
    }
}
