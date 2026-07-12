# Development

Deze repo bevat drie Flutter/Android-apps en één Kotlin/Spring-Boot-backend:

- **wind/** — PoC-app zonder backend-afhankelijkheid voor eigen bestaan (wel een
  chat-assistent-call voor de wind-/voorspellingstekst, zie hieronder). Alleen
  APK, geen web-deploy.
- **robberts_assistent/** — dagelijkse samenvatting + chat-assistent, Google-
  login. Draait als APK én als web-app op OpenShift.
- **notities/** — één auto-opslaande notitie, Google-login. Alleen APK.
- **robberts-assistent-backend/** — Kotlin/Spring Boot/Spring Modulith backend
  voor alle drie (auth, notes, summary, assistant-modules).

Zie `deployment.md` voor hoe elke component gebouwd/gedeployed wordt en
`secrets-local.md` voor lokale env-vars.

## Stack

- **Flutter-apps**: Flutter (stable channel), Dart SDK `>=3.0.0 <4.0.0`.
  `wind/` heeft daarnaast native Kotlin (App Actions-trampoline-activities,
  TextToSpeech, notificaties, eigen Google Sign-In — zie
  `wind/android/app/src/main/kotlin/nl/vdzon/wind/`).
- **Backend**: Kotlin, Spring Boot 3.5, Spring Modulith, Java 21. JdbcTemplate +
  Flyway voor notities (Postgres in productie, H2 in-memory lokaal/tests).
  Spring AI (`spring-ai-openai` + `spring-ai-client-chat`, handmatige bean-
  wiring, geen auto-configuratie-starter) voor de chat-assistent.

## Commands

Flutter-app-commando's draaien vanuit de app-map (`wind/`, `robberts_assistent/`
of `notities/`):

- Dependencies: `flutter pub get`
- Build (release-APK): `flutter build apk --release`
  (resultaat: `<app>/build/app/outputs/flutter-apk/app-release.apk`)
- Unit/smoke tests (Dart): `flutter test`
- Native unit tests (alleen `wind/`, Kotlin): `cd wind/android && ./gradlew test`
- Lint/format: `flutter analyze` en `dart format .`

Backend-commando's draaien vanuit `robberts-assistent-backend/`:

- Tests: `mvn test` (H2 in-memory, geen `RA_DATABASE_URL` nodig)
- Build: `mvn -DskipTests package`

## Bekende sandbox-beperking: Flutter-tests

**`flutter test` (en dus ook de Flutter-widget-tests) kunnen niet draaien in de
tester-/reviewer-sandbox**: die draait op `linux/arm64`, Google publiceert
alleen een x64 Flutter-SDK, en er is geen qemu/binfmt/docker/root beschikbaar om
die alsnog te draaien. De CI-workflows die `flutter test` wél draaien
(`build-apk.yml`, `robberts-assistent-apk.yml`, `notities-apk.yml`) triggeren
alleen op push naar `main`, dus er is voor een open PR/feature-branch ook geen
CI-bewijs om op terug te vallen.

Voor reviewer/tester betekent dit: ontbrekend `flutter test`-bewijs is **geen
blocker** voor een wijziging die uitsluitend Dart/Flutter-code raakt — een
grondige handmatige code-review tegen de acceptatiecriteria (plus, waar van
toepassing, groene backend-`mvn test`) is het geaccepteerde alternatief. Zie
`agents/reviewer.md`/`agents/tester.md`. Native Kotlin-tests (`./gradlew test`,
alleen relevant voor `wind/`) en backend-tests (`mvn test`) draaien wél gewoon
in de sandbox en blijven een harde eis.

## Structuur (verkort)

```
wind/                                   # PoC, geen web-deploy
  lib/                                  # Flutter-scherm (handmatig testen)
  android/app/src/main/kotlin/nl/vdzon/wind/
    AnswerTrampolineActivity.kt         # vraagt chat-assistent, TTS + notificatie
    AssistantClient.kt                  # native Google Sign-In + backend-call
    LoginActivity.kt                    # eenmalige, zichtbare Google-inlogstap
    WindAnswers.kt                      # statische terugvalteksten
    MainActivity.kt / WindSpeedActivity.kt / ForecastActivity.kt

robberts_assistent/                     # APK + web (OpenShift)
  lib/                                  # main.dart, api_client.dart, ...

notities/                               # APK only
  lib/

robberts-assistent-backend/
  src/main/kotlin/nl/vdzon/robbertsassistent/
    assistant/                          # AssistantService + ChatClient
      ai/                               # AiConfig, NotesTools, WindTools, MockChatModel
    auth/                               # Google-login, sessie-tokens
    notes/                              # notitie-CRUD
    summary/

.github/workflows/                      # build-apk.yml, robberts-assistent-apk.yml,
                                         # notities-apk.yml, backend-image.yml, frontend-image.yml
```

## Conventies

- `wind/`: antwoord-teksten staan op één plek per kant (`wind_data.dart` /
  `WindAnswers.kt`, dit laatste nu de terugvaltekst als de chat-assistent niet
  bereikbaar is) zodat scherm, spraak en notificatie identiek blijven; wijzig
  ze altijd samen.
- De Gradle-wrapper (`wind/android/gradlew`, `.bat`, `gradle-wrapper.jar`) is
  bewust gecommit omdat `flutter build` deze niet regenereert.
- App Actions-invocatie ("Hey Google, vraag Wind ...") en de notificatie op een
  Garmin-horloge zijn alleen op echte hardware te verifiëren, niet in CI.
