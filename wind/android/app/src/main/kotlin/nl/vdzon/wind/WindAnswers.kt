package nl.vdzon.wind

/**
 * Gedeelde bron van waarheid voor de antwoorden aan de native (Android) kant.
 *
 * Deze waarden zijn een 1-op-1 spiegel van `lib/wind_data.dart`, zodat de
 * gesproken tekst (TextToSpeech) en de notificatietekst identiek zijn aan wat
 * het Flutter-scherm toont. Wijzig je hier iets, pas dan ook `wind_data.dart`
 * aan (en omgekeerd).
 */
object WindAnswers {

    /** Antwoord op "huidige windsnelheid" (hardcoded). */
    const val WIND_SPEED =
        "De huidige windsnelheid is 18 kilometer per uur uit het zuidwesten."

    /** Antwoord op "voorspelling" (hardcoded voorspellingstekst). */
    const val FORECAST =
        "De verwachting: vanmiddag toenemende wind naar 25 kilometer per uur, " +
            "vanavond afnemend."
}
