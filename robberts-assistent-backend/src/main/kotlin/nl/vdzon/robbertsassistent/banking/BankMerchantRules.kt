package nl.vdzon.robbertsassistent.banking

/** Herken het bestedingsdoel, niet of de aankoop noodzakelijk was. Gemengde winkels blijven open. */
object BankMerchantRules {
    private data class MerchantRule(val category: BankCategory, val pattern: Regex)
    private fun rule(category: BankCategory, pattern: String) =
        MerchantRule(category, Regex("^(?:$pattern)(?=$|[^A-Z0-9])", RegexOption.IGNORE_CASE))

    // Alleen de winkelnaam; geen losse trefwoorden ergens in een betaalverzoek of factuur.
    private val merchantRules = listOf(
        // Door de gebruiker bevestigd: aankopen bij Welkoop zijn extra uitgaven.
        rule(BankCategory.EXTRA, "WELKOOP"),
        rule(BankCategory.CLOTHING, "DESIGUAL|G[ .-]?STAR(?: RAW)?|SUPERDRY|UNDIEMEISTER|ABOUT YOU|ZALANDO"),
        rule(BankCategory.CLOTHING, "ADIDAS|VANS EUROPE|O[.'’]?NEILL|ONEILL|LEVI['’]?S|THE STING|C&A|H&M|ZARA|AMERICA TODAY|TANTILLY"),
        rule(BankCategory.GROCERIES, "GOEMANS (?:HAYDNPLEINW|HAYDENPLEIN|HADYNPLEINR|COEVENHOVENR)"),
        rule(BankCategory.GROCERIES, "SIEBE REITSMA BROO|SLAGERIJ DE ROODE|DAVIDSON GROENTEN|SIMONS KAAS EN NOTEN|WOERDENSE KAASBOER|BAKKERIJ DE BASIS"),
        rule(BankCategory.GROCERIES, "BILLA DANKT|MPREIS FIL|FENEBERG MARKT|CARREFOUR"),
        rule(BankCategory.INSURANCE, "HARMONY INSURANCES|VOOGD VOOGD VERZEKERING|DELA\\s+NATURA"),
        rule(BankCategory.HEALTH, "INFOMEDICS|MAERE APOTHEEK|CHIROPRACTIE CASTR|PIETER WEEDA OSTEO"),
        rule(BankCategory.HOUSING, "WARMTE GROEP"),
        rule(BankCategory.TRANSPORT, "PARKBEE|STADSPARKEREN|Q[ -]?PARK|INTERPARKING|NS GROEP IZ NS REIZIGERS|RDW"),
        rule(BankCategory.TRANSPORT, "PARKING (?:ASCH VAN WIJCK|RINGERS ALKMAA|SCHELPHOEK ALK|GROTE KERK|ZAANTHEATER)"),
        rule(BankCategory.TRANSPORT, "PARKEERGARAGE DE K|PARKEREN (?:AMSTERDAM UMC|DEVENTER|GEM\\. BERGEN)|GEM BEVERWIJK PARK"),
        rule(BankCategory.TRANSPORT, "JAARBEURS - IP-PARKING|P1 ARENA|P2 VUMC BETAALAUTOMAAT|PARK\\. AMERSFOORT BV|VILLA ARENA P4/P5"),
        rule(BankCategory.TRANSPORT, "SANEF(?: AUTOROUTE)?|TUNNEL LIEFKENSHOEK NV|FASTNED"),
        rule(BankCategory.CHARITY, "STICHTING CORDAID|STG SAVE THE CHILDREN NE|STICHTING FREE A GIRL|ANTONI VAN LEEUWENHOEK F"),
        rule(BankCategory.EXTRA, "POSTCODE LOTERIJ|ZETURF - ZEBET NL|PATHE THUIS BY BUCKAROO"),
        rule(BankCategory.EXTRA, "STICHTING MUSEUMKAART|TICKETMASTER|TICKETSWAP|STICHTING ZAANTHEATER|STICHTING STADSSCHOUWB(?:URG|UR)?"),
        rule(BankCategory.EXTRA, "STADSSCHOUWBURG UTRE|STICHTING SCHOUWBURG A(?:MS)?|STICHTING DELAMAR THEATE(?:R)?|ST\\. DE LA MAR THEATER"),
        rule(BankCategory.EXTRA, "STICHTING CULTUURKOEPEL|STEDELIJK\\.MUSEUM\\.AMSTE|DE FILMKOEPEL|CINEWORLD|CORSO CASTRICUM|GAMESTATE HAARLEM"),
        rule(BankCategory.EXTRA, "DE EFTELING|STICHTING NATURA ARTIS|NATURALIS BIODIVERSITY|MUSEUM DE WAAG|HUIS VAN HILDE|KASTEEL CANNENBURCH|BURGENWELT EHRENBERG"),
        rule(BankCategory.EXTRA, "KENNEMER THEATER|CAPRERA|PODIUM VICTORIE"),
        rule(BankCategory.EXTRA, "FRIETHUIS \\.BIJ DE BOER\\.|FRIETHUYS BIJ DE BOER|LUNCHROOM-FRIETHUYS BIJ|DE PATATOLOOG|MCDONALDS"),
        rule(BankCategory.EXTRA, "GASTERIJ KRUISBERG|KOFFIE[ -]DIK|KOFFIEPOST|BRAFOER HEEMSKERK|FAMILY HEEMSKERK"),
        rule(BankCategory.EXTRA, "GRAND\\s*CAFE (?:VAN RUYSDAE|LAURENTZ|LUST)|CAFE (?:CHEERS|CANARDO|THIJSSEN|PITCHERS|'T SLUISJE)"),
        rule(BankCategory.EXTRA, "RESTAURANT (?:KOMPERDE|ARRIBA|MARIMBA)|NATURALIS RESTAURANT|EETCAFE (?:LAMORAAL|MEESTERS JUF)"),
        rule(BankCategory.EXTRA, "BEACHCLUB SUNSEA|BRASSERIE VILLA BR|LA ROYALE BRASSERIE|IJSSALON MAZZO"),
        rule(BankCategory.HOLIDAY, "PEZID(?: APARTMENTS)?|APARTHOTEL DELDEN|NATUURHUISJE|MS\\* HOTELLAROYALE"),
    )
    private val processor = Regex("^(?:CCV|BCK|BPT|BNG|CM\\.|C&M|PAY\\.NL|PAY|SUMUP|ZETTLE_|MOLLIE|PAYPAL_)\\s*\\*\\s*", RegexOption.IGNORE_CASE)
    private val ovpay = Regex("^NLOV[A-Z0-9]{14}$", RegexOption.IGNORE_CASE)
    private val sepaPurpose = Regex("(?i)\\bOmschrijving:\\s*(.*?)(?=\\s+(?:Kenmerk|IBAN):|$)")

    fun category(t: BankTransaction): BankCategory? {
        val merchant = t.counterparty.trim().replace(Regex("\\s+"), " ").replace(processor, "")
        // Wooncollecties van kledingketens hebben een ander doel dan kleding.
        if (Regex("^(?:ZARA HOME|H&M HOME)(?=$|[^A-Z0-9])", RegexOption.IGNORE_CASE).containsMatchIn(merchant)) return null
        merchantRules.firstOrNull { it.pattern.containsMatchIn(merchant) }?.let { return it.category }
        // OVpay heeft een bankkenmerk in plaats van een winkelnaam. Alleen in een pinbetaling.
        if (t.description.startsWith("BEA,", true) && ovpay.matches(merchant)) return BankCategory.TRANSPORT
        if (t.description.startsWith("ABN AMRO Bank N.V. Uw Maandtariferingsnota", true)) return BankCategory.BANK_FEES
        // Dit zijn specifieke abonnements-/aankoopomschrijvingen, niet alle betalingen aan deze organisatie.
        if (merchant.equals("DPG Media BV", true) && t.description.contains("Het Parool", true)) return BankCategory.EXTRA
        if (merchant.equals("NPO", true) && Regex("NPO P ?lus", RegexOption.IGNORE_CASE).containsMatchIn(t.description)) return BankCategory.EXTRA
        if (merchant.equals("PWN", true) && t.description.contains("Duinkaart bestelling", true)) return BankCategory.EXTRA
        if (!t.description.startsWith("SEPA ", true) || t.amount.signum() >= 0) return null
        val purpose = sepaPurpose.find(t.description)?.groupValues?.get(1).orEmpty()
        if (Regex("(?i)^(?:hypotheek\\s*rente|rente\\s*hypotheek)\\s+[0-9 ]+$").matches(purpose)) return BankCategory.HOUSING
        // Een betaalverzoek kan wél duidelijk zijn als het doel letterlijk vermeld staat.
        if (Regex("(?i)\\b(?:etentje|uit eten|theater voorstelling|efteling ticket|afscheidscadeau|cadeautje|kadootje)\\b").containsMatchIn(purpose)) return BankCategory.EXTRA
        return null
    }
}
