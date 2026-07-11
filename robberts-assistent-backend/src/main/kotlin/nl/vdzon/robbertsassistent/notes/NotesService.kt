package nl.vdzon.robbertsassistent.notes

import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

/**
 * Bewaart de ene notitie-string in het geheugen van dit proces. Bewust géén database: bij een
 * herstart van de backend is de inhoud dus weg — dat is voor nu acceptabel, dit is de eenvoudigste
 * opzet totdat er echte persistentie nodig is.
 */
@Service
class NotesService {
    private val notes = AtomicReference("")

    fun current(): String = notes.get()

    fun update(text: String): String {
        notes.set(text)
        return notes.get()
    }
}
