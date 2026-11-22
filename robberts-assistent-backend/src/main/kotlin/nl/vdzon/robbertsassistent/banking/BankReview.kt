package nl.vdzon.robbertsassistent.banking

/**
 * Indelen van openstaande betalingen vanuit één lijst. Het bereik bepaalt of er een regel ontstaat:
 * ONLY zet alleen de gekozen betalingen vast, MERCHANT leert de winkel, TEXT maakt een tekstregel.
 */
enum class BankReviewScope { ONLY, MERCHANT, TEXT }

data class BankReviewRow(
    val transaction: BankTransaction,
    val merchant: String,
    /** Gelijk voor alle betalingen die onder dezelfde winkelregel zouden vallen. */
    val merchantKey: String,
    val canLearn: Boolean,
)
data class BankReviewList(val rows: List<BankReviewRow>, val from: String?, val to: String?)
data class BankReviewRef(val month: String, val id: String)
data class BankReviewClassifyRequest(
    val category: BankCategory,
    val scope: BankReviewScope = BankReviewScope.ONLY,
    val transactions: List<BankReviewRef> = emptyList(),
    val text: String = "",
)
data class BankReviewClassifyResult(val count: Int)

/** Dezelfde tekst waarop een regel met `contains` zoekt. */
fun bankReviewText(t: BankTransaction): String = t.counterparty + " " + t.description

fun bankReviewList(rows: List<BankTransaction>): BankReviewList {
    val review = rows.filter { it.category == BankCategory.REVIEW }
        .sortedWith(compareByDescending<BankTransaction> { it.date }.thenBy { it.id })
    return BankReviewList(
        review.map { t ->
            val canLearn = bankCanLearnMerchant(t)
            BankReviewRow(
                transaction = t,
                merchant = bankMerchantLabel(t.counterparty).ifBlank { "Onbekende tegenpartij" },
                merchantKey = if (canLearn) bankHash("merchant:" + bankMerchantIdentity(t.counterparty)) else bankHash("transaction:" + t.id),
                canLearn = canLearn,
            )
        },
        rows.minOfOrNull { it.date }, rows.maxOfOrNull { it.date },
    )
}
