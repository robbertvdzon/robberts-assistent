package nl.vdzon.robbertsassistent.notes

data class NotesResponse(val text: String)
data class NotesUpdateRequest(val text: String = "")
