package nl.vdzon.robbertsassistent.banking

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Fictieve betalingen: geen geïmporteerde bankgegevens in testfixtures. */
class BankMerchantRulesTest {
    private fun payment(merchant: String, description: String = "Bestelling test", amount: String = "-12.50") =
        BankTransaction("fixture", "test-account", "2026-01-01", amount.toBigDecimal(), "EUR", merchant, "", description)
    private fun classify(t: BankTransaction, rules: List<BankRule> = emptyList()) =
        BankLedger.classify(t, emptyList(), rules)

    @Test fun `kledingwinkels en naamvarianten krijgen een eigen categorie`() {
        listOf("Desigual", "G-Star RAW", "G STAR RAW", "Superdry.nl", "Undiemeister B.V.",
            "ABOUT YOU", "Zalando via Payment Service", "Adidas Benelux B.V.", "Vans Europe",
            "O.Neill Europe B.V.", "O'Neill", "O’Neill", "Oneill Amsterdam", "Levi's Utrecht",
            "Levi’s Outlet", "Levis Outlet Amsterdam", "The Sting Alkmaar", "C&A 001 Teststad",
            "H&M", "Zara", "America Today", "Tantilly").forEach { merchant ->
            assertEquals(BankCategory.CLOTHING, classify(payment(merchant)).category, merchant)
        }
    }

    @Test fun `kleding is geen luxe en terugbetalingen verlagen de kledinguitgave`() {
        val debit = classify(payment("Desigual", amount = "-50.00"))
        val refund = classify(payment("Desigual", amount = "20.00")).copy(id = "refund")
        val month = BankLedger.month("2026-01", listOf(debit, refund), emptyList())
        val clothing = month.categories.single()
        assertEquals(BankCategory.CLOTHING, clothing.category)
        assertEquals("50.00", clothing.spent.toPlainString())
        assertEquals("20.00", clothing.received.toPlainString())
        assertEquals(0, month.extra.signum())
        assertEquals(0, month.review.signum())
    }

    @Test fun `eerder gestelde ai vraag wordt opgelost door duidelijke vaste regel`() {
        val oldQuestion = payment("CCV*Desigual").copy(aiReviewed = true, classificationSource = "QUESTION",
            question = "Was dit nodig?", suggestedCategory = BankCategory.EXTRA)
        val actual = classify(oldQuestion)
        assertEquals(BankCategory.CLOTHING, actual.category)
        assertEquals("RULE", actual.classificationSource)
        assertEquals("", actual.question)
        assertFalse(actual.aiReviewed)
    }

    @Test fun `handmatige indelingen en eigen regels hebben voorrang op winkelregels`() {
        val raw = payment("Desigual")
        assertEquals(BankCategory.BUSINESS, classify(raw.copy(category = BankCategory.BUSINESS, manual = true)).category)
        val rule = BankRule("user-rule", "Desigual", "", BankCategory.EXTRA, learned = true)
        val learned = classify(raw, listOf(rule))
        assertEquals(BankCategory.EXTRA, learned.category)
        assertEquals("LEARNED", learned.classificationSource)
        val ask = BankRule("ask-user", "Desigual", "", BankCategory.REVIEW)
        assertEquals(BankCategory.REVIEW, classify(raw, listOf(ask)).category)
    }

    @Test fun `winkelnaam in bankgegenereerde pinomschrijving krijgt dezelfde regel`() {
        val raw = payment("", "BEA, Google Pay CCV*Desigual,PAS000 NR:TEST01, 01.01.26/12:00 TESTSTAD")
        assertEquals(BankCategory.CLOTHING, classify(raw).category)
        assertEquals("CCV*Desigual", classify(raw).counterparty)
    }

    @Test fun `korte woorden en namen midden in andere winkels veroorzaken geen overeenkomst`() {
        listOf("NotDesigual", "Desigualish", "C&AB Services", "RDWorks", "ParkBeeKeeper",
            "The Stingray", "Voorbeeld via Tikkie", "MALDIVES HOTEL", "Vivaldi restaurant",
            "Jumbo Golf", "Zara Home", "H&M Home").forEach { merchant ->
            assertEquals(BankCategory.REVIEW, classify(payment(merchant, "Bestelling Desigual test")).category, merchant)
        }
    }

    @Test fun `betaalplatformen en gemengde winkels krijgen zonder inhoud geen categorie`() {
        listOf("Mollie", "PayPal", "Adyen", "Vinted via Mangopay", "bol.com", "Amazon EU SARL",
            "HEMA", "IKEA", "Action", "Kringloop Teststad", "Decathlon", "Futurumshop.nl",
            "Bergfreunde", "Sissy-Boy", "NYPD Teststad", "Onbekende via Tikkie").forEach { merchant ->
            assertEquals(BankCategory.REVIEW, classify(payment(merchant)).category, merchant)
        }
    }

    @Test fun `herkenbare doelen worden ook achter betaalverwerkers herkend`() {
        val fixtures = mapOf(
            "CCV*Slagerij De Roode" to BankCategory.GROCERIES,
            "BCK*Goemans HaydnpleinW" to BankCategory.GROCERIES,
            "Harmony Insurances" to BankCategory.INSURANCE,
            "DELA Natura- en levensverzekering" to BankCategory.INSURANCE,
            "Infomedics B.V." to BankCategory.HEALTH,
            "Warmte Groep B.V. via Mollie" to BankCategory.HOUSING,
            "Stichting Cordaid" to BankCategory.CHARITY,
            "Q-Park Operations Holding" to BankCategory.TRANSPORT,
            "BNG*Gem Beverwijk park" to BankCategory.TRANSPORT,
            "Mollie *Natuurhuisje" to BankCategory.HOLIDAY,
            "PAY.nl*Stichting Museumkaart" to BankCategory.EXTRA,
            "Postcode Loterij" to BankCategory.EXTRA,
            "CCV*Welkoop Teststad" to BankCategory.EXTRA,
        )
        fixtures.forEach { (merchant, expected) -> assertEquals(expected, classify(payment(merchant)).category, merchant) }
    }

    @Test fun `ovpay vereist bankgegenereerde pinbetaling en volledig formaat`() {
        val reference = "NLOV1234567890ABCD"
        assertEquals(BankCategory.TRANSPORT, classify(payment(reference,
            "BEA, Betaalpas $reference NR:TEST01, 01.01.26/12:00 TESTSTAD")).category)
        assertEquals(BankCategory.REVIEW, classify(payment(reference)).category)
        assertEquals(BankCategory.REVIEW, classify(payment("NLOV12345", "BEA, Betaalpas NLOV12345")).category)
        assertEquals(BankCategory.REVIEW, classify(payment("Hbergpark ING 0000")).category)
    }

    @Test fun `waterrekening en duinkaarten zijn verschillende doelen`() {
        assertEquals(BankCategory.HOUSING, classify(payment("Waterleidingbedrijf", "SEPA Incasso waterrekening")).category)
        assertEquals(BankCategory.EXTRA, classify(payment("PWN", "SEPA iDEAL Omschrijving: Duinkaart bestelling Kenmerk: test")).category)
        assertEquals(BankCategory.REVIEW, classify(payment("Waterleidingbedrijf", "BEA, Betaalpas Waterleidingbedrijf NR:TEST01, 01.01.26/12:00 TESTSTAD")).category)
        assertEquals(BankCategory.REVIEW, classify(payment("PWN", "Bestelling onbekend")).category)
    }

    @Test fun `alleen expliciet betaaldoel mag particuliere hypotheekrente herkennen`() {
        listOf("Hypotheek rente 2026", "Rente hypotheek 20 26").forEach { purpose ->
            val description = "SEPA Overboeking Naam: Voorbeeld Omschrijving: $purpose Kenmerk: test"
            assertEquals(BankCategory.HOUSING, classify(payment("Voorbeeldpersoon", description)).category)
            assertEquals(BankCategory.INCOME, classify(payment("Voorbeeldpersoon", description, "12.50")).category)
        }
        assertEquals(BankCategory.REVIEW, classify(payment("Hypotheek rente", "Onbekende overschrijving")).category)
        assertEquals(BankCategory.REVIEW, classify(payment("Voorbeeldpersoon", "SEPA Overboeking Omschrijving: bespreken hypotheek rente Kenmerk: test")).category)
    }

    @Test fun `doel in betaalverzoek mag duidelijk zijn maar de ontvanger blijft generiek`() {
        val party = "Voorbeeldpersoon via Tikkie"
        assertEquals(BankCategory.EXTRA, classify(payment(party,
            "SEPA iDEAL Naam: Voorbeeldpersoon Omschrijving: 1234 Etentje Kenmerk: test")).category)
        assertEquals(BankCategory.REVIEW, classify(payment(party,
            "SEPA iDEAL Naam: Voorbeeldpersoon Omschrijving: 1234 reparatie Kenmerk: test")).category)
    }

    @Test fun `bankkosten en media vereisen de juiste omschrijving`() {
        assertEquals(BankCategory.BANK_FEES, classify(payment("", "ABN AMRO Bank N.V. Uw Maandtariferingsnota staat klaar")).category)
        assertEquals(BankCategory.REVIEW, classify(payment("ABN AMRO Bank N.V.", "Onbekend")).category)
        listOf("NPO Plus", "NPO P lus").forEach { subscription ->
            assertEquals(BankCategory.EXTRA, classify(payment("NPO", "Lidmaatschap $subscription")).category)
        }
        assertEquals(BankCategory.REVIEW, classify(payment("NPO", "Onbekend")).category)
        assertEquals(BankCategory.EXTRA, classify(payment("DPG Media BV", "Het Parool abonnement")).category)
        assertEquals(BankCategory.REVIEW, classify(payment("DPG Media BV", "Onbekende factuur")).category)
    }
}
