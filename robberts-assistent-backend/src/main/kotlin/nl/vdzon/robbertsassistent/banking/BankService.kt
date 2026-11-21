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
class BankService(private val settings: BankSettings, private val store: BankStore, private val gateway: BankingGateway,
    private val events: org.springframework.context.ApplicationEventPublisher? = null) {
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
    private val seedRules: BankRules by lazy {
        if (settings.initialRulesFile.isBlank()) BankRules() else
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules().readValue(
                java.nio.file.Files.readString(java.nio.file.Path.of(settings.initialRulesFile)), BankRules::class.java)
    }
    fun rules(owner: String): BankRules {
        val current = store.read(owner, "rules", BankRules::class.java)
        val version = bankHash(seedRules.rules.joinToString { it.id } + seedRules.ownIbans.joinToString())
        val merged = if (current == null) seedRules else if (current.seedVersion != version)
            current.copy(rules = (current.rules + seedRules.rules).distinctBy { it.id }) else current
        return merged.copy(ownIbans = (merged.ownIbans + seedRules.ownIbans).distinct(), seedVersion = version)
    }
    fun connect(owner: String, requestedIban: String? = null): String {
        requireOwner(owner)
        val requested = requestedIban?.let { raw ->
            normalizeIban(raw).also { iban ->
                require(iban.matches(Regex("NL[0-9]{2}ABNA[0-9]{10}")))
                val rearranged = iban.drop(4) + iban.take(4)
                val digits = rearranged.map { if (it.isDigit()) it.toString() else (it.code - 'A'.code + 10).toString() }.joinToString("")
                require(digits.toBigInteger().mod(java.math.BigInteger.valueOf(97)).toInt() == 1)
            }
        }?.let(::listOf)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        store.write("pending", bankHash(state), PendingBankAuth(owner, Instant.now().plusSeconds(900)))
        val owned = (rules(owner).ownIbans + connection(owner).accounts.map { it.iban }).map(::normalizeIban).filter { it.isNotBlank() }.distinct()
        return gateway.start(state, requested ?: owned)
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
        if (accounts.isEmpty()) throw BankNoAccountsException()
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
                // De bank kan extra boekingsdatums buiten de aangevraagde periode leveren.
                // Filter op onze eigen periode; zo blokkeert één extra regel niet de hele import.
                if (row.date < from.toString() || row.date > today.toString()) return@rowLoop
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
            rows.forEach { t -> merged[t.id] = old[t.id]?.let { previous ->
                t.copy(category = previous.category, manual = previous.manual, classificationSource = previous.classificationSource,
                    reason = previous.reason, question = previous.question, suggestedCategory = previous.suggestedCategory, aiReviewed = previous.aiReviewed)
            } ?: t }
            store.write(owner, "month-$month", BankTransactions(merged.values.toList()))
        }
        current.accounts.forEach { store.write(owner, "import-${it.id}", ImportMarker(today.toString())) }
        return@exclusive current.copy(lastSync = Instant.now(), warnings = warnings).also { store.write(owner, "connection", it) }
    }.also { if (it.lastSync != null) events?.publishEvent(BankSynced(owner)) }
    private fun transactions(owner: String, month: String) =
        store.read(owner, "month-$month", BankTransactions::class.java)?.transactions.orEmpty()
    fun month(owner: String, month: String): BankMonth {
        requireOwner(owner)
        val validMonth = runCatching { YearMonth.parse(month).toString() }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Gebruik een maand als 2026-11.")
        }
        val connection = connection(owner)
        val rubrication = rules(owner)
        val rows = transactions(owner, validMonth).map { BankLedger.classify(it, connection.accounts, rubrication.rules, rubrication.ownIbans) }
        val warnings = connection.warnings +
            (if (connection.lastSync == null) listOf("Nog geen banktransacties opgehaald.") else emptyList()) +
            listOf("${connection.accounts.size} van ${(rubrication.ownIbans + connection.accounts.map { it.iban }).distinct().size} bekende eigen rekening(en) uitgelezen. Overboekingen tussen bekende eigen rekeningen zijn uitgesloten.") +
            (if (connection.accounts.any { it.validUntil <= Instant.now() }) listOf("Banktoestemming is verlopen; vernieuw de koppeling.") else emptyList())
        return BankLedger.month(validMonth, rows, warnings.distinct())
    }
    fun history(owner: String, end: String): BankHistory {
        requireOwner(owner)
        require(end.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")))
        val last = YearMonth.parse(end)
        require(last.year in 1900..2200)
        val connection = connection(owner)
        val rubrication = rules(owner)
        // Read shared metadata once; each historical month reads only its own encrypted record.
        val months = (11L downTo 0L).map { offset ->
            val month = last.minusMonths(offset).toString()
            val rows = transactions(owner, month).map {
                BankLedger.classify(it, connection.accounts, rubrication.rules, rubrication.ownIbans)
            }
            BankLedger.month(month, rows, emptyList())
        }
        val dates = months.flatMap { it.transactions }.map { it.date }
        val warnings = connection.warnings + listOf(
            "Datums tonen opgeslagen boekingen. Een lege maand betekent niet dat er geen uitgaven waren.",
            "Interne overboekingen zijn uitgesloten; creditcardbetalingen zijn zonder kaartdetails niet verder uitgesplitst.",
        ) + if (connection.accounts.any { it.validUntil <= Instant.now() })
            listOf("Banktoestemming is verlopen; vernieuw de koppeling.") else emptyList()
        return BankHistory(months, dates.minOrNull(), dates.maxOrNull(), warnings.distinct())
    }
    fun planning(owner: String, year: Int): BankPlanning {
        requireOwner(owner)
        require(year in 1900..2200)
        store.read(owner, "planning-$year", BankPlanning::class.java)?.let { return it }
        val previousYear = store.keys(owner).mapNotNull { key ->
            Regex("planning-(\\d{4})").matchEntire(key)?.groupValues?.get(1)?.toIntOrNull()
        }.filter { it < year && it in 1900..2200 }.maxOrNull() ?: return BankPlanning(year)
        val previous = store.read(owner, "planning-$previousYear", BankPlanning::class.java) ?: return BankPlanning(year)
        val annual = previous.items.filter { it.annual }.map { it.copy(certainty = BankPlanCertainty.ESTIMATE) }
        return BankPlanning(year, items = annual, inheritedFromYear = previousYear.takeIf { annual.isNotEmpty() })
    }
    fun savePlanning(owner: String, year: Int, planning: BankPlanning): BankPlanning {
        requireOwner(owner)
        val valid = planning.validated(year)
        return store.exclusive(owner) {
            store.write(owner, "planning-$year", valid)
            valid
        }
    }
    fun saveRules(owner: String, rules: BankRules): BankRules = store.exclusive(owner) {
        requireOwner(owner)
        require(rules.rules.size <= 500)
        require(rules.ownIbans.all { normalizeIban(it).matches(Regex("[A-Z]{2}[0-9A-Z]{13,32}")) })
        require(rules.rules.all { it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && it.contains.length <= 200 &&
            (it.contains.trim().length >= 3 || normalizeIban(it.iban).length >= 15 || it.required.any { word -> word.length >= 3 } || it.merchant.isNotBlank()) &&
            (it.merchant.isEmpty() || bankLearnableMerchantName(it.merchant)) &&
            it.iban.length <= 40 && it.required.size <= 20 && it.forbidden.size <= 20 && (it.required + it.forbidden).all { word -> word.length <= 200 } && it.accountSuffix.length <= 34 && it.direction in listOf("", "DBIT", "CRDT") })
        require(rules.rules.map { it.id }.distinct().size == rules.rules.size)
        return@exclusive rules.copy(ownIbans = (rules.ownIbans + rules(owner).ownIbans).distinct(), seedVersion = rules(owner).seedVersion).also { store.write(owner, "rules", it) }
    }
    /** Alle opgeslagen maanden, gerubriceerd zoals de overzichten ze tonen. */
    fun allTransactions(owner: String): List<BankTransaction> {
        requireOwner(owner)
        val connection = connection(owner)
        val rules = rules(owner)
        return store.keys(owner).filter { it.matches(Regex("month-\\d{4}-(0[1-9]|1[0-2])")) }.flatMap { key ->
            store.read(owner, key, BankTransactions::class.java)?.transactions.orEmpty()
                .filter { it.date.startsWith(key.removePrefix("month-")) }
                .map { BankLedger.classify(it, connection.accounts, rules.rules, rules.ownIbans) }
        }
    }
    fun reviewMerchants(owner: String): BankReviewMerchants = bankReviewMerchants(allTransactions(owner))
    fun classifyMerchant(owner: String, key: String, category: BankCategory): BankReviewMerchantResult {
        requireOwner(owner)
        require(key.matches(Regex("[a-f0-9]{64}")))
        require(category !in setOf(BankCategory.REVIEW, BankCategory.INTERNAL, BankCategory.INCOME))
        return store.exclusive(owner) {
            val group = reviewMerchants(owner).groups.firstOrNull { it.key == key }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Deze openstaande winkel is niet meer beschikbaar. Ververs het overzicht.")
            require(group.canLearn) { "Beoordeel dit betaalverzoek per transactie." }
            val identity = bankMerchantIdentity(group.merchant)
            val existing = rules(owner)
            val otherRules = existing.rules.filterNot { it.merchant.isNotBlank() && bankMerchantIdentity(it.merchant) == identity }
            require(otherRules.size < 500)
            val learned = BankRule("merchant-${bankHash(identity)}", "", "", category,
                label = "Door jou gekozen voor ${group.merchant.take(100)}", learned = true, merchant = identity)
            // Rules are applied on every read and import; historical manual choices remain untouched.
            store.write(owner, "rules", existing.copy(rules = listOf(learned) + otherRules))
            BankReviewMerchantResult(group.count)
        }
    }
    fun classify(owner: String, month: String, id: String, category: BankCategory, remember: Boolean = false, matchText: String = ""): Unit = store.exclusive(owner) {
        requireOwner(owner)
        YearMonth.parse(month)
        val rows = transactions(owner, month)
        if (rows.none { it.id == id }) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transactie niet gevonden.")
        val transaction = rows.single { it.id == id }
        if (remember) {
            require(matchText.trim().length in 3..200)
            val existing = rules(owner)
            val learned = BankRule(java.util.UUID.randomUUID().toString(), matchText.trim(), transaction.counterpartyIban, category,
                label = "Geleerd van jouw antwoord", learned = true)
            require(existing.rules.size < 500)
            store.write(owner, "rules", existing.copy(rules = listOf(learned) + existing.rules))
        }
        store.write(owner, "month-$month", BankTransactions(rows.map { if (it.id == id)
            it.copy(category = category, manual = category != BankCategory.REVIEW,
                classificationSource = if (category == BankCategory.REVIEW) "" else "USER", question = "",
                aiReviewed = if (category == BankCategory.REVIEW) false else it.aiReviewed,
                reason = if (category == BankCategory.REVIEW) "" else "Door jou bevestigd") else it }))
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
    val structuredIban = normalizeIban(json.path("${party}_account").path("iban").asText(""))
    val iban = structuredIban.ifBlank { bankTextIban(description) }
    // Verrijkte metadata mag de bestaande fallback-identiteit niet veranderen.
    val fingerprint = bankHash(listOf(accountId, date, amount.toPlainString(), counterparty, structuredIban, description).joinToString("\u0000"))
    val occurrence = occurrences.merge(fingerprint, 1, Int::plus)!!
    val id = bankHash(accountId + ":" + if (reference.isNotBlank()) "ref:$reference" else "fallback:$fingerprint:$occurrence")
    return BankTransaction(id, accountId, date, amount, "EUR", counterparty, iban, description, stableReference = reference.isNotBlank())
}

data class BankSynced(val owner: String)
