package nl.vdzon.robbertsassistent.watches

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class WatchRepositoryTest {
    private lateinit var repository: WatchRepository

    @BeforeEach
    fun setup() {
        repository = InMemoryWatchRepository()
    }

    private fun createWatch(
        id: String = "test-id",
        title: String = "Test Watch",
        active: Boolean = true,
        status: WatchStatus = WatchStatus.ONBEKEND,
    ) = Watch(
        id = id,
        title = title,
        url = "https://example.com",
        instruction = "Zoek iets",
        frequency = WatchFrequency.DAGELIJKS,
        status = status,
        active = active,
    )

    @Test
    fun `save en findById werken correct`() {
        val watch = createWatch()
        repository.save(watch)

        val found = repository.findById("test-id")
        assertNotNull(found)
        assertEquals("Test Watch", found?.title)
    }

    @Test
    fun `findById geeft null voor niet-bestaande watch`() {
        val found = repository.findById("niet-bestaand")
        assertNull(found)
    }

    @Test
    fun `all geeft alle watches terug`() {
        repository.save(createWatch(id = "w1", title = "Watch 1"))
        repository.save(createWatch(id = "w2", title = "Watch 2"))
        repository.save(createWatch(id = "w3", title = "Watch 3"))

        val all = repository.all()
        assertEquals(3, all.size)
    }

    @Test
    fun `activeWatches geeft alleen actieve watches`() {
        repository.save(createWatch(id = "w1", active = true))
        repository.save(createWatch(id = "w2", active = false))
        repository.save(createWatch(id = "w3", active = true))

        val active = repository.activeWatches()
        assertEquals(2, active.size)
        assertTrue(active.all { it.active })
    }

    @Test
    fun `delete verwijdert een watch`() {
        repository.save(createWatch(id = "w1"))
        repository.save(createWatch(id = "w2"))

        repository.delete("w1")

        assertNull(repository.findById("w1"))
        assertNotNull(repository.findById("w2"))
        assertEquals(1, repository.all().size)
    }

    @Test
    fun `save overschrijft bestaande watch`() {
        val original = createWatch(id = "w1", title = "Origineel")
        repository.save(original)

        val updated = original.copy(title = "Bijgewerkt", status = WatchStatus.GEVONDEN)
        repository.save(updated)

        val found = repository.findById("w1")
        assertEquals("Bijgewerkt", found?.title)
        assertEquals(WatchStatus.GEVONDEN, found?.status)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun `watch met alle velden wordt correct opgeslagen en opgehaald`() {
        val now = Instant.now()
        val watch = Watch(
            id = "full-watch",
            title = "Volledige Watch",
            url = "https://shop.example.com/product",
            instruction = "Check of de prijs onder €50 is",
            frequency = WatchFrequency.KANTOORUREN,
            status = WatchStatus.NIET_GEVONDEN,
            active = true,
            lastChecked = now,
            createdAt = now,
            updatedAt = now,
        )
        repository.save(watch)

        val found = repository.findById("full-watch")
        assertNotNull(found)
        assertEquals("Volledige Watch", found?.title)
        assertEquals("https://shop.example.com/product", found?.url)
        assertEquals("Check of de prijs onder €50 is", found?.instruction)
        assertEquals(WatchFrequency.KANTOORUREN, found?.frequency)
        assertEquals(WatchStatus.NIET_GEVONDEN, found?.status)
        assertTrue(found?.active == true)
        assertEquals(now, found?.lastChecked)
    }

    @Test
    fun `toggle van active werkt via save`() {
        val watch = createWatch(id = "toggle-test", active = true)
        repository.save(watch)

        val toggled = watch.copy(active = false)
        repository.save(toggled)

        val found = repository.findById("toggle-test")
        assertFalse(found?.active == true)
    }
}
