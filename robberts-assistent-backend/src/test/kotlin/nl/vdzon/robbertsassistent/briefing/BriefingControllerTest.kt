package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context (zelfde patroon als `assistant.AssistantIntegrationTest`) en
 * verifieert dat `GET /api/v1/briefing/weather-map/{slot}` een `Cache-Control: no-cache`-header
 * meestuurt, zodat een browser/HTTP-cache (bv. de web-variant van de app) geen oude weerkaart
 * hergebruikt na een refresh (SF-1228).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class BriefingControllerTest {

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

    @Autowired
    private lateinit var weatherMapStorage: WeatherMapStorage

    @Test
    fun `GET weather-map slot geeft Cache-Control no-cache terug`() {
        weatherMapStorage.store("morgen", byteArrayOf(1, 2, 3))

        val response = restTemplate.getForEntity("/api/v1/briefing/weather-map/morgen", ByteArray::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val cacheControl = response.headers.cacheControl
        assertTrue(cacheControl != null && cacheControl!!.contains("no-cache"))
    }
}
