# SF-1704 - Worklog

Story-context bij eerste pickup:
Launch-bron detecteren, loggen in backend en praatmodus openen

Implementeer alle drie de delen van SF-1704 in één samenhangende stap, inclusief de bijbehorende unit- en widget-tests.

DEEL 1 (Android native, robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/): nieuwe LaunchSource.kt met enum LaunchSourceType (ASSISTANT/LAUNCHER/OTHER/UNKNOWN), data class LaunchInfo(source, referrer, action, categories, extras) en een PURE classify(referrer: String?) zonder Android-classes, plus een functie die uit Activity+Intent een volledige LaunchInfo bouwt en een map-representatie voor het channel. Assistent-packages (com.google.android.googlequicksearchbox, com.google.android.apps.googleassistant, com.google.android.apps.bard, com.google.android.apps.gemini) en bekende launchers als constanten bovenaan, met comment dat de lijst bewust uitbreidbaar is. Referrer null/leeg => UNKNOWN, package eindigend op '.launcher' of in de launcherlijst => LAUNCHER, rest => OTHER. Extras defensief: runCatching per key, toString(), newlines vervangen, waarde afkappen (~200 tekens), aantal keys begrenzen (~50) - nooit crashen. MainActivity.kt: LaunchInfo bepalen in onCreate EN onNewIntent (al singleTop) en ontsluiten via een derde MethodChannel 'nl.vdzon.robberts_assistent/launch' in de stijl van de bestaande updater-/alarm-channels: pull ('launchInfo' opvragen, dekt de koude start) + push (invokeMethod bij onNewIntent). Voeg een android/app/src/test/...-sourceset toe met JUnit-testImplementation en een test op classify(...) die alle vier de uitkomsten dekt.

DEEL 2 (backend, nieuwe Modulith-module .../robbertsassistent/applaunch/): AppLaunch.kt (data class id/at/source/referrer/action/categories/extras/platform/appVersion + enum AppLaunchSource), AppLaunchRepository, FirestoreAppLaunchRepository, InMemoryAppLaunchRepository en AppLaunchStoreConfig exact volgens het patroon van watches/WatchRepository.kt, FirestoreWatchRepository.kt en WatchStoreConfig.kt (in-memory fallback zonder Firebase, runCatching rond de Firestore-init). AppLaunchService: opslaan met server-bepaalde id (UUID) en at (Instant.now(), clienttijd niet vertrouwen), laatste N teruggeven (default 50, nieuwste eerst), en bij opslaan alles ouder dan 30 dagen opruimen als best effort (runCatching + logger.warn; een falende opschoning mag het opslaan niet laten mislukken). Per opgeslagen launch precies EEN slf4j-INFO-regel, op een regel, in exact het formaat: APP_LAUNCH source=<SOURCE> platform=<platform> referrer=<referrer> action=<action> categories=<a,b> extras=<k=v;k=v> - ontbrekende waarden als 'null', lege lijsten/maps als lege waarde, newlines vervangen door een spatie zodat grep APP_LAUNCH altijd werkt. AppLaunchController: POST /api/v1/app-launches en GET /api/v1/app-launches?limit=50, beide achter authService.requireAuthorization(authorization) net als WatchesController; onbekende/ontbrekende source => UNKNOWN (geen 400); limit begrensd op max 200. Tests: AppLaunchServiceTest (opslaan, limiet/volgorde nieuwste-eerst, 30-dagen-opschoning) en AppLaunchControllerTest (POST, GET, auth-gate). De module mag alleen auth en firebase gebruiken zodat ModulithArchitectureTest groen blijft.

DEEL 3 (Flutter, robberts_assistent/lib/): nieuwe launch_source.dart die het MethodChannel alleen leest als !kIsWeb en de laatste launch aanbiedt via een ValueNotifier in de stijl van FcmService.deepLinkTarget (na afhandeling terug op null). api_client.dart: methode om een launch te posten naar POST /api/v1/app-launches, alleen bij aanwezig sessie-token, anders stil overslaan; fouten negeren/loggen, nooit crashen of de UI blokkeren. home_screen.dart: listener naast de bestaande deepLinkTarget-listener (addListener in initState + directe aanroep voor de koude start, removeListener in dispose); elke launch fire-and-forget posten; bij source == ASSISTANT tab 1 (Assistent) selecteren EN meteen AssistantScreen zonder conversationId pushen met startInVoiceMode: true en autoStartListening: true; bij elke andere bron gedrag ongewijzigd; op web een enkele launch met platform 'web' en source UNKNOWN zonder native gegevens. assistant_screen.dart: twee optionele parameters startInVoiceMode en autoStartListening (beide default false); startInVoiceMode laat _mode op _Mode.voice starten; autoStartListening roept _startListening() pas aan aan het EINDE van _initSpeech() en alleen als _speechAvailable waar is en de widget nog mounted - niet blind in initState. Geen nieuwe permissie-dependency: RECORD_AUDIO staat al in de manifest en speech_to_text.initialize() regelt de runtime-permissie zelf; is spraak niet beschikbaar, dan gewoon de bestaande foutmelding en mic-knop, nooit vastlopen of crashen. Widget-tests in robberts_assistent/test/: opent in praatmodus bij startInVoiceMode true, blijft in chatmodus bij false.

VERIFICATIEGRENS (expliciet uit de story): NIET proberen een Assistent-/Gemini-start na te bootsen in CI, emulator of preview. Klaar is: code compileert, bestaande tests groen, nieuwe unit-/widget-tests groen, mvn test + flutter analyze + flutter test groen en de APK-build slaagt. Kan de Kotlin/Gradle-unittest lokaal niet draaien (geen Android SDK), dan is dat acceptabel. Werk de worklog docs/stories/worklog/SF-1704-worklog.md bij met wat gedaan en gedraaid is.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1705 (development) — wat is er gebouwd

### Deel 1 — Android native launch-bron
- Nieuw `robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/LaunchSource.kt`:
  `enum LaunchSourceType { ASSISTANT, LAUNCHER, OTHER, UNKNOWN }`, `data class LaunchInfo(source,
  referrer, action, categories, extras)` met `toMap()` voor het MethodChannel, en een **pure**
  `LaunchSource.classify(referrer: String?)` zonder Android-classes. De assistent-packages en de
  bekende launchers staan als constante bovenaan met een comment dat de lijst bewust uitbreidbaar
  is zodra de echte logs bekend zijn.
- `LaunchSource.from(activity, intent)` verzamelt referrer (`activity.referrer`, host = package),
  action, categories en extras. Extras zijn defensief: `runCatching` per key, `toString()`,
  newlines vervangen, waarde afgekapt op 200 tekens, maximaal 50 keys.
- `MainActivity.kt`: bepaalt de `LaunchInfo` in `onCreate` (vóór `super.onCreate`, zodat Dart 'm
  niet eerder kan opvragen dan hij bestaat) én in `onNewIntent`, en ontsluit 'm via het derde
  MethodChannel `nl.vdzon.robberts_assistent/launch` — **pull** (`launchInfo`, dekt de koude start)
  en **push** (`invokeMethod("launchInfo", …)` bij `onNewIntent`). De bestaande updater- en
  alarm-channels zijn ongewijzigd.
- Nieuwe unittest-sourceset `android/app/src/test/kotlin/.../LaunchSourceTest.kt` +
  `testImplementation("junit:junit:4.13.2")` in `android/app/build.gradle.kts`; dekt alle vier de
  uitkomsten van `classify`.

### Deel 2 — Backend-module `applaunch`
- Nieuwe Modulith-module `nl/vdzon/robbertsassistent/applaunch/` die alleen `auth` en `firebase`
  gebruikt: `AppLaunch.kt` (+ `AppLaunchSource`), `AppLaunchRepository` (poort + in-memory),
  `FirestoreAppLaunchRepository` (collectie `app-launches`), `AppLaunchStoreConfig` (in-memory
  fallback zonder Firebase, `runCatching` rond de Firestore-init) — exact het patroon van
  `watches/`.
- `AppLaunchService`: server bepaalt `id` (UUID) en `at` (`Instant.now()`, injecteerbaar als
  `now`-lambda zodat tests een klok kunnen sturen); `recent(limit = 50)` nieuwste eerst, begrensd
  op 200; bij elke opslag een best-effort opschoning van alles ouder dan 30 dagen
  (`runCatching` + `logger.warn`, mag het opslaan nooit laten mislukken).
- Precies één INFO-regel per launch, op één regel:
  `APP_LAUNCH source=… platform=… referrer=… action=… categories=a,b extras=k=v;k=v`
  (ontbrekend = `null`, leeg = lege waarde, newlines → spatie). Uitlezen:
  `oc logs deploy/robberts-assistent-backend -n robberts-assistent | grep APP_LAUNCH`.
- `AppLaunchController`: `POST /api/v1/app-launches` + `GET /api/v1/app-launches?limit=50`, beide
  achter `authService.requireAuthorization(...)`. Onbekende/ontbrekende `source` → `UNKNOWN`
  (geen 400), `limit` begrensd op 200.

### Deel 3 — Flutter
- Nieuw `lib/launch_source.dart`: `AppLaunchInfo` + `LaunchSourceService` met een `ValueNotifier`
  (`lastLaunch`) in de stijl van `FcmService.deepLinkTarget`; leest het MethodChannel alleen als
  `!kIsWeb` (web meldt één launch met `platform = "web"`, `source = UNKNOWN`). `appVersion` komt
  uit de bestaande updater-channel/`UpdateChecker.selfPackageName`; lukt dat niet, dan `null`.
- `api_client.dart`: `logAppLaunch(...)` — zonder sessie-token stil overslaan, fouten genegeerd.
- `home_screen.dart`: listener naast de bestaande deep-link-listener (`addListener` + directe
  aanroep in `initState`, `removeListener` in `dispose`); elke launch wordt fire-and-forget gepost,
  en bij `source == ASSISTANT` wordt tab 1 (Assistent) geselecteerd en meteen een `AssistantScreen`
  zonder `conversationId` gepusht met `startInVoiceMode: true, autoStartListening: true`. Andere
  bronnen veranderen niets.
- `assistant_screen.dart`: nieuwe optionele `startInVoiceMode` en `autoStartListening` (beide
  default `false`); `_startListening()` wordt pas aan het eind van `_initSpeech()` aangeroepen en
  alleen als spraak beschikbaar is en de widget nog `mounted` is. Geen nieuwe permissie-dependency.

### Gedraaid
- `mvn -o test` (backend, na `rm -rf target`): **385 tests, 0 failures, 0 errors** — inclusief de
  nieuwe `AppLaunchServiceTest` (8) en `AppLaunchControllerTest`/`AppLaunchControllerAuthTest`, en
  de `ModulithArchitectureTest` blijft groen met de nieuwe module.
- `flutter analyze` (robberts_assistent): **No issues found**.
- `flutter test` (robberts_assistent): **65 tests, all passed** — inclusief de nieuwe widget-tests
  voor praat-/chatmodus en voor de assistent-/launcher-launch in `HomeScreen`.
- `flutter build web --release`: geslaagd (de `kIsWeb`-tak breekt de web-build niet).
- **Niet gedraaid, met reden:** `flutter build apk --release` en `./gradlew test` — deze container
  heeft geen Android SDK (`flutter doctor`: "Unable to locate Android SDK"), wat de story expliciet
  als acceptabel benoemt. Als vervanging is `LaunchSource.kt` wél echt gecompileerd (Kotlin
  2.1.21, tegen minimale Android-stubs) en zijn alle vier de `classify`-gevallen daadwerkelijk
  uitgevoerd en groen; de APK-build draait in CI.

### Review SF-1705 (reviewer)
Akkoord. Zelf gedraaid ter verificatie (naast het developer-bewijs):
- `mvn -o test -Dtest='AppLaunch*Test,ModulithArchitectureTest,WatchesControllerTest'` →
  14 tests, 0 failures/errors (incl. `ModulithArchitectureTest` met de nieuwe `applaunch`-module
  en één bestaande `@SpringBootTest` om te bevestigen dat de nieuwe beans wiren — de
  `now: () -> Instant`-defaultparameter op `AppLaunchService` blijkt gewoon te resolven).
- `flutter analyze` → No issues found; `flutter test` → 65/65 groen.
- `./gradlew test` (`LaunchSourceTest`) is hier niet uitvoerbaar: er is geen Gradle-wrapper in
  `robberts_assistent/android`. Conform de story-aanname acceptabel.

Aandachtspunten voor de handmatige telefoontest (geen blockers):
- **Warme start.** `MainActivity` is `singleTop`, dus een tweede "Hé Google"-start terwijl de app
  al draait komt in `onNewIntent`. `Activity.getReferrer()` valt daar terug op de referrer van de
  oorspronkelijke start als de nieuwe intent geen `EXTRA_REFERRER` bevat — de start kan dan als
  `LAUNCHER`/`OTHER` binnenkomen. Test dus expliciet zowel koud (app weggeswipet) als warm.
- **Logregel hangt aan een geslaagde opslag.** `AppLaunchService.record` logt pas ná
  `repository.save(...)`; faalt Firestore, dan is er géén `APP_LAUNCH`-regel. Zie je bij het
  grepppen niets, kijk dan ook naar Firestore-fouten in dezelfde logs.
- De JUnit-test op `classify` draait nergens automatisch (`flutter test` dekt alleen Dart, er is
  geen Gradle-teststap in CI) — bewust buiten scope van deze story.

### Laatste stap
Handmatig testen op Robberts telefoon is de laatste stap: alleen daar is te zien wát Google
Assistent/Gemini als referrer/extras meestuurt. Werkwijze: app een keer normaal starten en een
keer met "Hé Google, start Robberts assistent app", daarna
`oc logs deploy/robberts-assistent-backend -n robberts-assistent | grep APP_LAUNCH`. Blijkt het
Gemini-package niet in `ASSISTANT_PACKAGES` te staan, dan is dat één regel bijwerken in
`LaunchSource.kt`. Er is bewust géén poging gedaan een Assistent-/Gemini-start na te bootsen in
CI, emulator of preview.

## Testronde SF-1706 (tester) — 2026-08-01

### Uitgevoerd
- **Backend:** `mvn -o test` → **385 tests, 0 failures, 0 errors, BUILD SUCCESS** (incl.
  `AppLaunchServiceTest` 8, `AppLaunchControllerTest` 2, `AppLaunchControllerAuthTest` 2 en de
  groene `ModulithArchitectureTest` — de nieuwe `applaunch`-module breekt geen modulegrens).
- **App:** `flutter analyze` → *No issues found*; `flutter test` → **65/65 groen** (incl. de twee
  nieuwe `AssistantScreen`-praatmodustests en de twee nieuwe `HomeScreen`-launchtests).
- **Preview `robberts-assistent-pr-44`** (bevestigd op `head.sha = 40f6ab2`, gelijk aan de branch-HEAD):
  - `POST /api/v1/app-launches` met volledige gegevens → 200, server bepaalt `id` (UUID) en `at`.
  - Onbekende bron (`GEMINI_IETS`), lege body `{}` en lowercase `"assistant"` → 200, geen 400;
    respectievelijk `UNKNOWN`/`UNKNOWN`+`platform=onbekend`/`ASSISTANT`.
  - `GET /api/v1/app-launches` levert nieuwste eerst; `limit=1` werkt, `limit=9999` en `limit=0`
    geven 200 (begrensd, geen fout).
  - **AC6 live bewezen** via `oc logs deploy/robberts-assistent-backend -n robberts-assistent-pr-44
    | grep APP_LAUNCH`: precies één INFO-regel per opgeslagen launch, exact formaat, ontbrekende
    waarden als `null` en lege lijst/map als lege waarde. Newlines in `platform`/`referrer`/
    `action`/`categories`/`extras` worden spaties — de regel blijft één regel en blijft greppable.
  - **Web-launch E2E (Playwright):** het openen van de preview-app stuurt precies één
    `POST /api/v1/app-launches` met `{"source":"UNKNOWN","platform":"web",...}`, zonder
    JS-pagefouten; de bijbehorende `APP_LAUNCH … platform=web`-regel staat in de pod-log en de
    launch komt terug uit de `GET`. Bevestigt AC13 (web-build breekt niet) en de web-aanname.
  - Screenshots in `screenshots/`: startscherm (gedrag ongewijzigd bij een niet-assistent-start) en
    het praatmodus-scherm dat een ASSISTANT-start opent.
- Testdata was preview-only en in-memory; door een pod-rollover tijdens de run is de opslag
  vanzelf leeg — geen restanten.

### Bevinding (terug naar developer)
`MainActivity.onNewIntent` roept **`setIntent(intent)` niet aan**. `Activity.getReferrer()` leest
eerst `getIntent()` (`EXTRA_REFERRER`/`EXTRA_REFERRER_NAME`) en pas daarna de bij `attach()`
vastgelegde `mReferrer`. Omdat `getIntent()` zonder `setIntent()` het *oorspronkelijke* intent
blijft teruggeven — Flutters `FlutterActivity.onNewIntent` zet 'm ook niet (geverifieerd met
`javap` op `flutter.jar`) — wordt bij een warme start een door Gemini meegestuurde
`EXTRA_REFERRER` nooit gelezen. `action`/`categories`/`extras` komen wél uit het nieuwe intent,
dus de gelogde regel mengt nieuwe intent-gegevens met een oude referrer. `AlarmActivity.kt` in dit
repo doet het al wél goed. Zie de bevinding hieronder in de handover.

## Herstelronde SF-1705 (developer) — 2026-08-01

### Uitgevoerd
- [x] Testbevinding SF-1706 verholpen: `MainActivity.onNewIntent` roept nu `setIntent(intent)` aan
  vóór `LaunchSource.from(this, intent)`, met een korte toelichting in commentaar. Daardoor leest
  `Activity.getReferrer()` bij een warme start de `EXTRA_REFERRER` van het *nieuwe* intent i.p.v.
  die van het intent van de koude start, en mengt de `APP_LAUNCH`-regel geen nieuwe intent-gegevens
  meer met een oude referrer. Zelfde patroon als `alarm/AlarmActivity.kt`, dat het al zo doet.
- [x] Vangnet opnieuw gedraaid: `rm -rf target && mvn -o test` → **385 tests, 0 failures, 0 errors,
  BUILD SUCCESS**; `flutter analyze` → *No issues found*; `flutter test` → **65/65 groen**.

### Bewust niet gedaan
- De twee reviewer-**suggesties** uit SF-1705 (loggen vóór `repository.save(...)`, en
  `launchChannel` opruimen) zijn niet doorgevoerd: de eerste botst met AC6 ("bij elke **opgeslagen**
  launch precies één INFO-regel") en zou ook bij een mislukte opslag een regel produceren; de tweede
  is cosmetisch. Beide staan al als aandachtspunt in deze worklog.
- De resterende helft van de bevinding — bij een warme start *zónder* `EXTRA_REFERRER` valt
  `getReferrer()` terug op de bij `attach()` vastgelegde `mReferrer` van de koude start — is een
  Android-platformbeperking en niet in code op te lossen. Dat blijft een observatiepunt voor de
  handmatige telefoontest: start de app zowel koud als warm met "Hé Google, start Robberts
  assistent app" en vergelijk de twee `APP_LAUNCH`-regels.

### Laatste stap (ongewijzigd)
Handmatig testen op Robberts telefoon blijft de laatste stap; er is opnieuw geen poging gedaan een
Assistent-/Gemini-start na te bootsen in CI, emulator of preview.

## Review SF-1705 (reviewer, herstelronde) — 2026-08-01

Volledige story-diff `git diff main...HEAD` beoordeeld (19 bestanden, 3 delen), niet alleen de
laatste wijziging.

### Akkoord
- **Testbevinding SF-1706 correct verholpen**: `MainActivity.onNewIntent` roept `setIntent(intent)`
  aan *vóór* `LaunchSource.from(this, intent)`, met toelichtend commentaar. Daarmee leest
  `Activity.getReferrer()` de `EXTRA_REFERRER` van het nieuwe intent i.p.v. die van de koude start;
  zelfde patroon als `alarm/AlarmActivity.kt`. Verder is er niets aan de code gewijzigd.
- Alle 15 acceptatiecriteria nagelopen tegen de code; deel 1 (`LaunchSource.kt`/`MainActivity.kt`),
  deel 2 (module `applaunch`) en deel 3 (`launch_source.dart`/`home_screen.dart`/
  `assistant_screen.dart`) dekken ze. Logformaat, `null`-weergave, newline-vervanging, 30-dagen-
  opschoning als best effort, `limit`-begrenzing en de auth-gate zitten in tests.
- Eigen gerichte verificatie: `flutter test test/home_screen_test.dart test/assistant_screen_test.dart`
  → **15/15 groen**. Backend- en volledige Flutter-suite niet opnieuw gedraaid (developer-run
  385/0/0 + 65/65 op deze revisie; laatste commit raakt alleen Kotlin + worklog).

### Bevindingen (niet blokkerend)
- [info] `./gradlew` ontbreekt in `robberts_assistent/android`, dus `LaunchSourceTest` kon ook in
  deze sandbox niet draaien — conform de expliciete story-aanname.
- [suggestie] `launchChannel` wordt niet opgeruimd bij engine-detach; bij een `onNewIntent` na
  detach gaat de `invokeMethod` naar een oude messenger. Cosmetisch, geen crash.
- [suggestie] `AppLaunchService.recent(0)` wordt door `coerceIn(1, MAX_LIMIT)` stil `1` i.p.v. een
  lege lijst. Geen consument stuurt `limit=0`.
- [info] Warme start *zonder* `EXTRA_REFERRER` blijft op `mReferrer` van de koude start terugvallen
  (Android-platformbeperking) — terecht als observatiepunt voor de handmatige telefoontest.

Handmatig testen op Robberts telefoon blijft de laatste stap.

## Testronde 2 (SF-1706, tester)

Revisie onder test: `7e6bd74` — bevestigd als `head.sha` van PR #44, dus preview
`robberts-assistent-pr-44` draait exact deze branch-HEAD.

### Vangnet volledig groen
- `mvn -o test` → **385 tests, 0 failures, 0 errors, BUILD SUCCESS**
  (`AppLaunchServiceTest` 8, `AppLaunchControllerTest` 4, `AppLaunchControllerAuthTest` 2,
  `ModulithArchitectureTest` groen)
- `flutter analyze` → *No issues found*
- `flutter test` → **65/65 groen** (incl. de twee praatmodus-tests van AC12 en de twee
  launch-tests in `home_screen_test.dart` voor beide takken van AC10)

### Bevinding uit ronde 1 opnieuw gecontroleerd → verholpen
`MainActivity.onNewIntent` roept nu `setIntent(intent)` aan, en wel *vóór*
`LaunchSource.from(this, intent)`. Daarmee leest `Activity.getReferrer()` de `EXTRA_REFERRER`
van het nieuwe intent i.p.v. die van de koude start; de logregel mengt geen nieuwe intent-data
meer met een oude referrer. Zelfde patroon als `alarm/AlarmActivity.kt`. Dit is de enige
codewijziging sinds ronde 1.

### E2E opnieuw geverifieerd op preview pr-44
- `POST`/`GET /api/v1/app-launches`: server bepaalt `id` + `at`, nieuwste eerst, `limit=1`
  respecteert, `limit=9999` geeft HTTP 200 (begrensd, geen 400), onbekende bron (`"BANAAN"`)
  en ontbrekende `source` worden `UNKNOWN` zonder 400.
- **AC6 hard bewezen** via `oc logs deploy/robberts-assistent-backend -n robberts-assistent-pr-44
  | grep APP_LAUNCH`: precies één INFO-regel per launch, exact formaat, ontbrekende waarden als
  `null`, lege lijst/map als lege waarde, en een `\n` in een extra-waarde wordt een spatie —
  met `cat -A` geverifieerd dat het regeleinde pas ná `extras=` valt.
- **Web-launch E2E (Playwright, `page.on('request')` + `page.on('pageerror')`)**: het openen van
  de preview stuurt precies één `POST /api/v1/app-launches` met
  `{"source":"UNKNOWN","platform":"web",...}`, zonder JS-fouten → AC13 + de web-aanname bevestigd.
- Auth-gate is op preview niet negatief te testen (`RA_PREVIEW_SKIP_GOOGLE_AUTH`: ook
  `/api/v1/watches` geeft 200 met een bogus token); AC5's 401-gedrag is gedekt door
  `AppLaunchControllerTest` + `AppLaunchControllerAuthTest`.

### Screenshots (`/work/screenshots/`)
- `SF-1706-r2-start-normaal.png` — normale (niet-assistent) start: ongewijzigd startscherm
- `SF-1706-r2-assistent-tab-ongewijzigd.png` — Assistent-tab, geen automatisch geopend gesprek
- `SF-1706-r2-nieuw-gesprek-chatmodus.png` — nieuw gesprek opent in **Chatten**, niet in Praten
  (visuele bevestiging van de `startInVoiceMode = false`-default, AC11)

### Niet uitgevoerd (met reden)
- `LaunchSourceTest` (AC4) is inhoudelijk nagelopen — dekt alle vier de gevallen van AC2 — maar
  niet uitgevoerd: er is geen Android SDK/`gradlew` in de sandbox. Expliciet toegestaan door de
  story-aanname "Draaien van de Kotlin-unittest".
- De release-APK-build (deel van AC14) is om dezelfde reden niet lokaal draaibaar; die loopt via
  de bestaande apk-build-workflow. De enige Android-wijziging in deze ronde is één regel met de
  standaard `Activity.setIntent(Intent)`-API.
- Conform AC15 is er geen poging gedaan een Assistent-/Gemini-start na te bootsen.
  **Handmatig testen op Robberts telefoon blijft de laatste stap** — alleen daar is te zien wat
  Gemini daadwerkelijk als referrer/extras meestuurt, zodat `ASSISTANT_PACKAGES` bijgesteld kan
  worden.

### Testdata
Drie via `curl` aangemaakte launches op de preview zijn tijdens de run vanzelf opgeruimd door een
ArgoCD-pod-rollover (in-memory opslag); er staat alleen nog de web-launch van de Playwright-run,
die bij de volgende rollover eveneens verdwijnt. Geen persistente data geraakt.

**Oordeel: alle 15 acceptatiecriteria gehaald, vangnet groen → `tested`.**
