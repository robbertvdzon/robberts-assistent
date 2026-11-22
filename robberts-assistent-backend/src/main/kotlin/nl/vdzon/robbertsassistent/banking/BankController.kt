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

data class BankStatus(val configured: Boolean, val accounts: List<BankAccountView>, val lastSync: String?, val warnings: List<String>, val availableAccounts: List<BankAccountChoice> = emptyList())
data class BankAccountView(val id: String, val iban: String, val name: String, val validUntil: String)
data class BankAccountChoice(val iban: String, val connected: Boolean)
data class BankConnectRequest(val iban: String? = null)
data class BankCategoryRequest(val category: BankCategory, val remember: Boolean = false, val matchText: String = "")
@RestController
@RequestMapping("/api/v1/banking")
class BankController(private val auth: AuthService, private val bank: BankService, private val settings: BankSettings, private val categorization: BankCategorizationRunner? = null) {
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)
    private fun owner(header: String?): String = bank.resolveOwner(auth.requireAuthorization(header))
    private fun <T> privateResponse(body: T) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
    @GetMapping fun status(@RequestHeader("Authorization", required = false) header: String?): ResponseEntity<BankStatus> {
        val identity = auth.requireAuthorization(header)
        if (!settings.configured) return privateResponse(BankStatus(false, emptyList(), null, listOf("Bankkoppeling is nog niet ingesteld.")))
        return privateResponse(statusFor(bank.resolveOwner(identity)))
    }
    private fun statusFor(owner: String): BankStatus {
        val connection = bank.connection(owner)
        return BankStatus(true, connection.accounts.map { BankAccountView(it.id, it.iban, it.name, it.validUntil.toString()) },
            connection.lastSync?.toString(), connection.warnings,
            (bank.rules(owner).ownIbans + connection.accounts.map { it.iban }).filter { it.isNotBlank() }.distinct()
                .map { iban -> BankAccountChoice(iban, connection.accounts.any { it.iban == iban }) })
    }
    @PostMapping("/connect") fun connect(@RequestHeader("Authorization", required = false) header: String?, @RequestBody(required = false) request: BankConnectRequest?) =
        privateResponse(mapOf("url" to bank.connect(owner(header), request?.iban)))
    @PostMapping("/sync") fun sync(@RequestHeader("Authorization", required = false) header: String?): ResponseEntity<BankStatus> {
        val owner = owner(header)
        bank.sync(owner)
        return privateResponse(statusFor(owner))
    }
    @GetMapping("/month") fun month(@RequestHeader("Authorization", required = false) header: String?,
        @RequestParam(required = false) month: String?) = privateResponse(bank.month(owner(header), month ?: YearMonth.now(ZoneId.of("Europe/Amsterdam")).toString()))
    @GetMapping("/rules") fun rules(@RequestHeader("Authorization", required = false) header: String?) = privateResponse(bank.rules(owner(header)))
    @GetMapping("/history") fun history(@RequestHeader("Authorization", required = false) header: String?,
        @RequestParam(required = false) end: String?) = privateResponse(bank.history(owner(header),
            end ?: YearMonth.now(ZoneId.of("Europe/Amsterdam")).toString()))
    @GetMapping("/planning") fun planning(@RequestHeader("Authorization", required = false) header: String?,
        @RequestParam year: Int) = privateResponse(bank.planning(owner(header), year))
    @PutMapping("/planning") fun savePlanning(@RequestHeader("Authorization", required = false) header: String?,
        @RequestParam year: Int, @RequestBody planning: BankPlanning) = privateResponse(bank.savePlanning(owner(header), year, planning))
    @PutMapping("/rules") fun saveRules(@RequestHeader("Authorization", required = false) header: String?, @RequestBody rules: BankRules) =
        privateResponse(bank.saveRules(owner(header), rules))
    @GetMapping("/review-merchants") fun reviewMerchants(@RequestHeader("Authorization", required = false) header: String?) =
        privateResponse(bank.reviewMerchants(owner(header)))
    @PutMapping("/review-merchants/{key}") fun classifyMerchant(@RequestHeader("Authorization", required = false) header: String?,
        @PathVariable key: String, @RequestBody request: BankReviewMerchantRequest) =
        privateResponse(bank.classifyMerchant(owner(header), key, request.category))
    @GetMapping("/review") fun review(@RequestHeader("Authorization", required = false) header: String?) =
        privateResponse(bank.reviewList(owner(header)))
    @PostMapping("/review/classify") fun classifyReview(@RequestHeader("Authorization", required = false) header: String?,
        @RequestBody request: BankReviewClassifyRequest) = privateResponse(bank.classifyReview(owner(header), request))
    @PutMapping("/month/{month}/transactions/{id}/category") fun classify(@RequestHeader("Authorization", required = false) header: String?,
        @PathVariable month: String, @PathVariable id: String, @RequestBody request: BankCategoryRequest): ResponseEntity<Map<String, Boolean>> {
        bank.classify(owner(header), month, id, request.category, request.remember, request.matchText)
        return privateResponse(mapOf("saved" to true))
    }
    @GetMapping("/categorization") fun categorization(@RequestHeader("Authorization", required = false) header: String?): ResponseEntity<BankCategorizationJob> {
        val owner = owner(header)
        return privateResponse(categorization?.status(owner) ?: BankCategorizationJob())
    }
    @PostMapping("/categorize") fun categorize(@RequestHeader("Authorization", required = false) header: String?): ResponseEntity<BankCategorizationJob> {
        val owner = owner(header)
        return privateResponse(categorization?.start(owner) ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
    }
    /** Callback vertrouwt alleen kortlevende, eenmalige server-state, nooit een owner uit de URL. */
    @GetMapping("/callback", produces = [MediaType.TEXT_HTML_VALUE])
    fun callback(@RequestParam state: String, @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?): ResponseEntity<String> {
        var status = 400
        var portal = false
        val message = try {
            if (bank.callback(state, code, error)) {
                status = 200
                "Banktoestemming opgeslagen. Je kunt terug naar Bankzaken in de assistent."
            } else {
                logger.warn("Bankcallback geweigerd: ongeldig/verlopen state of geen toestemming")
                if (error != null) "De banktoestemming is onderbroken of geweigerd. Begin opnieuw vanuit Bankzaken."
                else "Deze koppellink is verlopen of al gebruikt. Open Bankzaken en begin een nieuwe koppeling; ververs deze callbackpagina niet."
            }
        } catch (ex: Exception) {
            val causes = generateSequence<Throwable>(ex) { it.cause }.take(10).toList()
            val provider = causes.filterIsInstance<BankUnavailableException>().firstOrNull()
            // Geen exceptionberichten: die kunnen rekening-id's of providerdata bevatten.
            logger.warn("Bankcallback mislukt: type={}, providerStatus={}", causes.last().javaClass.simpleName, provider?.httpStatus)
            when {
                causes.any { it is BankNoAccountsException } ->
                    "De bank heeft geen rekeningen teruggegeven voor deze toestemming. Start opnieuw vanuit Bankzaken via Nieuwe rekening toevoegen en gebruik het IBAN van de gewenste rekening. Laat de rekeninghouder bij de bank toestemming geven."
                provider?.httpStatus == 403 -> {
                    status = 403
                    portal = true
                    "Enable Banking weigert toegang tot deze rekening. Geef de rekening eerst vrij in de Enable Banking-portal via API applications → Robberts Assistent → Link accounts. Geef daar zelf banktoestemming. Start daarna een nieuwe koppeling vanuit Bankzaken. Toestemming vanuit de assistent alleen voegt een rekening niet toe aan de portalvrijgave."
                }
                causes.any { it is BankBusyException } -> {
                    status = 409
                    "Je bankadministratie wordt nog bijgewerkt. Wacht tot dat klaar is en begin daarna een nieuwe koppeling vanuit Bankzaken."
                }
                provider != null -> {
                    status = 502
                    portal = true
                    "Enable Banking kon de banktoestemming niet verwerken. Controleer eerst in de Enable Banking-portal of deze rekening bij Linked accounts staat. Probeer daarna een nieuwe koppeling vanuit Bankzaken."
                }
                else -> "De banktoestemming kon niet worden opgeslagen. Begin opnieuw vanuit Bankzaken. Als dit terugkomt, laat de koppeling onderzoeken."
            }
        }
        val portalLink = if (portal) "<p><a href=\"https://enablebanking.com/cp/applications\" rel=\"noreferrer\">Open Enable Banking-portal</a></p>" else ""
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .header("Referrer-Policy", "no-referrer").header("Content-Security-Policy", "default-src 'none'; style-src 'none'; frame-ancestors 'none'; base-uri 'none'")
            .body("<!doctype html><html lang=\"nl\"><meta charset=\"utf-8\"><title>Bankkoppeling</title><h1>Bankkoppeling</h1><p>$message</p>$portalLink<a href=\"/#/meer/bankzaken\">Terug naar Bankzaken</a></html>")
    }
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid() = ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("message" to "Ongeldige bankgegevens of rubriceringsregel."))
    @ExceptionHandler(BankBusyException::class)
    fun busy() = ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(mapOf("message" to "Bankzaken worden al bijgewerkt. Probeer straks opnieuw."))
    @ExceptionHandler(BankUnavailableException::class)
    fun unavailable() = ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore()).body(mapOf("message" to "Ophalen is niet gelukt. Probeer later opnieuw of vernieuw je banktoestemming."))
}
