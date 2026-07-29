package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WatchServiceTest {
    private val repository = InMemoryWatchRepository()
    private val service = WatchService(repository)

    @Test
    fun `aanmaken bewaart alle afzonderlijke invoervelden en beginstatus`() {
        val watch = service.create(
            title = "  Concertkaartjes ",
            url = " https://example.com/tickets ",
            instruction = " Zoek twee kaarten. ",
            frequency = WatchFrequency.KANTOORUREN,
            notifyOnFound = true,
        )

        assertEquals("Concertkaartjes", watch.title)
        assertEquals("https://example.com/tickets", watch.url)
        assertEquals("Zoek twee kaarten.", watch.instruction)
        assertEquals(WatchStatus.NOG_NIET_GECONTROLEERD, watch.status)
        assertEquals("Nog niet gecontroleerd.", watch.statusDescription)
        assertTrue(watch.active)
        assertEquals(watch, service.list().single())
    }

    @Test
    fun `lege titel en instructie worden duidelijk geweigerd`() {
        val titleError = assertFailsWith<WatchValidationException> {
            service.create("", "https://example.com", "zoek", WatchFrequency.DAGELIJKS, false)
        }
        val instructionError = assertFailsWith<WatchValidationException> {
            service.create("titel", "https://example.com", " ", WatchFrequency.DAGELIJKS, false)
        }

        assertTrue(titleError.message.orEmpty().contains("Titel"))
        assertTrue(instructionError.message.orEmpty().contains("Zoekinstructie"))
    }

    @Test
    fun `lege relatieve en niet-http urls worden geweigerd`() {
        listOf("", "/tickets", "ftp://example.com/tickets", "https:///tickets").forEach { invalid ->
            val error = assertFailsWith<WatchValidationException> {
                service.create("titel", invalid, "zoek", WatchFrequency.DAGELIJKS, false)
            }
            assertTrue(error.message.orEmpty().contains("HTTP(S)"))
        }
    }

    @Test
    fun `verwijderen haalt watch uit fallbackopslag`() {
        val watch = service.create("titel", "https://example.com", "zoek", WatchFrequency.DAGELIJKS, false)

        service.delete(watch.id)

        assertTrue(service.list().isEmpty())
    }

    @Test
    fun `zonder firebaseconfig kiest storeconfig de in-memory fallback`() {
        val firebase = FirebaseProvider(
            AppSecrets(
                rememberSecret = "x",
                googleClientId = "x",
                allowedEmails = setOf("robbert@vdzon.com"),
            ),
        )

        assertIs<InMemoryWatchRepository>(WatchStoreConfig().watchRepository(firebase))
    }
}
