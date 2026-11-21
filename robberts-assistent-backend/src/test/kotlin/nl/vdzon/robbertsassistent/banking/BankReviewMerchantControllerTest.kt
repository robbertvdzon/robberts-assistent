package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.auth.AuthService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class BankReviewMerchantControllerTest {
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

    @Test fun `merchant review endpoints require owner and return private bounded groups`() {
        store.write(owner, "month-2026-10", BankTransactions(listOf(BankTransaction("id", "account", "2026-10-01",
            "-12.50".toBigDecimal(), "EUR", "Testwinkel", "", ""))))
        val url = "/api/v1/banking/review-merchants"
        mvc.perform(get(url)).andExpect(status().isUnauthorized)
        mvc.perform(get(url).header("Authorization", "Bearer other")).andExpect(status().isForbidden)
        mvc.perform(get(url).header("Authorization", "Bearer owner"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.transactionCount").value(1))
            .andExpect(jsonPath("$.groups[0].merchant").value("Testwinkel"))
            .andExpect(jsonPath("$.groups[0].canLearn").value(true))
            .andExpect(jsonPath("$.groups[0].examples[0].id").value("id"))
        val key = bank.reviewMerchants(owner).groups.single().key
        val body = """{"category":"CLOTHING"}"""
        mvc.perform(put("$url/$key").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(put("$url/$key").header("Authorization", "Bearer other").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(put("$url/$key").header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.count").value(1))
        mvc.perform(get(url).header("Authorization", "Bearer owner"))
            .andExpect(jsonPath("$.transactionCount").value(0)).andExpect(jsonPath("$.groups").isEmpty)
    }
}
