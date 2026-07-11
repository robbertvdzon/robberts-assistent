package nl.vdzon.robbertsassistent.notes

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

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
}
