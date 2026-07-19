package nl.vdzon.robbertsassistent.notes

import org.springframework.stereotype.Service

/**
 * Bewaart de ene notitie-string via [NotesRepository] (Firestore in prod, in-memory als fallback).
 */
@Service
class NotesService(private val repository: NotesRepository) {

    fun current(): String = repository.current()

    fun update(text: String): String = repository.update(text)
}
