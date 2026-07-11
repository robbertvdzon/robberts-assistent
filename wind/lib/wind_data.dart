/// Gedeelde bron van waarheid voor de antwoorden die de Wind-app geeft.
///
/// Deze waarden worden 1-op-1 gespiegeld in de native Kotlin-bron
/// (`android/app/src/main/kotlin/nl/vdzon/wind/WindAnswers.kt`), zodat de
/// tekst op het Flutter-scherm identiek is aan wat de trampoline-activities
/// uitspreken en in een notificatie posten. Wijzig je hier iets, pas dan ook
/// `WindAnswers.kt` aan (en omgekeerd).
class WindData {
  const WindData._();

  /// Antwoord op "huidige windsnelheid". Hardcoded zodat het scherm, de
  /// gesproken tekst en de notificatietekst exact overeenkomen.
  static const String windSpeedAnswer = 'De huidige wind is 18 knopen.';

  /// Antwoord op "voorspelling". Hardcoded voorspellingstekst.
  static const String forecastAnswer =
      'De verwachting: vanmiddag toenemende wind naar 25 kilometer per uur, '
      'vanavond afnemend.';
}
