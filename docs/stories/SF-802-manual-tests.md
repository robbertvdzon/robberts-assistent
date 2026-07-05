# SF-802 - PoC: Wind-app

## Story

PoC: Wind-app

<!-- refined-by-factory -->

## Scope

PoC voor een Android-app **"Wind"** die bewijst dat de keten "Hey Google" → Android App Actions → eigen app werkt met een hands-free gevoel (spraak + notificatie, geen zichtbaar scherm), vóórdat er een backend wordt gebouwd. Er wordt in deze story géén backend, echte weerdata of andere app gebouwd.

In scope:
- Een Flutter/Android-app "Wind" (nieuw project in deze repo) met minimaal 2 App Actions-capabilities gedefinieerd in `shortcuts.xml`:
  - "huidige windsnelheid" → hardcoded of random waarde
  - "voorspelling" → hardcoded tekst
- Per capability een native Android trampoline-activity (`Theme.Translucent.NoDisplay` of vergelijkbaar) die:
  - het antwoord uitspreekt via Android `TextToSpeech`
  - een Android-notificatie post met dezelfde tekst
  - zichzelf direct afsluit, zonder zichtbaar scherm
- Een eenvoudig, handmatig te openen app-scherm dat dezelfde waarden toont, zodat de app ook zonder spraak te testen is.
- GitHub Actions-workflow die bij push naar `main` de Flutter-app bouwt tot een release-APK en die publiceert als downloadbare GitHub Release (via `softprops/action-gh-release`), zodat de APK zonder Play Store of lokale build-omgeving te installeren is.
- Repo-documentatie: aanvullen van de nog-lege `docs/factory/`-templates met concrete repo-informatie (build/test-commando's, stack, structuur).

Buiten scope:
- Backend, OpenShift, Postgres, echte weerdata, Todo-app, Assistent-app, Telegram-koppeling — komen in latere stories.

## Acceptance criteria

1. De repo bevat een buildbare Flutter/Android-app "Wind"; `flutter build apk --release` (of het gedocumenteerde equivalent) slaagt.
2. `shortcuts.xml` bevat minimaal 2 App Actions-capabilities: één voor "huidige windsnelheid" en één voor "voorspelling", correct gekoppeld aan de bijbehorende trampoline-activities.
3. Voor elke capability bestaat een native trampoline-activity die a) het antwoord uitspreekt via `TextToSpeech`, b) een notificatie post met dezelfde tekst, en c) zichzelf direct afsluit zonder een zichtbaar scherm te tonen.
4. "huidige windsnelheid" geeft een hardcoded/random windsnelheid; "voorspelling" geeft een hardcoded voorspellingstekst. De uitgesproken tekst en de notificatietekst zijn identiek.
5. Bij handmatig openen van de app verschijnt een eenvoudig scherm dat dezelfde windsnelheid- en voorspellingswaarden toont, zodat de app zonder spraak te testen is.
6. Een GitHub Actions-workflow bouwt bij push naar `main` een release-APK en publiceert deze als downloadbare GitHub Release (`softprops/action-gh-release`); de APK is als asset te downloaden.
7. De `docs/factory/`-documenten (`development.md`, `technical-spec.md`, `functional-spec.md`) zijn aangevuld met concrete repo-informatie (o.a. build-/testcommando's, stack en projectstructuur) i.p.v. de placeholder-teksten.
8. Het worklog `docs/stories/worklog/SF-802-worklog.md` is bijgewerkt met plan, uitgevoerde stappen en resultaten.
9. De PR-beschrijving bevat handmatige testinstructies voor: testen via Google's App Actions test-tool, een echte "Hey Google, vraag Wind ..."-flow op een telefoon, en het controleren van de notificatie op een Garmin-horloge via Garmin Connect smart-notifications. (Deze handmatige verificaties zijn buiten CI en gelden als afgeronde documentatie, niet als geautomatiseerde test.)

## Aannames

- De app wordt als nieuw Flutter-project (met Android-host) in deze repo aangelegd; er is nog geen bestaande app-code, alleen een README.
- De genoemde referentie-workflow (`softwarefactory/.github/workflows/dashboard-frontend-image.yml`, job `build-apk`) dient als voorbeeld/analogie; de daadwerkelijke workflow wordt in déze repo geschreven en hoeft niet identiek te zijn.
- `PLAN.md` uit de story-beschrijving bestaat (nog) niet in deze repo; de story-description zelf is de leidende context. De developer hoeft geen `PLAN.md` te maken tenzij dat elders vereist blijkt.
- Windsnelheid- en voorspellingswaarden mogen vrij worden gekozen (hardcoded of random) zolang uitgesproken en genotificeerde tekst overeenkomen.
- App-naam voor App Actions-invocatie is "Wind"; het exacte invocatie-fraseringspatroon mag door de developer worden ingevuld conform Google App Actions-conventies.
- De trampoline-activities en TTS/notificatie-logica worden native (Kotlin/Java) geïmplementeerd; koppeling met Flutter mag via platform channels of losstaande activities, naar keuze van de developer.
- Handmatige verificatie op echte hardware (telefoon, Garmin-horloge, Google-testtool) kan de agent niet zelf uitvoeren; deze wordt als instructie in de PR gedocumenteerd voor menselijke uitvoering.
- Minimale Android-versie/SDK, package-naam en signing-config mogen door de developer met redelijke defaults worden gekozen (debug-signing volstaat voor een installeerbare PoC-APK, tenzij anders vereist).

## Eindsamenvatting

# Eindsamenvatting — SF-802: PoC Wind-app

## Wat is gebouwd
Een nieuwe Flutter/Android-app **"Wind"** (in `wind/`, package `nl.vdzon.wind`) die de keten **"Hey Google" → Android App Actions → eigen app** bewijst met een hands-free gevoel (spraak + notificatie, geen zichtbaar scherm). Geen backend of echte weerdata — puur PoC, conform scope.

Opgeleverde onderdelen:
- **2 App Actions-capabilities** in `res/xml/shortcuts.xml`: `GET_WIND_SPEED` (huidige windsnelheid) en `GET_WIND_FORECAST` (voorspelling), gekoppeld aan native activities.
- **Native Kotlin trampoline-activities** (`AnswerTrampolineActivity` + `WindSpeedActivity`/`ForecastActivity`): `Theme.Translucent.NoDisplay` + `noHistory`/`excludeFromRecents` → geen zichtbaar scherm. Ze spreken het antwoord uit via `TextToSpeech` (nl_NL, sluit pas af bij `onDone` zodat spraak niet afkapt), posten een notificatie met dezelfde tekst (notification channel + `POST_NOTIFICATIONS`-guard voor Android 13+) en sluiten zichzelf direct af.
- **Flutter-scherm** (`lib/main.dart`) dat dezelfde waarden in kaarten toont, zodat de app ook zonder spraak te testen is.
- **CI-workflow** (`.github/workflows/build-apk.yml`): bouwt bij push naar `main` (+ `workflow_dispatch`) een release-APK en publiceert deze als downloadbare GitHub Release via `softprops/action-gh-release`.
- **Docs**: `development.md`, `technical-spec.md`, `functional-spec.md` aangevuld met concrete stack/build/test-info; `wind/README.md` toegevoegd; worklog bijgewerkt.

## Belangrijkste keuzes
- **Windsnelheid hardcoded i.p.v. random**, zodat scherm-, spraak- én notificatietekst gegarandeerd identiek zijn. Antwoordteksten staan gedeeld in `lib/wind_data.dart` (Flutter) en `WindAnswers.kt` (native), 1-op-1 gelijk gehouden en geverifieerd.
- **Defaults**: minSdk 23, compile/targetSdk 34, debug-signing (volstaat voor installeerbare PoC-APK). Gradle-wrapper bewust meegecommit (wordt niet door `flutter build` geregenereerd).
- Trampoline/TTS/notificatie **native (Kotlin)** uitgevoerd, losstaand van de Flutter-UI.

## Wat is getest
- Unit-/smoke-tests op de waarden-logica: `test/wind_data_test.dart` (Dart) en `WindAnswersTest.kt` (Kotlin/JUnit).
- Reviewer heeft de volledige story-diff t.o.v. `main` beoordeeld: alle 9 acceptatiecriteria afgedekt, tekst-gelijkheid en trampoline-flow correct, CI-pad juist. Resultaat: **akkoord, geen bugs/regressies**.

## Bewust niet gedaan / kanttekeningen
- **Build & tests niet lokaal gedraaid**: in de build-omgeving ontbreken Flutter-/Dart-/Android-SDK's. `flutter test`/`flutter build apk` draaien in CI bij push naar `main`; XML/projectbestanden zijn wel op well-formedness gecontroleerd.
- **Kotlin-test draait (nog) niet in CI**: de workflow doet `flutter test`, geen `./gradlew test`. Reviewer markeerde dit als niet-blokkerende suggestie.
- **Handmatige hardware-verificatie** kan de factory niet zelf uitvoeren; gedocumenteerd als PR-instructies (`docs/stories/SF-802-manual-tests.md`): Google App Actions test-tool, echte "Hey Google, vraag Wind …"-flow op telefoon, en notificatiecheck op Garmin-horloge via Garmin Connect. Deze gelden als afgeronde documentatie, niet als geautomatiseerde test.
- **Buiten scope** (latere stories): backend, OpenShift, Postgres, echte weerdata, Todo-/Assistent-app, Telegram-koppeling.
