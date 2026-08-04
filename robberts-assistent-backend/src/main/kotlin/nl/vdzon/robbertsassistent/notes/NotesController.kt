package nl.vdzon.robbertsassistent.notes

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class NotesController(
    private val authService: AuthService,
    private val notesService: NotesService,
) {
    // --- Bestaande endpoints; werken sinds SF-1892 op het standaarddocument ('todo'). ---

    @GetMapping("/api/v1/notes")
    fun get(@RequestHeader("Authorization", required = false) authorization: String?): NotesResponse {
        authService.requireAuthorization(authorization)
        return NotesResponse(text = notesService.current())
    }

    @PutMapping("/api/v1/notes")
    fun update(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: NotesUpdateRequest,
    ): NotesResponse {
        authService.requireAuthorization(authorization)
        return NotesResponse(text = notesService.update(request.text))
    }

    /** Versie-overzicht, nieuwste eerst, maximaal [NotesService.MAX_VERSIONS]; zonder tekst. */
    @GetMapping("/api/v1/notes/versions")
    fun versions(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): NoteVersionsResponse {
        authService.requireAuthorization(authorization)
        return summaries(notesService.versions())
    }

    /** De volledige markdown-tekst van één versie; onbekend id => 404. */
    @GetMapping("/api/v1/notes/versions/{id}")
    fun version(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): NoteVersionResponse {
        authService.requireAuthorization(authorization)
        return versionResponse(notesService.version(id))
    }

    // --- Documenten (SF-1892). Let op: `/order` staat vóór `/{id}`. ---

    /** Alle documenten op volgorde; triggert de migratie naar het 'todo'-document. */
    @GetMapping("/api/v1/notes/documents")
    fun documents(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): NoteDocumentsResponse {
        authService.requireAuthorization(authorization)
        return NoteDocumentsResponse(
            documents = notesService.documents().map {
                NoteDocumentSummary(id = it.id, title = it.title, order = it.order)
            },
        )
    }

    /** Nieuw, leeg document onderaan de volgorde; lege titel => 400, dubbele titel => 409. */
    @PostMapping("/api/v1/notes/documents")
    fun createDocument(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: NoteDocumentTitleRequest,
    ): NoteDocumentSummary {
        authService.requireAuthorization(authorization)
        val document = notesService.createDocument(request.title)
        return NoteDocumentSummary(id = document.id, title = document.title, order = document.order)
    }

    /** Nieuwe volgorde; een onbekend id => 404. */
    @PutMapping("/api/v1/notes/documents/order")
    fun reorderDocuments(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: NoteDocumentOrderRequest,
    ): NoteDocumentsResponse {
        authService.requireAuthorization(authorization)
        notesService.reorder(request.ids)
        return documents(authorization)
    }

    /** Hernoemen; bewust een eigen pad, zodat het niet botst met het opslaan van tekst. */
    @PutMapping("/api/v1/notes/documents/{id}/title")
    fun renameDocument(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: NoteDocumentTitleRequest,
    ): NoteDocumentSummary {
        authService.requireAuthorization(authorization)
        val document = notesService.renameDocument(id, request.title)
        return NoteDocumentSummary(id = document.id, title = document.title, order = document.order)
    }

    /** Verwijdert het document inclusief versies; het laatste document verwijderen => 409. */
    @DeleteMapping("/api/v1/notes/documents/{id}")
    fun deleteDocument(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): NoteDocumentsResponse {
        authService.requireAuthorization(authorization)
        notesService.deleteDocument(id)
        return documents(authorization)
    }

    @GetMapping("/api/v1/notes/documents/{id}")
    fun document(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): NoteDocumentResponse {
        authService.requireAuthorization(authorization)
        val document = notesService.document(id)
        return NoteDocumentResponse(id = document.id, title = document.title, text = document.text)
    }

    /** Tekst opslaan (+ versie-record); onbekend id => 404. */
    @PutMapping("/api/v1/notes/documents/{id}")
    fun updateDocument(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: NoteDocumentTextRequest,
    ): NoteDocumentResponse {
        authService.requireAuthorization(authorization)
        val text = notesService.updateText(id, request.text)
        return NoteDocumentResponse(id = id, title = notesService.document(id).title, text = text)
    }

    @GetMapping("/api/v1/notes/documents/{id}/versions")
    fun documentVersions(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): NoteVersionsResponse {
        authService.requireAuthorization(authorization)
        return summaries(notesService.versions(id))
    }

    @GetMapping("/api/v1/notes/documents/{id}/versions/{versionId}")
    fun documentVersion(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @PathVariable versionId: String,
    ): NoteVersionResponse {
        authService.requireAuthorization(authorization)
        return versionResponse(notesService.version(id, versionId))
    }

    private fun summaries(versions: List<NoteVersion>) = NoteVersionsResponse(
        versions = versions.map { NoteVersionSummary(id = it.id, savedAt = it.savedAt.toString()) },
    )

    private fun versionResponse(version: NoteVersion?): NoteVersionResponse {
        val found = version ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Versie niet gevonden")
        return NoteVersionResponse(
            id = found.id,
            savedAt = found.savedAt.toString(),
            text = found.text,
        )
    }

    // Vertaalt de module-eigen fouten naar HTTP-statussen; de service zelf blijft zo web-vrij.
    @ExceptionHandler(NoteDocumentNotFoundException::class)
    fun handleNotFound(error: NoteDocumentNotFoundException) = errorBody(HttpStatus.NOT_FOUND, error)

    @ExceptionHandler(NoteTitleInvalidException::class)
    fun handleInvalidTitle(error: NoteTitleInvalidException) = errorBody(HttpStatus.BAD_REQUEST, error)

    @ExceptionHandler(NoteDocumentConflictException::class)
    fun handleConflict(error: NoteDocumentConflictException) = errorBody(HttpStatus.CONFLICT, error)

    private fun errorBody(status: HttpStatus, error: Throwable): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).body(mapOf("error" to (error.message ?: status.reasonPhrase)))
}
