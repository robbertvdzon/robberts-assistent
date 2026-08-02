package nl.vdzon.robbertsassistent.notes

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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context (zelfde patroon als `watches.WatchesControllerTest`) en
 * verifieert de versie-endpoints van de notitie: volgorde, inhoud en de 404 bij een onbekend id
 * (SF-1808). De 401-zonder-header is een losse unittest, zie [NotesControllerAuthTest].
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class NotesControllerTest {

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

    private fun put(text: String) = restTemplate.exchange(
        "/api/v1/notes",
        HttpMethod.PUT,
        HttpEntity(
            """{"text":${quote(text)}}""",
            HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
        ),
        String::class.java,
    )

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun `versie-endpoints geven de opgeslagen versies nieuwste eerst met tekst op id`() {
        val uniek = "SF-1808 test ${System.nanoTime()}"
        assertEquals(HttpStatus.OK, put("$uniek eerste").statusCode)
        assertEquals(HttpStatus.OK, put("$uniek tweede").statusCode)

        val lijst = restTemplate.getForEntity("/api/v1/notes/versions", String::class.java)
        assertEquals(HttpStatus.OK, lijst.statusCode)
        val body = lijst.body.orEmpty()
        assertTrue(body.startsWith("""{"versions":["""), "onverwachte body: $body")
        // Geen tekst in het overzicht.
        assertTrue(!body.contains(uniek), "overzicht mag geen tekst bevatten: $body")

        val ids = Regex("\"id\":\"(.*?)\"").findAll(body).map { it.groupValues[1] }.toList()
        val savedAts = Regex("\"savedAt\":\"(.*?)\"").findAll(body).map { it.groupValues[1] }.toList()
        assertTrue(ids.size >= 2, "verwacht minstens twee versies, kreeg $body")
        assertEquals(ids.size, savedAts.size)
        // Nieuwste eerst: de savedAt-waarden (ISO-8601 UTC) lopen aflopend.
        assertEquals(savedAts.sortedDescending(), savedAts)

        val nieuwste = restTemplate.getForEntity("/api/v1/notes/versions/${ids.first()}", String::class.java)
        assertEquals(HttpStatus.OK, nieuwste.statusCode)
        assertNotNull(nieuwste.body)
        assertTrue(nieuwste.body!!.contains("$uniek tweede"), "onverwachte body: ${nieuwste.body}")
        assertTrue(nieuwste.body!!.contains("\"savedAt\":\""))
    }

    @Test
    fun `een onbekend versie-id geeft 404`() {
        val response = restTemplate.getForEntity("/api/v1/notes/versions/bestaat-niet", String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}

/**
 * De auth-gate: zonder geldig Bearer-token geven de versie-endpoints dezelfde 401 als de
 * bestaande notes-endpoints (uit [AuthService.requireAuthorization], hier zonder
 * preview-uitzondering). Zelfde patroon als `applaunch.AppLaunchControllerAuthTest`.
 */
class NotesControllerAuthTest {
    private val secrets = AppSecrets(
        rememberSecret = "test-remember-secret",
        googleClientId = "test-client-id.apps.googleusercontent.com",
        allowedEmails = setOf("robbert@vdzon.com"),
    )
    private val authService = AuthService(secrets, GoogleIdTokenVerifier { error("niet gebruikt") })
    private val controller = NotesController(authService, NotesService(InMemoryNotesRepository()))

    @Test
    fun `versie-overzicht zonder token geeft 401`() {
        val error = assertFailsWith<ResponseStatusException> { controller.versions(null) }
        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
    }

    @Test
    fun `versie op id zonder token geeft 401`() {
        val error = assertFailsWith<ResponseStatusException> { controller.version(null, "wat-dan-ook") }
        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
    }

    @Test
    fun `de bestaande notes-endpoints geven diezelfde 401`() {
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            assertFailsWith<ResponseStatusException> { controller.get(null) }.statusCode,
        )
    }
}
