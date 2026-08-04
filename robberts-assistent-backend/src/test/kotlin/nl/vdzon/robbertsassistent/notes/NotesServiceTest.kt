package nl.vdzon.robbertsassistent.notes

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            override fun addVersion(documentId: String, text: String, savedAt: Instant): NoteVersion =
                throw IllegalStateException("Firestore down")
        }
        val serviceMetKapotteVersies = NotesService(brokenVersions) { clock }

        assertEquals("blijft staan", serviceMetKapotteVersies.update("blijft staan"))
        assertEquals("blijft staan", serviceMetKapotteVersies.current())
        assertTrue(serviceMetKapotteVersies.versions().isEmpty())
    }

    // --- Documenten (SF-1892) ---

    @Test
    fun `migratie levert een leeg todo-document op en is idempotent`() {
        val eerst = service.documents()
        assertEquals(listOf(DEFAULT_DOCUMENT_TITLE), eerst.map { it.title })
        assertEquals(DEFAULT_DOCUMENT_ID, eerst.single().id)
        assertEquals(0, eerst.single().order)
        assertEquals("", eerst.single().text)

        assertEquals(1, service.documents().size)
        assertEquals(1, service.documents().count { it.title == DEFAULT_DOCUMENT_TITLE })
    }

    @Test
    fun `migratie behoudt de bestaande notitietekst en de bestaande versies`() {
        val bestaand = InMemoryNotesRepository(legacyText = "oude notitie")
        val bestaandeVersie = bestaand.addVersion(DEFAULT_DOCUMENT_ID, "oude notitie", clock)
        val migrerend = NotesService(bestaand) { clock }

        val documenten = migrerend.documents()

        assertEquals(DEFAULT_DOCUMENT_TITLE, documenten.single().title)
        assertEquals("oude notitie", documenten.single().text)
        assertEquals("oude notitie", migrerend.current())
        assertEquals(listOf(bestaandeVersie.id), migrerend.versions().map { it.id })
    }

    @Test
    fun `een nieuw document is leeg en komt onderaan de volgorde`() {
        val recepten = service.createDocument(" recepten ")

        assertEquals("recepten", recepten.title)
        assertEquals(1, recepten.order)
        assertEquals("", recepten.text)
        assertEquals(listOf("todo", "recepten"), service.documents().map { it.title })
    }

    @Test
    fun `hernoemen wijzigt alleen de titel`() {
        val recepten = service.createDocument("recepten")

        service.renameDocument(recepten.id, "Kookboek")

        assertEquals("Kookboek", service.document(recepten.id).title)
        assertEquals(listOf("todo", "Kookboek"), service.documents().map { it.title })
    }

    @Test
    fun `verwijderen haalt het document en zijn versies weg`() {
        val recepten = service.createDocument("recepten")
        service.updateText(recepten.id, "pannenkoeken")
        assertEquals(1, service.versions(recepten.id).size)

        service.deleteDocument(recepten.id)

        assertEquals(listOf("todo"), service.documents().map { it.title })
        assertTrue(repository.allVersions(recepten.id).isEmpty())
    }

    @Test
    fun `herordenen levert dichte posities op en zet niet-genoemde documenten erachter`() {
        val recepten = service.createDocument("recepten")
        val klussen = service.createDocument("klussen")

        service.reorder(listOf(klussen.id, recepten.id))

        val documenten = service.documents()
        assertEquals(listOf("klussen", "recepten", "todo"), documenten.map { it.title })
        assertEquals(listOf(0, 1, 2), documenten.map { it.order })
    }

    @Test
    fun `tekst en versies zijn per document gescheiden`() {
        val recepten = service.createDocument("recepten")
        service.updateText(DEFAULT_DOCUMENT_ID, "todo-tekst")
        clock = clock.plusSeconds(60)
        service.updateText(recepten.id, "recepten-tekst")

        assertEquals("todo-tekst", service.document(DEFAULT_DOCUMENT_ID).text)
        assertEquals("recepten-tekst", service.document(recepten.id).text)
        assertEquals(listOf("todo-tekst"), service.versions(DEFAULT_DOCUMENT_ID).map { it.text })
        assertEquals(listOf("recepten-tekst"), service.versions(recepten.id).map { it.text })

        val receptVersie = service.versions(recepten.id).single()
        assertNull(service.version(DEFAULT_DOCUMENT_ID, receptVersie.id))
        assertNotNull(service.version(recepten.id, receptVersie.id))
    }

    @Test
    fun `dubbel opslaan van dezelfde tekst voegt per document geen versie toe`() {
        val recepten = service.createDocument("recepten")
        service.updateText(recepten.id, "zelfde")
        clock = clock.plusSeconds(10)
        service.updateText(recepten.id, "zelfde")

        assertEquals(1, service.versions(recepten.id).size)
    }

    @Test
    fun `een onbekend document-id geeft een niet-gevonden-fout`() {
        assertFailsWith<NoteDocumentNotFoundException> { service.document("bestaat-niet") }
        assertFailsWith<NoteDocumentNotFoundException> { service.updateText("bestaat-niet", "x") }
        assertFailsWith<NoteDocumentNotFoundException> { service.versions("bestaat-niet") }
        assertFailsWith<NoteDocumentNotFoundException> { service.deleteDocument("bestaat-niet") }
        assertFailsWith<NoteDocumentNotFoundException> { service.renameDocument("bestaat-niet", "x") }
        assertFailsWith<NoteDocumentNotFoundException> { service.reorder(listOf("bestaat-niet")) }
    }

    @Test
    fun `een lege of te lange titel is ongeldig`() {
        assertFailsWith<NoteTitleInvalidException> { service.createDocument("   ") }
        assertFailsWith<NoteTitleInvalidException> { service.createDocument("a".repeat(61)) }
        assertEquals(60, service.createDocument("a".repeat(60)).title.length)
    }

    @Test
    fun `een dubbele titel geeft een conflict, ook met andere hoofdletters`() {
        service.createDocument("recepten")

        assertFailsWith<NoteDocumentConflictException> { service.createDocument(" RECEPTEN ") }

        val klussen = service.createDocument("klussen")
        assertFailsWith<NoteDocumentConflictException> { service.renameDocument(klussen.id, "Recepten") }
        // Hernoemen naar de eigen titel mag wel.
        assertEquals("klussen", service.renameDocument(klussen.id, "klussen").title)
    }

    @Test
    fun `het laatste document kan niet verwijderd worden`() {
        val todo = service.documents().single()

        assertFailsWith<NoteDocumentConflictException> { service.deleteDocument(todo.id) }

        assertEquals(listOf(todo.id), service.documents().map { it.id })
    }

    @Test
    fun `de oude notitie-API blijft op het standaarddocument werken`() {
        val recepten = service.createDocument("recepten")
        service.updateText(recepten.id, "pannenkoeken")

        service.update("todo-tekst")

        assertEquals("todo-tekst", service.current())
        assertEquals("todo-tekst", service.document(DEFAULT_DOCUMENT_ID).text)
        assertEquals(listOf("todo-tekst"), service.versions().map { it.text })
    }

    @Test
    fun `zonder standaarddocument valt de oude API terug op het eerste document`() {
        val recepten = service.createDocument("recepten")
        service.deleteDocument(DEFAULT_DOCUMENT_ID)

        service.update("nu hier")

        assertEquals("nu hier", service.current())
        assertEquals(recepten.id, service.defaultDocument().id)
    }
}
