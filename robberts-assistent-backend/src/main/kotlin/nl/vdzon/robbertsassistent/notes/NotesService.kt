package nl.vdzon.robbertsassistent.notes

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * Bewaart de ene notitie-string in de database (tabel `notes`, altijd rij-id 1 — zie
 * V1__create_notes_table.sql). Postgres in productie (RA_DATABASE_URL), H2 in-memory
 * lokaal/tests wanneer die niet gezet is (zie application.yml).
 */
@Service
class NotesService(private val jdbcTemplate: JdbcTemplate) {

    fun current(): String =
        jdbcTemplate.query("SELECT text FROM notes WHERE id = 1") { rs, _ -> rs.getString("text") }
            .firstOrNull() ?: ""

    fun update(text: String): String {
        jdbcTemplate.update("UPDATE notes SET text = ? WHERE id = 1", text)
        return text
    }
}
