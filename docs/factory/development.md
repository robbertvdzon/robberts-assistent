# Development

De repository bevat de **Wind** PoC-app: een Flutter/Android-app in de map
`wind/`. Zie `wind/README.md` voor de functionele beschrijving.

## Stack

- Flutter (stable channel), Dart SDK `>=3.0.0 <4.0.0`.
- Android-host met native Kotlin (App Actions-trampoline-activities,
  TextToSpeech, notificaties).
- Gradle 8.3 (wrapper is gecommit), Android Gradle Plugin 8.1.0,
  Kotlin 1.9.10, JDK 17, compileSdk 34, minSdk 23.

## Commands

Alle app-commando's draaien vanuit de map `wind/`:

- Dependencies: `flutter pub get`
- Build (release-APK): `flutter build apk --release`
  (resultaat: `wind/build/app/outputs/flutter-apk/app-release.apk`)
- Unit/smoke tests (Dart): `flutter test`
- Native unit tests (Kotlin): `cd android && ./gradlew test`
- Lint/format: `flutter analyze` en `dart format .`

## Structuur

```
wind/
  lib/
    main.dart          # handmatig te openen Flutter-scherm
    wind_data.dart     # gedeelde antwoord-teksten (Dart-kant)
  test/
    wind_data_test.dart
  android/app/src/main/
    AndroidManifest.xml
    res/xml/shortcuts.xml            # 2 App Actions-capabilities
    kotlin/nl/vdzon/wind/
      WindAnswers.kt                 # gedeelde antwoord-teksten (native kant)
      AnswerTrampolineActivity.kt    # TTS + notificatie + direct afsluiten
      WindSpeedActivity.kt           # capability "huidige windsnelheid"
      ForecastActivity.kt            # capability "voorspelling"
      MainActivity.kt                # FlutterActivity-host
  android/app/src/test/kotlin/nl/vdzon/wind/WindAnswersTest.kt
.github/workflows/build-apk.yml      # CI: bouwt APK + GitHub Release
```

## Conventions & teststrategie

- Antwoord-teksten staan op één plek per kant (`wind_data.dart` /
  `WindAnswers.kt`) zodat scherm, spraak en notificatie identiek blijven;
  wijzig ze altijd samen.
- Tests dekken de waarden-logica (niet-leeg, juiste eenheid/inhoud, onderling
  verschillend). Het UI- en TTS-/notificatiegedrag wordt handmatig op hardware
  geverifieerd (zie PR-instructies); dat valt buiten CI.
- De Gradle-wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) is bewust
  gecommit omdat `flutter build` deze niet regenereert.
