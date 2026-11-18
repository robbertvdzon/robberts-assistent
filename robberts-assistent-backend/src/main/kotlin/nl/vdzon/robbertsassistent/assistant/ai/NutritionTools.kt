package nl.vdzon.robbertsassistent.assistant.ai

import java.time.LocalDate
import nl.vdzon.robbertsassistent.insights.InsightService
import nl.vdzon.robbertsassistent.nutrition.MealService
import nl.vdzon.robbertsassistent.wellbeing.CheckInService
import nl.vdzon.robbertsassistent.wellbeing.CheckInSource
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/** Geeft de chat-assistent toegang tot het eetdagboek, de check-ins en de overzichten. */
@Component
class NutritionTools(
    private val meals: MealService,
    private val checkIns: CheckInService,
    private val insights: InsightService,
) {
    @Tool(
        description = "Leg vast wat Robbert heeft gegeten of gedronken, in zijn eigen woorden (bv. 'een appel en " +
            "een koffie met melk'). Het eetdagboek rekent de calorieën zelf uit; het voorstel staat daarna in " +
            "de app onder Eten. Gebruik dit als hij zegt dat hij iets gegeten heeft en dat wil bijhouden.",
    )
    fun logMeal(@ToolParam(description = "Wat hij at of dronk, zo letterlijk mogelijk") description: String): String {
        if (description.isBlank()) return "Ik heb een omschrijving nodig van wat je at."
        meals.create(emptyList(), description, null)
        return "Vastgelegd. Het voorstel met calorieën staat over een halve minuut in de app onder Eten."
    }

    @Tool(
        description = "Leg vast hoe Robbert zich voelt: honger (0 = geen, 1 = trek, 2 = honger, 3 = slap van de " +
            "honger) en/of energie (1 = moe tot 5 = fit). Gebruik dit als hij zegt dat hij trek of honger heeft, " +
            "slap is, moe is of zich fit voelt.",
    )
    fun logCheckIn(
        @ToolParam(description = "Honger 0–3, of leeg als hij daar niets over zei", required = false) hunger: Int?,
        @ToolParam(description = "Energie 1–5, of leeg als hij daar niets over zei", required = false) energy: Int?,
        @ToolParam(description = "Korte toelichting in zijn woorden, mag leeg", required = false) note: String?,
    ): String = try {
        checkIns.record(hunger, energy, note.orEmpty(), CheckInSource.CHAT)
        "Vastgelegd."
    } catch (e: IllegalArgumentException) {
        e.message ?: "Dat kon ik niet vastleggen."
    }

    @Tool(
        description = "Wat Robbert op een dag at: de eetmomenten met calorieën en de dagtotalen (kcal, eiwit, " +
            "vezels, groente, fruit). Zonder datum vandaag.",
    )
    fun getDayNutrition(
        @ToolParam(description = "Datum als jjjj-mm-dd, of leeg voor vandaag", required = false) date: String?,
    ): String {
        val day = runCatching { date?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now(MealService.ZONE) }
            .getOrElse { return "Ongeldige datum; gebruik jjjj-mm-dd." }
        val result = meals.day(day)
        if (result.meals.isEmpty()) return "Op $day is niets vastgelegd."
        val t = result.totals
        return buildString {
            appendLine("$day: ${t.kcal} kcal, eiwit ${t.proteinG.toInt()} g, vezels ${t.fiberG.toInt()} g, groente ${t.vegetableG} g, fruit ${t.fruitG} g.")
            result.meals.forEach { meal ->
                val time = meal.eatenAt.atZone(MealService.ZONE).toLocalTime().toString().take(5)
                appendLine("- $time ${meal.title.ifBlank { "Eetmoment" }} (${meal.kcal} kcal): ${meal.items.joinToString(", ") { it.name }}")
            }
        }.trim()
    }

    @Tool(
        description = "Weekoverzicht van eten en bewegen over de laatste zeven dagen: per dag gegeten en verbruikte " +
            "calorieën, stappen en slaap, plus de gemiddelden. Gebruik dit voor vragen als 'hoeveel heb ik deze " +
            "week gegeten' of 'zit ik onder mijn verbruik'.",
    )
    fun getWeekNutrition(): String {
        val week = insights.week(LocalDate.now(InsightService.ZONE))
        return buildString {
            week.days.forEach { d ->
                appendLine(
                    "${d.date}: gegeten ${if (d.mealCount > 0) "${d.eatenKcal} kcal" else "niets vastgelegd"}" +
                        (d.burnedKcal?.let { ", verbruikt $it kcal" } ?: "") +
                        (d.steps?.let { ", $it stappen" } ?: "") +
                        (d.sleepMinutes?.let { ", ${it / 60} u ${it % 60} min geslapen" } ?: ""),
                )
            }
            appendLine("Gemiddeld gegeten: ${week.avgEatenKcal ?: "onbekend"} kcal per dag; gemiddeld verbruikt: ${week.avgBurnedKcal ?: "onbekend"} kcal.")
        }.trim()
    }

    @Tool(
        description = "De verbanden die uit Robberts eigen gegevens komen tussen eten, slaap, bewegen, honger en " +
            "energie, elk met de getallen erachter en of er al genoeg waarnemingen zijn.",
    )
    fun getInsights(): String {
        val list = insights.insights()
        if (list.isEmpty()) return "Er zijn nog niet genoeg check-ins en maaltijden om verbanden te zien."
        return list.joinToString("\n") { "${it.title} (${it.strength.name.lowercase().replace('_', ' ')}): ${it.text}" }
    }
}
