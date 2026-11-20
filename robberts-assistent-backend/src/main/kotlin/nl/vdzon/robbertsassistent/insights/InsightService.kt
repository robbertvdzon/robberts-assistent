package nl.vdzon.robbertsassistent.insights

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import nl.vdzon.robbertsassistent.activity.ActivityDay
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.nutrition.ItemBasis
import nl.vdzon.robbertsassistent.nutrition.Meal
import nl.vdzon.robbertsassistent.nutrition.MealService
import nl.vdzon.robbertsassistent.nutrition.MealStatus
import nl.vdzon.robbertsassistent.wellbeing.CheckIn
import nl.vdzon.robbertsassistent.wellbeing.CheckInService
import nl.vdzon.robbertsassistent.wellbeing.Nap
import nl.vdzon.robbertsassistent.wellbeing.NapService
import org.springframework.stereotype.Service

/** Eén groep in een vergelijking: het gemiddelde en op hoeveel waarnemingen het rust. */
data class InsightGroup(val label: String, val value: Double, val n: Int)

enum class InsightStrength { DUIDELIJK, NOG_TE_WEINIG }

/**
 * Eén bevinding, altijd met de getallen erachter. [strength] zegt of er genoeg waarnemingen zijn
 * om er iets aan te hebben; het is samenhang, geen bewezen oorzaak.
 */
data class Insight(
    val key: String,
    val title: String,
    val text: String,
    val strength: InsightStrength,
    val groups: List<InsightGroup>,
)

/** Eén dag in het weekoverzicht. Wat ontbreekt blijft `null`. */
data class OverviewDay(
    val date: LocalDate,
    val eatenKcal: Int,
    val burnedKcal: Int?,
    val proteinG: Double,
    val fiberG: Double,
    val vegetableG: Int,
    val steps: Int?,
    val sleepMinutes: Int?,
    val weightKg: Double?,
    val mealCount: Int,
)

data class WeightPoint(val date: LocalDate, val weightKg: Double)

data class WeekOverview(
    val days: List<OverviewDay>,
    /** Gemiddelden over de dagen waarop iets is gegeten resp. waarvoor verbruik bekend is. */
    val avgEatenKcal: Int?,
    val avgBurnedKcal: Int?,
    /**
     * Gemiddeld gegeten min verbruikt, alleen over afgeronde dagen waarvoor beide bekend zijn.
     * Vandaag telt niet mee: die dag is nog niet af en zou altijd een groot tekort laten zien.
     */
    val avgBalanceKcal: Int?,
    val avgProteinG: Double?,
    val avgFiberG: Double?,
    val avgVegetableG: Int?,
    /** Aandeel van de calorieën per onderbouwing (GEWOGEN, ETIKET, RECEPT, VERGELEKEN, GESCHAT), in procenten. */
    val basisShare: Map<String, Int>,
    /** Aandeel bevestigde maaltijden dat zonder wijziging is overgenomen, in procenten. */
    val unchangedShare: Int?,
    val weights: List<WeightPoint>,
)

/**
 * Rekent de overzichten en verbanden uit. Bewust gewone rekensommen in code en geen AI: een
 * taalmodel maakt rekenfouten, en elke bevinding moet na te rekenen zijn.
 */
@Service
class InsightService(
    private val meals: MealService,
    private val checkIns: CheckInService,
    private val activity: ActivityService,
    private val naps: NapService,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun week(end: LocalDate): WeekOverview {
        val start = end.minusDays(6)
        val today = LocalDate.now(clock.withZone(ZONE))
        val mealList = meals.between(start.atStartOfDay(ZONE).toInstant(), end.plusDays(1).atStartOfDay(ZONE).toInstant())
            .filter { it.status != MealStatus.MISLUKT && it.status != MealStatus.ANALYSEREN }
        val byDate = mealList.groupBy { it.eatenAt.atZone(ZONE).toLocalDate() }
        val activityByDate = activity.between(start, end).associateBy { it.date }
        val days = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            val items = byDate[date].orEmpty().flatMap { it.eatenItems }
            val a = activityByDate[date]
            OverviewDay(
                date = date,
                eatenKcal = items.sumOf { it.kcal },
                burnedKcal = a?.totalKcal,
                proteinG = items.sumOf { it.proteinG },
                fiberG = items.sumOf { it.fiberG },
                vegetableG = items.sumOf { it.vegetableG },
                steps = a?.steps,
                sleepMinutes = a?.sleep?.minutes,
                weightKg = a?.weightKg,
                mealCount = byDate[date].orEmpty().size,
            )
        }
        val eaten = days.filter { it.mealCount > 0 }
        val burned = days.mapNotNull { it.burnedKcal }
        val allItems = mealList.flatMap { it.eatenItems }
        val totalKcal = allItems.sumOf { it.kcal }
        val confirmed = mealList.filter { it.status == MealStatus.BEVESTIGD || it.status == MealStatus.VERBETERD }
        return WeekOverview(
            days = days,
            avgEatenKcal = eaten.takeIf { it.isNotEmpty() }?.map { it.eatenKcal }?.average()?.toInt(),
            avgBurnedKcal = burned.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            avgBalanceKcal = days
                .filter { it.date.isBefore(today) && it.mealCount > 0 && it.burnedKcal != null }
                .takeIf { it.isNotEmpty() }?.map { it.eatenKcal - it.burnedKcal!! }?.average()?.toInt(),
            avgProteinG = eaten.takeIf { it.isNotEmpty() }?.map { it.proteinG }?.average(),
            avgFiberG = eaten.takeIf { it.isNotEmpty() }?.map { it.fiberG }?.average(),
            avgVegetableG = eaten.takeIf { it.isNotEmpty() }?.map { it.vegetableG }?.average()?.toInt(),
            basisShare = if (totalKcal == 0) emptyMap() else ItemBasis.entries.associate { basis ->
                basis.name to (allItems.filter { it.basis == basis }.sumOf { it.kcal } * 100.0 / totalKcal).toInt()
            }.filterValues { it > 0 },
            unchangedShare = confirmed.takeIf { it.isNotEmpty() }
                ?.let { list -> list.count { it.status == MealStatus.BEVESTIGD } * 100 / list.size },
            weights = activity.between(end.minusDays(WEIGHT_DAYS), end)
                .mapNotNull { day -> day.weightKg?.let { WeightPoint(day.date, it) } },
        )
    }

    /** Eén regel over vandaag, voor de briefing en de chat. `null` als er niets te melden is. */
    fun todayLine(): String? {
        val today = LocalDate.now(clock.withZone(ZONE))
        val day = week(today).days.last()
        val parts = listOfNotNull(
            if (day.mealCount > 0) "gegeten ${day.eatenKcal} kcal" else null,
            day.burnedKcal?.let { "verbruikt $it kcal" },
            day.steps?.let { "$it stappen" },
            if (day.mealCount > 0) "eiwit ${day.proteinG.toInt()} g, vezels ${day.fiberG.toInt()} g, groente ${day.vegetableG} g" else null,
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.replaceFirstChar { it.uppercase() }
    }

    /** De verbanden over de afgelopen [WINDOW_DAYS] dagen. Bevindingen zonder gegevens vallen weg. */
    fun insights(): List<Insight> {
        val now = clock.instant()
        val today = now.atZone(ZONE).toLocalDate()
        val from = today.minusDays(WINDOW_DAYS)
        val fromInstant = from.atStartOfDay(ZONE).toInstant()
        val mealList = meals.between(fromInstant, now)
            .filter { it.status != MealStatus.MISLUKT && it.status != MealStatus.ANALYSEREN && it.kcal > 0 }
            .sortedBy { it.eatenAt }
        val checkInList = checkIns.between(fromInstant, now).sortedBy { it.at }
        val days = activity.between(from, today).associateBy { it.date }
        val napList = naps.between(fromInstant, now).filter { it.end != null }
        return listOfNotNull(
            sleepAndEnergy(checkInList, days),
            faintAndGap(checkInList, mealList),
            satietyPerMeal(mealList, checkInList),
            proteinAndSatiety(mealList, checkInList),
            workoutAndMorningHunger(checkInList, days),
            lunchAndAfternoonEnergy(mealList, checkInList),
            napAndEveningEnergy(napList, checkInList, today),
            shortNightAndNap(napList, days, today),
        )
    }

    // ---- de afzonderlijke verbanden -------------------------------------------------------------

    private fun sleepAndEnergy(checkIns: List<CheckIn>, days: Map<LocalDate, ActivityDay>): Insight? {
        val energyPerDay = checkIns.filter { it.energy != null }
            .groupBy { it.at.atZone(ZONE).toLocalDate() }
            .mapValues { (_, list) -> list.map { it.energy!!.toDouble() }.average() }
        val long = mutableListOf<Double>()
        val short = mutableListOf<Double>()
        energyPerDay.forEach { (date, energy) ->
            val minutes = days[date]?.sleep?.minutes ?: return@forEach
            if (minutes >= 420) long += energy else if (minutes < 360) short += energy
        }
        return compare(
            key = "slaap-energie",
            title = "Slaap en energie",
            a = "na 7 uur slaap of meer" to long,
            b = "na minder dan 6 uur" to short,
            unit = "energie",
            minDiff = 0.4,
        )
    }

    private fun faintAndGap(checkIns: List<CheckIn>, meals: List<Meal>): Insight? {
        fun hoursSinceMeal(c: CheckIn): Double? =
            meals.lastOrNull { it.eatenAt.isBefore(c.at) && Duration.between(it.eatenAt, c.at) < Duration.ofHours(16) }
                ?.let { Duration.between(it.eatenAt, c.at).toMinutes() / 60.0 }
        val faint = checkIns.filter { it.hunger == CheckInService.MAX_HUNGER }.mapNotNull(::hoursSinceMeal)
        val others = checkIns.filter { it.hunger != null && it.hunger < CheckInService.MAX_HUNGER }.mapNotNull(::hoursSinceMeal)
        if (faint.isEmpty()) return null
        val late = faint.count { it > 4.5 }
        return Insight(
            key = "slap-tijd-sinds-eten",
            title = "Slap van de honger",
            text = "$late van de ${faint.size} keer zat er meer dan 4,5 uur tussen je laatste maaltijd en het moment dat je slap was." +
                if (others.isNotEmpty()) " Op de andere momenten was dat gemiddeld ${fmt(others.average())} uur." else "",
            strength = if (faint.size >= 5) InsightStrength.DUIDELIJK else InsightStrength.NOG_TE_WEINIG,
            groups = listOfNotNull(
                InsightGroup("uren sinds eten, bij slap", faint.average(), faint.size),
                others.takeIf { it.isNotEmpty() }?.let { InsightGroup("uren sinds eten, anders", it.average(), it.size) },
            ),
        )
    }

    /**
     * Hoe lang hield een maaltijd vol: de tijd tot de eerste check-in met trek of meer, vóór de
     * volgende maaltijd en binnen acht uur. Een check-in "voor het eten" van de vólgende maaltijd
     * telt dus mee; die zegt precies hoe het er vlak voor die maaltijd voor stond.
     */
    private fun fullHours(meals: List<Meal>, checkIns: List<CheckIn>): Map<Meal, Double> =
        meals.mapIndexedNotNull { index, meal ->
            val nextMeal = meals.getOrNull(index + 1)?.eatenAt
            val limit = meal.eatenAt.plus(Duration.ofHours(8))
            val firstHunger = checkIns.firstOrNull {
                it.at.isAfter(meal.eatenAt.plus(Duration.ofMinutes(20))) && (it.hunger ?: 0) >= 1 &&
                    !it.at.isAfter(limit) && (nextMeal == null || !it.at.isAfter(nextMeal.plus(Duration.ofMinutes(5))))
            } ?: return@mapIndexedNotNull null
            meal to Duration.between(meal.eatenAt, firstHunger.at).toMinutes() / 60.0
        }.toMap()

    private fun satietyPerMeal(meals: List<Meal>, checkIns: List<CheckIn>): Insight? {
        val perTitle = fullHours(meals.filter { it.kcal >= 150 }, checkIns).entries
            .groupBy({ it.key.title.ifBlank { "Eetmoment" } }, { it.value })
            .filterValues { it.size >= 2 }
        if (perTitle.isEmpty()) return null
        val groups = perTitle.map { (title, hours) -> InsightGroup(title, hours.average(), hours.size) }.sortedByDescending { it.value }
        return Insight(
            key = "vol-per-maaltijd",
            title = "Hoe lang een maaltijd je vol houdt",
            text = groups.joinToString(". ") { "${it.label}: gemiddeld ${fmt(it.value)} uur tot je weer trek had (${it.n} keer)" } + ".",
            strength = if (groups.all { it.n >= 8 }) InsightStrength.DUIDELIJK else InsightStrength.NOG_TE_WEINIG,
            groups = groups,
        )
    }

    private fun proteinAndSatiety(meals: List<Meal>, checkIns: List<CheckIn>): Insight? {
        val full = fullHours(meals.filter { it.kcal >= 250 }, checkIns)
        if (full.size < 4) return null
        // Eiwit als aandeel van de calorieën (4 kcal per gram), zodat een grote maaltijd niet vanzelf "eiwitrijk" is.
        fun share(meal: Meal) = meal.eatenItems.sumOf { it.proteinG } * 4 / meal.kcal
        val median = full.keys.map(::share).sorted().let { it[it.size / 2] }
        val (rich, poor) = full.entries.partition { share(it.key) >= median }
        return compare(
            key = "eiwit-vol",
            title = "Eiwit en hoe lang je vol zit",
            a = "eiwitrijkere maaltijden" to rich.map { it.value },
            b = "eiwitarmere maaltijden" to poor.map { it.value },
            unit = "uur tot trek",
            minDiff = 0.5,
        )
    }

    private fun workoutAndMorningHunger(checkIns: List<CheckIn>, days: Map<LocalDate, ActivityDay>): Insight? {
        val morning = checkIns.filter { it.hunger != null && it.at.atZone(ZONE).toLocalTime().isBefore(LocalTime.NOON) }
            .groupBy { it.at.atZone(ZONE).toLocalDate() }
            .mapValues { (_, list) -> list.map { it.hunger!!.toDouble() }.average() }
        val after = mutableListOf<Double>()
        val notAfter = mutableListOf<Double>()
        morning.forEach { (date, hunger) ->
            val yesterday = days[date.minusDays(1)] ?: return@forEach
            if (yesterday.workouts.isNotEmpty()) after += hunger else notAfter += hunger
        }
        return compare(
            key = "training-honger",
            title = "De ochtend na een training",
            a = "de ochtend na een training" to after,
            b = "andere ochtenden" to notAfter,
            unit = "honger (0–4)",
            minDiff = 0.3,
        )
    }

    private fun lunchAndAfternoonEnergy(meals: List<Meal>, checkIns: List<CheckIn>): Insight? {
        fun between(t: LocalTime, from: LocalTime, to: LocalTime) = !t.isBefore(from) && t.isBefore(to)
        val lunchKcal = meals.filter { between(it.eatenAt.atZone(ZONE).toLocalTime(), LocalTime.of(11, 0), LocalTime.of(14, 30)) }
            .groupBy { it.eatenAt.atZone(ZONE).toLocalDate() }.mapValues { (_, list) -> list.sumOf { it.kcal } }
        val afternoon = checkIns.filter { it.energy != null && between(it.at.atZone(ZONE).toLocalTime(), LocalTime.of(14, 0), LocalTime.of(17, 0)) }
            .groupBy { it.at.atZone(ZONE).toLocalDate() }.mapValues { (_, list) -> list.map { it.energy!!.toDouble() }.average() }
        val big = mutableListOf<Double>()
        val small = mutableListOf<Double>()
        afternoon.forEach { (date, energy) ->
            val kcal = lunchKcal[date] ?: return@forEach
            if (kcal > 700) big += energy else small += energy
        }
        return compare(
            key = "lunch-middagenergie",
            title = "Lunch en energie in de middag",
            a = "na een lunch boven 700 kcal" to big,
            b = "na een kleinere lunch" to small,
            unit = "energie",
            minDiff = 0.4,
        )
    }

    /**
     * Dagen vanaf het eerste vastgelegde dutje tot en met gisteren. Daarvoor bestonden dutjes nog
     * niet in de app, dus die dagen zeggen niets over "geen dutje"; vandaag is nog niet af.
     */
    private fun napEra(naps: List<Nap>, today: LocalDate): List<LocalDate> {
        val first = naps.minOfOrNull { it.start.atZone(ZONE).toLocalDate() } ?: return emptyList()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.toList()
    }

    private fun napAndEveningEnergy(naps: List<Nap>, checkIns: List<CheckIn>, today: LocalDate): Insight? {
        val napEndByDate = naps.groupBy { it.start.atZone(ZONE).toLocalDate() }.mapValues { (_, list) -> list.maxOf { it.end!! } }
        val withNap = mutableListOf<Double>()
        val without = mutableListOf<Double>()
        napEra(naps, today).forEach { date ->
            val napEnd = napEndByDate[date]
            val energy = checkIns.filter {
                it.energy != null && it.at.atZone(ZONE).toLocalDate() == date &&
                    !it.at.atZone(ZONE).toLocalTime().isBefore(LocalTime.of(16, 0)) && (napEnd == null || it.at.isAfter(napEnd))
            }.map { it.energy!!.toDouble() }
            if (energy.isEmpty()) return@forEach
            if (napEnd != null) withNap += energy.average() else without += energy.average()
        }
        return compare(
            key = "dutje-avondenergie",
            title = "Dutje en energie later op de dag",
            a = "op dagen met een dutje (na 16:00)" to withNap,
            b = "op dagen zonder dutje (na 16:00)" to without,
            unit = "energie",
            minDiff = 0.4,
        )
    }

    private fun shortNightAndNap(naps: List<Nap>, days: Map<LocalDate, ActivityDay>, today: LocalDate): Insight? {
        val napDates = naps.map { it.start.atZone(ZONE).toLocalDate() }.toSet()
        val short = mutableListOf<Double>()
        val long = mutableListOf<Double>()
        napEra(naps, today).forEach { date ->
            val minutes = days[date]?.sleep?.minutes ?: return@forEach
            val napped = if (date in napDates) 100.0 else 0.0
            if (minutes < 390) short += napped else long += napped
        }
        return compare(
            key = "korte-nacht-dutje",
            title = "Korte nacht en een dutje",
            a = "na minder dan 6,5 uur slaap" to short,
            b = "na 6,5 uur slaap of meer" to long,
            unit = "% van de dagen een dutje",
            minDiff = 15.0,
        )
    }

    /** Vergelijkt twee groepen. Zonder gegevens in beide groepen is er geen bevinding. */
    private fun compare(
        key: String,
        title: String,
        a: Pair<String, List<Double>>,
        b: Pair<String, List<Double>>,
        unit: String,
        minDiff: Double,
    ): Insight? {
        if (a.second.isEmpty() || b.second.isEmpty()) return null
        val avgA = a.second.average()
        val avgB = b.second.average()
        val enough = a.second.size >= MIN_N && b.second.size >= MIN_N
        val clear = enough && kotlin.math.abs(avgA - avgB) >= minDiff
        val text = "${a.first.replaceFirstChar { it.uppercase() }}: gemiddeld ${fmt(avgA)} $unit (${a.second.size} keer). " +
            "${b.first.replaceFirstChar { it.uppercase() }}: ${fmt(avgB)} (${b.second.size} keer)." +
            if (enough && !clear) " Het verschil is klein." else ""
        return Insight(
            key = key,
            title = title,
            text = text,
            strength = if (enough) InsightStrength.DUIDELIJK else InsightStrength.NOG_TE_WEINIG,
            groups = listOf(InsightGroup(a.first, avgA, a.second.size), InsightGroup(b.first, avgB, b.second.size)),
        )
    }

    private fun fmt(value: Double): String = String.format(Locale.of("nl"), "%.1f", value)

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        const val WINDOW_DAYS = 56L
        private const val WEIGHT_DAYS = 120L
        private const val MIN_N = 8
    }
}
