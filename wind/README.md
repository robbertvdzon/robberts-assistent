# Wind

Native Android-app (geen Flutter-runtime, MainActivity is pure Kotlin) die de
keten **"Hey Google" → Android App Actions → eigen app** met een hands-free
gevoel afhandelt: het antwoord wordt uitgesproken (TextToSpeech) én als
notificatie gepost, zónder zichtbaar scherm.

## Wat het doet

Drie trampoline-activities (`Theme` volledig transparant, geen zichtbaar
scherm — zie `TrampolineTheme`), elk gekoppeld aan een App Actions-capability
(`android/app/src/main/res/xml/shortcuts.xml`) of het app-icoon zelf:

| Trigger | Activity | Vraagt |
|---------|----------|--------|
| App-icoon / "Hey Google, open Wind" | `MainActivity` | huidige windsnelheid |
| `custom.actions.intent.GET_WIND_SPEED` | `WindSpeedActivity` | huidige windsnelheid |
| `custom.actions.intent.GET_WIND_FORECAST` | `ForecastActivity` | voorspelling |

Elke trampoline-activity haalt eerst (async, stil — geen inlog-UI) een actueel
antwoord op bij `robberts-assistent-backend`'s chat-assistent
(`AssistantClient.kt`), valt bij falen terug op een statische tekst
(`WindAnswers.kt`), post een notificatie met dat antwoord, spreekt het uit via
`TextToSpeech`, en sluit zichzelf pas af nadat het uitspreken klaar is.

Zolang er nog geen gecachete Google-sessie is, stuurt het app-icoon eenmalig
naar `LoginActivity` (wél een zichtbaar scherm) — daarna gaat "Hey Google, open
Wind" weer direct headless via silent sign-in.

Bij opstarten checkt de app (max 1x/12u, achtergrondthread) of er een nieuwere
versie op GitHub staat; zo ja, post `WindUpdateChecker.kt` een notificatie
(tikken opent de release-pagina in de browser) — géén dialoogje, dat zou de
instant-antwoord-belofte breken.

## Build & test

Vereist de Flutter SDK (stable channel) en een Android SDK.

```bash
cd wind
flutter pub get
flutter test                 # Dart unit-/smoke-tests (statische terugvalteksten)
flutter build apk --release \
  --build-number=<N> \
  --dart-define=GOOGLE_CLIENT_ID=<web-oauth-client-id>
# resultaat: build/app/outputs/flutter-apk/app-release.apk

# Native Kotlin smoke-tests:
cd android && ./gradlew test
```

CI (`.github/workflows/build-apk.yml`) draait de Dart-tests + bouwt de
release-APK (met de gedeelde release-keystore en een oplopend `--build-number`)
bij elke push naar `main`, en publiceert 'm naar de vaste GitHub-Release-tag
`wind-latest`.

## Handmatige verificatie (buiten CI)

Zie de PR-beschrijving voor de stappen: testen via Google's App Actions
test-tool, de echte "Hey Google, vraag Wind ..."-flow op een telefoon, en het
controleren van de notificatie op een Garmin-horloge via Garmin Connect
smart-notifications.
