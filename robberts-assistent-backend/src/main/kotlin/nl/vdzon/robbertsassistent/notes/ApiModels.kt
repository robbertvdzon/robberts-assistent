package nl.vdzon.robbertsassistent.notes

data class NotesResponse(val text: String)
data class NotesUpdateRequest(val text: String = "")

/** Eén regel in het versie-overzicht: bewust zónder tekst, dat scheelt een hoop bytes. */
data class NoteVersionSummary(val id: String, val savedAt: String)
data class NoteVersionsResponse(val versions: List<NoteVersionSummary>)
data class NoteVersionResponse(val id: String, val savedAt: String, val text: String)
