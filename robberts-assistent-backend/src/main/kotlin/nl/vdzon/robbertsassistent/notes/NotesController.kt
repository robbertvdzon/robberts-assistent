package nl.vdzon.robbertsassistent.notes

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
        return NoteVersionsResponse(
            versions = notesService.versions().map {
                NoteVersionSummary(id = it.id, savedAt = it.savedAt.toString())
            },
        )
    }

    /** De volledige markdown-tekst van één versie; onbekend id => 404. */
    @GetMapping("/api/v1/notes/versions/{id}")
    fun version(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): NoteVersionResponse {
        authService.requireAuthorization(authorization)
        val version = notesService.version(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Versie niet gevonden")
        return NoteVersionResponse(
            id = version.id,
            savedAt = version.savedAt.toString(),
            text = version.text,
        )
    }
}
