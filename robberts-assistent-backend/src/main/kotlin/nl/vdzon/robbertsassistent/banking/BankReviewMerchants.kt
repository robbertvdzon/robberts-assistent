package nl.vdzon.robbertsassistent.banking

import java.math.BigDecimal
import java.util.Locale

data class BankReviewMerchantGroup(
    val key: String,
    val merchant: String,
    val count: Int,
    val spent: BigDecimal,
    val from: String,
    val to: String,
    val examples: List<BankTransaction>,
    val canLearn: Boolean,
)
data class BankReviewMerchants(
    val groups: List<BankReviewMerchantGroup>,
    val transactionCount: Int,
    val from: String?,
    val to: String?,
)
data class BankReviewMerchantRequest(val category: BankCategory)
data class BankReviewMerchantResult(val count: Int)

private val merchantProcessorPrefix = Regex("^(?:CCV|BCK|BPT|BNG|CM\\.|C&M|PAY\\.NL|PAY|SUMUP|ZETTLE_|MOLLIE|PAYPAL_)\\s*\\*\\s*", RegexOption.IGNORE_CASE)
private val genericMerchant = Regex("^(?:STICHTING |ST\\.? )?(?:MOLLIE|PAYPAL|PAY(?:\\.NL)?|ADYEN|KLARNA|MANGOPAY|STRIPE|BUCKAROO|SUMUP|ZETTLE|CCV|WORLDLINE|MULTISAFEPAY|ONLINE PAYMENTS?|DERDENGELDEN)(?:$|[ ._*-])", RegexOption.IGNORE_CASE)
private val personMerchant = Regex("^(?:(?:HR|MW|DHR|MEVR|DE HEER|MEVROUW)\\.?\\s|[A-Z]\\.)|\\b(?:EN\\.OF|EN/OF|EO)\\b", RegexOption.IGNORE_CASE)

/** Preserve the branch/location and spelling: no fuzzy matching, chain expansion, or description search. */
fun bankMerchantLabel(name: String): String {
    var merchant = name.trim().replace(Regex("\\s+"), " ")
    // Some processors nest their identifiers. Each iteration removes a non-empty leading prefix,
    // so this terminates within the input length and repeated normalization cannot change the key.
    while (true) {
        val prefix = merchantProcessorPrefix.find(merchant) ?: return merchant
        merchant = merchant.substring(prefix.range.last + 1).trim()
    }
}
fun bankMerchantIdentity(name: String): String = bankMerchantLabel(name).uppercase(Locale.ROOT)

private fun ambiguousPaymentRequest(t: BankTransaction): Boolean {
    // ABN may split words in the name/description, e.g. "Bet aalverzoek" or "Tik kie".
    val text = (t.counterparty + " " + t.description).uppercase(Locale.ROOT).replace(Regex("\\s+"), "")
    return text.contains("TIKKIE") || text.contains("BETAALVERZOEK") || text.contains("VIARABOBETA") ||
        text.contains("VIAINGBETA") || text.contains("VIAASNBANKBETA")
}

fun bankLearnableMerchantName(name: String): Boolean {
    val merchant = bankMerchantLabel(name)
    return merchant.length in 3..200 && !genericMerchant.containsMatchIn(merchant) && !personMerchant.containsMatchIn(merchant)
}

fun bankCanLearnMerchant(t: BankTransaction): Boolean {
    if (t.manual || !bankLearnableMerchantName(t.counterparty) || ambiguousPaymentRequest(t)) return false
    // A transfer's user-entered beneficiary name is not sufficient evidence of a merchant identity.
    if (Regex("(?i)^SEPA\\s+(?:Overboeking|Periodieke overb\\.)").containsMatchIn(t.description)) return false
    return true
}

fun bankExactMerchantMatches(rule: BankRule, t: BankTransaction): Boolean = rule.merchant.isBlank() ||
    (bankCanLearnMerchant(t) &&
        bankMerchantIdentity(rule.merchant) == bankMerchantIdentity(t.counterparty))

fun bankReviewMerchants(rows: List<BankTransaction>): BankReviewMerchants {
    val review = rows.filter { it.category == BankCategory.REVIEW }
    val groups = review.groupBy { t ->
        if (bankCanLearnMerchant(t)) bankHash("merchant:" + bankMerchantIdentity(t.counterparty))
        else bankHash("transaction:" + t.id)
    }.map { (key, transactions) ->
        val first = transactions.first()
        BankReviewMerchantGroup(
            key = key,
            merchant = bankMerchantLabel(first.counterparty).ifBlank { "Onbekende tegenpartij" },
            count = transactions.size,
            spent = transactions.filter { it.amount.signum() < 0 }.sumOf { -it.amount },
            from = transactions.minOf { it.date },
            to = transactions.maxOf { it.date },
            examples = transactions.sortedWith(compareByDescending<BankTransaction> { it.date }.thenBy { it.id }).take(3),
            canLearn = bankCanLearnMerchant(first),
        )
    }.sortedWith(compareByDescending<BankReviewMerchantGroup> { it.count }.thenByDescending { it.spent }.thenBy { it.merchant }.thenBy { it.key })
    return BankReviewMerchants(groups, review.size, rows.minOfOrNull { it.date }, rows.maxOfOrNull { it.date })
}
