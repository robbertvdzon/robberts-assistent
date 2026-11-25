package nl.vdzon.robbertsassistent.nutrition

import java.time.Instant

/** Waar een maaltijd in zijn levensloop staat. */
enum class MealStatus {
    /** De AI is bezig (eerste analyse, of opnieuw na een extra foto/tekstcorrectie). */
    ANALYSEREN,

    /** Er ligt een voorstel dat de gebruiker nog niet heeft bevestigd. Telt wel mee in de dag. */
    VOORSTEL,

    /** Bevestigd zonder wijziging ten opzichte van het eerste AI-voorstel. */
    BEVESTIGD,

    /** Bevestigd nadat de gebruiker iets heeft aangepast. */
    VERBETERD,

    /** De analyse is mislukt; opnieuw proberen kan via `retry`. */
    MISLUKT,
}

/** Waar het getal van een onderdeel op rust, van meest naar minst betrouwbaar. */
enum class ItemBasis { GEWOGEN, ETIKET, RECEPT, VERGELEKEN, GESCHAT }

/**
 * Rol van een bevestigde maaltijd in de referentiebibliotheek: de verzameling foto's waarmee de AI
 * een nieuwe foto vergelijkt. Elke combinatie van gerecht, servies en hoeveelheid komt er één keer in.
 */
enum class ReferenceRole {
    /** Nog niet beoordeeld (niet bevestigd, of de beoordeling moet nog lopen). */
    ONBEKEND,

    /** Staat met [Meal.referencePhotoId] in de bibliotheek. */
    REFERENTIE,

    /** Zelfde gerecht, servies en hoeveelheid als [Meal.duplicateOf]; wordt niet nog eens gebruikt. */
    DUBBEL,

    /** Geen bruikbare bordfoto (alleen een etiket of recept, of helemaal geen foto). */
    GEEN,
}

/** Eén onderdeel van een maaltijd, met voedingswaarden voor de gegeten hoeveelheid. */
data class MealItem(
    val name: String,
    val grams: Int,
    val kcal: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val vegetableG: Int = 0,
    val fruitG: Int = 0,
    /** De AI twijfelt over product of hoeveelheid. */
    val uncertain: Boolean = false,
    val basis: ItemBasis = ItemBasis.GESCHAT,
) {
    /** Dit onderdeel voor [percent] procent opgegeten. */
    fun scaled(percent: Int): MealItem = if (percent >= 100) this else copy(
        grams = grams * percent / 100,
        kcal = kcal * percent / 100,
        proteinG = proteinG * percent / 100,
        carbsG = carbsG * percent / 100,
        fatG = fatG * percent / 100,
        fiberG = fiberG * percent / 100,
        vegetableG = vegetableG * percent / 100,
        fruitG = fruitG * percent / 100,
    )
}

/**
 * Een eetmoment: één of meer foto's (bord, etiket, recept, …) en/of een notitie, plus wat de AI
 * ervan maakte en wat de gebruiker daarvan vond. [aiProposal] is het éérste voorstel en blijft
 * bewaard, zodat later meetbaar is hoe vaak een voorstel zonder wijziging wordt overgenomen.
 */
data class Meal(
    val id: String,
    val eatenAt: Instant,
    val title: String = "",
    val photoIds: List<String> = emptyList(),
    val note: String = "",
    val status: MealStatus = MealStatus.ANALYSEREN,
    val items: List<MealItem> = emptyList(),
    val aiProposal: List<MealItem> = emptyList(),
    /** Korte opmerking van de AI bij het voorstel (bv. waar de twijfel zit). */
    val remark: String = "",
    /** Tekstcorrecties die de gebruiker heeft gegeven, in volgorde. */
    val corrections: List<String> = emptyList(),
    /** Wat de assistent van deze maaltijd heeft onthouden (leeg als er niets te leren viel). */
    val learned: String = "",
    val error: String = "",
    /**
     * Welk deel hij ervan opat. [items] blijft de hele portie zoals gefotografeerd en gewogen (dat
     * is wat de referentiebibliotheek en het geheugen nodig hebben); de dag telt [eatenItems].
     */
    val eatenPercent: Int = 100,
    val referenceRole: ReferenceRole = ReferenceRole.ONBEKEND,
    /** Wat er op de referentiefoto te zien is: servies, vulling en gerecht. */
    val referenceDescription: String = "",
    /** De foto waarop het eten zelf staat (niet het etiket); alleen bij REFERENTIE. */
    val referencePhotoId: String = "",
    /** Id van de maaltijd waarvan dit een dubbele is; alleen bij DUBBEL. */
    val duplicateOf: String = "",
    /** Door Robbert zelf gezet in het beheerscherm; de AI beoordeelt deze maaltijd dan niet opnieuw. */
    val referenceManual: Boolean = false,
    /** Automatisch overgenomen uit deze maaltijd; telt niet mee als nieuw bewijs van een eetgewoonte. */
    val automaticSourceMealId: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** De onderdelen zoals ze meetellen voor de dag: de portie maal het opgegeten deel. */
    val eatenItems: List<MealItem> get() = if (eatenPercent >= 100) items else items.map { it.scaled(eatenPercent) }

    val kcal: Int get() = eatenItems.sumOf { it.kcal }
}

/** Opgetelde waarden van een reeks maaltijden. */
data class NutritionTotals(
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val vegetableG: Int,
    val fruitG: Int,
) {
    companion object {
        fun of(meals: List<Meal>): NutritionTotals {
            val items = meals.filter { it.status != MealStatus.MISLUKT }.flatMap { it.eatenItems }
            return NutritionTotals(
                kcal = items.sumOf { it.kcal },
                proteinG = items.sumOf { it.proteinG },
                carbsG = items.sumOf { it.carbsG },
                fatG = items.sumOf { it.fatG },
                fiberG = items.sumOf { it.fiberG },
                vegetableG = items.sumOf { it.vegetableG },
                fruitG = items.sumOf { it.fruitG },
            )
        }
    }
}
