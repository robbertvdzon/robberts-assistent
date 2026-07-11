package nl.vdzon.wind

/**
 * Gedeelde bron van waarheid voor de antwoorden aan de native (Android) kant.
 * Gebruikt door zowel de trampoline-activities als (voorheen) het
 * Flutter-scherm in `lib/wind_data.dart` — dat scherm wordt momenteel niet
 * meer getoond (`MainActivity` is nu zelf een trampoline-activity), maar de
 * tekst-bron blijft gedeeld voor als de UI later terugkomt.
 */
object WindAnswers {

    /** Antwoord op "huidige windsnelheid" (hardcoded). */
    const val WIND_SPEED = "De huidige wind is 18 knopen."

    /** Antwoord op "voorspelling" (hardcoded voorspellingstekst). */
    const val FORECAST =
        "De verwachting: vanmiddag toenemende wind naar 25 kilometer per uur, " +
            "vanavond afnemend."
}
