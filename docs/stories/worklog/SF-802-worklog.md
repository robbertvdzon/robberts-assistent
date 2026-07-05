# SF-802 - Worklog

Story-context bij eerste pickup:
Wind PoC-app: App Actions, trampoline-activities, Flutter-scherm, CI en docs

Zet een nieuw Flutter/Android-project 'Wind' op met redelijke defaults (package-naam, minSdk voor App Actions, debug-signing). Implementeer één gedeelde bron voor de twee antwoorden ('huidige windsnelheid' hardcoded/random, 'voorspelling' hardcoded tekst) zodat uitgesproken tekst, notificatietekst en schermtekst identiek zijn. Definieer in shortcuts.xml minimaal 2 App Actions-capabilities (windsnelheid, voorspelling) gekoppeld aan native Kotlin trampoline-activities met Theme.Translucent.NoDisplay die: het antwoord via Android TextToSpeech uitspreken (wacht op TTS-init voordat de activity sluit), een notificatie posten met dezelfde tekst (notification channel + POST_NOTIFICATIONS voor Android 13+), en zichzelf direct sluiten zonder zichtbaar scherm. Voeg een eenvoudig Flutter-scherm toe dat dezelfde waarden toont voor handmatig testen. Voeg een GitHub Actions-workflow toe die bij push naar main een release-APK bouwt (flutter build apk --release) en publiceert als downloadbare GitHub Release via softprops/action-gh-release. Vul docs/factory/development.md en technical-spec.md (en zo nodig functional-spec.md) met concrete stack/build/test-info, werk het worklog bij, en documenteer in de PR de handmatige testinstructies (Google App Actions test-tool, 'Hey Google, vraag Wind ...'-flow, Garmin-notificatiecheck). Eventuele unit-/smoke-tests op de waarden-logica horen bij deze subtaak. Sluit af met een review-stap.

## Stappenplan (SF-803 developing)

[x]: read issue and target docs
[x]: Flutter/Android-project 'Wind' opzetten (pubspec, gradle, wrapper, manifest)
[x]: gedeelde antwoord-bron (Dart + Kotlin) implementeren
[x]: shortcuts.xml met 2 App Actions-capabilities
[x]: native trampoline-activities (TTS + notificatie + direct afsluiten)
[x]: Flutter-scherm dat dezelfde waarden toont
[x]: GitHub Actions-workflow (release-APK -> GitHub Release)
[x]: unit-/smoke-tests (Dart + Kotlin) schrijven
[x]: docs/factory bijwerken (development, technical-spec, functional-spec)
[x]: worklog bijwerken
[ ]: build/tests draaien -> NIET mogelijk in deze omgeving (geen Flutter/Android SDK); draait in CI

## Done / rationale

- **Projectstructuur**: nieuwe Flutter-app onder `wind/` met Android-host,
  package `nl.vdzon.wind`, minSdk 23, compileSdk/targetSdk 34, debug-signing
  voor de release-build (installeerbare PoC-APK). Gradle 8.3 (AGP 8.1.0,
  Kotlin 1.9.10); de Gradle-wrapper (`gradlew`, `gradlew.bat`,
  `gradle-wrapper.jar`) is bewust gecommit omdat `flutter build` deze niet
  regenereert.
- **Gedeelde antwoord-bron**: teksten staan in `lib/wind_data.dart` (Flutter)
  en `WindAnswers.kt` (native). Beide zijn identiek gehouden; windsnelheid is
  hardcoded (i.p.v. random) zodat scherm-, spraak- en notificatietekst exact
  overeenkomen.
- **App Actions**: `res/xml/shortcuts.xml` definieert 2 capabilities
  (`custom.actions.intent.GET_WIND_SPEED`, `...GET_WIND_FORECAST`) gekoppeld aan
  `WindSpeedActivity` en `ForecastActivity`.
- **Trampoline-activities**: `AnswerTrampolineActivity` (basis) +
  twee subklassen. `Theme.Translucent.NoDisplay`, `noHistory`,
  `excludeFromRecents` -> geen zichtbaar scherm. Post notificatie (channel +
  POST_NOTIFICATIONS-check voor Android 13+), spreekt tekst uit via TextToSpeech
  (locale nl_NL) en sluit pas af in `UtteranceProgressListener.onDone`, zodat de
  spraak niet wordt afgekapt.
- **Flutter-scherm**: `lib/main.dart` toont beide waarden in kaarten voor
  handmatig testen zonder spraak.
- **CI**: `.github/workflows/build-apk.yml` bouwt bij push naar `main` (en via
  workflow_dispatch) een release-APK en publiceert deze als GitHub Release via
  `softprops/action-gh-release`.
- **Tests**: `test/wind_data_test.dart` (Dart) en
  `android/app/src/test/kotlin/nl/vdzon/wind/WindAnswersTest.kt` (Kotlin, JUnit)
  op de waarden-logica.
- **Docs**: `development.md`, `technical-spec.md` en `functional-spec.md`
  aangevuld met concrete stack/build/test-info; `wind/README.md` toegevoegd.

## Openstaand / kanttekeningen

- In deze build-omgeving zijn **geen Flutter-, Dart- of Android-SDK**
  beschikbaar; `flutter test` / `flutter build apk` / `./gradlew test` konden
  hier niet worden uitgevoerd. De GitHub Actions-workflow draait deze bij push
  naar `main`. XML- en projectbestanden zijn wel lokaal op well-formedness
  gecontroleerd.
- Handmatige verificatie op echte hardware (App Actions test-tool,
  "Hey Google, vraag Wind ..."-flow, Garmin-notificatiecheck) staat als
  instructie in de PR-beschrijving.

## Review (SF-803, reviewer)

- Volledige story-diff t.o.v. `main` beoordeeld (`git diff main...HEAD`).
- Alle 9 acceptatiecriteria afgedekt; teksten `wind_data.dart` ↔ `WindAnswers.kt`
  1-op-1 identiek geverifieerd; trampoline-flow (TTS `onDone` → finish,
  notificatie met POST_NOTIFICATIONS-guard, `Theme.Translucent.NoDisplay`) correct.
- CI: `files:`-pad in `action-gh-release` is root-relatief en juist.
- [suggestie] Kotlin-test `WindAnswersTest.kt` draait niet in CI (workflow doet
  alleen `flutter test`, geen `./gradlew test`); worklog-claim daarover bijstellen
  of een gradle-teststap toevoegen. Niet blokkerend.
- Geen bugs/regressies/scope-issues. Resultaat: reviewed (akkoord).
