package nl.vdzon.robbertsassistent.alarms

import nl.vdzon.robbertsassistent.scheduling.Recurrence
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Beheert alarms (aanmaken, opsommen, verwijderen). De backend vuurt alarms niet af — de app synct
 * deze lijst en plant ze lokaal via Android AlarmManager.
 */
@Service
class AlarmsService(private val repository: AlarmRepository) {

    fun create(message: String, time: Instant, recurrence: Recurrence? = null): Alarm =
        repository.save(Alarm(id = UUID.randomUUID().toString(), message = message, time = time, recurrence = recurrence))

    fun list(): List<Alarm> = repository.all().sortedBy { it.time }

    fun delete(id: String) = repository.delete(id)
}
