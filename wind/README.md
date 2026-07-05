# Wind (PoC)

Proof-of-concept Android-app die aantoont dat de keten
**"Hey Google" → Android App Actions → eigen app** werkt met een hands-free
gevoel: het antwoord wordt uitgesproken (TextToSpeech) én als notificatie
gepost, zónder zichtbaar scherm. Er is (nog) géén backend of echte weerdata.

## Wat het doet

Twee App Actions-capabilities (`android/app/src/main/res/xml/shortcuts.xml`):

| Capability | Trampoline-activity | Antwoord |
|------------|---------------------|----------|
| `custom.actions.intent.GET_WIND_SPEED` | `WindSpeedActivity` | huidige windsnelheid (hardcoded) |
| `custom.actions.intent.GET_WIND_FORECAST` | `ForecastActivity` | voorspelling (hardcoded) |

Elke trampoline-activity (`Theme.Translucent.NoDisplay`, geen zichtbaar scherm):

1. post een notificatie met het antwoord (notification channel; op Android 13+
   alleen als `POST_NOTIFICATIONS` is verleend);
2. spreekt datzelfde antwoord uit via `TextToSpeech` en sluit zichzelf pas af
   nadat het uitspreken klaar is (`UtteranceProgressListener`).

De uitgesproken tekst, de notificatietekst en de tekst op het handmatig te
openen Flutter-scherm zijn identiek. De teksten staan op één plek per kant:

- Flutter: `lib/wind_data.dart` (`WindData`)
- Native: `android/app/src/main/kotlin/nl/vdzon/wind/WindAnswers.kt`

Wijzig je de ene, pas dan ook de andere aan.

## Build & test

Vereist de Flutter SDK (stable channel) en een Android SDK.

```bash
cd wind
flutter pub get
flutter test                 # Dart unit-/smoke-tests op de waarden-logica
flutter build apk --release  # release-APK in build/app/outputs/flutter-apk/

# Native Kotlin smoke-tests:
cd android && ./gradlew test
```

CI (`.github/workflows/build-apk.yml`) draait `flutter test` + `flutter build
apk --release` bij elke push naar `main` en publiceert de APK als GitHub
Release.

## Handmatige verificatie (buiten CI)

Zie de PR-beschrijving voor de stappen: testen via Google's App Actions
test-tool, de echte "Hey Google, vraag Wind ..."-flow op een telefoon, en het
controleren van de notificatie op een Garmin-horloge via Garmin Connect
smart-notifications.
