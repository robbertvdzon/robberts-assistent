package nl.vdzon.robbertsassistent.notes

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Opslag-poort voor Robberts ene notitie plus de versiegeschiedenis daarvan.
 * Firestore in prod, in-memory fallback zonder Firebase.
 * (Voorheen Postgres/Neon; gemigreerd naar Firestore zodat Neon opgezegd kan worden.)
 */
interface NotesRepository {
    fun current(): String
    fun update(text: String): String

    /** Bewaart [text] als nieuwe versie en geeft het opgeslagen record terug. */
    fun addVersion(text: String, savedAt: Instant): NoteVersion

    /** De [limit] nieuwste versies, nieuwste eerst. */
    fun latestVersions(limit: Int): List<NoteVersion>

    /** Eén versie op id, of `null` als die niet (meer) bestaat. */
    fun version(id: String): NoteVersion?

    /** Alle versies, nieuwste eerst — bedoeld voor het nachtelijke opruimen. */
    fun allVersions(): List<NoteVersion>

    /** Verwijdert de versie met [id]; een onbekend id is geen fout. */
    fun deleteVersion(id: String)
}

class InMemoryNotesRepository : NotesRepository {
    private val note = AtomicReference("")
    private val versions = mutableListOf<NoteVersion>()

    override fun current(): String = note.get()

    override fun update(text: String): String {
        note.set(text)
        return text
    }

    override fun addVersion(text: String, savedAt: Instant): NoteVersion {
        val version = NoteVersion(id = UUID.randomUUID().toString(), text = text, savedAt = savedAt)
        synchronized(versions) { versions.add(version) }
        return version
    }

    override fun latestVersions(limit: Int): List<NoteVersion> = allVersions().take(limit)

    override fun version(id: String): NoteVersion? =
        synchronized(versions) { versions.firstOrNull { it.id == id } }

    // `asReversed()` vóór het (stabiele) sorteren zodat bij een gelijk tijdstip de laatst
    // toegevoegde versie vooraan staat — anders zou een save binnen dezelfde milliseconde
    // als "oudste" gelden.
    override fun allVersions(): List<NoteVersion> =
        synchronized(versions) { versions.asReversed().sortedByDescending { it.savedAt } }

    override fun deleteVersion(id: String) {
        synchronized(versions) { versions.removeAll { it.id == id } }
    }
}
