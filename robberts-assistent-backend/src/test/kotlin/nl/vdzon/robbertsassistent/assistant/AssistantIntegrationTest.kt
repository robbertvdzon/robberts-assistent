package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context (H2 in-memory database, geen echte OpenAI-call dankzij
 * `mockAi = true`) en verifieert de end-to-end flow: HTTP-request -> AssistantController ->
 * AssistantService -> ChatClient -> MockChatModel -> HTTP-response. Vervangt AppConfig's
 * AppSecrets-bean (die anders `secrets.env` nodig heeft, niet aanwezig in CI) met vaste
 * testwaarden; `allow-bean-definition-overriding` staat daarom alleen hier aan.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class AssistantIntegrationTest {

    @TestConfiguration
    class TestSecretsConfig {
        @Bean
        fun appSecrets(): AppSecrets = AppSecrets(
            rememberSecret = "test-remember-secret",
            googleClientId = "test-client-id.apps.googleusercontent.com",
            allowedEmails = setOf("robbert@vdzon.com"),
            previewSkipGoogleAuth = true,
            mockAi = true,
        )
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `POST assistant message beantwoordt via het mock-model, geen echte OpenAI-call nodig`() {
        val response = restTemplate.postForEntity<AssistantMessageResponse>(
            "/api/v1/assistant/message",
            AssistantMessageRequest(text = "wat is de wind"),
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val text = response.body?.text.orEmpty()
        assertTrue(text.contains("wat is de wind"), "verwacht dat het mock-antwoord de vraag citeert: $text")
    }
}
