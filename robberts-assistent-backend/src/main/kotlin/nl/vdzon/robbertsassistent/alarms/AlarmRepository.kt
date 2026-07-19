package nl.vdzon.robbertsassistent.alarms

import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor alarms. Firestore in prod, in-memory fallback; [AlarmRepositoryConfig] kiest.
 */
interface AlarmRepository {
    fun save(alarm: Alarm): Alarm
    fun all(): List<Alarm>
    fun delete(id: String)
}

class InMemoryAlarmRepository : AlarmRepository {
    private val store = ConcurrentHashMap<String, Alarm>()
    override fun save(alarm: Alarm): Alarm { store[alarm.id] = alarm; return alarm }
    override fun all(): List<Alarm> = store.values.toList()
    override fun delete(id: String) { store.remove(id) }
}
