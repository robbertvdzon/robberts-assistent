package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/watches")
class WatchesController(
    private val auth: AuthService,
    private val service: WatchesService,
) {
    @GetMapping
    fun list(@RequestHeader("Authorization") authorization: String): ResponseEntity<WatchListResponse> {
        auth.requireAuthorization(authorization)
        val watches = service.all().map { it.toDto() }
        return ResponseEntity.ok(WatchListResponse(watches))
    }

    @PostMapping
    fun create(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: CreateWatchRequest,
    ): ResponseEntity<WatchDto> {
        auth.requireAuthorization(authorization)
        val frequency = runCatching { WatchFrequency.valueOf(request.frequency) }
            .getOrElse { return ResponseEntity.badRequest().build() }
        val watch = service.create(
            title = request.title,
            url = request.url,
            instruction = request.instruction,
            frequency = frequency,
        )
        return ResponseEntity.ok(watch.toDto())
    }

    @PatchMapping("/{id}/toggle")
    fun toggle(
        @RequestHeader("Authorization") authorization: String,
        @PathVariable id: String,
    ): ResponseEntity<WatchDto> {
        auth.requireAuthorization(authorization)
        val watch = service.toggle(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(watch.toDto())
    }

    @DeleteMapping("/{id}")
    fun delete(
        @RequestHeader("Authorization") authorization: String,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        auth.requireAuthorization(authorization)
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    private fun Watch.toDto() = WatchDto(
        id = id,
        title = title,
        url = url,
        instruction = instruction,
        frequency = frequency.name,
        status = status.name,
        active = active,
        lastChecked = lastChecked?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}

data class WatchListResponse(val watches: List<WatchDto>)

data class WatchDto(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: String,
    val status: String,
    val active: Boolean,
    val lastChecked: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateWatchRequest(
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: String,
)
