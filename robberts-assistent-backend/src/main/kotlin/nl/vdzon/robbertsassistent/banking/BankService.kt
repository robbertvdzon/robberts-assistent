package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Base64

@Service
class BankService(private val settings: BankSettings, private val store: BankStore, private val gateway: BankingGateway) {
    /** Meerdere expliciet ingestelde logins kunnen bij dezelfde administratie horen. */
    fun resolveOwner(identity: String): String {
        if (!settings.configured) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bankkoppeling is nog niet ingesteld.")
        if (identity.lowercase() !in settings.authorizedEmails)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Geen toegang tot deze bankadministratie.")
        return settings.owner
    }
    fun requireOwner(owner: String) {
        if (!settings.configured) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bankkoppeling is nog niet ingesteld.")
        if (owner != settings.owner) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Geen toegang tot deze bankadministratie.")
    }
    fun connection(owner: String): BankConnection = store.read(owner, "connection", BankConnection::class.java) ?: BankConnection()
    fun rules(owner: String): BankRules = store.read(owner, "rules", BankRules::class.java) ?: BankRules()
    fun connect(owner: String): String {
        requireOwner(owner)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        store.write("pending", bankHash(state), PendingBankAuth(owner, Instant.now().plusSeconds(900)))
        return gateway.start(state)
    }
    fun callback(state: String, code: String?, error: String?): Boolean {
        if (state.length !in 40..100) return false
        val pending = store.consumeState(state) ?: return false
        if (pending.expiresAt <= Instant.now() || pending.owner != settings.owner || error != null || code.isNullOrBlank()) return false
        requireOwner(pending.owner)
        val response = gateway.session(code)
        val until = Instant.parse(response.path("access").path("valid_until").asText())
        val accounts = response.path("accounts").map { a ->
            val iban = normalizeIban(a.path("account_id").path("iban").asText())
            val hash = a.path("identification_hash").asText()
            require(hash.isNotBlank())
            BankAccount(bankHash(iban.ifBlank { hash }), a.path("uid").asText().also { java.util.UUID.fromString(it) },
                iban, a.path("name").asText("Betaalrekening"), response.path("session_id").asText(), until)
        }
        require(accounts.isNotEmpty())
        store.exclusive(pending.owner) {
            val previous = connection(pending.owner)
            store.write(pending.owner, "connection", previous.copy(accounts =
                (previous.accounts.filter { old -> accounts.none { it.id == old.id || (it.iban.isNotBlank() && it.iban == old.iban) } } + accounts)))
        }
        // Sessie eerst duurzaam opslaan. Een importfout mag de toestemming niet verloren laten gaan.
        runCatching { sync(pending.owner) }.onFailure {
            store.exclusive(pending.owner) {
                val current = connection(pending.owner)
                store.write(pending.owner, "connection", current.copy(warnings = listOf("Toestemming opgeslagen; ophalen is nog niet gelukt. Probeer opnieuw bij te werken.")))
            }
        }
        return true
    }
    fun sync(owner: String): BankConnection = store.exclusive(owner) {
        requireOwner(owner)
        val current = connection(owner)
        if (current.accounts.isEmpty()) return@exclusive current
        val today = LocalDate.now(ZoneId.of("Europe/Amsterdam"))
        val warnings = mutableListOf("Overzichten bevatten alleen gekoppelde rekeningen en beschikbare bankhistorie. Creditcarddetails ontbreken nog.")
        val imported = mutableListOf<BankTransaction>()
        val deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos()
        current.accounts.forEach { account ->
            check(System.nanoTime() < deadline) { "Bankimport duurt te lang; niets opgeslagen" }
            if (account.validUntil <= Instant.now()) throw BankUnavailableException()
            // Een vaste begindatum per rekening voorkomt verschuivende fallback-identiteiten.
            val marker = store.read(owner, "import-${account.id}", ImportMarker::class.java)
            val from = if (marker == null) today.minusYears(1) else today.minusDays(89)
            val occurrences = mutableMapOf<String, Int>()
            val seen = mutableMapOf<String, BankTransaction>()
            gateway.transactions(account.uid, from, today).forEach rowLoop@{ json ->
                if (json.path("status").asText("BOOK") != "BOOK") return@rowLoop
                val row = parseTransaction(account.id, json, occurrences)
                require(row.date >= from.toString() && row.date <= today.toString()) { "Bankdatum buiten opgevraagde periode" }
                val duplicate = seen.put(row.id, row)
                require(duplicate == null || duplicate == row) { "Tegenstrijdige bankreferenties" }
            }
            imported.addAll(seen.values)
        }
        if (imported.any { !it.stableReference }) warnings += "Sommige betalingen missen een unieke bankreferentie; controleer aantallen na opnieuw koppelen."
        // Pas schrijven nadat alle rekeningen/pagina's succesvol zijn opgehaald.
        imported.groupBy { it.date.take(7) }.forEach { (month, rows) ->
            val old = transactions(owner, month).associateBy { it.id }
            val merged = old.toMutableMap()
            rows.forEach { t -> merged[t.id] = old[t.id]?.takeIf { it.manual }?.let { t.copy(category = it.category, manual = true) } ?: t }
            store.write(owner, "month-$month", BankTransactions(merged.values.toList()))
        }
        current.accounts.forEach { store.write(owner, "import-${it.id}", ImportMarker(today.toString())) }
        return@exclusive current.copy(lastSync = Instant.now(), warnings = warnings).also { store.write(owner, "connection", it) }
    }
    private fun transactions(owner: String, month: String) =
        store.read(owner, "month-$month", BankTransactions::class.java)?.transactions.orEmpty()
    fun month(owner: String, month: String): BankMonth {
        requireOwner(owner)
        val validMonth = runCatching { YearMonth.parse(month).toString() }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Gebruik een maand als 2026-11.")
        }
        val connection = connection(owner)
        val rows = transactions(owner, validMonth).map { BankLedger.classify(it, connection.accounts, rules(owner).rules) }
        val warnings = connection.warnings +
            (if (connection.lastSync == null) listOf("Nog geen banktransacties opgehaald.") else emptyList()) +
            listOf("${connection.accounts.size} rekening(en) gekoppeld. Overboekingen naar nog niet gekoppelde eigen rekeningen moeten worden beoordeeld.") +
            (if (connection.accounts.any { it.validUntil <= Instant.now() }) listOf("Banktoestemming is verlopen; vernieuw de koppeling.") else emptyList())
        return BankLedger.month(validMonth, rows, warnings.distinct())
    }
    fun saveRules(owner: String, rules: BankRules): BankRules = store.exclusive(owner) {
        requireOwner(owner)
        require(rules.rules.size <= 200)
        require(rules.rules.all { it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && it.contains.length <= 200 &&
            (it.contains.trim().length >= 3 || normalizeIban(it.iban).length >= 15) && it.iban.length <= 40 })
        require(rules.rules.map { it.id }.distinct().size == rules.rules.size)
        return@exclusive rules.also { store.write(owner, "rules", it) }
    }
    fun classify(owner: String, month: String, id: String, category: BankCategory): Unit = store.exclusive(owner) {
        requireOwner(owner)
        YearMonth.parse(month)
        val rows = transactions(owner, month)
        if (rows.none { it.id == id }) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transactie niet gevonden.")
        store.write(owner, "month-$month", BankTransactions(rows.map { if (it.id == id) it.copy(category = category, manual = true) else it }))
    }
}
data class ImportMarker(val importedAt: String)

internal fun parseTransaction(accountId: String, json: JsonNode, occurrences: MutableMap<String, Int>): BankTransaction {
    val amountNode = json.path("transaction_amount")
    require(amountNode.path("currency").asText() == "EUR") { "Afwijkende valuta: import vereist controle" }
    val absolute = amountNode.path("amount").asText().toBigDecimal().abs().setScale(2, java.math.RoundingMode.UNNECESSARY)
    val debit = when (json.path("credit_debit_indicator").asText()) { "DBIT" -> true; "CRDT" -> false; else -> error("Onbekende betaalrichting") }
    val date = LocalDate.parse(json.path("booking_date").asText()).toString()
    val party = if (debit) "creditor" else "debtor"
    val description = json.path("remittance_information").map { it.asText() }.joinToString(" ")
    val reference = json.path("entry_reference").asText("").ifBlank { json.path("transaction_id").asText("") }
    val amount = if (debit) -absolute else absolute
    val counterparty = json.path(party).path("name").asText("")
    val iban = normalizeIban(json.path("${party}_account").path("iban").asText(""))
    val fingerprint = bankHash(listOf(accountId, date, amount.toPlainString(), counterparty, iban, description).joinToString("\u0000"))
    val occurrence = occurrences.merge(fingerprint, 1, Int::plus)!!
    val id = bankHash(accountId + ":" + if (reference.isNotBlank()) "ref:$reference" else "fallback:$fingerprint:$occurrence")
    return BankTransaction(id, accountId, date, amount, "EUR", counterparty, iban, description, stableReference = reference.isNotBlank())
}
