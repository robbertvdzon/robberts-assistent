package nl.vdzon.robbertsassistent.banking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class BankControllerTest {
    private val settings = BankSettings("test", "unused", "unused", "owner@example.invalid", "https://example.invalid/callback", "alias@example.invalid")
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val store = MemoryBankStore(mapper)
    private val gateway = mock(BankingGateway::class.java)
    private val bank = BankService(settings, store, gateway)
    private val auth = mock(AuthService::class.java).also {
        `when`(it.requireAuthorization("Bearer owner")).thenReturn(settings.owner)
        `when`(it.requireAuthorization("Bearer alias")).thenReturn("alias@example.invalid")
        `when`(it.requireAuthorization("Bearer other")).thenReturn("other@example.invalid")
        `when`(it.requireAuthorization(null)).thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED))
    }
    private val mvc = MockMvcBuilders.standaloneSetup(BankController(auth, bank, settings)).build()

    @Test fun `bankstatus bevat geen API-sessie en mag niet gecachet worden`() {
        store.write(settings.owner, "connection", BankConnection(listOf(BankAccount("id", "uid", "NL00TEST0000000001", "Test", "sensitive-session-id", Instant.now().plusSeconds(600)))))
        mvc.perform(get("/api/v1/banking").header("Authorization", "Bearer owner"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.accounts[0].sessionId").doesNotExist())
            .andExpect(jsonPath("$.accounts[0].uid").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-session-id"))))
        verifyNoInteractions(gateway)
    }
    @Test fun `expliciete loginalias leest dezelfde administratie als de eigenaar`() {
        store.write(settings.owner, "connection", BankConnection(listOf(BankAccount("canonical-account", "uid", "NL00TEST0000000001", "Test", "secret", Instant.now().plusSeconds(600)))))
        mvc.perform(get("/api/v1/banking").header("Authorization", "Bearer alias"))
            .andExpect(status().isOk).andExpect(jsonPath("$.accounts[0].id").value("canonical-account"))
        mvc.perform(get("/api/v1/banking/month").param("month", "2026-11").header("Authorization", "Bearer alias"))
            .andExpect(status().isOk).andExpect(jsonPath("$.month").value("2026-11"))
        mvc.perform(get("/api/v1/banking/rules").header("Authorization", "Bearer alias"))
            .andExpect(status().isOk).andExpect(jsonPath("$.rules").isArray)
    }
    @Test fun `andere appgebruiker en anonieme gebruiker krijgen geen bankgegevens`() {
        mvc.perform(get("/api/v1/banking").header("Authorization", "Bearer other")).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/banking")).andExpect(status().isUnauthorized)
        verifyNoInteractions(gateway)
    }
    @Test fun `callback reflecteert geen code of provider error en blokkeert referrers`() {
        mvc.perform(get("/api/v1/banking/callback").param("state", "x".repeat(43))
            .param("code", "secret-code").param("error", "<script>private</script>"))
            .andExpect(status().isBadRequest).andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-code"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))))
    }
    @Test fun `preview zonder Google-auth mag geen echte bankcredentials gebruiken`() {
        val secrets = mock(AppSecrets::class.java)
        `when`(secrets.previewSkipGoogleAuth).thenReturn(true)
        val firebase = mock(FirebaseProvider::class.java)
        assertThrows(IllegalStateException::class.java) { BankConfig().bankStore(settings, firebase, mapper, secrets) }
        verifyNoInteractions(firebase)
    }
}
