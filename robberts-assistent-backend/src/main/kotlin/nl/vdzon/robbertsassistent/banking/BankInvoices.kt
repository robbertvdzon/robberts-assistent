package nl.vdzon.robbertsassistent.banking

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate

/** Alleen wat voor de betaalcontrole nodig is; adres en e-mail uit de export worden niet bewaard. */
data class BankInvoice(
    val number: String,
    val date: String,
    val dueDate: String,
    val description: String,
    val amount: BigDecimal,
    val customerCompany: String,
    val customerName: String,
    val exportPaid: BigDecimal = BigDecimal.ZERO,
    val manualPaid: Boolean = false,
    val manualTransactionId: String = "",
    val rejectedTransactionIds: List<String> = emptyList(),
)
data class BankInvoiceRecord(val invoices: List<BankInvoice> = emptyList(), val importedAt: String? = null)

enum class BankInvoiceStatus { PAID, LIKELY, CHECK, OPEN }
data class BankInvoicePayment(val transactionId: String, val date: String, val amount: BigDecimal,
    val counterparty: String, val description: String)
data class BankInvoiceView(
    val number: String,
    val date: String,
    val dueDate: String,
    val description: String,
    val amount: BigDecimal,
    val customer: String,
    val status: BankInvoiceStatus,
    val overdue: Boolean,
    val reason: String,
    val manual: Boolean,
    val payments: List<BankInvoicePayment>,
    val candidates: List<BankInvoicePayment>,
)
data class BankInvoiceTotal(val count: Int, val amount: BigDecimal)
data class BankInvoiceReport(
    val invoices: List<BankInvoiceView>,
    val paid: BankInvoiceTotal,
    val likely: BankInvoiceTotal,
    val open: BankInvoiceTotal,
    val overdue: BankInvoiceTotal,
    val importedAt: String?,
    val transactionsFrom: String?,
    val transactionsTo: String?,
    val warnings: List<String>,
)
data class BankInvoiceImportRequest(val content: String = "")
data class BankInvoiceDecisionRequest(val action: String = "", val transactionId: String = "")

private val invoiceStopWords = setOf("van", "der", "den", "voor", "het", "een", "the", "and", "stichting", "vereniging",
    "afdeling", "holding", "beheer", "com", "www")

private fun invoiceFold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase()
private fun invoiceTokens(value: String): List<String> = invoiceFold(value).split(Regex("[^a-z0-9]+"))
    .filter { it.length >= 3 && it !in invoiceStopWords }.distinct()
private fun invoiceCompact(value: String): String = invoiceFold(value).replace(Regex("[^a-z0-9]+"), "")

/** Naam telt alleen mee naast een exact bedrag; het resultaat blijft dan "waarschijnlijk". */
internal fun invoiceNameMatches(invoice: BankInvoice, transaction: BankTransaction): Boolean {
    val text = transaction.counterparty + " " + transaction.description
    val words = invoiceTokens(text).toSet()
    val compact = invoiceCompact(text)
    val company = invoiceTokens(invoice.customerCompany)
    if (company.isNotEmpty()) {
        val whole = invoiceCompact(invoice.customerCompany)
        if (whole.length >= 5 && compact.contains(whole)) return true
        if (company.count { it in words } >= (company.size + 1) / 2) return true
    }
    val person = invoiceTokens(invoice.customerName)
    return person.size >= 2 && person.all { it in words }
}

/** Het nummer moet los staan; gespatieerde nummers ("2026 1100 33") tellen ook. */
internal fun invoiceNumberMentioned(number: String, text: String): Boolean {
    if (number.length < 4) return false
    if (Regex("(?<![A-Za-z0-9])${Regex.escape(number)}(?![A-Za-z0-9])").containsMatchIn(text)) return true
    return number.length >= 8 && Regex("(?<![0-9])${number.toList().joinToString("[ .-]?") { Regex.escape(it.toString()) }}(?![0-9])")
        .containsMatchIn(text)
}

object BankInvoiceMatcher {
    private fun payment(t: BankTransaction) = BankInvoicePayment(t.id, t.date, t.amount, t.counterparty, t.description.take(300))
    private fun money(value: BigDecimal) = "€ " + value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    fun match(invoices: List<BankInvoice>, transactions: List<BankTransaction>, today: LocalDate): List<BankInvoiceView> {
        val credits = transactions.filter { it.amount.signum() > 0 && it.category != BankCategory.INTERNAL }
            .sortedWith(compareBy<BankTransaction> { it.date }.thenBy { it.id })
        val byId = credits.associateBy { it.id }
        val used = mutableSetOf<String>()
        val result = mutableMapOf<String, BankInvoiceView>()
        fun view(i: BankInvoice, status: BankInvoiceStatus, reason: String, payments: List<BankTransaction> = emptyList(),
            candidates: List<BankTransaction> = emptyList(), manual: Boolean = false) {
            used += payments.map { it.id }
            val unsettled = status == BankInvoiceStatus.OPEN || status == BankInvoiceStatus.CHECK
            result[i.number] = BankInvoiceView(i.number, i.date, i.dueDate, i.description, i.amount,
                i.customerCompany.ifBlank { i.customerName }, status,
                unsettled && i.dueDate.isNotBlank() && i.dueDate < today.toString(), reason, manual,
                payments.map(::payment), candidates.map(::payment))
        }

        // 1. Eigen keuzes en wat Rompslomp zelf al als betaald kent gaan voor alles.
        invoices.forEach { i ->
            if (i.manualPaid) view(i, BankInvoiceStatus.PAID, "Door jou als betaald gemarkeerd",
                listOfNotNull(byId[i.manualTransactionId]), manual = true)
            else if (i.amount.signum() > 0 && i.exportPaid >= i.amount) view(i, BankInvoiceStatus.PAID, "Volgens Rompslomp betaald")
        }

        // 2. Factuurnummer in de omschrijving, ook als één bijschrijving meerdere facturen dekt.
        val mentions = credits.associateWith { t ->
            val text = t.counterparty + " " + t.description
            invoices.filter { it.number !in result && t.id !in it.rejectedTransactionIds && invoiceNumberMentioned(it.number, text) }
        }.filterValues { it.isNotEmpty() }
        mentions.forEach { (t, mentioned) ->
            val pending = mentioned.filter { it.number !in result }
            if (t.id !in used && pending.isNotEmpty() && pending.sumOf { it.amount }.compareTo(t.amount) == 0)
                pending.forEach { view(it, BankInvoiceStatus.PAID, if (pending.size == 1) "Factuurnummer staat in de omschrijving"
                    else "Samen met ${pending.size - 1} andere factuur/facturen in één bijschrijving betaald", listOf(t)) }
        }
        invoices.filter { it.number !in result }.forEach { i ->
            val own = mentions.filter { (t, mentioned) -> t.id !in used && mentioned.any { it.number == i.number } }.keys.toList()
            if (own.isEmpty()) return@forEach
            val total = own.sumOf { it.amount }
            if (own.all { mentions.getValue(it).size == 1 } && total.compareTo(i.amount) == 0)
                view(i, BankInvoiceStatus.PAID, "In ${own.size} delen betaald; factuurnummer staat in de omschrijving", own)
            else view(i, BankInvoiceStatus.CHECK, "Factuurnummer gevonden, maar ${money(total)} ontvangen wijkt af van ${money(i.amount)}", own)
        }
        val referenced = mentions.keys.map { it.id }.toSet()

        // 3. Zelfde bedrag én herkenbare klantnaam: oudste factuur krijgt de oudste bijschrijving.
        fun sameAmount(i: BankInvoice) = credits.filter { t -> t.id !in used && t.id !in referenced &&
            t.id !in i.rejectedTransactionIds && t.amount.compareTo(i.amount) == 0 && t.date >= i.date }
        invoices.filter { it.number !in result }.sortedWith(compareBy<BankInvoice> { it.date }.thenBy { it.number }).forEach { i ->
            sameAmount(i).firstOrNull { invoiceNameMatches(i, it) }?.let {
                view(i, BankInvoiceStatus.LIKELY, "Zelfde bedrag en klantnaam, zonder factuurnummer", listOf(it))
            }
        }

        // 4. Rest staat open; bijschrijvingen met hetzelfde bedrag zijn alleen een suggestie.
        invoices.filter { it.number !in result }.forEach { i ->
            val candidates = sameAmount(i).take(3)
            view(i, BankInvoiceStatus.OPEN, if (candidates.isEmpty()) "Geen passende bijschrijving gevonden"
                else "Bijschrijving met hetzelfde bedrag gevonden, maar zonder factuurnummer of klantnaam", candidates = candidates)
        }
        return invoices.sortedWith(compareByDescending<BankInvoice> { it.date }.thenByDescending { it.number })
            .map { result.getValue(it.number) }
    }
}
