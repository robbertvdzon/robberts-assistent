package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.watches.InMemoryWatchRepository
import nl.vdzon.robbertsassistent.watches.WatchService
import nl.vdzon.robbertsassistent.watches.WatchStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchToolsTest {
    private val repository = InMemoryWatchRepository()
    private val service = WatchService(repository)
    private val tools = WatchTools(service)

    @Test
    fun `listWatches meldt netjes dat er geen zoekopdrachten zijn`() {
        assertEquals("Er lopen op dit moment geen zoekopdrachten.", tools.listWatches())
    }

    @Test
    fun `listWatches toont titel, url, instructie, status en id-prefix`() {
        tools.createWatch("Vakantiehuis", "https://example.com/huisjes", "een vrij huisje in juli")
        val id = service.list().single().id

        val result = tools.listWatches()
        assertTrue(result.contains("Vakantiehuis"))
        assertTrue(result.contains("https://example.com/huisjes"))
        assertTrue(result.contains("een vrij huisje in juli"))
        assertTrue(result.contains("elk uur overdag"))
        assertTrue(result.contains("NOG_NIET_GECONTROLEERD"))
        assertTrue(result.contains("actief"))
        assertTrue(result.contains("nog niet gecontroleerd"))
        assertTrue(result.contains("[id ${id.take(8)}]"))
    }

    @Test
    fun `createWatch maakt een zoekopdracht aan met de opgegeven gegevens`() {
        val result = tools.createWatch(
            title = "Kaartjes",
            url = "https://example.com/tickets",
            instruction = "kaartjes weer beschikbaar",
            notifyOnFound = false,
        )

        val watch = service.list().single()
        assertEquals("Kaartjes", watch.title)
        assertEquals("https://example.com/tickets", watch.url)
        assertEquals("kaartjes weer beschikbaar", watch.instruction)
        assertFalse(watch.notifyOnFound)
        assertTrue(result.contains("Kaartjes"))
        assertTrue(result.contains("[id ${watch.id.take(8)}]"))
    }

    @Test
    fun `createWatch noemt geen frequentiekeuze meer in de bevestiging`() {
        val result = tools.createWatch("A", "https://example.com/a", "iets")

        assertTrue(result.contains("elk uur overdag"))
        assertFalse(result.contains("kantooruren", ignoreCase = true))
        assertFalse(result.contains("dagelijks", ignoreCase = true))
    }

    @Test
    fun `createWatch geeft een leesbare foutmelding bij een ongeldige URL`() {
        val result = tools.createWatch("Kapot", "geen-url", "iets zoeken")

        assertTrue(result.startsWith("Zoekopdracht niet aangemaakt:"))
        assertTrue(result.contains("Webadres"))
        assertTrue(service.list().isEmpty())
    }

    @Test
    fun `updateWatch meldt het als het id onbekend is`() {
        assertEquals("Geen zoekopdracht gevonden met id abc123.", tools.updateWatch("abc123"))
    }

    @Test
    fun `updateWatch wijzigt alleen de meegegeven velden en zet de watch weer actief`() {
        tools.createWatch(
            title = "Vakantiehuis",
            url = "https://example.com/huisjes",
            instruction = "een vrij huisje in juli",
            notifyOnFound = false,
        )
        // Simuleer een al gevonden, daarmee gedeactiveerde zoekopdracht.
        val original = repository.save(
            service.list().single().copy(
                status = WatchStatus.GEVONDEN,
                statusDescription = "Gevonden!",
                lastCheckedAt = Instant.parse("2026-07-30T08:00:00Z"),
                active = false,
            ),
        )

        val result = tools.updateWatch(original.id.take(8), title = "Vakantiehuis")

        val updated = service.list().single()
        assertEquals("Vakantiehuis", updated.title)
        assertEquals("https://example.com/huisjes", updated.url)
        assertEquals("een vrij huisje in juli", updated.instruction)
        assertFalse(updated.notifyOnFound)
        assertTrue(updated.active)
        assertEquals(WatchStatus.NOG_NIET_GECONTROLEERD, updated.status)
        assertTrue(result.contains("weer op actief"))
        assertTrue(result.contains("opnieuw gecontroleerd"))
        assertFalse(result.contains("kantooruren", ignoreCase = true))
        assertFalse(result.contains("dagelijks", ignoreCase = true))
    }

    @Test
    fun `updateWatch kan de pushvoorkeur driewaardig aanpassen`() {
        tools.createWatch("Kaartjes", "https://example.com/tickets", "iets", notifyOnFound = true)
        val id = service.list().single().id

        tools.updateWatch(id, notifyOnFound = "nee")
        assertFalse(service.list().single().notifyOnFound)

        tools.updateWatch(id, title = "Kaartjes 2")
        assertFalse(service.list().single().notifyOnFound)

        tools.updateWatch(id, notifyOnFound = "ja")
        assertTrue(service.list().single().notifyOnFound)
    }

    @Test
    fun `updateWatch geeft een leesbare foutmelding bij een ongeldige URL`() {
        tools.createWatch("Kaartjes", "https://example.com/tickets", "iets")
        val id = service.list().single().id

        val result = tools.updateWatch(id, url = "geen-url")

        assertTrue(result.startsWith("Zoekopdracht niet aangepast:"))
        assertEquals("https://example.com/tickets", service.list().single().url)
    }
}
