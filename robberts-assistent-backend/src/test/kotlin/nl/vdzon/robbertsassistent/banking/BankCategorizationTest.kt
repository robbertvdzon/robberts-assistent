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
    private fun awaitFinished(runner: BankCategorizationRunner): BankCategorizationJob {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (runner.status(owner).status == "RUNNING" && System.nanoTime() < deadline) Thread.sleep(10)
        return runner.status(owner)
    }

    @Test fun `grotere batches draaien begrensd parallel en tellen geen resultaten dubbel`() {
        store.write(owner, "month-2026-11", BankTransactions((0 until 250).map { row("id-$it", "Testfirma $it") }))
        val entered = java.util.concurrent.CountDownLatch(3)
        val release = java.util.concurrent.CountDownLatch(1)
        val active = java.util.concurrent.atomic.AtomicInteger()
        val maximum = java.util.concurrent.atomic.AtomicInteger()
        val sizes = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val ai = object : BankAiPort {
            override fun assess(items: List<BankAiItem>): List<BankAiDecision> {
                sizes += items.size
                val current = active.incrementAndGet()
                maximum.accumulateAndGet(current, ::maxOf)
                entered.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS))
                    return items.map { decision(BankCategory.HEALTH).copy(id = it.id) }
                } finally { active.decrementAndGet() }
            }
        }
        val runner = BankCategorizationRunner(settings, bank, store, ai)
        try {
            runner.start(owner)
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Er moeten drie batches tegelijk kunnen werken")
            assertEquals(3, maximum.get())
            release.countDown()
            val job = awaitFinished(runner)
            assertEquals("DONE", job.status)
            assertEquals(listOf(50, 100, 100), sizes.sorted())
            assertEquals(250, job.completed)
            assertEquals(3, job.batchesCompleted)
            assertEquals(250, bank.month(owner, "2026-11").transactions.count { it.category == BankCategory.HEALTH })
        } finally { release.countDown(); runner.close() }
    }

    @Test fun `snelle batch wordt opgeslagen zonder op trage batch te wachten en handmatig antwoord wint`() {
        store.write(owner, "month-2026-11", BankTransactions(listOf(row("slow", "Langzaam"), row("fast", "Snel"))))
        val slowEntered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val ai = object : BankAiPort {
            override fun assess(items: List<BankAiItem>): List<BankAiDecision> {
                if (items.single().merchant == "Langzaam") { slowEntered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                return items.map { decision(BankCategory.HEALTH).copy(id = it.id) }
            }
        }
        val runner = BankCategorizationRunner(settings, bank, store, ai, batchSize = 1, parallelism = 2)
        try {
            runner.start(owner)
            assertTrue(slowEntered.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (runner.status(owner).completed == 0 && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(1, runner.status(owner).completed)
            bank.classify(owner, "2026-11", "slow", BankCategory.EXTRA)
            release.countDown()
            assertEquals("DONE", awaitFinished(runner).status)
            assertEquals(BankCategory.EXTRA, bank.month(owner, "2026-11").transactions.first { it.id == "slow" }.category)
        } finally { release.countDown(); runner.close() }
    }

    @Test fun `mislukte batch blokkeert andere batches niet en opnieuw starten verwerkt alleen wat ontbreekt`() {
        store.write(owner, "month-2026-11", BankTransactions(listOf(row("bad", "Fout"), row("good", "Goed"))))
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val seen = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val ai = object : BankAiPort {
            override fun assess(items: List<BankAiItem>): List<BankAiDecision> {
                seen.addAll(items.map { it.merchant })
                if (items.any { it.merchant == "Fout" } && fail.get()) error("Testfout zonder financiële gegevens")
                return items.map { decision(BankCategory.HEALTH).copy(id = it.id) }
            }
        }
        val runner = BankCategorizationRunner(settings, bank, store, ai, batchSize = 1, parallelism = 2)
        try {
            runner.start(owner)
            assertEquals("ERROR", awaitFinished(runner).status)
            assertEquals(1, runner.status(owner).completed)
            fail.set(false); seen.clear()
            runner.start(owner)
            assertEquals("DONE", awaitFinished(runner).status)
            assertEquals(listOf("Fout"), seen.toList())
            assertEquals(1, runner.status(owner).completed)
        } finally { runner.close() }
    }

    @Test fun `hervatten na uitrol bewaart de voortgang en verwerkt alleen ontbrekende indelingen`() {
        val old = row("old", "Al klaar").copy(category = BankCategory.HEALTH, aiReviewed = true, classificationSource = "AI")
        store.write(owner, "month-2026-11", BankTransactions(listOf(old, row("new", "Nog verwerken"))))
        store.write(owner, "categorization", BankCategorizationJob("old-run", "ERROR", total = 2, completed = 1))
        val seen = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val ai = object : BankAiPort {
            override fun assess(items: List<BankAiItem>): List<BankAiDecision> {
                seen.addAll(items.map { it.merchant })
                return items.map { decision(BankCategory.HEALTH).copy(id = it.id) }
            }
        }
        val runner = BankCategorizationRunner(settings, bank, store, ai)
        try {
            runner.start(owner, recover = true)
            val job = awaitFinished(runner)
            assertEquals("DONE", job.status)
            assertEquals(2, job.total)
            assertEquals(2, job.completed)
            assertEquals(listOf("Nog verwerken"), seen.toList())
        } finally { runner.close() }
    }
    @Test fun `onvolledige AI output wordt niet als een volledige batch opgeslagen`() {
        store.write(owner, "month-2026-11", BankTransactions(listOf(row("1", "Een"), row("2", "Twee"))))
        val ai = object : BankAiPort {
            override fun assess(items: List<BankAiItem>) = listOf(decision(BankCategory.HEALTH).copy(id = items.first().id))
        }
        val runner = BankCategorizationRunner(settings, bank, store, ai)
        try {
            runner.start(owner)
            assertEquals("ERROR", awaitFinished(runner).status)
            assertEquals(0, runner.status(owner).completed)
            assertTrue(bank.month(owner, "2026-11").transactions.none { it.aiReviewed })
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
