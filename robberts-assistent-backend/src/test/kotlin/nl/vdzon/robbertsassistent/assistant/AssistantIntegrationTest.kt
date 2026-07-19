package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context (H2 in-memory database, geen echte OpenAI-call dankzij
 * `mockAi = true`, geen Firebase-config dus in-memory gespreksopslag) en verifieert de end-to-end
 * flow: multipart HTTP-request -> AssistantController -> AssistantService -> ChatClient ->
 * MockChatModel -> HTTP-response, plus het ophalen van de gespreklijst en -detail. Vervangt
 * AppConfig's AppSecrets-bean (die anders `secrets.env` nodig heeft, niet aanwezig in CI) met
 * vaste testwaarden; `allow-bean-definition-overriding` staat daarom alleen hier aan.
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
    fun `POST assistant chat beantwoordt via het mock-model en het gesprek is daarna op te halen`() {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("message", "wat is de wind")
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val request = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(
            "/api/v1/assistant/chat",
            request,
            AssistantChatResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val chatBody = response.body!!
        assertTrue(chatBody.reply.contains("wat is de wind"), "verwacht dat het mock-antwoord de vraag citeert: ${chatBody.reply}")
        assertTrue(chatBody.title.isNotBlank())
        assertEquals(2, chatBody.messages.size)

        val conversationResponse = restTemplate.getForEntity(
            "/api/v1/assistant/conversations/${chatBody.conversationId}",
            AssistantConversationResponse::class.java,
        )
        assertEquals(HttpStatus.OK, conversationResponse.statusCode)
        assertEquals(2, conversationResponse.body?.messages?.size)
        assertEquals(chatBody.title, conversationResponse.body?.title)

        val listResponse = restTemplate.getForEntity(
            "/api/v1/assistant/conversations",
            Array<ConversationSummaryDto>::class.java,
        )
        assertEquals(HttpStatus.OK, listResponse.statusCode)
        assertTrue(listResponse.body!!.any { it.conversationId == chatBody.conversationId })
    }
}
