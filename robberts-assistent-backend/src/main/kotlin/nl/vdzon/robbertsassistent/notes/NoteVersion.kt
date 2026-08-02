package nl.vdzon.robbertsassistent.notes

import java.time.Instant

/**
 * Eén bewaarde versie van de notitie: de volledige markdown-tekst zoals die op [savedAt] is
 * opgeslagen. [id] is een ondoorzichtige string (Firestore-auto-id of een UUID in-memory).
 */
data class NoteVersion(
    val id: String,
    val text: String,
    val savedAt: Instant,
)
