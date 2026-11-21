package nl.vdzon.robbertsassistent.banking

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

/**
 * Betaalcontrole voor verkoopfacturen: de Rompslomp-export levert de facturen, de bankkoppeling
 * de bijschrijvingen. Er wordt niets naar Rompslomp of de bank teruggeschreven.
 */
@Service
class BankInvoiceService(private val bank: BankService, private val store: BankStore) {
    private val key = "invoices"
    private fun record(owner: String) = store.read(owner, key, BankInvoiceRecord::class.java) ?: BankInvoiceRecord()

    fun report(owner: String, today: LocalDate = LocalDate.now(ZoneId.of("Europe/Amsterdam"))): BankInvoiceReport {
        val transactions = bank.allTransactions(owner)
        val record = record(owner)
        val views = BankInvoiceMatcher.match(record.invoices, transactions, today)
        fun total(rows: List<BankInvoiceView>) = BankInvoiceTotal(rows.size, rows.sumOf { it.amount })
        val unsettled = views.filter { it.status == BankInvoiceStatus.OPEN || it.status == BankInvoiceStatus.CHECK }
        val credits = transactions.filter { it.amount.signum() > 0 }.map { it.date }
        val first = record.invoices.minOfOrNull { it.date }
        val warnings = buildList {
            if (record.invoices.isEmpty()) add("Nog geen facturen ingelezen. Exporteer in Rompslomp via Facturen → Exporteer → Download als .xls en lees dat bestand hier in.")
            else if (credits.isEmpty()) add("Er zijn nog geen bijschrijvingen bekend. Koppel de rekening waarop deze facturen betaald worden; tot die tijd staat alles open.")
            else if (first != null && credits.min() > first) add("Bijschrijvingen zijn bekend vanaf ${credits.min()}. Oudere betalingen kunnen daardoor ontbreken.")
            if (record.invoices.isNotEmpty()) add("Alleen bijschrijvingen op gekoppelde rekeningen tellen mee. \"Waarschijnlijk betaald\" is een voorstel op bedrag en naam; bevestig bij twijfel zelf.")
        }
        return BankInvoiceReport(views, total(views.filter { it.status == BankInvoiceStatus.PAID }),
            total(views.filter { it.status == BankInvoiceStatus.LIKELY }), total(unsettled), total(unsettled.filter { it.overdue }),
            record.importedAt, credits.minOrNull(), credits.maxOrNull(), warnings)
    }

    /** Een nieuwe export vervangt de factuurgegevens, maar jouw eigen keuzes blijven staan. */
    fun import(owner: String, bytes: ByteArray): BankInvoiceReport {
        bank.requireOwner(owner)
        val parsed = RompslompInvoiceExport.parse(bytes)
        store.exclusive(owner) {
            val old = record(owner).invoices.associateBy { it.number }
            val merged = old + parsed.associate { new ->
                new.number to (old[new.number]?.let { new.copy(manualPaid = it.manualPaid,
                    manualTransactionId = it.manualTransactionId, rejectedTransactionIds = it.rejectedTransactionIds) } ?: new)
            }
            require(merged.size <= 2_000) { "Te veel facturen" }
            store.write(owner, key, BankInvoiceRecord(merged.values.toList(), Instant.now().toString()))
        }
        return report(owner)
    }

    fun decide(owner: String, number: String, action: String, transactionId: String): BankInvoiceReport {
        bank.requireOwner(owner)
        require(transactionId.isEmpty() || transactionId.matches(Regex("[a-f0-9]{64}")))
        store.exclusive(owner) {
            val current = record(owner)
            val invoice = current.invoices.firstOrNull { it.number == number }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Factuur niet gevonden.")
            val updated = when (action) {
                "PAID" -> invoice.copy(manualPaid = true, manualTransactionId = transactionId,
                    rejectedTransactionIds = invoice.rejectedTransactionIds - transactionId)
                "REJECT" -> {
                    require(transactionId.isNotEmpty())
                    invoice.copy(manualPaid = false, manualTransactionId = "",
                        rejectedTransactionIds = (invoice.rejectedTransactionIds + transactionId).distinct().takeLast(50))
                }
                "RESET" -> invoice.copy(manualPaid = false, manualTransactionId = "", rejectedTransactionIds = emptyList())
                else -> throw IllegalArgumentException("Onbekende actie")
            }
            store.write(owner, key, current.copy(invoices = current.invoices.map { if (it.number == number) updated else it }))
        }
        return report(owner)
    }
}

@RestController
@RequestMapping("/api/v1/banking/invoices")
class BankInvoiceController(private val auth: AuthService, private val bank: BankService, private val invoices: BankInvoiceService) {
    private fun owner(header: String?): String = bank.resolveOwner(auth.requireAuthorization(header))
    private fun <T> privateResponse(body: T) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)

    @GetMapping fun report(@RequestHeader("Authorization", required = false) header: String?) =
        privateResponse(invoices.report(owner(header)))
    /** Base64 in JSON: de app heeft al een JSON-route en het bestand is klein. */
    @PostMapping("/import") fun import(@RequestHeader("Authorization", required = false) header: String?,
        @RequestBody request: BankInvoiceImportRequest): ResponseEntity<BankInvoiceReport> {
        val owner = owner(header)
        require(request.content.length <= RompslompInvoiceExport.MAX_FILE_BYTES * 4 / 3 + 4)
        return privateResponse(invoices.import(owner, Base64.getDecoder().decode(request.content)))
    }
    @PutMapping("/{number}") fun decide(@RequestHeader("Authorization", required = false) header: String?,
        @PathVariable number: String, @RequestBody request: BankInvoiceDecisionRequest) =
        privateResponse(invoices.decide(owner(header), number, request.action, request.transactionId))

    @ExceptionHandler(IllegalArgumentException::class, NumberFormatException::class, java.time.DateTimeException::class)
    fun invalid() = ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
        .body(mapOf("message" to "Dit is geen bruikbare factuurexport van Rompslomp (Facturen → Exporteer → Download als .xls)."))
    @ExceptionHandler(org.xml.sax.SAXException::class)
    fun unreadable() = invalid()
    @ExceptionHandler(BankBusyException::class)
    fun busy() = ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
        .body(mapOf("message" to "Bankzaken worden al bijgewerkt. Probeer straks opnieuw."))
}
