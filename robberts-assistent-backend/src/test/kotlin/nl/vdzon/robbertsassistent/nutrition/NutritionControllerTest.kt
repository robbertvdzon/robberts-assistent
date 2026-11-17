package nl.vdzon.robbertsassistent.nutrition

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boot de volledige Spring-context met de mock-AI (zelfde patroon als `watches.WatchesControllerTest`)
 * en loopt de hele route door: foto insturen, voorstel ophalen, verbeteren, dagoverzicht.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class NutritionControllerTest {

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

    private fun id(body: String) = Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    private fun awaitProposal(id: String): String {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val body = restTemplate.getForObject("/api/v1/meals/$id", String::class.java).orEmpty()
            if (body.contains("\"status\":\"VOORSTEL\"")) return body
            Thread.sleep(20)
        }
        error("Geen voorstel binnen 5 s")
    }

    @Test
    fun `foto insturen, voorstel ophalen, verbeteren en terugzien in het dagoverzicht`() {
        val photo = object : ByteArrayResource(byteArrayOf(1, 2, 3)) {
            override fun getFilename() = "bord.jpg"
        }
        val form = LinkedMultiValueMap<String, Any>().apply {
            add("photos", HttpEntity(photo, HttpHeaders().apply { contentType = MediaType.IMAGE_JPEG }))
            add("note", "avondeten")
            add("eatenAt", "2026-11-17T17:42:00Z")
        }
        val created = restTemplate.postForEntity(
            "/api/v1/meals",
            HttpEntity(form, HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }),
            String::class.java,
        )
        assertEquals(HttpStatus.OK, created.statusCode)
        assertTrue(created.body.orEmpty().contains("\"status\":\"ANALYSEREN\""))
        val mealId = id(created.body.orEmpty())

        val proposal = awaitProposal(mealId)
        assertTrue(proposal.contains("\"title\":\"Avondeten\""))
        assertTrue(proposal.contains("\"kcal\":605"))

        val photoId = Regex("\"photoIds\":\\[\"([^\"]+)\"").find(proposal)!!.groupValues[1]
        val storedPhoto = restTemplate.getForEntity("/api/v1/meals/photos/$photoId", ByteArray::class.java)
        assertEquals(HttpStatus.OK, storedPhoto.statusCode)
        assertEquals(3, storedPhoto.body!!.size)

        val confirmed = restTemplate.exchange(
            "/api/v1/meals/$mealId",
            HttpMethod.PUT,
            HttpEntity(
                """{"items":[{"name":"Vegetarische rookworst","grams":250,"kcal":520,"proteinG":40.0,"basis":"GEWOGEN"}]}""",
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
            String::class.java,
        )
        assertEquals(HttpStatus.OK, confirmed.statusCode)
        assertTrue(confirmed.body.orEmpty().contains("\"status\":\"VERBETERD\""))
        assertTrue(confirmed.body.orEmpty().contains("\"basis\":\"GEWOGEN\""))

        val day = restTemplate.getForObject("/api/v1/meals?date=2026-11-17", String::class.java).orEmpty()
        assertTrue(day.contains("\"date\":\"2026-11-17\""))
        assertTrue(day.contains("\"totals\":{\"kcal\":520"))
        assertTrue(day.contains(mealId))

        restTemplate.delete("/api/v1/meals/$mealId")
        assertEquals(HttpStatus.NOT_FOUND, restTemplate.getForEntity("/api/v1/meals/$mealId", String::class.java).statusCode)
    }

    @Test
    fun `leeg eetmoment is een 400 en het geheugen is te lezen en te wijzigen`() {
        val empty = restTemplate.postForEntity(
            "/api/v1/meals",
            HttpEntity(
                LinkedMultiValueMap<String, Any>().apply { add("note", "") },
                HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA },
            ),
            String::class.java,
        )
        assertEquals(HttpStatus.BAD_REQUEST, empty.statusCode)

        restTemplate.put(
            "/api/v1/nutrition/memory",
            HttpEntity("""{"text":"Koffie is met halfvolle melk."}""", HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        )
        val memory = restTemplate.getForObject("/api/v1/nutrition/memory", String::class.java).orEmpty()
        assertTrue(memory.contains("Koffie is met halfvolle melk."))
    }
}
