package nl.vdzon.robbertsassistent.banking

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BankMerchantTest {
    private fun row(description: String, merchant: String = "") = BankTransaction(
        "original-id", "account", "2026-10-01", "-20.00".toBigDecimal(), "EUR", merchant, "", description,
    )

    @Test fun `card headers expose merchant without payment reference or location`() {
        assertEquals("CCV*Modewinkel", bankCardMerchant(
            "BEA, Betaalpas CCV*Modewinkel,PAS123 NR:CT123456, 01.10.26/12:34 AMSTERDAM"))
        assertEquals("BCK*Jumbo Centrum", bankCardMerchant(
            "BEA, Google Pay BCK*Jumbo Centrum NR:CT123456, 01.10.26/12:34 AMSTERDAM KAARTNUMMER: **0123"))
        assertEquals("Winkel", bankCardMerchant(
            "BEA, Apple Pay Winkel,PAS123 NR:A12345, 01.10.26/12:34 AMSTERDAM"))
        assertEquals("Gamma Test (RPA)", bankCardMerchant(
            "BEA,BETAALPAS Gamma Test (RPA) NR:HK1234  ,01.10.26/12:34 TEST, LAND: NLD KAARTNUMMER:**0123 TERUGBOEKING BEA-TRANSACTIE"))
        assertEquals("ESSO TEST NOORD CO", bankCardMerchant(
            "BEA,BETAALPAS ESSO TEST NOORD CO 01.10.26/12:34 TEST NOORD, LAND: BEL KAARTNUMMER:**0123 KOSTEN 0,15EUR ACHTERAF BEREKEND"))
        assertEquals("IKEA Test", bankCardMerchant(
            "BEA, Betaalpas IKEA Test,PAS123 NR:PHY6YK   01.10.26/12.34 TEST TERUGBOEKING-BEA-TRANSACTIE"))
    }

    @Test fun `free text SEPA transfers withdrawals and incomplete card headers are not merchant sources`() {
        listOf(
            "SEPA Overboeking Naam: Vriend Omschrijving: BEA, Betaalpas Albert Heijn,PAS123 NR:CT123456, 01.10.26/12:34 TEST",
            "GEA, Betaalpas Geldmaat,PAS123 NR:CT123456, 01.10.26/12:34 TEST",
            "BEA, Betaalpas Albert Heijn", "BEA, Betaalpas Winkel NR: niet-bankmetadata",
            "SEPA iDEAL Naam: Tikkie Omschrijving: Jumbo boodschappen",
        ).forEach { assertEquals("", bankCardMerchant(it), it) }
    }

    @Test fun `existing stored blank payees are enriched before rule classification without changing identity`() {
        val raw = row("BEA, Google Pay BCK*Jumbo Centrum NR:CT123456, 01.10.26/12:34 TEST KAARTNUMMER: **0123")
            .copy(aiReviewed = true, classificationSource = "QUESTION", question = "Waarvoor was dit?")
        val classified = BankLedger.classify(raw, emptyList(), emptyList())
        assertEquals("BCK*Jumbo Centrum", classified.counterparty)
        assertEquals(raw.id, classified.id)
        assertEquals(raw.description, classified.description)
        assertEquals(BankCategory.GROCERIES, classified.category)
        assertEquals("RULE", classified.classificationSource)
        assertEquals("", classified.question)
    }

    @Test fun `structured payees and manual classifications keep precedence`() {
        val raw = row("BEA, Betaalpas Jumbo Centrum,PAS123 NR:CT123456, 01.10.26/12:34 TEST", "Bank-provided payee")
            .copy(category = BankCategory.BUSINESS, manual = true)
        val classified = BankLedger.classify(raw, emptyList(), emptyList())
        assertEquals("Bank-provided payee", classified.counterparty)
        assertEquals(BankCategory.BUSINESS, classified.category)
        assertEquals("USER", classified.classificationSource)
        val blank = BankLedger.classify(raw.copy(counterparty = ""), emptyList(), emptyList())
        assertEquals("Jumbo Centrum", blank.counterparty)
        assertEquals(BankCategory.BUSINESS, blank.category)
    }
}
