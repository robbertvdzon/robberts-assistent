package nl.vdzon.robbertsassistent.notes

import java.util.concurrent.atomic.AtomicReference

/**
 * Opslag-poort voor Robberts ene notitie. Firestore in prod, in-memory fallback zonder Firebase.
 * (Voorheen Postgres/Neon; gemigreerd naar Firestore zodat Neon opgezegd kan worden.)
 */
interface NotesRepository {
    fun current(): String
    fun update(text: String): String
}

class InMemoryNotesRepository : NotesRepository {
    private val note = AtomicReference("")
    override fun current(): String = note.get()
    override fun update(text: String): String {
        note.set(text)
        return text
    }
}
