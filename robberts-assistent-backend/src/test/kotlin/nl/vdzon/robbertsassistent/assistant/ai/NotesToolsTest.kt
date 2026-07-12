package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.notes.NotesService
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Zelfde H2-in-memory-opzet als [nl.vdzon.robbertsassistent.notes.NotesServiceTest]. */
class NotesToolsTest {
    private lateinit var tools: NotesTools

    @BeforeTest
    fun setUp() {
        val dataSource = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build()
        Flyway.configure().dataSource(dataSource).load().migrate()
        tools = NotesTools(NotesService(JdbcTemplate(dataSource)))
    }

    @Test
    fun `getNotes meldt expliciet dat de notitie leeg is`() {
        assertEquals("(de notitie is leeg)", tools.getNotes())
    }

    @Test
    fun `updateNotes overschrijft en getNotes leest terug`() {
        tools.updateNotes("Boodschappen: melk, eieren")

        assertEquals("Boodschappen: melk, eieren", tools.getNotes())
    }
}
