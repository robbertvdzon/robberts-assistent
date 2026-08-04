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

    // --- Documenten (SF-1892) ---

    private fun json(body: String) = HttpEntity(
        body,
        HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
    )

    private fun send(method: HttpMethod, path: String, body: String? = null) =
        restTemplate.exchange(path, method, body?.let { json(it) }, String::class.java)

    private fun documentenBody() = restTemplate.getForEntity("/api/v1/notes/documents", String::class.java)

    private fun idVoorTitel(title: String): String? {
        val body = documentenBody().body.orEmpty()
        return Regex("""\{"id":"(.*?)","title":"(.*?)","order":(\d+)\}""").findAll(body)
            .firstOrNull { it.groupValues[2] == title }
            ?.groupValues?.get(1)
    }

    @Test
    fun `documenten-endpoint levert het gemigreerde todo-document en blijft er bij één`() {
        val eerst = documentenBody()
        assertEquals(HttpStatus.OK, eerst.statusCode)
        val body = eerst.body.orEmpty()
        assertTrue(body.startsWith("""{"documents":["""), "onverwachte body: $body")
        assertTrue(body.contains(""""id":"note","title":"todo""""), "onverwachte body: $body")

        val nogEens = documentenBody().body.orEmpty()
        assertEquals(1, Regex(""""title":"todo"""").findAll(nogEens).count(), "body: $nogEens")
    }

    @Test
    fun `aanmaken, hernoemen, herordenen en verwijderen van documenten werkt`() {
        val titel = "recepten-${System.nanoTime()}"
        val aangemaakt = send(HttpMethod.POST, "/api/v1/notes/documents", """{"title":"$titel"}""")
        assertEquals(HttpStatus.OK, aangemaakt.statusCode)
        val id = assertNotNull(idVoorTitel(titel), "document niet in de lijst: ${documentenBody().body}")

        // Tekst opslaan levert een versie op, en die hoort alleen bij dit document.
        assertEquals(
            HttpStatus.OK,
            send(HttpMethod.PUT, "/api/v1/notes/documents/$id", """{"text":"pannenkoeken"}""").statusCode,
        )
        val document = restTemplate.getForEntity("/api/v1/notes/documents/$id", String::class.java)
        assertTrue(document.body.orEmpty().contains("pannenkoeken"), "body: ${document.body}")
        val versies = restTemplate.getForEntity(
            "/api/v1/notes/documents/$id/versions",
            String::class.java,
        )
        val versieIds = Regex("\"id\":\"(.*?)\"").findAll(versies.body.orEmpty())
            .map { it.groupValues[1] }.toList()
        assertEquals(1, versieIds.size, "body: ${versies.body}")
        val versie = restTemplate.getForEntity(
            "/api/v1/notes/documents/$id/versions/${versieIds.single()}",
            String::class.java,
        )
        assertTrue(versie.body.orEmpty().contains("pannenkoeken"), "body: ${versie.body}")
        // Diezelfde versie hoort niet bij het standaarddocument.
        assertEquals(
            HttpStatus.NOT_FOUND,
            restTemplate.getForEntity(
                "/api/v1/notes/documents/note/versions/${versieIds.single()}",
                String::class.java,
            ).statusCode,
        )

        // Hernoemen.
        val nieuweTitel = "kookboek-${System.nanoTime()}"
        assertEquals(
            HttpStatus.OK,
            send(HttpMethod.PUT, "/api/v1/notes/documents/$id/title", """{"title":"$nieuweTitel"}""").statusCode,
        )
        assertEquals(id, idVoorTitel(nieuweTitel))

        // Herordenen: dit document vooraan, de rest erachter met dichte posities.
        val herordend = send(HttpMethod.PUT, "/api/v1/notes/documents/order", """{"ids":["$id"]}""")
        assertEquals(HttpStatus.OK, herordend.statusCode)
        val orders = Regex("\"order\":(\\d+)").findAll(herordend.body.orEmpty())
            .map { it.groupValues[1].toInt() }.toList()
        assertEquals(orders.indices.toList(), orders, "body: ${herordend.body}")
        assertTrue(herordend.body.orEmpty().startsWith("""{"documents":[{"id":"$id""""))

        // Verwijderen.
        assertEquals(HttpStatus.OK, send(HttpMethod.DELETE, "/api/v1/notes/documents/$id").statusCode)
        assertEquals(null, idVoorTitel(nieuweTitel))
    }

    @Test
    fun `foutgevallen leveren 404, 400 en 409 op`() {
        assertEquals(
            HttpStatus.NOT_FOUND,
            restTemplate.getForEntity("/api/v1/notes/documents/bestaat-niet", String::class.java).statusCode,
        )
        assertEquals(
            HttpStatus.NOT_FOUND,
            send(HttpMethod.PUT, "/api/v1/notes/documents/bestaat-niet", """{"text":"x"}""").statusCode,
        )
        assertEquals(
            HttpStatus.NOT_FOUND,
            send(HttpMethod.PUT, "/api/v1/notes/documents/order", """{"ids":["bestaat-niet"]}""").statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            send(HttpMethod.POST, "/api/v1/notes/documents", """{"title":"   "}""").statusCode,
        )

        val titel = "dubbel-${System.nanoTime()}"
        assertEquals(
            HttpStatus.OK,
            send(HttpMethod.POST, "/api/v1/notes/documents", """{"title":"$titel"}""").statusCode,
        )
        assertEquals(
            HttpStatus.CONFLICT,
            send(HttpMethod.POST, "/api/v1/notes/documents", """{"title":"${titel.uppercase()}"}""").statusCode,
        )
        send(HttpMethod.DELETE, "/api/v1/notes/documents/${idVoorTitel(titel)}")
    }

    @Test
    fun `de oude notes-endpoints werken op het todo-document`() {
        val uniek = "SF-1892 todo ${System.nanoTime()}"
        assertEquals(HttpStatus.OK, put(uniek).statusCode)

        val viaDocument = restTemplate.getForEntity("/api/v1/notes/documents/note", String::class.java)
        assertEquals(HttpStatus.OK, viaDocument.statusCode)
        assertTrue(viaDocument.body.orEmpty().contains(uniek), "body: ${viaDocument.body}")
        assertTrue(viaDocument.body.orEmpty().contains(""""title":"todo""""), "body: ${viaDocument.body}")

        val viaOud = restTemplate.getForEntity("/api/v1/notes", String::class.java)
        assertTrue(viaOud.body.orEmpty().contains(uniek), "body: ${viaOud.body}")
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
