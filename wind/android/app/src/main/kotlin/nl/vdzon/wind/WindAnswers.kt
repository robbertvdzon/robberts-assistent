package nl.vdzon.wind

/**
 * Terugvalteksten voor als de chat-assistent niet bereikbaar is (zie [AssistantClient],
 * [AnswerTrampolineActivity.fallbackAnswer]) — geen netwerk, niet ingelogd, timeout, ...
 * Voorheen de enige (hardcoded) bron; nu de laatste line of defense onder een echt AI-antwoord.
 */
object WindAnswers {

    /** Terugval-antwoord op "huidige windsnelheid". */
    const val WIND_SPEED = "De huidige wind is 18 knopen."

    /** Terugval-antwoord op "voorspelling". */
    const val FORECAST =
        "De verwachting: vanmiddag toenemende wind naar 25 kilometer per uur, " +
            "vanavond afnemend."
}
