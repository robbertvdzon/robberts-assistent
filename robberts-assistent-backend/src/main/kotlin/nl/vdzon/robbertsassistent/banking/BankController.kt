package nl.vdzon.robbertsassistent.banking

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.YearMonth
import java.time.ZoneId

data class BankStatus(val configured: Boolean, val accounts: List<BankAccountView>, val lastSync: String?, val warnings: List<String>)
data class BankAccountView(val id: String, val iban: String, val name: String, val validUntil: String)
data class BankCategoryRequest(val category: BankCategory)
@RestController
@RequestMapping("/api/v1/banking")
class BankController(private val auth: AuthService, private val bank: BankService, private val settings: BankSettings) {
    private fun owner(header: String?): String = auth.requireAuthorization(header).also { bank.requireOwner(it) }
    private fun <T> privateResponse(body: T) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
    @GetMapping fun status(@RequestHeader("Authorization", required = false) header: String?): ResponseEntity<BankStatus> {
        val identity = auth.requireAuthorization(header)
        if (!settings.configured) return privateResponse(BankStatus(false, emptyList(), null, listOf("Bankkoppeling is nog niet ingesteld.")))
        bank.requireOwner(identity)
        return privateResponse(statusFor(identity))
    }
    private fun statusFor(owner: String): BankStatus {
        val connection = bank.connection(owner)
        return BankStatus(true, connection.accounts.map { BankAccountView(it.id, it.iban, it.name, it.validUntil.toString()) },
            connection.lastSync?.toString(), connection.warnings)
    }
    @PostMapping("/connect") fun connect(@RequestHeader("Authorization", required = false) header: String?) =
        privateResponse(mapOf("url" to bank.connect(owner(header))))
    @PostMapping("/sync") fun sync(@RequestHeader("Authorization", required = false) header: String?): ResponseEntity<BankStatus> {
        val owner = owner(header)
        bank.sync(owner)
        return privateResponse(statusFor(owner))
    }
    @GetMapping("/month") fun month(@RequestHeader("Authorization", required = false) header: String?,
        @RequestParam(required = false) month: String?) = privateResponse(bank.month(owner(header), month ?: YearMonth.now(ZoneId.of("Europe/Amsterdam")).toString()))
    @GetMapping("/rules") fun rules(@RequestHeader("Authorization", required = false) header: String?) = privateResponse(bank.rules(owner(header)))
    @PutMapping("/rules") fun saveRules(@RequestHeader("Authorization", required = false) header: String?, @RequestBody rules: BankRules) =
        privateResponse(bank.saveRules(owner(header), rules))
    @PutMapping("/month/{month}/transactions/{id}/category") fun classify(@RequestHeader("Authorization", required = false) header: String?,
        @PathVariable month: String, @PathVariable id: String, @RequestBody request: BankCategoryRequest): ResponseEntity<Map<String, Boolean>> {
        bank.classify(owner(header), month, id, request.category)
        return privateResponse(mapOf("saved" to true))
    }
    /** Callback vertrouwt alleen kortlevende, eenmalige server-state, nooit een owner uit de URL. */
    @GetMapping("/callback", produces = [MediaType.TEXT_HTML_VALUE])
    fun callback(@RequestParam state: String, @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?): ResponseEntity<String> {
        val success = runCatching { bank.callback(state, code, error) }.getOrDefault(false)
        val message = if (success) "Banktoestemming opgeslagen. Je kunt terug naar Bankzaken in de assistent." else "Koppelen is niet gelukt of de link is verlopen. Begin opnieuw vanuit Bankzaken."
        return ResponseEntity.status(if (success) 200 else 400).cacheControl(CacheControl.noStore())
            .header("Referrer-Policy", "no-referrer").header("Content-Security-Policy", "default-src 'none'; style-src 'none'; frame-ancestors 'none'; base-uri 'none'")
            .body("<!doctype html><html lang=\"nl\"><meta charset=\"utf-8\"><title>Bankkoppeling</title><h1>Bankkoppeling</h1><p>$message</p><a href=\"/\">Terug naar de assistent</a></html>")
    }
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid() = ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("message" to "Ongeldige bankgegevens of rubriceringsregel."))
    @ExceptionHandler(BankBusyException::class)
    fun busy() = ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(mapOf("message" to "Bankzaken worden al bijgewerkt. Probeer straks opnieuw."))
    @ExceptionHandler(BankUnavailableException::class)
    fun unavailable() = ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore()).body(mapOf("message" to "Ophalen is niet gelukt. Probeer later opnieuw of vernieuw je banktoestemming."))
}
