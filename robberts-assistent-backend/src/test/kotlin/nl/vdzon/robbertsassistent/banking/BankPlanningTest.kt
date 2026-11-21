package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant

class BankPlanningTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val owner = "owner@example.invalid"
    private val settings = BankSettings("test", "unused", "unused", owner, "https://example.invalid/callback")
    private val store = MemoryBankStore(mapper)
    private val gateway = mock(BankingGateway::class.java)
    private val bank = BankService(settings, store, gateway)
    private val holiday = BankPlanItem("holiday", " Wintersport ", BigDecimal("3500"), 2, annual = true)

    @Test fun `historie houdt twaalf maanden aan en verrekent geen interne betaling als uitgave`() {
        store.write(owner, "connection", BankConnection(listOf(
            BankAccount("account", "uid", "NL00TEST0000000001", "Test", "secret", Instant.now().plusSeconds(600)))))
        fun row(id: String, date: String, amount: String, category: BankCategory) = BankTransaction(
            id, "account", date, BigDecimal(amount), "EUR", "Testwinkel", "", "", category, manual = true)
        store.write(owner, "month-2026-01", BankTransactions(listOf(
            row("cost", "2026-01-02", "-100.00", BankCategory.INSURANCE),
            row("refund", "2026-01-04", "20.00", BankCategory.INSURANCE),
            row("internal", "2026-01-05", "-500.00", BankCategory.INTERNAL),
            row("wrong-month", "2025-12-31", "-100.00", BankCategory.EXTRA))))
        store.write(owner, "month-2025-12", BankTransactions(listOf(row("outside", "2025-12-01", "-99.00", BankCategory.EXTRA))))
        store.write("other@example.invalid", "month-2026-12", BankTransactions(listOf(row("other-owner", "2026-12-31", "-99.00", BankCategory.EXTRA))))
        val report = bank.history(owner, "2026-12")
        assertEquals(12, report.months.size)
        assertEquals("2026-01", report.months.first().month)
        assertEquals("2026-12", report.months.last().month)
        assertEquals("2026-01-02", report.from)
        assertEquals("2026-01-05", report.to)
        assertEquals(BigDecimal("100.00"), report.months.first().spent)
        assertEquals(BigDecimal("20.00"), report.months.first().received)
        assertEquals(BigDecimal("80.00"), report.months.first().categories.single().let { it.spent - it.received })
        assertTrue(report.months.last().transactions.isEmpty())
        assertTrue(report.warnings.any { it.contains("lege maand") })
        verifyNoInteractions(gateway)
    }

    @Test fun `lege historie heeft geen verzonnen begin en einddatum`() {
        val report = bank.history(owner, "2026-12")
        assertNull(report.from)
        assertNull(report.to)
        assertEquals(12, report.months.size)
        assertTrue(report.months.all { it.transactions.isEmpty() })
        assertThrows(IllegalArgumentException::class.java) { bank.history(owner, "2026-13") }
        assertThrows(IllegalArgumentException::class.java) { bank.history(owner, "2026-1") }
    }

    @Test fun `kledingcorrectie leert ook retouren en heeft eigen budget en historietotaal`() {
        fun row(id: String, amount: String) = BankTransaction(id, "account", "2026-10-15",
            BigDecimal(amount), "EUR", "Test Kledingzaak", "", "Kledingbetaling")
        store.write(owner, "month-2026-10", BankTransactions(listOf(row("purchase", "-120.00"), row("return", "30.00"))))
        bank.classify(owner, "2026-10", "purchase", BankCategory.CLOTHING, remember = true, matchText = "Test Kledingzaak")
        bank.savePlanning(owner, 2027, BankPlanning(2027, budgets = mapOf(BankCategory.CLOTHING to BigDecimal("150"))))

        val month = bank.history(owner, "2026-10").months.last()
        val clothing = month.categories.single()
        assertEquals(BankCategory.CLOTHING, clothing.category)
        assertEquals("Kleding", clothing.label)
        assertEquals(BigDecimal("90.00"), clothing.spent - clothing.received)
        assertEquals(0, month.extra.signum())
        assertEquals(0, month.review.signum())
        assertTrue(month.transactions.all { it.category == BankCategory.CLOTHING })
        assertEquals("LEARNED", month.transactions.single { it.id == "return" }.classificationSource)
        assertEquals(BigDecimal("150.00"), bank.planning(owner, 2027).budgets[BankCategory.CLOTHING])
        verifyNoInteractions(gateway)
    }

    @Test fun `planning bewaart bedragen en budgetten onder juiste eigenaar en jaar`() {
        val plan = BankPlanning(2027, BigDecimal("6850"), BigDecimal("4000.5"), BigDecimal("2000"),
            mapOf(BankCategory.EXTRA to BigDecimal("500"), BankCategory.GROCERIES to BigDecimal("850")), listOf(holiday))
        val saved = bank.savePlanning(owner, 2027, plan)
        assertEquals("Wintersport", saved.items.single().title)
        assertEquals(BigDecimal("6850.00"), saved.monthlyIncome)
        assertEquals(BigDecimal("4000.50"), saved.monthlyExpenses)
        assertEquals(saved, bank.planning(owner, 2027))
        assertNull(store.read("other@example.invalid", "planning-2027", BankPlanning::class.java))
        assertEquals(listOf("planning-2027"), store.keys(owner))
        verifyNoInteractions(gateway)
    }

    @Test fun `jaarlijkse posten komen als onveranderde ramingen uit dichtstbijzijnde eerder opgeslagen jaar`() {
        bank.savePlanning(owner, 2025, BankPlanning(2025, items = listOf(holiday.copy(amount = BigDecimal("1000")))))
        bank.savePlanning(owner, 2027, BankPlanning(2027, monthlyIncome = BigDecimal("6000"),
            items = listOf(holiday, holiday.copy(id = "sofa", title = "Bank", annual = false))))
        val keysBefore = store.keys(owner).sorted()
        val carry = bank.planning(owner, 2029)
        assertEquals(2029, carry.year)
        assertEquals(2027, carry.inheritedFromYear)
        assertNull(carry.monthlyIncome)
        assertNull(carry.monthlyExpenses)
        assertEquals(1, carry.items.size)
        assertEquals(BigDecimal("3500.00"), carry.items.single().amount)
        assertEquals(BankPlanCertainty.ESTIMATE, carry.items.single().certainty)
        assertEquals(2, carry.items.single().month)
        assertEquals(keysBefore, store.keys(owner).sorted(), "GET must not save anything")
        assertEquals(BankPlanCertainty.PLANNED, bank.planning(owner, 2027).items.first().certainty)
        assertNull(bank.savePlanning(owner, 2029, carry).inheritedFromYear)
        assertTrue(bank.planning(owner, 2024).items.isEmpty())
    }

    @Test fun `expliciet leeg nieuw jaar blijft leeg en herhaalt verwijderde posten niet`() {
        bank.savePlanning(owner, 2026, BankPlanning(2026, items = listOf(holiday)))
        bank.savePlanning(owner, 2027, BankPlanning(2027))
        assertTrue(bank.planning(owner, 2027).items.isEmpty())
        assertTrue(bank.planning(owner, 2028).items.isEmpty())
        assertNull(bank.planning(owner, 2028).inheritedFromYear)
    }

    @Test fun `ongeldige bedragen en posten worden atomair geweigerd`() {
        val original = bank.savePlanning(owner, 2027, BankPlanning(2027, items = listOf(holiday)))
        val invalid = listOf(
            original.copy(year = 2028),
            original.copy(monthlyIncome = BigDecimal("-1")),
            original.copy(monthlyExpenses = BigDecimal("10000000.01")),
            original.copy(openingReserve = BigDecimal("0.001")),
            original.copy(budgets = mapOf(BankCategory.REVIEW to BigDecimal.ONE)),
            original.copy(budgets = mapOf(BankCategory.EXTRA to BigDecimal("-10"))),
            original.copy(items = listOf(holiday.copy(id = "../../x"))),
            original.copy(items = listOf(holiday.copy(title = " "))),
            original.copy(items = listOf(holiday.copy(title = "x".repeat(121)))),
            original.copy(items = listOf(holiday.copy(month = 0))),
            original.copy(items = listOf(holiday.copy(month = 13))),
            original.copy(items = listOf(holiday.copy(amount = BigDecimal("1.001")))),
            original.copy(items = listOf(holiday, holiday)),
            original.copy(items = (1..101).map { holiday.copy(id = "item-$it") }),
        )
        invalid.forEach { plan ->
            assertThrows(IllegalArgumentException::class.java) { bank.savePlanning(owner, 2027, plan) }
            assertEquals(original, bank.planning(owner, 2027))
        }
        assertThrows(IllegalArgumentException::class.java) { bank.planning(owner, 1800) }
        assertThrows(IllegalArgumentException::class.java) { bank.planning(owner, 2201) }
    }

    @Test fun `andere eigenaar mag historie of planning niet lezen of schrijven`() {
        assertThrows(ResponseStatusException::class.java) { bank.history("other@example.invalid", "2026-12") }
        assertThrows(ResponseStatusException::class.java) { bank.planning("other@example.invalid", 2027) }
        assertThrows(ResponseStatusException::class.java) { bank.savePlanning("other@example.invalid", 2027, BankPlanning(2027)) }
        assertTrue(store.keys(owner).isEmpty())
        assertTrue(store.keys("other@example.invalid").isEmpty())
    }
}
