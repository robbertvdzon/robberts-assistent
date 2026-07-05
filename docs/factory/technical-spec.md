# Technical Spec

## Stack & versies

- **Flutter** (stable) + **Dart** `>=3.0.0 <4.0.0` voor UI.
- **Android host** in **Kotlin 1.9.10**; JDK 17.
- **Android Gradle Plugin** 8.1.0, **Gradle** 8.3 (wrapper gecommit).
- `compileSdk`/`targetSdk` = 34, `minSdk` = 23.
- Package/applicationId: `nl.vdzon.wind`.
- Dependency: `androidx.core:core-ktx` (o.a. `NotificationCompat`).

## Architectuur

- **App Actions** worden gedeclareerd in `res/xml/shortcuts.xml` als twee
  capabilities met custom intents, gekoppeld aan native trampoline-activities.
- **Trampoline-activities** (`AnswerTrampolineActivity` + twee subklassen)
  draaien met `@android:style/Theme.Translucent.NoDisplay`, `noHistory` en
  `excludeFromRecents` → geen zichtbaar scherm. Ze:
  1. maken/gebruiken een notification channel en posten een notificatie;
  2. spreken de tekst uit via `TextToSpeech`;
  3. sluiten pas af in de `UtteranceProgressListener.onDone` (of bij
     TTS-fout/ontbreken), zodat het uitspreken niet wordt afgekapt.
- **Flutter-scherm** (`MainActivity` als `FlutterActivity` + `lib/main.dart`)
  toont dezelfde waarden voor handmatig testen.
- **Gedeelde waarheid**: antwoord-teksten staan in `WindAnswers.kt` (native) en
  `wind_data.dart` (Flutter). Deze moeten synchroon blijven.

## Codeconventies

- Native code onder `android/app/src/main/kotlin/nl/vdzon/wind/`.
- Één verantwoordelijkheid per trampoline-activity; gedeelde logica in de
  abstracte basisklasse.
- Nederlandse UI- en antwoordteksten; TTS-locale `nl_NL`.

## Bekende valkuilen

- **POST_NOTIFICATIONS** is runtime-permissie op Android 13+; zonder toestemming
  wordt de notificatie overgeslagen (geen crash). Voor een volledige demo moet
  de gebruiker de permissie verlenen (bv. door de app één keer te openen en toe
  te staan).
- **TTS-timing**: de activity mag niet afsluiten vóór `onDone`, anders wordt de
  spraak afgekapt.
- **Gradle-wrapper** wordt door `flutter build` niet geregenereerd en is daarom
  bewust gecommit; verwijder deze niet.
- App Actions-invocatie ("Hey Google, vraag Wind ...") en de notificatie op een
  Garmin-horloge zijn alleen op echte hardware te verifiëren, niet in CI.
