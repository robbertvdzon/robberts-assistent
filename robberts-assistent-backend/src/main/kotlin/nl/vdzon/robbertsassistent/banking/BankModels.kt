package nl.vdzon.robbertsassistent.banking

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

enum class BankCategory(val label: String) {
    REVIEW("Nog te beoordelen"), GROCERIES("Boodschappen"), HOUSING("Wonen en vaste lasten"),
    INSURANCE("Verzekeringen"), TRANSPORT("Vervoer"), HEALTH("Zorg"), TAX("Belastingen"),
    BUSINESS("Zakelijk"), PENSION("Pensioen en sparen"), CHARITY("Goede doelen"),
    EXTRA("Overig / extra"), INTERNAL("Interne overboeking"), INCOME("Inkomsten")
}
data class BankAccount(val id: String, val uid: String, val iban: String, val name: String,
    val sessionId: String, val validUntil: Instant)
data class BankConnection(val accounts: List<BankAccount> = emptyList(), val lastSync: Instant? = null,
    val warnings: List<String> = emptyList())
data class BankTransaction(val id: String, val accountId: String, val date: String,
    val amount: BigDecimal, val currency: String, val counterparty: String, val counterpartyIban: String,
    val description: String, val category: BankCategory = BankCategory.REVIEW,
    val manual: Boolean = false, val stableReference: Boolean = true)
data class BankTransactions(val transactions: List<BankTransaction> = emptyList())
data class BankRule(val id: String, val contains: String, val iban: String, val category: BankCategory)
data class BankRules(val rules: List<BankRule> = emptyList())
data class PendingBankAuth(val owner: String, val expiresAt: Instant)
data class CategoryTotal(val category: BankCategory, val label: String, val spent: BigDecimal,
    val received: BigDecimal, val count: Int)
data class BankMonth(val month: String, val spent: BigDecimal, val received: BigDecimal,
    val extra: BigDecimal, val review: BigDecimal, val categories: List<CategoryTotal>,
    val transactions: List<BankTransaction>, val warnings: List<String>)
fun bankHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
fun normalizeIban(value: String) = value.replace(" ", "").uppercase()

/** Onbekend betekent nooit automatisch luxe. Alleen expliciete regels/correcties bepalen extra. */
object BankLedger {
    fun classify(t: BankTransaction, accounts: List<BankAccount>, rules: List<BankRule>): BankTransaction {
        if (t.manual) return t
        if (t.counterpartyIban.isNotBlank() && accounts.any { normalizeIban(it.iban) == t.counterpartyIban })
            return t.copy(category = BankCategory.INTERNAL)
        val rule = rules.firstOrNull { r ->
            (r.iban.isBlank() || normalizeIban(r.iban) == t.counterpartyIban) &&
                (r.contains.isBlank() || (t.counterparty + " " + t.description).contains(r.contains, true))
        }
        return t.copy(category = rule?.category ?: if (t.amount.signum() > 0) BankCategory.INCOME else BankCategory.REVIEW)
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
