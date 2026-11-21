package nl.vdzon.robbertsassistent.banking

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

enum class BankCategory(val label: String) {
    REVIEW("Nog te beoordelen"), GROCERIES("Boodschappen"), HOUSING("Wonen en vaste lasten"),
    INSURANCE("Verzekeringen"), TRANSPORT("Vervoer"), HEALTH("Zorg"), TAX("Belastingen"),
    BUSINESS("Zakelijk"), PENSION("Pensioen en sparen"), CHARITY("Goede doelen"),
    BANK_FEES("Bankkosten"), CREDIT_CARD("Creditcard, nog zonder details"), EXTRA("Overig / extra"), INTERNAL("Interne overboeking"), INCOME("Inkomsten")
}
data class BankAccount(val id: String, val uid: String, val iban: String, val name: String,
    val sessionId: String, val validUntil: Instant)
data class BankConnection(val accounts: List<BankAccount> = emptyList(), val lastSync: Instant? = null,
    val warnings: List<String> = emptyList())
data class BankTransaction(val id: String, val accountId: String, val date: String,
    val amount: BigDecimal, val currency: String, val counterparty: String, val counterpartyIban: String,
    val description: String, val category: BankCategory = BankCategory.REVIEW,
    val manual: Boolean = false, val stableReference: Boolean = true,
    val classificationSource: String = "", val reason: String = "", val question: String = "",
    val suggestedCategory: BankCategory? = null, val aiReviewed: Boolean = false)
data class BankTransactions(val transactions: List<BankTransaction> = emptyList())
data class BankRule(val id: String, val contains: String, val iban: String, val category: BankCategory,
    val required: List<String> = emptyList(), val forbidden: List<String> = emptyList(),
    val accountSuffix: String = "", val direction: String = "", val label: String = "", val learned: Boolean = false)
data class BankRules(val rules: List<BankRule> = emptyList(), val ownIbans: List<String> = emptyList(), val seedVersion: String = "")
data class PendingBankAuth(val owner: String, val expiresAt: Instant)
data class CategoryTotal(val category: BankCategory, val label: String, val spent: BigDecimal,
    val received: BigDecimal, val count: Int)
data class BankMonth(val month: String, val spent: BigDecimal, val received: BigDecimal,
    val extra: BigDecimal, val review: BigDecimal, val categories: List<CategoryTotal>,
    val transactions: List<BankTransaction>, val warnings: List<String>)
fun bankHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
fun bankTextIban(description: String): String = Regex("(?i)^SEPA (?:Periodieke overb\\.|overboeking)\\s+IBAN:\\s*([A-Z]{2}[0-9]{2}[A-Z0-9]{11,30})\\s+BIC:")
    .find(description)?.groupValues?.get(1)?.let(::normalizeIban).orEmpty()
fun normalizeIban(value: String) = value.replace(" ", "").uppercase()

/** Onbekend betekent nooit automatisch luxe. Alleen expliciete regels/correcties bepalen extra. */
object BankLedger {
    fun classify(raw: BankTransaction, accounts: List<BankAccount>, rules: List<BankRule>, ownIbans: List<String> = emptyList()): BankTransaction {
        val t = if (raw.counterpartyIban.isBlank()) raw.copy(counterpartyIban = bankTextIban(raw.description)) else raw
        if (t.manual) return t.copy(classificationSource = "USER", question = "")
        val owned = (accounts.map { it.iban } + ownIbans).map(::normalizeIban).filter { it.isNotBlank() }
        if (t.counterpartyIban.isNotBlank() && t.counterpartyIban in owned)
            return t.copy(category = BankCategory.INTERNAL, classificationSource = "INTERNAL", reason = "Overboeking tussen bekende eigen rekeningen", question = "", aiReviewed = false)
        val text = t.counterparty + " " + t.description
        val sourceIban = accounts.firstOrNull { it.id == t.accountId }?.iban.orEmpty()
        val rule = rules.firstOrNull { r ->
            (r.iban.isBlank() || normalizeIban(r.iban) == t.counterpartyIban) &&
                (r.contains.isBlank() || text.contains(r.contains, true)) &&
                r.required.all { text.contains(it, true) } && r.forbidden.none { text.contains(it, true) } &&
                (r.accountSuffix.isBlank() || sourceIban.endsWith(r.accountSuffix)) &&
                (r.direction.isBlank() || (r.direction == "DBIT" && t.amount.signum() < 0) || (r.direction == "CRDT" && t.amount.signum() > 0))
        }
        if (rule != null) return t.copy(category = rule.category,
            classificationSource = if (rule.category == BankCategory.REVIEW) "QUESTION" else if (rule.learned) "LEARNED" else "RULE",
            reason = rule.label.ifBlank { "Vaste rubriceringsregel" },
            question = if (rule.category == BankCategory.REVIEW) "Waarvoor was deze betaling?" else "", aiReviewed = rule.category == BankCategory.REVIEW)
        val builtin = BankDefaults.category(t)
        if (builtin != null) return t.copy(category = builtin, classificationSource = "RULE", reason = "Duidelijke tegenpartij of betaalomschrijving", question = "", aiReviewed = false)
        if (t.aiReviewed) return t
        return t.copy(category = if (t.amount.signum() > 0) BankCategory.INCOME else BankCategory.REVIEW,
            classificationSource = "", question = if (t.amount.signum() < 0) "Waarvoor was deze betaling? Was dit nodig of een extra aankoop/activiteit?" else "")
    }
    fun month(month: String, transactions: List<BankTransaction>, warnings: List<String>): BankMonth {
        val rows = transactions.filter { it.date.startsWith(month) }.sortedWith(compareByDescending<BankTransaction> { it.date }.thenBy { it.id })
        val included = rows.filter { it.category != BankCategory.INTERNAL }
        val totals = included.groupBy { it.category }.map { (c, ts) ->
            CategoryTotal(c, c.label, ts.filter { it.amount.signum() < 0 }.sumOf { -it.amount },
                ts.filter { it.amount.signum() > 0 }.sumOf { it.amount }, ts.size)
        }.sortedByDescending { it.spent }
        return BankMonth(month, totals.sumOf { it.spent }, totals.sumOf { it.received },
            totals.filter { it.category == BankCategory.EXTRA }.sumOf { it.spent - it.received },
            totals.filter { it.category == BankCategory.REVIEW }.sumOf { it.spent }, totals, rows, warnings)
    }
}
