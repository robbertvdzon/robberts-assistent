package nl.vdzon.robbertsassistent.notes

/**
 * Eén notitiedocument. [id] is ondoorzichtig (`note` voor het gemigreerde standaarddocument,
 * Firestore-auto-ids voor de rest), [order] bepaalt de volgorde in de app (dicht, 0..n-1) en
 * [text] is de volledige platte markdown-inhoud.
 */
data class NoteDocument(
    val id: String,
    val title: String,
    val order: Int,
    val text: String = "",
)

/**
 * Het standaarddocument: het bestaande Firestore-document `notes/note` dat bij de migratie de
 * titel 'todo' krijgt. De oude endpoints, `briefing.WeekTasksSectionProvider` en de naamloze
 * AI-tools werken hierop — de identiteit hangt aan het id, niet aan de titel.
 */
const val DEFAULT_DOCUMENT_ID = "note"

/** Titel die het standaarddocument bij de migratie krijgt. */
const val DEFAULT_DOCUMENT_TITLE = "todo"

/** Maximale lengte van een documenttitel; houdt naam-matching in de chat behapbaar. */
const val MAX_TITLE_LENGTH = 60

/** Onbekend document- of versie-id => 404. */
class NoteDocumentNotFoundException(message: String) : RuntimeException(message)

/** Lege/te lange titel => 400. */
class NoteTitleInvalidException(message: String) : RuntimeException(message)

/** Dubbele titel of het laatste document verwijderen => 409. */
class NoteDocumentConflictException(message: String) : RuntimeException(message)
