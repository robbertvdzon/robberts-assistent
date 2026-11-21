package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Instant
import java.util.concurrent.TimeUnit

class BankCategorizationTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val owner = "test@example.invalid"
    private val settings = BankSettings("test", "unused", "unused", owner, "https://example.invalid/callback")
    private val store = MemoryBankStore(mapper)
    private val bank = BankService(settings, store, mock(BankingGateway::class.java))
    private fun row(id: String, merchant: String) = BankTransaction(id, "account", "2026-11-01", "-10.00".toBigDecimal(), "EUR", merchant, "", "Aankoop")
    private fun decision(category: BankCategory, needsUser: Boolean = false, confidence: Double = 0.99) =
        BankAiDecision("item-0", category, confidence, needsUser, "Reden", "Waarvoor was deze aankoop?")

    @Test fun `AI mag onzekere en gemengde aankopen niet automatisch als extra boeken`() {
        assertEquals(BankCategory.REVIEW, applyBankAi(row("1", "bol.com"), decision(BankCategory.EXTRA)).category)
        assertEquals(BankCategory.REVIEW, applyBankAi(row("1", "Winkel"), decision(BankCategory.EXTRA, true)).category)
        assertEquals(BankCategory.REVIEW, applyBankAi(row("1", "Winkel"), decision(BankCategory.EXTRA, confidence = 0.8)).category)
        assertEquals(BankCategory.REVIEW, applyBankAi(row("1", "Winkel"), decision(BankCategory.INTERNAL)).category)
        val accepted = applyBankAi(row("1", "Theater"), decision(BankCategory.EXTRA))
        assertEquals(BankCategory.EXTRA, accepted.category)
        assertEquals("AI", accepted.classificationSource)
    }
    @Test fun `bekende eigen tegenrekening is intern ook als toestemming daarvoor nog ontbreekt`() {
        val t = row("1", "Eigen rekening").copy(counterpartyIban = "NL00TEST0000000002")
        assertEquals(BankCategory.INTERNAL, BankLedger.classify(t, emptyList(), emptyList(), listOf("NL00TEST0000000002")).category)
    }
    @Test fun `regelvoorwaarden blijven beperkt tot bronrekening richting en vereiste tekst`() {
        val account = BankAccount("account", "uid", "NL00TEST0000000001", "Test", "session", Instant.now().plusSeconds(600))
        val rule = BankRule("legacy", "", "", BankCategory.BUSINESS, required = listOf("ACCOUNTANT"), forbidden = listOf("PRIVE"), accountSuffix = "0000000001", direction = "DBIT")
        assertEquals(BankCategory.BUSINESS, BankLedger.classify(row("1", "Accountant"), listOf(account), listOf(rule)).category)
        assertEquals(BankCategory.REVIEW, BankLedger.classify(row("1", "Accountant prive"), listOf(account), listOf(rule)).category)
        assertEquals(BankCategory.INCOME, BankLedger.classify(row("1", "Accountant").copy(amount = "10.00".toBigDecimal()), listOf(account), listOf(rule)).category)
    }
    @Test fun `antwoord leert alleen een regel na expliciete keuze en bewaart handmatige indeling`() {
        store.write(owner, "month-2026-11", BankTransactions(listOf(row("1", "Testwinkel"), row("2", "Testwinkel"))))
        bank.classify(owner, "2026-11", "1", BankCategory.HEALTH, true, "Testwinkel")
        assertTrue(bank.rules(owner).rules.single().learned)
        val report = bank.month(owner, "2026-11")
        assertTrue(report.transactions.all { it.category == BankCategory.HEALTH })
        assertTrue(report.transactions.first { it.id == "1" }.manual)
        assertFalse(report.transactions.first { it.id == "2" }.manual)
    }
    @Test fun `gehele historie krijgt regels en AI vragen zonder handmatige correcties te overschrijven`() {
        val manual = row("manual", "Testwinkel").copy(manual = true, category = BankCategory.HEALTH)
        store.write(owner, "month-2026-11", BankTransactions(listOf(row("bill", "Ziggo"), manual, row("fun", "Theater"), row("question", "bol.com"))))
        store.write(owner, "month-2026-10", BankTransactions(listOf(row("old", "Theater").copy(date = "2026-10-01"))))
        var analyzed = 0
        val ai = object : BankAiPort {
            override fun assess(items: List<BankAiItem>): List<BankAiDecision> {
                analyzed += items.size
                return items.map { decision(BankCategory.EXTRA).copy(id = it.id) }
            }
        }
        val runner = BankCategorizationRunner(settings, bank, store, ai)
        try {
            runner.start(owner)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (runner.status(owner).status == "RUNNING" && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals("DONE", runner.status(owner).status)
            assertEquals(2, analyzed) // Identieke herhalingen over twee maanden zijn één AI-vraag.
            val rows = bank.month(owner, "2026-11").transactions.associateBy { it.id }
            assertEquals(BankCategory.HOUSING, rows["bill"]!!.category)
            assertEquals(BankCategory.HEALTH, rows["manual"]!!.category)
            assertEquals(BankCategory.EXTRA, rows["fun"]!!.category)
            assertEquals(BankCategory.REVIEW, rows["question"]!!.category)
            assertTrue(rows["question"]!!.question.isNotBlank())
            assertEquals(BankCategory.EXTRA, bank.month(owner, "2026-10").transactions.single().category)
        } finally { runner.close() }
    }
    @Test fun `een verwijderde regel blijft niet als oude AI beslissing doorwerken`() {
        val ai = applyBankAi(row("1", "Theater"), decision(BankCategory.EXTRA))
        val ruled = BankLedger.classify(ai, emptyList(), listOf(BankRule("test", "Theater", "", BankCategory.HEALTH)))
        assertEquals(BankCategory.HEALTH, ruled.category)
        assertEquals(BankCategory.REVIEW, BankLedger.classify(ruled, emptyList(), emptyList()).category)
    }
    @Test fun `AI input verwijdert rekeningnummers en lange identificaties`() {
        val safe = bankAiText("Betaling NL91ABNA0417164300 factuur 1234567890")
        assertFalse(safe.contains("NL91ABNA0417164300"))
        assertFalse(safe.contains("1234567890"))
    }
}
