package nl.vdzon.robbertsassistent.banking

import java.math.BigDecimal

/** Dates describe observed bookings, not a guarantee that the full interval was imported. */
data class BankHistory(val months: List<BankMonth>, val from: String?, val to: String?, val warnings: List<String>)

enum class BankPlanKind { EXPENSE, INCOME }
enum class BankPlanCertainty { PLANNED, ESTIMATE }
data class BankPlanItem(
    val id: String,
    val title: String,
    val amount: BigDecimal,
    val month: Int,
    val kind: BankPlanKind = BankPlanKind.EXPENSE,
    val certainty: BankPlanCertainty = BankPlanCertainty.PLANNED,
    val annual: Boolean = false,
)

/** Cash-flow inputs: annual items are additional to the regular monthly baseline. */
data class BankPlanning(
    val year: Int,
    val monthlyIncome: BigDecimal? = null,
    val monthlyExpenses: BigDecimal? = null,
    val openingReserve: BigDecimal = BigDecimal.ZERO,
    val budgets: Map<BankCategory, BigDecimal> = emptyMap(),
    val items: List<BankPlanItem> = emptyList(),
    val inheritedFromYear: Int? = null,
)

internal fun BankPlanning.validated(requestedYear: Int): BankPlanning {
    require(requestedYear in 1900..2200 && year == requestedYear)
    fun money(value: BigDecimal): BigDecimal {
        require(value.signum() >= 0 && value <= BigDecimal("10000000"))
        require(value.stripTrailingZeros().scale() <= 2)
        return value.setScale(2)
    }
    require(budgets.keys.none { it in setOf(BankCategory.INTERNAL, BankCategory.INCOME, BankCategory.REVIEW) })
    require(items.size <= 100 && items.map { it.id }.distinct().size == items.size)
    return copy(
        monthlyIncome = monthlyIncome?.let(::money),
        monthlyExpenses = monthlyExpenses?.let(::money),
        openingReserve = money(openingReserve),
        budgets = budgets.mapValues { money(it.value) },
        items = items.map {
            require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(it.title.trim().length in 1..120 && it.title.none(Char::isISOControl))
            require(it.month in 1..12)
            it.copy(title = it.title.trim(), amount = money(it.amount))
        },
        // The provenance is set by GET only. Saving confirms a plan for this year.
        inheritedFromYear = null,
    )
}
