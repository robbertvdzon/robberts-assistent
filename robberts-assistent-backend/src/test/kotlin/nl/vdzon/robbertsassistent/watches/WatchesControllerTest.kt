package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context (zelfde patroon als `briefing.BriefingControllerTest`) en
 * verifieert dat aanmaken/bijwerken van een zoekopdracht werkt zónder `frequency` in de body, en
 * dat de response dat veld ook niet meer teruggeeft (SF-1698).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class WatchesControllerTest {

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

    private fun json(body: String) = HttpEntity(
        body,
        HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
    )

    @Test
    fun `aanmaken en bijwerken zonder frequency slaagt en levert geen frequency terug`() {
        val created = restTemplate.postForEntity(
            "/api/v1/watches",
            json(
                """
                {"title":"Kaarten","url":"https://example.com/tickets",
                 "instruction":"twee kaarten","notifyOnFound":true}
                """.trimIndent(),
            ),
            String::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val createdBody = created.body.orEmpty()
        assertTrue(createdBody.contains("\"title\":\"Kaarten\""))
        assertFalse(createdBody.contains("frequency"))

        val id = Regex("\"id\":\"([^\"]+)\"").find(createdBody)!!.groupValues[1]
        val updated = restTemplate.exchange(
            "/api/v1/watches/$id",
            HttpMethod.PUT,
            json(
                """
                {"title":"Kaarten 2","url":"https://example.com/tickets",
                 "instruction":"drie kaarten","notifyOnFound":false}
                """.trimIndent(),
            ),
            String::class.java,
        )

        assertEquals(HttpStatus.OK, updated.statusCode)
        val updatedBody = updated.body.orEmpty()
        assertTrue(updatedBody.contains("\"title\":\"Kaarten 2\""))
        assertFalse(updatedBody.contains("frequency"))

        restTemplate.delete("/api/v1/watches/$id")
    }
}
