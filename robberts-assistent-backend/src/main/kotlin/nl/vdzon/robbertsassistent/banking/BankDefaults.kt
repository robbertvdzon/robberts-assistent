package nl.vdzon.robbertsassistent.banking

/** Alleen brede, eenduidige doeleinden. Warenhuizen en betaalplatformen blijven vragen. */
object BankDefaults {
    fun category(t: BankTransaction): BankCategory? {
        val name = t.counterparty.uppercase()
        val text = (name + " " + t.description).uppercase()
        if (listOf("BRAND NEW DAY", "BITVAVO").any { text.contains(it) }) return BankCategory.PENSION
        if (listOf("INT CARD SERVICES", "INTERNATIONAL CARD SERVICES").any { text.contains(it) }) return BankCategory.CREDIT_CARD
        if (listOf("ZIGGO", "KPN", "VODAFONE", "WATERLEIDINGBEDRIJF", "OBVION", "ZONNEPLAN", "VATTENFALL", "ENECO").any { name.contains(it) }) return BankCategory.HOUSING
        if (listOf("ALBERT HEIJN", "AHOLD", "JUMBO", "VOMAR", "DEKAMARKT", "LIDL", "ALDI", "DIRK VAN DEN BROEK").any { name.contains(it) }) return BankCategory.GROCERIES
        if (listOf("BELASTINGDIENST", "HOOGHEEMRAADSCHAP", "GEMEENTELIJKE HEFFINGEN").any { text.contains(it) }) return BankCategory.TAX
        if (name.contains("ABN AMRO") && (text.contains("KOSTEN") || text.contains("BETAALPAKKET"))) return BankCategory.BANK_FEES
        if (listOf("UNICEF", "OXFAM NOVIB", "ARTSEN ZONDER GRENZEN", "KANKERBESTRIJDING", "AIDSFONDS").any { name.contains(it) }) return BankCategory.CHARITY
        return null
    }
}
