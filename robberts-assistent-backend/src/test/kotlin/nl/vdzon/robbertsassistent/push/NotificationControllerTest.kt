package nl.vdzon.robbertsassistent.push

import java.time.Instant
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.auth.GoogleIdTokenVerifier
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NotificationControllerTest {
    private val history = InMemoryNotificationHistory().apply {
        save(AppNotification("a", "Ontbijt", "Je kunt deze invoer wissen.", mapOf("type" to "meal"), Instant.parse("2026-11-25T11:00:00Z")))
        save(AppNotification("b", "Lunch", "Volledige tekst", mapOf("type" to "meal"), Instant.parse("2026-11-25T16:00:00Z")))
    }
    private val auth = AuthService(
        AppSecrets(rememberSecret = "test", googleClientId = "test", allowedEmails = setOf("test@example.com")),
        mock(GoogleIdTokenVerifier::class.java),
    )
    private val bearer = "Bearer ${auth.loginForAgent("test@example.com").token}"
    private val mvc = MockMvcBuilders.standaloneSetup(NotificationController(auth, history)).build()

    @Test
    fun `historie en gelezenstatus zijn afgeschermd`() {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/notifications/a/read")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer ongeldig"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `pagina geeft volledige berichten en cursor en lezen laat alle berichten staan`() {
        mvc.perform(get("/api/v1/notifications?limit=1").header("Authorization", bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value("b"))
            .andExpect(jsonPath("$.items[0].body").value("Volledige tekst"))
            .andExpect(jsonPath("$.items[0].data.type").value("meal"))
            .andExpect(jsonPath("$.nextCursor").value("b"))
        mvc.perform(post("/api/v1/notifications/b/read").header("Authorization", bearer))
            .andExpect(status().isOk).andExpect(jsonPath("$.readAt").isNotEmpty)
        mvc.perform(get("/api/v1/notifications?before=b&limit=1").header("Authorization", bearer))
            .andExpect(status().isOk).andExpect(jsonPath("$.items[0].id").value("a"))
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer))
            .andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    fun `ongeldige paginering en onbekende meldingen krijgen duidelijke foutstatus`() {
        for (query in listOf("limit=0", "limit=101", "before=..", "before=a%2Fb")) {
            mvc.perform(get("/api/v1/notifications?$query").header("Authorization", bearer))
                .andExpect(status().isBadRequest)
        }
        mvc.perform(post("/api/v1/notifications/onbekend/read").header("Authorization", bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/api/v1/notifications?before=onbekend").header("Authorization", bearer))
            .andExpect(status().isNotFound)
    }
}
