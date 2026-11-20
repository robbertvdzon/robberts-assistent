package nl.vdzon.robbertsassistent.nutrition

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.push.PushService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MealServiceTest {
    private val item = MealItem("Havermout", 60, 220, proteinG = 8.0, fiberG = 6.0)

    /** Analyzer die een vaste uitkomst geeft en bijhoudt wat hij binnenkreeg. */
    private class FakeAnalyzer(var result: MealAnalysis) : MealAnalyzer {
        val requests = mutableListOf<AnalysisRequest>()
        var learnResult: LearnResult? = null
        var fail = false

        override fun analyze(request: AnalysisRequest): MealAnalysis {
            requests += request
            if (fail) error("kapot")
            return result
        }

        override fun learn(memory: String, meal: Meal): LearnResult? = learnResult

        /** Oordeel over de referentiebibliotheek; standaard geen (dan blijft de maaltijd onbeoordeeld). */
        var verdict: (Meal, List<Meal>) -> ReferenceVerdict? = { _, _ -> null }
        val catalogued = mutableListOf<Pair<Meal, List<StoredMealPhoto>>>()

        override fun catalogue(meal: Meal, photos: List<StoredMealPhoto>, existing: List<Meal>): ReferenceVerdict? {
            catalogued += meal to photos
            return verdict(meal, existing)
        }
    }

    private val repository = InMemoryMealRepository()
    private val memory = InMemoryFoodMemoryRepository()
    private val analyzer = FakeAnalyzer(MealAnalysis("Ontbijt", "", listOf(item)))
    private val push = Mockito.mock(PushService::class.java)
    private val fetched = mutableListOf<String>()
    private val recipeFetcher = object : RecipeFetcher {
        override fun fetch(url: String): String? {
            fetched += url
            return if (url.contains("kapot")) null else "Ingrediënten voor 4 personen: 400 g pasta, 2 el olijfolie"
        }
    }
    private val service = MealService(repository, InMemoryMealPhotoStorage(), memory, analyzer, push, recipeFetcher)

    private fun awaitStatus(id: String, vararg statuses: MealStatus): Meal {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val meal = service.get(id)
            if (meal.status in statuses) return meal
            Thread.sleep(10)
        }
        error("Status ${statuses.toList()} niet bereikt, nu ${service.get(id).status}")
    }

    @Test
    fun `nieuw eetmoment wordt een voorstel en stuurt een push`() {
        val created = service.create(listOf(MealPhotoUpload(byteArrayOf(1), "image/jpeg")), "", null)
        assertEquals(MealStatus.ANALYSEREN, created.status)

        val meal = awaitStatus(created.id, MealStatus.VOORSTEL)
        assertEquals("Ontbijt", meal.title)
        assertEquals(listOf(item), meal.items)
        assertEquals(listOf(item), meal.aiProposal)
        assertEquals(1, analyzer.requests.single().photos.size)
        Mockito.verify(push).sendToAll("Ontbijt: 220 kcal", "Havermout", mapOf("type" to "meal", "mealId" to meal.id))
    }

    @Test
    fun `zonder foto en zonder notitie mag niet`() {
        assertFailsWith<IllegalArgumentException> { service.create(emptyList(), "  ", null) }
    }

    @Test
    fun `voorstel overnemen is BEVESTIGD, eigen versie is VERBETERD en bewaart het eerste voorstel`() {
        val first = awaitStatus(service.create(emptyList(), "havermout", null).id, MealStatus.VOORSTEL)
        assertEquals(MealStatus.BEVESTIGD, service.confirm(first.id).status)

        val second = awaitStatus(service.create(emptyList(), "havermout", null).id, MealStatus.VOORSTEL)
        val weighed = item.copy(grams = 75, kcal = 275, basis = ItemBasis.GEWOGEN)
        val confirmed = service.confirm(second.id, listOf(weighed))
        assertEquals(MealStatus.VERBETERD, confirmed.status)
        assertEquals(listOf(weighed), confirmed.items)
        assertEquals(listOf(item), confirmed.aiProposal)
    }

    @Test
    fun `tekstcorrectie rekent opnieuw door met de correctie en het vorige voorstel`() {
        val meal = awaitStatus(service.create(emptyList(), "ontbijt", null).id, MealStatus.VOORSTEL)
        val kwark = MealItem("Kwark", 200, 120)
        analyzer.result = MealAnalysis("Ontbijt", "", listOf(kwark))

        service.correctByText(meal.id, "het was kwark")
        val corrected = awaitStatus(meal.id, MealStatus.VOORSTEL)

        assertEquals(listOf(kwark), corrected.items)
        assertEquals(listOf(item), corrected.aiProposal)
        val request = analyzer.requests.last()
        assertEquals(listOf("het was kwark"), request.corrections)
        assertEquals(listOf(item), request.previous)
        assertEquals(MealStatus.VERBETERD, service.confirm(meal.id).status)
    }

    @Test
    fun `een foto die het model niet kon openen wordt MISLUKT in plaats van een verzonnen voorstel`() {
        analyzer.result = MealAnalysis("Tussendoor", "FOTO NIET GELEZEN: bestand niet gevonden", emptyList())
        val meal = awaitStatus(service.create(listOf(MealPhotoUpload(byteArrayOf(1), "image/jpeg")), "", null).id, MealStatus.MISLUKT)
        assertEquals("De foto kon niet worden bekeken. Probeer het opnieuw.", meal.error)
        assertTrue(meal.items.isEmpty())

        analyzer.result = MealAnalysis("Tussendoor", "", listOf(item))
        service.retry(meal.id)
        assertEquals(listOf(item), awaitStatus(meal.id, MealStatus.VOORSTEL).items)
    }

    @Test
    fun `mislukte analyse wordt MISLUKT en kan opnieuw`() {
        analyzer.fail = true
        val meal = awaitStatus(service.create(emptyList(), "iets", null).id, MealStatus.MISLUKT)
        assertTrue(meal.error.isNotEmpty())

        analyzer.fail = false
        service.retry(meal.id)
        assertEquals(MealStatus.VOORSTEL, awaitStatus(meal.id, MealStatus.VOORSTEL).status)
    }

    @Test
    fun `bevestigen werkt het geheugen bij en zet erbij wat geleerd is`() {
        analyzer.learnResult = LearnResult("Ontbijt-havermout: 60 g", "Je ontbijt is 60 g havermout.")
        val meal = awaitStatus(service.create(emptyList(), "ontbijt", null).id, MealStatus.VOORSTEL)
        service.confirm(meal.id)

        val deadline = System.currentTimeMillis() + 5000
        while (service.get(meal.id).learned.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals("Je ontbijt is 60 g havermout.", service.get(meal.id).learned)
        assertEquals("Ontbijt-havermout: 60 g", memory.current())
    }

    @Test
    fun `dagoverzicht telt alleen de maaltijden van die dag in Nederlandse tijd`() {
        // 23:30 Nederlandse wintertijd op 16 november is 22:30 UTC; 00:30 op de 17e is 23:30 UTC de 16e.
        val late = service.create(emptyList(), "laat", Instant.parse("2026-11-16T22:30:00Z"))
        val afterMidnight = service.create(emptyList(), "na middernacht", Instant.parse("2026-11-16T23:30:00Z"))
        awaitStatus(late.id, MealStatus.VOORSTEL)
        awaitStatus(afterMidnight.id, MealStatus.VOORSTEL)

        val day16 = service.day(LocalDate.parse("2026-11-16"))
        val day17 = service.day(LocalDate.parse("2026-11-17"))
        assertEquals(listOf(late.id), day16.meals.map { it.id })
        assertEquals(listOf(afterMidnight.id), day17.meals.map { it.id })
        assertEquals(220, day16.totals.kcal)
        assertEquals(6.0, day16.totals.fiberG)
    }

    @Test
    fun `parser haalt JSON uit een antwoord met tekst en codeblok eromheen`() {
        val answer = """
            Hier is het:
            ```json
            {"titel":"Lunch","opmerking":"twijfel over de kaas",
             "items":[{"naam":"Boterham kaas","gram":70,"kcal":165,"eiwit":9.5,"koolhydraten":15,
                       "vet":7,"vezels":2.4,"groente_gram":0,"fruit_gram":0,"twijfel":true,"basis":"etiket"}]}
            ```
        """.trimIndent()
        val analysis = MealAnalysisParser.parse(answer, ObjectMapper())
        assertEquals("Lunch", analysis.title)
        val parsed = analysis.items.single()
        assertEquals("Boterham kaas", parsed.name)
        assertEquals(165, parsed.kcal)
        assertEquals(2.4, parsed.fiberG)
        assertTrue(parsed.uncertain)
        assertEquals(ItemBasis.ETIKET, parsed.basis)
        assertFailsWith<IllegalArgumentException> { MealAnalysisParser.parse("geen json", ObjectMapper()) }
    }

    @Suppress("unused")
    private val unusedFirebase: FirebaseProvider? = null

    @Test
    fun `gewogen maaltijden met foto gaan als referentie mee, dichtst bij het tijdstip eerst`() {
        fun weighedMeal(at: String, grams: Int): Meal {
            val created = service.create(listOf(MealPhotoUpload(byteArrayOf(grams.toByte()), "image/jpeg")), "", Instant.parse(at))
            awaitStatus(created.id, MealStatus.VOORSTEL)
            return service.confirm(created.id, listOf(item.copy(grams = grams, basis = ItemBasis.GEWOGEN)))
        }
        weighedMeal("2026-11-10T06:30:00Z", 58) // ontbijt
        weighedMeal("2026-11-11T17:30:00Z", 200) // avondeten
        weighedMeal("2026-11-12T06:40:00Z", 62) // ontbijt
        // Zonder foto: telt wel mee voor de gewogen porties, niet als referentiefoto.
        val textOnly = awaitStatus(service.create(emptyList(), "ontbijt", Instant.parse("2026-11-13T06:35:00Z")).id, MealStatus.VOORSTEL)
        service.confirm(textOnly.id, listOf(item.copy(grams = 60, basis = ItemBasis.GEWOGEN)))

        val breakfast = service.create(listOf(MealPhotoUpload(byteArrayOf(1), "image/jpeg")), "", Instant.parse("2026-11-17T06:35:00Z"))
        awaitStatus(breakfast.id, MealStatus.VOORSTEL)

        val request = analyzer.requests.last()
        assertEquals(mapOf("Havermout" to listOf(58, 200, 62, 60)), request.weighedPortions)
        assertEquals(3, request.references.size)
        // De twee ontbijten staan voor het avondeten; bij gelijke afstand in tijd de nieuwste eerst.
        assertTrue(request.references[0].description.contains("2026-11-12") && request.references[0].description.contains("62 g (gewogen)"))
        assertTrue(request.references[1].description.contains("2026-11-10"))
        assertTrue(request.references[2].description.contains("2026-11-11"))
        assertEquals(62, request.references[0].photo.bytes.single().toInt())
    }

    private fun awaitRole(id: String, role: ReferenceRole): Meal {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val meal = service.get(id)
            if (meal.referenceRole == role) return meal
            Thread.sleep(10)
        }
        error("Rol $role niet bereikt, nu ${service.get(id).referenceRole}")
    }

    private fun confirmedMeal(at: String, grams: Int, vararg photos: Int): Meal {
        val uploads = photos.map { MealPhotoUpload(byteArrayOf(it.toByte()), "image/jpeg") }
        val created = service.create(uploads, if (uploads.isEmpty()) "ontbijt" else "", Instant.parse(at))
        awaitStatus(created.id, MealStatus.VOORSTEL)
        return service.confirm(created.id, listOf(item.copy(grams = grams)))
    }

    /** Zelfde aantal gram als een bestaande referentie is een dubbele; de tweede foto is de bordfoto. */
    private val byGrams: (Meal, List<Meal>) -> ReferenceVerdict? = { meal, existing ->
        val same = existing.firstOrNull { it.items == meal.items }
        if (same != null) ReferenceVerdict(ReferenceRole.DUBBEL, "kom", 0, same.id)
        else ReferenceVerdict(ReferenceRole.REFERENTIE, "kom met ${meal.items.single().grams} g", meal.photoIds.size - 1)
    }

    @Test
    fun `bevestigde maaltijden vullen de bibliotheek, een dubbele gaat niet nog eens mee`() {
        analyzer.verdict = byGrams
        val small = awaitRole(confirmedMeal("2026-11-10T06:30:00Z", 30, 11, 12).id, ReferenceRole.REFERENTIE)
        val again = awaitRole(confirmedMeal("2026-11-11T06:30:00Z", 30, 21).id, ReferenceRole.DUBBEL)
        val large = awaitRole(confirmedMeal("2026-11-12T18:30:00Z", 85, 31).id, ReferenceRole.REFERENTIE)
        val noPhoto = awaitRole(confirmedMeal("2026-11-13T06:30:00Z", 50).id, ReferenceRole.GEEN)

        assertEquals("kom met 30 g", small.referenceDescription)
        assertEquals(small.photoIds[1], small.referencePhotoId)
        assertEquals(small.id, again.duplicateOf)
        assertEquals("", again.referencePhotoId)
        // Zonder foto valt er niets te beoordelen: de AI wordt er niet voor aangeroepen.
        assertTrue(analyzer.catalogued.none { it.first.id == noPhoto.id })

        val next = service.create(listOf(MealPhotoUpload(byteArrayOf(1), "image/jpeg")), "", Instant.parse("2026-11-17T06:35:00Z"))
        awaitStatus(next.id, MealStatus.VOORSTEL)
        val references = analyzer.requests.last().references
        // Alle unieke referenties, nieuwste eerst, ongeacht het tijdstip van de dag en zonder weging.
        assertEquals(listOf(31, 12), references.map { it.photo.bytes.single().toInt() })
        assertTrue(references[0].description.contains("kom met 85 g") && references[0].description.contains("85 g (bevestigd)"))
        assertTrue(references[1].description.contains("2026-11-10"))
        assertEquals(large.id, service.get(large.id).id)
    }

    @Test
    fun `onderdelen wijzigen beoordeelt opnieuw, ongewijzigd bevestigen niet`() {
        analyzer.verdict = byGrams
        val meal = awaitRole(confirmedMeal("2026-11-10T06:30:00Z", 30, 11).id, ReferenceRole.REFERENTIE)
        service.confirm(meal.id)
        assertEquals(ReferenceRole.REFERENTIE, service.get(meal.id).referenceRole)
        assertEquals(1, analyzer.catalogued.size)

        service.confirm(meal.id, listOf(item.copy(grams = 45)))
        val changed = awaitRole(meal.id, ReferenceRole.REFERENTIE)
        assertEquals(2, analyzer.catalogued.size)
        assertEquals("kom met 45 g", changed.referenceDescription)
    }

    @Test
    fun `na verwijderen van een referentie neemt de dubbele zijn plek over`() {
        analyzer.verdict = byGrams
        val first = awaitRole(confirmedMeal("2026-11-10T06:30:00Z", 30, 11).id, ReferenceRole.REFERENTIE)
        val second = awaitRole(confirmedMeal("2026-11-11T06:30:00Z", 30, 21).id, ReferenceRole.DUBBEL)

        service.delete(first.id)

        val promoted = awaitRole(second.id, ReferenceRole.REFERENTIE)
        assertEquals("", promoted.duplicateOf)
        assertEquals(second.photoIds.single(), promoted.referencePhotoId)
    }

    @Test
    fun `eigen keuze in het beheer blijft staan en laat een dubbele de plek overnemen`() {
        analyzer.verdict = byGrams
        val first = awaitRole(confirmedMeal("2026-11-10T06:30:00Z", 30, 11).id, ReferenceRole.REFERENTIE)
        val second = awaitRole(confirmedMeal("2026-11-11T06:30:00Z", 30, 21).id, ReferenceRole.DUBBEL)
        assertEquals(listOf(second.id, first.id), service.referenceMeals().map { it.id })

        val off = service.setReference(first.id, use = false, description = null)
        assertEquals(ReferenceRole.GEEN, off.referenceRole)
        assertTrue(off.referenceManual)
        assertEquals("", off.referencePhotoId)
        awaitRole(second.id, ReferenceRole.REFERENTIE)

        // Andere hoeveelheden bevestigen laat de AI de eigen keuze niet overschrijven.
        service.confirm(first.id, listOf(item.copy(grams = 45)))
        Thread.sleep(100)
        assertEquals(ReferenceRole.GEEN, service.get(first.id).referenceRole)

        val on = service.setReference(first.id, use = true, description = " Klein blauw kopje ")
        assertEquals(ReferenceRole.REFERENTIE, on.referenceRole)
        assertEquals("Klein blauw kopje", on.referenceDescription)
        assertEquals(first.photoIds.single(), on.referencePhotoId)
    }

    @Test
    fun `een voorstel of een maaltijd zonder foto kan geen modelgerecht worden`() {
        val proposal = awaitStatus(service.create(listOf(MealPhotoUpload(byteArrayOf(5), "image/jpeg")), "", null).id, MealStatus.VOORSTEL)
        assertFailsWith<IllegalStateException> { service.setReference(proposal.id, use = true, description = null) }

        val withoutPhoto = awaitStatus(service.create(emptyList(), "havermout", null).id, MealStatus.VOORSTEL)
        service.confirm(withoutPhoto.id)
        assertFailsWith<IllegalArgumentException> { service.setReference(withoutPhoto.id, use = true, description = null) }
        assertEquals(emptyList(), service.referenceMeals())
    }

    @Test
    fun `bij de start krijgen onbeoordeelde maaltijden alsnog een plek, oudste eerst`() {
        val first = confirmedMeal("2026-11-10T06:30:00Z", 30, 11)
        val second = confirmedMeal("2026-11-11T06:30:00Z", 30, 21)
        val proposal = awaitStatus(service.create(listOf(MealPhotoUpload(byteArrayOf(5), "image/jpeg")), "", null).id, MealStatus.VOORSTEL)
        Thread.sleep(100)
        assertEquals(ReferenceRole.ONBEKEND, service.get(first.id).referenceRole)

        analyzer.verdict = byGrams
        service.catalogueBacklog()

        awaitRole(second.id, ReferenceRole.DUBBEL)
        assertEquals(ReferenceRole.REFERENTIE, service.get(first.id).referenceRole)
        // Een voorstel is nog niet bevestigd en hoort niet in de bibliotheek.
        assertEquals(ReferenceRole.ONBEKEND, service.get(proposal.id).referenceRole)
    }

    @Test
    fun `oordeel over een referentie wordt gelezen en een onbekend origineel wordt een nieuwe referentie`() {
        val mapper = ObjectMapper()
        val existing = listOf(Meal(id = "abcdef12-3456", eatenAt = Instant.now(), createdAt = Instant.now(), updatedAt = Instant.now()))
        fun parse(json: String) = ReferenceVerdictParser.parse(json, mapper, 3, existing)

        assertEquals(
            ReferenceVerdict(ReferenceRole.REFERENTIE, "blauw kopje", 1),
            parse("""Hier: {"rol": "referentie", "foto": 2, "dubbel_van": "", "beschrijving": "blauw kopje"}"""),
        )
        assertEquals(
            ReferenceVerdict(ReferenceRole.DUBBEL, "blauw kopje", 0, "abcdef12-3456"),
            parse("""{"rol": "DUBBEL", "foto": 1, "dubbel_van": "abcdef12", "beschrijving": "blauw kopje"}"""),
        )
        assertEquals(
            ReferenceVerdict(ReferenceRole.REFERENTIE, "blauw kopje", 2),
            parse("""{"rol": "DUBBEL", "foto": 9, "dubbel_van": "zzzz9999", "beschrijving": "blauw kopje"}"""),
        )
        assertEquals(ReferenceVerdict(ReferenceRole.GEEN), parse("""{"rol": "GEEN", "foto": 1, "beschrijving": ""}"""))
        assertEquals(null, parse("""{"rol": "weet ik niet"}"""))
    }

    @Test
    fun `deels opgegeten telt minder voor de dag maar de portie en de referentie blijven heel`() {
        analyzer.verdict = byGrams
        val meal = awaitRole(confirmedMeal("2026-11-10T06:30:00Z", 60, 11).id, ReferenceRole.REFERENTIE)

        val eaten = service.setEaten(meal.id, 75)

        assertEquals(60, eaten.items.single().grams)
        assertEquals(45, eaten.eatenItems.single().grams)
        assertEquals(165, eaten.kcal)
        assertEquals(ReferenceRole.REFERENTIE, eaten.referenceRole)
        val day = service.day(LocalDate.parse("2026-11-10"))
        assertEquals(165, day.totals.kcal)
        assertEquals(6.0, day.totals.proteinG)
        assertFailsWith<IllegalArgumentException> { service.setEaten(meal.id, 0) }

        // De referentie voor een volgende foto noemt de hele portie.
        val next = service.create(listOf(MealPhotoUpload(byteArrayOf(1), "image/jpeg")), "", Instant.parse("2026-11-12T06:35:00Z"))
        awaitStatus(next.id, MealStatus.VOORSTEL)
        assertTrue(analyzer.requests.last().references.single().description.contains("60 g"))
    }

    @Test
    fun `de AI kan uit de notitie halen dat niet alles op is`() {
        analyzer.result = MealAnalysis("Ontbijt", "", listOf(item), eatenPercent = 75)
        val meal = awaitStatus(service.create(emptyList(), "havermout, kwart laten staan", null).id, MealStatus.VOORSTEL)
        assertEquals(75, meal.eatenPercent)
        assertEquals(listOf(item), meal.items)

        val parsed = MealAnalysisParser.parse("""{"titel": "Lunch", "opgegeten_procent": 150, "items": []}""", ObjectMapper())
        assertEquals(100, parsed.eatenPercent)
        assertEquals(null, MealAnalysisParser.parse("""{"titel": "Lunch", "items": []}""", ObjectMapper()).eatenPercent)
    }

    @Test
    fun `link in de notitie levert de tekst van het recept, een kapotte link houdt niets tegen`() {
        val meal = service.create(emptyList(), "pasta van https://voorbeeld.nl/recept/pasta. En https://kapot.nl/x", null)
        awaitStatus(meal.id, MealStatus.VOORSTEL)

        assertEquals(listOf("https://voorbeeld.nl/recept/pasta", "https://kapot.nl/x"), fetched)
        val recipes = analyzer.requests.last().recipes
        assertEquals(setOf("https://voorbeeld.nl/recept/pasta"), recipes.keys)
        assertTrue(recipes.values.single().contains("400 g pasta"))
    }

    @Test
    fun `receptpagina wordt leesbare tekst zonder scripts en opmaak`() {
        val html = "<html><head><title>x</title><style>p{}</style></head><body><script>alert(1)</script>" +
            "<h1>Pasta</h1><ul><li>400&nbsp;g pasta</li><li>2 el olie &amp; zout</li></ul></body></html>"
        assertEquals("Pasta\n400 g pasta\n2 el olie & zout", RecipeLinks.toText(html))
        assertEquals(emptyList(), RecipeLinks.find("geen link hier"))
    }
}
