package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class BankReviewMerchantsTest {
    private val owner = "test@example.invalid"
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val store = MemoryBankStore(mapper)
    private val settings = BankSettings("test-app", "unused", "unused", owner, "https://example.invalid/callback")
    private val gateway = mock(BankingGateway::class.java)
    private val bank = BankService(settings, store, gateway)
    private fun row(id: String, merchant: String, date: String = "2026-10-01", amount: String = "-20.00", description: String = "") =
        BankTransaction(id, "account", date, amount.toBigDecimal(), "EUR", merchant, "", description,
            classificationSource = "QUESTION", question = "Waarvoor was dit?", aiReviewed = true)
    private fun seed(vararg rows: BankTransaction) = rows.groupBy { it.date.take(7) }.forEach { (month, transactions) ->
        store.write(owner, "month-$month", BankTransactions(transactions))
    }

    @Test fun `all months group exact names and processor aliases with bounded examples`() {
        seed(
            row("1", "CCV*Testwinkel Centrum", "2025-12-01"),
            row("2", "Testwinkel Centrum", "2026-02-01"),
            row("3", " testwinkel   centrum ", "2026-06-01"),
            row("4", "BCK*Testwinkel Centrum", "2026-10-01"),
            row("5", "Testwinkel West", "2026-10-02"),
            row("classified", "Bekende winkel", "2025-11-01").copy(category = BankCategory.HOUSING),
        )
        // Malformed storage placement must not make a duplicate history entry.
        store.write(owner, "month-2026-03", BankTransactions(listOf(row("outside", "Testwinkel Centrum", "2026-04-01"))))
        val result = bank.reviewMerchants(owner)
        assertEquals(5, result.transactionCount)
        assertEquals("2025-11-01", result.from)
        assertEquals("2026-10-02", result.to)
        assertEquals(2, result.groups.size)
        val main = result.groups.first()
        assertEquals(4, main.count)
        assertEquals("80.00", main.spent.toPlainString())
        assertEquals("2025-12-01", main.from)
        assertEquals("2026-10-01", main.to)
        assertTrue(main.canLearn)
        assertEquals(3, main.examples.size)
        assertEquals(listOf("4", "3", "2"), main.examples.map { it.id })
        assertEquals(main.key, bank.reviewMerchants(owner).groups.first().key)
        verifyNoInteractions(gateway)
    }

    @Test fun `ambiguous people payment requests and platforms stay individual`() {
        val merchants = listOf("", "PayPal", "PAY.nl", "Stichting Pay.nl", "PAY", "Mollie Payments", "Stichting Mollie Payments", "Adyen NV", "Klarna", "Mangopay SA", "Hr Test", "J. Voorbeeld")
        val rows = merchants.flatMapIndexed { i, name -> listOf(row("$i-a", name), row("$i-b", name)) } + listOf(
            row("request-a", "Testpersoon via Rabo B", description = "SEPA iDEAL Naam: Testpersoon via Rabo Bet aalverzoek"),
            row("request-b", "Testpersoon via Rabo B", description = "SEPA iDEAL Naam: Testpersoon via Rabo Bet aalverzoek"),
            row("transfer", "Jan Voorbeeld", description = "SEPA Overboeking IBAN: TEST BIC: TEST Naam: Jan Voorbeeld"),
        )
        seed(*rows.toTypedArray())
        val groups = bank.reviewMerchants(owner).groups
        assertEquals(rows.size, groups.size)
        assertTrue(groups.all { !it.canLearn && it.count == 1 && it.examples.size == 1 })
        assertThrows(IllegalArgumentException::class.java) { bank.classifyMerchant(owner, groups.first().key, BankCategory.EXTRA) }
        assertNull(store.read(owner, "rules", BankRules::class.java))
    }

    @Test fun `identifiable merchant via processor is learnable but generic processor is not`() {
        assertTrue(bankCanLearnMerchant(row("merchant", "Testwinkel via Mollie")))
        assertTrue(bankCanLearnMerchant(row("card", "CCV*Testwinkel")))
        assertTrue(bankCanLearnMerchant(row("named-pay-merchant", "PAY.nl*Testwinkel")))
        assertFalse(bankCanLearnMerchant(row("platform", "Mollie Payments")))
        assertFalse(bankCanLearnMerchant(row("request", "Testwinkel via Tik kie")))
        assertEquals("TESTWINKEL CENTRUM", bankMerchantIdentity(" CCV*Testwinkel  Centrum "))
        val nested = "CCV*BNG*BCK*Testwinkel Centrum"
        assertEquals("TESTWINKEL CENTRUM", bankMerchantIdentity(nested))
        assertEquals(bankMerchantIdentity(nested), bankMerchantIdentity(bankMerchantLabel(nested)))
        assertEquals("ONBEKEND*TESTWINKEL", bankMerchantIdentity("CCV*Onbekend*Testwinkel"))
        assertNotEquals(bankMerchantIdentity("Testwinkel Centrum"), bankMerchantIdentity("Testwinkel West"))
    }

    @Test fun `merchant choice learns history future payments and refunds without rewriting manual choices`() {
        val original = row("old", "CCV*BNG*Testwinkel Centrum", "2026-01-01")
        val manual = row("manual", "Testwinkel Centrum", "2026-01-02").copy(manual = true, category = BankCategory.BUSINESS)
        seed(original, row("recent", "Testwinkel Centrum"), manual,
            row("refund", "Testwinkel Centrum", amount = "5.00").copy(category = BankCategory.INCOME),
            row("longer", "Testwinkel Centrum Outlet"), row("description", "Andere winkel", description = "Gekocht bij Testwinkel Centrum"))
        val group = bank.reviewMerchants(owner).groups.single { it.count == 2 }
        assertEquals(2, bank.classifyMerchant(owner, group.key, BankCategory.CLOTHING).count)
        val rules = bank.rules(owner)
        assertEquals(1, rules.rules.size)
        assertEquals("TESTWINKEL CENTRUM", rules.rules.single().merchant)
        assertTrue(rules.rules.single().learned)
        assertEquals("", rules.rules.single().contains)
        // SaveRules must round-trip merchant-only rules rather than rejecting a blank contains field.
        assertEquals(rules.rules, bank.saveRules(owner, rules).rules)
        assertEquals(BankCategory.CLOTHING, bank.month(owner, "2026-01").transactions.single { it.id == "old" }.category)
        assertEquals(BankCategory.BUSINESS, bank.month(owner, "2026-01").transactions.single { it.id == "manual" }.category)
        val october = bank.month(owner, "2026-10").transactions.associateBy { it.id }
        assertEquals(BankCategory.CLOTHING, october.getValue("refund").category)
        assertEquals("LEARNED", october.getValue("recent").classificationSource)
        assertEquals(BankCategory.REVIEW, october.getValue("longer").category)
        assertEquals(BankCategory.REVIEW, october.getValue("description").category)
        val future = BankLedger.classify(row("future", "BCK*CCV*BNG*Testwinkel Centrum", "2027-01-01"), emptyList(), rules.rules)
        assertEquals(BankCategory.CLOTHING, future.category)
        assertEquals(original, store.read(owner, "month-2026-01", BankTransactions::class.java)!!.transactions.single { it.id == "old" })
        assertThrows(ResponseStatusException::class.java) { bank.classifyMerchant(owner, group.key, BankCategory.EXTRA) }
        verifyNoInteractions(gateway)
    }

    @Test fun `merchant rule cannot match a named person payment request through description or structured payee`() {
        val rule = BankRule("merchant-test", "", "", BankCategory.CLOTHING, merchant = "Testwinkel", learned = true)
        val request = row("tikkie", "Testwinkel", description = "SEPA iDEAL Naam: Voorbeeld via Tikkie Omschrijving: kleding")
        assertEquals(BankCategory.REVIEW, BankLedger.classify(request, emptyList(), listOf(rule)).category)
        val transfer = row("transfer", "Testwinkel", description = "SEPA Overboeking IBAN: TEST BIC: TEST Naam: Testwinkel")
        assertEquals(BankCategory.REVIEW, BankLedger.classify(transfer, emptyList(), listOf(rule)).category)
        val ordinary = row("merchant", "Testwinkel")
        assertEquals(BankCategory.CLOTHING, BankLedger.classify(ordinary, emptyList(), listOf(rule)).category)
    }

    @Test fun `invalid scope categories and stale keys do not save rules`() {
        seed(row("1", "Testwinkel"))
        val key = bank.reviewMerchants(owner).groups.single().key
        listOf(BankCategory.REVIEW, BankCategory.INTERNAL, BankCategory.INCOME).forEach {
            assertThrows(IllegalArgumentException::class.java) { bank.classifyMerchant(owner, key, it) }
        }
        assertThrows(IllegalArgumentException::class.java) { bank.classifyMerchant(owner, "bad-key", BankCategory.EXTRA) }
        assertThrows(ResponseStatusException::class.java) { bank.classifyMerchant(owner, "a".repeat(64), BankCategory.EXTRA) }
        assertThrows(ResponseStatusException::class.java) { bank.reviewMerchants("another@example.invalid") }
        assertThrows(ResponseStatusException::class.java) { bank.classifyMerchant("another@example.invalid", key, BankCategory.EXTRA) }
        assertNull(store.read(owner, "rules", BankRules::class.java))
    }
}
