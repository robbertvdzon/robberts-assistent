package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.auth.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class BankReviewTest {
    private val owner = "owner@example.invalid"
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val store = MemoryBankStore(mapper)
    private val settings = BankSettings("test-app", "unused", "unused", owner, "https://example.invalid/callback")
    private val bank = BankService(settings, store, mock(BankingGateway::class.java))
    private val auth = mock(AuthService::class.java).also {
        `when`(it.requireAuthorization("Bearer owner")).thenReturn(owner)
        `when`(it.requireAuthorization("Bearer other")).thenReturn("other@example.invalid")
        `when`(it.requireAuthorization(null)).thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED))
    }
    private val mvc = MockMvcBuilders.standaloneSetup(BankController(auth, bank, settings)).build()

    private fun id(n: Int) = bankHash("t$n")
    private fun pin(n: Int, date: String, merchant: String, amount: String = "-10.00") =
        BankTransaction(id(n), "account", date, amount.toBigDecimal(), "EUR", merchant, "", "BEA, Betaalpas $merchant,PAS001")
    private fun seed() {
        store.write(owner, "month-2026-10", BankTransactions(listOf(
            pin(1, "2026-10-03", "Testbouwmarkt Noord"), pin(2, "2026-10-09", "Testbouwmarkt Zuid"), pin(3, "2026-10-12", "Testsportzaak"))))
        store.write(owner, "month-2026-11", BankTransactions(listOf(
            pin(4, "2026-11-02", "Testbouwmarkt Noord"), pin(5, "2026-11-05", "Testsportzaak"))))
    }
    private fun open() = bank.reviewList(owner).rows.map { it.transaction.id }.toSet()
    private fun ref(n: Int, month: String) = BankReviewRef(month, id(n))

    @Test fun `lijst toont alle openstaande betalingen met winkelsleutel`() {
        seed()
        val list = bank.reviewList(owner)
        assertEquals(5, list.rows.size)
        assertEquals(id(5), list.rows.first().transaction.id)
        val north = list.rows.filter { it.merchant == "Testbouwmarkt Noord" }
        assertEquals(2, north.size)
        assertEquals(1, north.map { it.merchantKey }.distinct().size)
        assertTrue(north.all { it.canLearn })
    }

    @Test fun `alleen deze betaling maakt geen regel`() {
        seed()
        val result = bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.DIY, BankReviewScope.ONLY,
            listOf(ref(1, "2026-10"), ref(5, "2026-11"))))
        assertEquals(2, result.count)
        assertEquals(setOf(id(2), id(3), id(4)), open())
        assertTrue(bank.rules(owner).rules.isEmpty())
        assertEquals(BankCategory.DIY, bank.month(owner, "2026-11").transactions.single { it.id == id(5) }.category)
    }

    @Test fun `winkel verwerkt alle betalingen bij die winkel en volgende`() {
        seed()
        val result = bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.DIY, BankReviewScope.MERCHANT, listOf(ref(1, "2026-10"))))
        assertEquals(2, result.count)
        assertEquals(setOf(id(2), id(3), id(5)), open())
        store.write(owner, "month-2026-12", BankTransactions(listOf(pin(6, "2026-12-01", "Testbouwmarkt Noord"))))
        assertEquals(BankCategory.DIY, bank.month(owner, "2026-12").transactions.single().category)
    }

    @Test fun `tekstregel pakt meerdere winkels in een keer`() {
        seed()
        val result = bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.DIY, BankReviewScope.TEXT,
            listOf(ref(1, "2026-10")), "testbouwmarkt"))
        assertEquals(3, result.count)
        assertEquals(setOf(id(3), id(5)), open())
    }

    @Test fun `ongeldige keuzes worden geweigerd`() {
        seed()
        assertThrows(IllegalArgumentException::class.java) {
            bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.INCOME, BankReviewScope.ONLY, listOf(ref(1, "2026-10"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.DIY, BankReviewScope.TEXT, listOf(ref(1, "2026-10")), "ab"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.DIY, BankReviewScope.MERCHANT,
                listOf(ref(1, "2026-10"), ref(3, "2026-10"))))
        }
        assertThrows(ResponseStatusException::class.java) {
            bank.classifyReview(owner, BankReviewClassifyRequest(BankCategory.DIY, BankReviewScope.ONLY, listOf(BankReviewRef("2026-10", bankHash("weg")))))
        }
        assertEquals(5, open().size)
    }

    @Test fun `endpoints vereisen de eigenaar`() {
        seed()
        val body = """{"category":"DIY","scope":"MERCHANT","transactions":[{"month":"2026-10","id":"${id(3)}"}]}"""
        mvc.perform(get("/api/v1/banking/review")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/banking/review").header("Authorization", "Bearer other")).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/banking/review").header("Authorization", "Bearer owner"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.rows.length()").value(5))
            .andExpect(jsonPath("$.rows[0].transaction.id").value(id(5)))
        mvc.perform(post("/api/v1/banking/review/classify").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/banking/review/classify").header("Authorization", "Bearer other")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        mvc.perform(post("/api/v1/banking/review/classify").header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.count").value(2))
    }
}
