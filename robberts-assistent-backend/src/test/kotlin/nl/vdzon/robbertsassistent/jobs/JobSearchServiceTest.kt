package nl.vdzon.robbertsassistent.jobs

import nl.vdzon.robbertsassistent.push.PushService
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JobSearchServiceTest {
    private class FakeFinder : JobFinder {
        var found = listOf<FoundJob>()
        var learned: LearnedPreferences? = null
        var fail = false
        var lastSettings: JobSearchSettings? = null

        override fun find(settings: JobSearchSettings, known: List<JobLead>): List<FoundJob> {
            lastSettings = settings
            if (fail) error("runtime stuk")
            return found
        }

        override fun learn(settings: JobSearchSettings, lead: JobLead): LearnedPreferences? = learned
    }

    private val repository = InMemoryJobRepository()
    private val finder = FakeFinder()
    private val push = Mockito.mock(PushService::class.java)
    private val service = JobSearchService(repository, finder, push)

    private fun job(title: String, company: String, url: String = "https://x.nl/$title") = FoundJob(title, company, "Haarlem", url, "samenvatting")

    @Test
    fun `nieuwe vacatures worden bewaard en gemeld, een tweede ronde meldt ze niet opnieuw`() {
        finder.found = listOf(job("Kotlin dev", "Acme"), job("AI engineer", "Beta"))

        assertEquals(2, service.run(notify = true).size)
        Mockito.verify(push).sendToAll("2 nieuwe vacatures", "Kotlin dev bij Acme; AI engineer bij Beta", mapOf("type" to "jobs"))
        assertEquals("2 nieuwe vacatures gevonden.", service.settings().lastRunNote)

        // Zelfde vacature via een andere link, of dezelfde link met een volgparameter: niet opnieuw.
        finder.found = listOf(
            job("Kotlin dev", "Acme", "https://elders.nl/1"),
            job("Andere titel", "Beta", "https://www.x.nl/AI engineer/?utm=1"),
            job("Java dev", "Gamma"),
        )
        assertEquals(listOf("Java dev"), service.run(notify = true).map { it.title })
        assertEquals(3, service.leads().size)
        assertEquals("1 nieuwe vacature gevonden.", service.settings().lastRunNote)
    }

    @Test
    fun `een afgewezen bedrijf blijft daarna weg, ook als de AI het toch meldt`() {
        finder.found = listOf(job("AI engineer", "Tata Steel"))
        val lead = service.run(notify = false).single()

        // Zonder AI-antwoord: het bedrijf gaat op de lijst omdat de reden het bij naam noemt.
        val rated = service.rate(lead.id, LeadVerdict.NIET, "Tata vind ik niet interessant")
        assertEquals(LeadVerdict.NIET, rated.verdict)
        assertEquals(listOf("Tata Steel"), service.settings().blockedCompanies)
        assertTrue(service.settings().preferences.contains("Niet interessant: AI engineer bij Tata Steel (Tata vind ik niet interessant)"))

        finder.found = listOf(job("Java dev", "Tata Steel Nederland"), job("Data engineer", "TATA"), job("Kotlin dev", "Acme"))
        assertEquals(listOf("Acme"), service.run(notify = false).map { it.company })
        assertEquals(listOf("Tata Steel"), finder.lastSettings!!.blockedCompanies)
    }

    @Test
    fun `afwijzen om de functie blokkeert het bedrijf niet, en de AI kan de voorkeuren herschrijven`() {
        finder.found = listOf(job("Tester", "Acme"), job("Kotlin dev", "Beta"))
        val (tester, kotlin) = service.run(notify = false)

        service.rate(tester.id, LeadVerdict.NIET, "Ik wil niet testen")
        assertEquals(emptyList(), service.settings().blockedCompanies)

        finder.learned = LearnedPreferences("- Zoekt Kotlin\n- Geen testfuncties", listOf("Gamma", " gamma ", ""))
        service.rate(kotlin.id, LeadVerdict.INTERESSANT, "Leuke techniek")
        assertEquals("- Zoekt Kotlin\n- Geen testfuncties", service.settings().preferences)
        assertEquals(listOf("Gamma"), service.settings().blockedCompanies)

        assertFailsWith<IllegalArgumentException> { service.rate(kotlin.id, LeadVerdict.NIEUW, "") }
        assertFailsWith<LeadNotFoundException> { service.rate("bestaat-niet", LeadVerdict.NIET, "") }
    }

    @Test
    fun `interessant vinden haalt het bedrijf weer van de lijst`() {
        service.updateSettings("zoekvraag", "", listOf("Acme", "Beta"))
        val lead = JobLead("1", "Kotlin dev", "Acme", "Haarlem", "https://x.nl/1", "", java.time.Instant.now(), LeadVerdict.INTERESSANT)

        val learned = JobSearchService.fallbackLearning(service.settings(), lead)

        assertEquals(listOf("Beta"), learned.blockedCompanies)
    }

    @Test
    fun `een mislukte zoekronde laat een melding achter en geen push`() {
        finder.fail = true

        assertEquals(emptyList(), service.run(notify = true))
        assertEquals("Zoeken is mislukt; morgen opnieuw.", service.settings().lastRunNote)
        Mockito.verifyNoInteractions(push)
    }

    @Test
    fun `instellingen bewaren de zoekvraag en ontdubbelen de bedrijvenlijst`() {
        val saved = service.updateSettings(" Kotlin in Haarlem ", " geen consultancy ", listOf("Acme", "acme", " "))

        assertEquals("Kotlin in Haarlem", saved.description)
        assertEquals("geen consultancy", saved.preferences)
        assertEquals(listOf("Acme"), saved.blockedCompanies)
        assertFailsWith<IllegalArgumentException> { service.updateSettings(" ", "", emptyList()) }
    }
}
