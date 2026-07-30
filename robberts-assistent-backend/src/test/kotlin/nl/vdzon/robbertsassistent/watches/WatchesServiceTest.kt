package nl.vdzon.robbertsassistent.watches

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchesServiceTest {
    private val service = WatchesService(InMemoryWatchRepository())

    @Test
    fun `create maakt een watch met defaults`() {
        val watch = service.create("Aaltjes", "https://example.com", "check voorraad", WatchFrequency.DAGELIJKS)

        assertEquals("Aaltjes", watch.title)
        assertEquals("https://example.com", watch.url)
        assertEquals("check voorraad", watch.instruction)
        assertEquals(WatchFrequency.DAGELIJKS, watch.frequency)
        assertEquals(WatchStatus.ONBEKEND, watch.status)
        assertNull(watch.statusText)
        assertNull(watch.lastChecked)
        assertTrue(watch.active)
    }

    @Test
    fun `list geeft alle watches terug`() {
        service.create("Watch 1", "https://a.com", "inst 1", WatchFrequency.KANTOORUREN)
        service.create("Watch 2", "https://b.com", "inst 2", WatchFrequency.DAGELIJKS)

        assertEquals(2, service.list().size)
    }

    @Test
    fun `delete verwijdert de watch`() {
        val watch = service.create("Weg", "https://x.com", "x", WatchFrequency.DAGELIJKS)
        service.delete(watch.id)

        assertTrue(service.list().isEmpty())
    }

    @Test
    fun `save slaat wijzigingen op`() {
        val watch = service.create("Test", "https://t.com", "t", WatchFrequency.DAGELIJKS)
        val updated = watch.copy(status = WatchStatus.GEVONDEN, statusText = "Gevonden!")
        service.save(updated)

        val fetched = service.findById(watch.id)
        assertEquals(WatchStatus.GEVONDEN, fetched?.status)
        assertEquals("Gevonden!", fetched?.statusText)
    }

    @Test
    fun `findById retourneert null voor onbekende id`() {
        assertNull(service.findById("onbestaand"))
    }
}
