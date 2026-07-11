package nl.vdzon.robbertsassistent.notes

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesServiceTest {
    private lateinit var service: NotesService

    @BeforeTest
    fun setUp() {
        // Zelfde H2-in-memory-pad als lokaal draaien zonder RA_DATABASE_URL, maar hier
        // per test een verse, geïsoleerde database met de echte Flyway-migratie erop.
        val dataSource = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build()
        Flyway.configure().dataSource(dataSource).load().migrate()
        service = NotesService(JdbcTemplate(dataSource))
    }

    @Test
    fun `starts empty and round-trips an update`() {
        assertEquals("", service.current())

        service.update("Boodschappen: melk, eieren")
        assertEquals("Boodschappen: melk, eieren", service.current())

        service.update("Overschreven")
        assertEquals("Overschreven", service.current())
    }
}
