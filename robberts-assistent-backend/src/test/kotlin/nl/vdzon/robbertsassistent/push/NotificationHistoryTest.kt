package nl.vdzon.robbertsassistent.push

import java.time.Instant
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.test.*

class NotificationHistoryTest {
    private val history = InMemoryNotificationHistory()
    private val now = Instant.parse("2026-11-25T11:00:00Z")

    private fun entry(id: String, at: Instant = now) = AppNotification(
        id, "Ontbijt automatisch ingevuld", "Je kunt deze invoer wissen als je echt niet hebt ontbeten.",
        mapOf("type" to "meal", "mealId" to "meal-1"), at,
    )

    @Test
    fun `bladeren verliest geen meldingen met dezelfde tijd of tussentijds nieuw bericht`() {
        for (id in listOf("a", "b", "c", "d")) history.save(entry(id))
        val first = history.page(null, 2)
        assertEquals(listOf("d", "c"), first.items.map { it.id })
        assertEquals("c", first.nextCursor)
        history.save(entry("nieuw", now.plusSeconds(1)))
        val second = history.page(first.nextCursor, 2)
        assertEquals(listOf("b", "a"), second.items.map { it.id })
        assertNull(second.nextCursor)
    }

    @Test
    fun `lezen bewaart volledige tekst doel en andere meldingen en houdt eerste leestijd`() {
        history.save(entry("a"))
        history.save(entry("b"))
        val read = history.markRead("a", now.plusSeconds(60))
        assertEquals(now.plusSeconds(60), read.readAt)
        assertEquals(read, history.markRead("a", now.plusSeconds(120)))
        val entries = history.page(null, 50).items
        assertEquals(2, entries.size)
        assertEquals(entry("a").body, entries.single { it.id == "a" }.body)
        assertEquals(entry("a").data, read.data)
        assertNull(entries.single { it.id == "b" }.readAt)
        assertFailsWith<NotificationNotFoundException> { history.markRead("onbekend", now) }
    }

    @Test
    fun `melding wordt ook bewaard zonder Firebase of geregistreerde telefoon`() {
        val firebase = FirebaseProvider(AppSecrets(rememberSecret = "x", googleClientId = "x", allowedEmails = setOf("test@example.com")))
        val push = PushService(firebase, InMemoryFcmTokenStore(), history)
        val data = mapOf("type" to "meal", "mealId" to "meal-1")
        assertEquals(0, push.sendToAll("Ontbijt", "Je kunt de invoer wissen.", data))
        val saved = history.page(null, 50).items.single()
        assertEquals("Ontbijt", saved.title)
        assertEquals("Je kunt de invoer wissen.", saved.body)
        assertEquals(data, saved.data)
        assertNull(saved.readAt)
    }

    @Test
    fun `meerdere telefoons krijgen dezelfde melding en geschiedenis bevat slechts een bericht`() {
        val firebase = mock(FirebaseProvider::class.java)
        val messaging = mock(com.google.firebase.messaging.FirebaseMessaging::class.java)
        `when`(firebase.isConfigured).thenReturn(true)
        `when`(firebase.messaging()).thenReturn(messaging)
        val tokens = InMemoryFcmTokenStore().apply { add("telefoon-1"); add("telefoon-2") }
        val push = PushService(firebase, tokens, history)
        assertEquals(2, push.sendToAll("Titel", "Hele tekst", mapOf("type" to "briefing")))
        val saved = history.page(null, 50).items.single()
        val sent = mockingDetails(messaging).invocations.map { it.arguments.single() as com.google.firebase.messaging.Message }
        assertEquals(2, sent.size)
        for (message in sent) {
            val json = com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toString(message)
            assertTrue(json.contains(saved.id))
            assertTrue(json.contains("notificationId"))
        }
    }

    @Test
    fun `lege historie en onbekende cursor zijn expliciet`() {
        assertEquals(NotificationPage(emptyList(), null), history.page(null, 50))
        assertFailsWith<NotificationNotFoundException> { history.page("onbekend", 50) }
    }
}
