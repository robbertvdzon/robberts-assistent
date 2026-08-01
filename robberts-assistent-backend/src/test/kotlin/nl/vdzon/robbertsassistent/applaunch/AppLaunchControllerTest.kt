package nl.vdzon.robbertsassistent.applaunch

import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.auth.GoogleIdTokenVerifier
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
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context (zelfde patroon als `watches.WatchesControllerTest`) en
 * verifieert POST + GET van `/api/v1/app-launches`. De auth-gate zelf zit in
 * [AppLaunchControllerAuthTest], omdat de preview-context de autorisatie bewust overslaat.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class AppLaunchControllerTest {

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
    fun `posten slaat de launch op en teruglezen geeft de nieuwste eerst`() {
        val posted = restTemplate.postForEntity(
            "/api/v1/app-launches",
            json(
                """
                {"source":"ASSISTANT","platform":"android","referrer":"com.google.android.apps.gemini",
                 "action":"android.intent.action.MAIN","categories":["android.intent.category.LAUNCHER"],
                 "extras":{"query":"wat is de wind"},"appVersion":"42"}
                """.trimIndent(),
            ),
            String::class.java,
        )

        assertEquals(HttpStatus.OK, posted.statusCode)
        val postedBody = posted.body.orEmpty()
        assertTrue(postedBody.contains("\"source\":\"ASSISTANT\""))
        assertTrue(postedBody.contains("\"platform\":\"android\""))
        // Server-side bepaald: de client stuurt id/at niet mee.
        assertTrue(postedBody.contains("\"id\":\""))
        assertTrue(postedBody.contains("\"at\":"))

        restTemplate.postForEntity(
            "/api/v1/app-launches",
            json("""{"source":"LAUNCHER","platform":"android"}"""),
            String::class.java,
        )

        val listed = restTemplate.getForEntity("/api/v1/app-launches?limit=50", String::class.java)
        assertEquals(HttpStatus.OK, listed.statusCode)
        val body = listed.body.orEmpty()
        assertTrue(body.contains("\"source\":\"ASSISTANT\""))
        assertTrue(body.contains("\"source\":\"LAUNCHER\""))
        // De nieuwste-eerst-volgorde zelf zit in AppLaunchServiceTest, met een bestuurbare klok:
        // twee HTTP-posts vlak na elkaar kunnen dezelfde milliseconde krijgen.
    }

    @Test
    fun `een onbekende of ontbrekende bron wordt UNKNOWN en geen 400`() {
        val onbekend = restTemplate.postForEntity(
            "/api/v1/app-launches",
            json("""{"source":"GEMINI_NIEUW","platform":"android"}"""),
            String::class.java,
        )
        assertEquals(HttpStatus.OK, onbekend.statusCode)
        assertTrue(onbekend.body.orEmpty().contains("\"source\":\"UNKNOWN\""))

        val zonder = restTemplate.postForEntity(
            "/api/v1/app-launches",
            json("""{"platform":"web"}"""),
            String::class.java,
        )
        assertEquals(HttpStatus.OK, zonder.statusCode)
        assertTrue(zonder.body.orEmpty().contains("\"source\":\"UNKNOWN\""))
    }
}

/**
 * De auth-gate: zonder geldig Bearer-token geven beide endpoints dezelfde 401 als de andere
 * controllers (dat komt uit [AuthService.requireAuthorization], hier zonder preview-uitzondering).
 */
class AppLaunchControllerAuthTest {
    private val secrets = AppSecrets(
        rememberSecret = "test-remember-secret",
        googleClientId = "test-client-id.apps.googleusercontent.com",
        allowedEmails = setOf("robbert@vdzon.com"),
    )
    private val authService = AuthService(secrets, GoogleIdTokenVerifier { error("niet gebruikt") })
    private val service = AppLaunchService(InMemoryAppLaunchRepository())
    private val controller = AppLaunchController(authService, service)

    @Test
    fun `posten zonder token geeft 401`() {
        val error = assertFailsWith<ResponseStatusException> {
            controller.record(null, AppLaunchRequest(source = "ASSISTANT", platform = "android"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
    }

    @Test
    fun `ophalen zonder token geeft 401`() {
        val error = assertFailsWith<ResponseStatusException> { controller.list(null, 50) }
        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
    }
}
