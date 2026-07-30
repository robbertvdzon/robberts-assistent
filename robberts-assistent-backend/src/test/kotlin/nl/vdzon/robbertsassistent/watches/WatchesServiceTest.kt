package nl.vdzon.robbertsassistent.watches

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WatchesServiceTest {
    private val service = WatchesService(InMemoryWatchRepository())

    @Test
    fun `create slaat op met status ONBEKEND en actief`() {
        val watch = service.create("aaltjes", "https://example.com", "seintje geven", WatchFrequency.DAGELIJKS, true)
        assertEquals(WatchStatus.ONBEKEND, watch.status)
        assertTrue(watch.active)
    }

    @Test
    fun `list sorteert alfabetisch op titel`() {
        service.create("zebra", "https://example.com/1", "x", WatchFrequency.DAGELIJKS, true)
        service.create("aap", "https://example.com/2", "y", WatchFrequency.DAGELIJKS, true)
        assertEquals(listOf("aap", "zebra"), service.list().map { it.title })
    }

    @Test
    fun `update overschrijft de velden en reactiveert de watch`() {
        val created = service.create("titel", "https://example.com", "instructie", WatchFrequency.DAGELIJKS, false)
        service.save(created.copy(status = WatchStatus.GEVONDEN, active = false, statusText = "gevonden!"))

        val updated = service.update(
            created.id,
            "nieuwe titel",
            "https://example.com/2",
            "nieuwe instructie",
            WatchFrequency.KANTOORUREN,
            true,
        )

        assertEquals("nieuwe titel", updated.title)
        assertEquals("https://example.com/2", updated.url)
        assertEquals("nieuwe instructie", updated.instruction)
        assertEquals(WatchFrequency.KANTOORUREN, updated.frequency)
        assertTrue(updated.notifyOnFound)
        assertTrue(updated.active)
        assertEquals(WatchStatus.ONBEKEND, updated.status)
        assertEquals(null, updated.lastCheckedAt)
    }

    @Test
    fun `update van een onbekend id faalt`() {
        assertFailsWith<Exception> {
            service.update("onbekend-id", "t", "u", "i", WatchFrequency.DAGELIJKS, true)
        }
    }

    @Test
    fun `active geeft alleen actieve watches`() {
        val actief = service.create("actief", "https://example.com", "x", WatchFrequency.DAGELIJKS, true)
        val inactief = service.create("inactief", "https://example.com", "y", WatchFrequency.DAGELIJKS, true)
        service.save(inactief.copy(active = false))

        assertEquals(listOf(actief.id), service.active().map { it.id })
    }

    @Test
    fun `delete verwijdert de watch`() {
        val watch = service.create("weg", "https://example.com", "x", WatchFrequency.DAGELIJKS, true)
        service.delete(watch.id)
        assertTrue(service.list().isEmpty())
    }
}
