package nl.vdzon.robbertsassistent.notes

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesServiceTest {
    private val repository = InMemoryNotesRepository()
    private var clock = Instant.parse("2026-08-02T10:00:00Z")
    private val service = NotesService(repository) { clock }

    @Test
    fun `starts empty and round-trips an update`() {
        assertEquals("", service.current())

        service.update("Boodschappen: melk, eieren")
        assertEquals("Boodschappen: melk, eieren", service.current())

        service.update("Overschreven")
        assertEquals("Overschreven", service.current())
    }

    @Test
    fun `elke update bewaart een versie met tekst en opslagmoment`() {
        service.update("eerste")
        clock = clock.plusSeconds(60)
        service.update("tweede")

        val versions = service.versions()
        assertEquals(listOf("tweede", "eerste"), versions.map { it.text })
        assertEquals(Instant.parse("2026-08-02T10:01:00Z"), versions.first().savedAt)
        assertEquals(Instant.parse("2026-08-02T10:00:00Z"), versions.last().savedAt)
    }

    @Test
    fun `een tweede update met exact dezelfde tekst voegt geen versie toe`() {
        service.update("zelfde tekst")
        clock = clock.plusSeconds(10)
        service.update("zelfde tekst")

        assertEquals(1, service.versions().size)

        // Wijzigen en weer terug levert wél drie versies op — dat is gewenst voor terugkijken.
        clock = clock.plusSeconds(10)
        service.update("iets anders")
        clock = clock.plusSeconds(10)
        service.update("zelfde tekst")
        assertEquals(3, service.versions().size)
    }

    @Test
    fun `de eerste versie wordt ook bewaard als de tekst leeg is`() {
        service.update("")

        assertEquals(1, service.versions().size)
        assertEquals("", service.versions().single().text)
    }

    @Test
    fun `versie op id ophalen geeft de tekst terug, onbekend id geeft null`() {
        service.update("inhoud van toen")
        val id = service.versions().single().id

        val version = service.version(id)
        assertNotNull(version)
        assertEquals("inhoud van toen", version.text)
        assertNull(service.version("bestaat-niet"))
    }

    @Test
    fun `versies zijn begrensd op tweehonderd, nieuwste eerst`() {
        repeat(205) {
            clock = clock.plusSeconds(60)
            service.update("versie $it")
        }

        val versions = service.versions()
        assertEquals(200, versions.size)
        assertEquals("versie 204", versions.first().text)
        assertEquals("versie 5", versions.last().text)
    }

    @Test
    fun `een falende versie-opslag laat de notitie zelf gewoon opgeslagen`() {
        val brokenVersions = object : NotesRepository by repository {
            override fun addVersion(text: String, savedAt: Instant): NoteVersion =
                throw IllegalStateException("Firestore down")
        }
        val serviceMetKapotteVersies = NotesService(brokenVersions) { clock }

        assertEquals("blijft staan", serviceMetKapotteVersies.update("blijft staan"))
        assertEquals("blijft staan", serviceMetKapotteVersies.current())
        assertTrue(serviceMetKapotteVersies.versions().isEmpty())
    }
}
