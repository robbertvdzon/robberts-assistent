package nl.vdzon.robbertsassistent.scheduling

import java.time.Instant
import java.time.ZoneId

/** Eenheid van herhaling. */
enum class RecurrenceUnit { DAYS, WEEKS, MONTHS, YEARS }

/**
 * Herhaling van een reminder/alarm: elke [interval] × [unit] (bv. elke 3 maanden). Gedeeld door de
 * reminders- en alarms-modules. `null` betekent eenmalig.
 */
data class Recurrence(val unit: RecurrenceUnit, val interval: Int) {

    /** Het eerstvolgende tijdstip ná [time], kalender-correct (maanden/jaren via de zone). */
    fun nextAfter(time: Instant): Instant {
        val n = interval.toLong()
        val zoned = time.atZone(ZONE)
        val next = when (unit) {
            RecurrenceUnit.DAYS -> zoned.plusDays(n)
            RecurrenceUnit.WEEKS -> zoned.plusWeeks(n)
            RecurrenceUnit.MONTHS -> zoned.plusMonths(n)
            RecurrenceUnit.YEARS -> zoned.plusYears(n)
        }
        return next.toInstant()
    }

    /** Voor Firestore-opslag. */
    fun toMap(): Map<String, Any> = mapOf("unit" to unit.name, "interval" to interval)

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        /** Herstel uit een Firestore-map (null als afwezig/ongeldig). */
        fun fromMap(map: Map<String, Any?>?): Recurrence? {
            if (map == null) return null
            val unit = (map["unit"] as? String)?.let { runCatching { RecurrenceUnit.valueOf(it) }.getOrNull() } ?: return null
            val interval = (map["interval"] as? Number)?.toInt() ?: return null
            return Recurrence(unit, interval)
        }

        /** Parse een eenheid uit vrije tekst (voor de AI-tools); null bij onbekend. */
        fun unitFromText(text: String?): RecurrenceUnit? = when (text?.trim()?.lowercase()) {
            "dag", "dagen", "day", "days" -> RecurrenceUnit.DAYS
            "week", "weken", "weeks" -> RecurrenceUnit.WEEKS
            "maand", "maanden", "month", "months" -> RecurrenceUnit.MONTHS
            "jaar", "jaren", "year", "years" -> RecurrenceUnit.YEARS
            else -> null
        }
    }
}
