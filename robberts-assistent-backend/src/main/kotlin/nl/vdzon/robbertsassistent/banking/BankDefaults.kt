package nl.vdzon.robbertsassistent.banking

/** Alleen brede, eenduidige doeleinden. Warenhuizen en betaalplatformen blijven vragen. */
object BankDefaults {
    fun category(t: BankTransaction): BankCategory? {
        val name = t.counterparty.uppercase()
        val text = (name + " " + t.description).uppercase()
        if (listOf("BRAND NEW DAY", "BITVAVO").any { text.contains(it) }) return BankCategory.PENSION
        if (listOf("INT CARD SERVICES", "INTERNATIONAL CARD SERVICES").any { text.contains(it) }) return BankCategory.CREDIT_CARD
        fun nameHas(value: String) = Regex("(?<![A-Z0-9])" + Regex.escape(value) + "(?![A-Z])").containsMatchIn(name)
        if (listOf("ZIGGO", "KPN", "VODAFONE", "OBVION", "ZONNEPLAN", "VATTENFALL", "ENECO").any(::nameHas)) return BankCategory.HOUSING
        // Waterbedrijven verkopen ook duinkaarten aan de kassa; dat is geen waterrekening.
        if (!t.description.startsWith("BEA,", true) && nameHas("WATERLEIDINGBEDRIJF")) return BankCategory.HOUSING
        if (listOf("ALBERT HEIJN", "AHOLD", "JUMBO", "VOMAR", "DEKAMARKT", "LIDL", "ALDI", "DIRK VAN DEN BROEK").any(::nameHas) &&
            !Regex("\\bJUMBO\\s+GOLF\\b").containsMatchIn(name)) return BankCategory.GROCERIES
        if (listOf("BELASTINGDIENST", "HOOGHEEMRAADSCHAP", "GEMEENTELIJKE HEFFINGEN").any { text.contains(it) }) return BankCategory.TAX
        if (name.contains("ABN AMRO") && (text.contains("KOSTEN") || text.contains("BETAALPAKKET"))) return BankCategory.BANK_FEES
        if (listOf("UNICEF", "OXFAM NOVIB", "ARTSEN ZONDER GRENZEN", "KANKERBESTRIJDING", "AIDSFONDS").any { name.contains(it) }) return BankCategory.CHARITY
        return BankMerchantRules.category(t)
    }
}
