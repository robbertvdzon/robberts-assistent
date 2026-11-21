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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.http.MediaType
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
    @Test fun `provider weigering verwijst naar portalvrijgave in plaats van verlopen link`() {
        val state = "p".repeat(43)
        store.write("pending", bankHash(state), PendingBankAuth(settings.owner, Instant.now().plusSeconds(600)))
        `when`(gateway.session("private-auth-code")).thenThrow(BankUnavailableException(403))
        mvc.perform(get("/api/v1/banking/callback").param("state", state).param("code", "private-auth-code"))
            .andExpect(status().isForbidden)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Link accounts")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("https://enablebanking.com/cp/applications")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-auth-code"))))
        mvc.perform(get("/api/v1/banking/callback").param("state", state).param("code", "private-auth-code"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("al gebruikt")))
    }
    @Test fun `lege banktoestemming geeft gericht hersteladvies zonder credentials`() {
        val state = "e".repeat(43)
        store.write("pending", bankHash(state), PendingBankAuth(settings.owner, Instant.now().plusSeconds(600)))
        `when`(gateway.session("empty-private-code")).thenReturn(mapper.readTree("""{"access":{"valid_until":"${Instant.now().plusSeconds(600)}"},"accounts":[]}"""))
        mvc.perform(get("/api/v1/banking/callback").param("state", state).param("code", "empty-private-code"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Nieuwe rekening toevoegen")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("empty-private-code"))))
    }
    @Test fun `preview zonder Google-auth mag geen echte bankcredentials gebruiken`() {
        val secrets = mock(AppSecrets::class.java)
        `when`(secrets.previewSkipGoogleAuth).thenReturn(true)
        val firebase = mock(FirebaseProvider::class.java)
        assertThrows(IllegalStateException::class.java) { BankConfig().bankStore(settings, firebase, mapper, secrets) }
        verifyNoInteractions(firebase)
    }
    @Test fun `historie en planning vereisen eigenaar en zijn niet cachebaar`() {
        listOf("/api/v1/banking/history?end=2026-11", "/api/v1/banking/planning?year=2027").forEach { url ->
            mvc.perform(get(url)).andExpect(status().isUnauthorized)
            mvc.perform(get(url).header("Authorization", "Bearer other")).andExpect(status().isForbidden)
            mvc.perform(get(url).header("Authorization", "Bearer alias"))
                .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
        }
        val body = """{"year":2027,"monthlyIncome":6850,"items":[{"id":"trip","title":"Vakantie","amount":3000,"month":7,"annual":true}]}"""
        mvc.perform(put("/api/v1/banking/planning?year=2027").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/banking/planning?year=2027").header("Authorization", "Bearer other")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        mvc.perform(put("/api/v1/banking/planning?year=2027").header("Authorization", "Bearer alias")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.monthlyIncome").value(6850)).andExpect(jsonPath("$.items[0].title").value("Vakantie"))
        mvc.perform(get("/api/v1/banking/planning?year=2027").header("Authorization", "Bearer owner"))
            .andExpect(jsonPath("$.items[0].title").value("Vakantie"))
        mvc.perform(put("/api/v1/banking/planning?year=2028").header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
        verifyNoInteractions(gateway)
    }
    @Test fun `planning endpoint weigert ongeldige jaartallen en ongeldige json zonder eerdere planning te wijzigen`() {
        val valid = """{"year":2027,"monthlyIncome":6850,"items":[]}"""
        mvc.perform(put("/api/v1/banking/planning?year=2027").header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk)
        listOf("1899", "2201", "geen-jaar").forEach { year ->
            mvc.perform(get("/api/v1/banking/planning?year=$year").header("Authorization", "Bearer owner"))
                .andExpect(status().isBadRequest)
        }
        val invalid = listOf(
            """{"year":2027,"monthlyIncome":-1}""",
            """{"year":2027,"monthlyIncome":1.001}""",
            """{"year":2027,"items":[{"id":"trip","title":"Vakantie","amount":100,"month":13}]}""",
            """{"year":2027,"items":[{"id":"trip","title":"Vakantie","amount":100,"month":2,"kind":"TRANSFER"}]}""",
            """{"year":2027,"items":[{"id":"trip","amount":100,"month":2}]}""",
            """{"year":2027,"budgets":{"UNKNOWN":100}}""",
            """{"year":2027,"monthlyIncome":NaN}""",
        )
        invalid.forEach { body ->
            mvc.perform(put("/api/v1/banking/planning?year=2027").header("Authorization", "Bearer owner")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
            mvc.perform(get("/api/v1/banking/planning?year=2027").header("Authorization", "Bearer owner"))
                .andExpect(jsonPath("$.monthlyIncome").value(6850))
        }
        mvc.perform(get("/api/v1/banking/history?end=2026-13").header("Authorization", "Bearer owner"))
            .andExpect(status().isBadRequest)
    }
}
