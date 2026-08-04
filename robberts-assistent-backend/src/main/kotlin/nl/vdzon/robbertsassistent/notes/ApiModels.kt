package nl.vdzon.robbertsassistent.notes

data class NotesResponse(val text: String)
data class NotesUpdateRequest(val text: String = "")

/** Eén regel in het versie-overzicht: bewust zónder tekst, dat scheelt een hoop bytes. */
data class NoteVersionSummary(val id: String, val savedAt: String)
data class NoteVersionsResponse(val versions: List<NoteVersionSummary>)
data class NoteVersionResponse(val id: String, val savedAt: String, val text: String)

/** Eén regel in het documenten-overzicht: bewust zónder tekst. */
data class NoteDocumentSummary(val id: String, val title: String, val order: Int)
data class NoteDocumentsResponse(val documents: List<NoteDocumentSummary>)
data class NoteDocumentResponse(val id: String, val title: String, val text: String)

data class NoteDocumentTitleRequest(val title: String = "")
data class NoteDocumentTextRequest(val text: String = "")
data class NoteDocumentOrderRequest(val ids: List<String> = emptyList())
