package nl.vdzon.robbertsassistent.reminders

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory reminder-opslag voor fase 0 (leeg na herstart). Geleverd via [ConditionalOnMissingBean]
 * zodat de Firestore-implementatie (fase 2) — zodra die als bean bestaat — automatisch de plek
 * overneemt, zonder de service/scheduler te raken.
 */
@Configuration
class InMemoryReminderRepositoryConfig {
    @Bean
    @ConditionalOnMissingBean(ReminderRepository::class)
    fun inMemoryReminderRepository(): ReminderRepository = InMemoryReminderRepository()
}

class InMemoryReminderRepository : ReminderRepository {
    private val store = ConcurrentHashMap<String, Reminder>()

    override fun save(reminder: Reminder): Reminder {
        store[reminder.id] = reminder
        return reminder
    }

    override fun all(): List<Reminder> = store.values.toList()

    override fun due(now: Instant): List<Reminder> =
        store.values.filter { !it.delivered && !it.dueAt.isAfter(now) }

    override fun markDelivered(id: String) {
        store.computeIfPresent(id) { _, reminder -> reminder.copy(delivered = true) }
    }

    override fun delete(id: String) {
        store.remove(id)
    }
}
