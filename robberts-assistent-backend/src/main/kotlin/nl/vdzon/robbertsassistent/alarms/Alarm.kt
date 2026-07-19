package nl.vdzon.robbertsassistent.alarms

import nl.vdzon.robbertsassistent.scheduling.Recurrence
import java.time.Instant

/**
 * Eén alarm: een bericht dat op [time] een echt telefoon-alarm laat afgaan. Anders dan een reminder
 * wordt een alarm NIET door de backend afgevuurd — de app synct de lijst en plant ze lokaal
 * (Android AlarmManager). [recurrence] `null` = eenmalig. De app schuift herhalende alarms lokaal
 * door en verwijdert eenmalige na afgaan.
 */
data class Alarm(
    val id: String,
    val message: String,
    val time: Instant,
    val recurrence: Recurrence? = null,
    val active: Boolean = true,
)
