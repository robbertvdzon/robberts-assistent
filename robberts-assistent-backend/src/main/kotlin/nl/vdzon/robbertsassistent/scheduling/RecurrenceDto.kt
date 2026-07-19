package nl.vdzon.robbertsassistent.scheduling

/** Herhaling in de REST-API: unit = DAYS/WEEKS/MONTHS/YEARS, interval = elke N. Gedeeld door reminders + alarms. */
data class RecurrenceDto(val unit: String = "", val interval: Int = 0) {
    fun toRecurrence(): Recurrence? {
        if (interval <= 0) return null
        val u = runCatching { RecurrenceUnit.valueOf(unit.trim().uppercase()) }.getOrNull() ?: return null
        return Recurrence(u, interval)
    }
}

fun Recurrence.toDto() = RecurrenceDto(unit = unit.name, interval = interval)
