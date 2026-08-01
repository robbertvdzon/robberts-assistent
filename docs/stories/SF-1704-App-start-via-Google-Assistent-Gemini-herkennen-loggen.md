# SF-1704 - App-start via Google Assistent/Gemini herkennen, loggen in de backend en direct in praatmodus openen

## Story

App-start via Google Assistent/Gemini herkennen, loggen in de backend en direct in praatmodus openen

<!-- refined-by-factory -->

## Samenvatting

Als Robbert de app start met "Hé Google, start Robberts assistent app", opent de app meteen een
nieuw gesprek in praatmodus en luistert hij al, zodat Robbert direct zijn vraag kan stellen. Start
hij de app op de gewone manier, dan verandert er niets.

Daarnaast legt elke app-start een regeltje vast in de backend: waar de start vandaan kwam en wat er
precies meegestuurd werd. Dat is nodig omdat nog niet zeker is wat Google Assistent/Gemini meestuurt;
met die logs kan de herkenning later scherper gezet worden.

Deze story is bewust niet automatisch te testen: wat Gemini meestuurt is alleen op een echte telefoon
te zien. Handmatig testen op de telefoon is dus de laatste stap.

## Scope

### Deel 1 — Launch-bron bepalen (Android native)

In `robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/`:

- Nieuwe `LaunchSource.kt` met een data class `LaunchInfo(source, referrer, action, categories, extras)`
  en een enum `LaunchSourceType { ASSISTANT, LAUNCHER, OTHER, UNKNOWN }`.
- De classificatie is een **pure functie** die alleen de referrer-string als parameter krijgt
  (`classify(referrer: String?): LaunchSourceType`), zonder Android-classes, zodat hij los testbaar is.
  Daarnaast een functie die uit een `Activity` + `Intent` een volledige `LaunchInfo` bouwt.
- `ASSISTANT` als het referrer-package een van deze is (constante lijst bovenaan het bestand, expliciet
  bedoeld om later uit te breiden zodra de echte logs bekend zijn):
  `com.google.android.googlequicksearchbox`, `com.google.android.apps.googleassistant`,
  `com.google.android.apps.bard`, `com.google.android.apps.gemini`.
- `LAUNCHER` als het referrer-package eindigt op `.launcher` of in een korte constante lijst bekende
  launchers staat (o.a. `com.google.android.apps.nexuslauncher`, `com.android.launcher3`).
- Referrer ontbreekt/leeg → `UNKNOWN`; alle overige packages → `OTHER`.
- Het verzamelen van intent-gegevens is defensief: alle extra-keys met hun waarde als string
  (`toString()`), nooit crashen op onbekende/rare extra-typen (`runCatching` per key), en
  waarden worden afgekapt (max ~200 tekens per waarde, max ~50 keys) en van newlines ontdaan.
- `MainActivity.kt`: bepaal de `LaunchInfo` in `onCreate` én in `onNewIntent` (de activity is al
  `launchMode="singleTop"`) en ontsluit die via een nieuw MethodChannel
  `nl.vdzon.robberts_assistent/launch`, in dezelfde stijl als de bestaande updater-/alarm-channels:
  - **pull**: Flutter roept methode `launchInfo` aan en krijgt de laatst bekende `LaunchInfo` als map
    (dekt de koude start, waarbij Flutter nog niet luisterde);
  - **push**: bij `onNewIntent` roept native `invokeMethod("launchInfo", <map>)` aan op datzelfde channel.

### Deel 2 — Loggen in de backend

Nieuwe Modulith-module `robberts-assistent-backend/src/main/kotlin/nl/vdzon/robbertsassistent/applaunch/`:

- `AppLaunch.kt`: data class met `id: String`, `at: Instant`, `source: AppLaunchSource`
  (enum `ASSISTANT/LAUNCHER/OTHER/UNKNOWN`), `referrer: String?`, `action: String?`,
  `categories: List<String>`, `extras: Map<String, String>`, `platform: String`, `appVersion: String?`.
- `AppLaunchRepository` (poort) + `FirestoreAppLaunchRepository` + `InMemoryAppLaunchRepository` +
  `AppLaunchStoreConfig`, exact volgens het patroon van `watches/WatchRepository.kt`,
  `FirestoreWatchRepository.kt` en `WatchStoreConfig.kt` (in-memory fallback zonder Firebase,
  `runCatching` rond de Firestore-init).
- `AppLaunchService`:
  - opslaan (server bepaalt `id` (UUID) en `at` (`Instant.now()`); clienttijd wordt niet vertrouwd);
  - de laatste N teruggeven (default 50), nieuwste eerst;
  - bij het opslaan alles ouder dan 30 dagen verwijderen; een falende opschoning mag het opslaan
    niet laten mislukken (best effort, `runCatching` + `logger.warn`).
- `AppLaunchController`: `POST /api/v1/app-launches` (body = de launch-gegevens) en
  `GET /api/v1/app-launches?limit=50`, beide achter `authService.requireAuthorization(authorization)`
  net als `WatchesController`. Een onbekende/ontbrekende `source` in de body wordt `UNKNOWN`
  (geen 400); `limit` wordt begrensd op maximaal 200.
- Bij elke opgeslagen launch gaat er precies één slf4j-INFO-regel uit, op één regel, in exact dit formaat:
  `APP_LAUNCH source=<SOURCE> platform=<platform> referrer=<referrer> action=<action> categories=<a,b> extras=<k=v;k=v>`
  Dit is de manier waarop de gegevens uitgelezen worden:
  `oc logs deploy/robberts-assistent-backend -n robberts-assistent | grep APP_LAUNCH`
- Tests: `AppLaunchServiceTest` (opslaan, limiet/volgorde nieuwste-eerst, opschonen ouder dan 30 dagen)
  en `AppLaunchControllerTest` (POST + GET, inclusief de auth-gate).

### Deel 3 — Flutter: melden en gedrag

In `robberts_assistent/lib/`:

- Nieuwe `launch_source.dart`: leest de `LaunchInfo` van het MethodChannel (alleen als `!kIsWeb`,
  net als de bestaande self-update-check in `home_screen.dart`), en biedt de laatst ontvangen
  launch aan via een `ValueNotifier`, in de stijl van `FcmService.deepLinkTarget` (na afhandeling
  weer op `null` gezet, zodat hij niet opnieuw afgaat).
- `api_client.dart`: methode om een launch te posten naar `POST /api/v1/app-launches`. Posten gebeurt
  alleen als er een sessie-token is; zonder token wordt de launch stil overgeslagen. Een mislukte
  post wordt gelogd/genegeerd — nooit crashen, nooit de UI blokkeren.
- `home_screen.dart`: luister op de launch-notifier zoals nu op `FcmService.deepLinkTarget`
  (`addListener` in `initState` + directe aanroep voor de koude start, `removeListener` in `dispose`).
  - Elke ontvangen launch wordt gepost naar de backend (fire-and-forget).
  - Bij `source == ASSISTANT`: selecteer de Assistent-tab (index 1, `ConversationsScreen`) én push
    meteen `AssistantScreen(api: ..., startInVoiceMode: true, autoStartListening: true)` zónder
    `conversationId` (dus een nieuw gesprek).
  - Bij elke andere bron verandert er niets aan het huidige gedrag.
- `assistant_screen.dart`: twee nieuwe optionele parameters op `AssistantScreen`:
  `startInVoiceMode` (default `false`) en `autoStartListening` (default `false`).
  - Bij `startInVoiceMode` start `_mode` op `_Mode.voice` in plaats van `_Mode.chat`.
  - Bij `autoStartListening` wordt `_startListening()` pas aangeroepen aan het einde van `_initSpeech()`,
    en alleen als `_speechAvailable` waar is en de widget nog `mounted` is — dus niet blind in `initState`.
  - Is spraak niet beschikbaar of de microfoonpermissie geweigerd (`_speech.initialize()` geeft `false`),
    dan toont het scherm gewoon de bestaande foutmelding en de mic-knop: nooit vastlopen of crashen.
- Widget-tests in `robberts_assistent/test/`: `AssistantScreen` opent in praatmodus als
  `startInVoiceMode` `true` is, en blijft in chatmodus als hij `false` is.

### Buiten scope

- Elke poging om een Assistent-/Gemini-start na te bootsen in CI, emulator of preview-omgeving.
- Een app-scherm dat de gelogde launches toont (uitlezen gaat via `oc logs … | grep APP_LAUNCH`).
- Het definitief vaststellen van de detectielijst met assistent-packages — die wordt bijgesteld
  zodra de echte logs bekend zijn.
- Wijzigingen aan de `assistant`-backendmodule, de chatflow of bestaande endpoints.

## Acceptance criteria

1. Er is een `LaunchSource.kt` met een pure, van Android-classes onafhankelijke classificatiefunctie
   die op basis van een referrer-string `ASSISTANT`, `LAUNCHER`, `OTHER` of `UNKNOWN` teruggeeft,
   met de assistent-packages als duidelijk uitbreidbare constante bovenaan het bestand.
2. Referrer `null`/leeg geeft `UNKNOWN`; een package dat eindigt op `.launcher` of in de bekende-
   launcherlijst staat geeft `LAUNCHER`; de vier genoemde Google-packages geven `ASSISTANT`;
   alle overige geven `OTHER`.
3. `MainActivity` bepaalt de `LaunchInfo` zowel in `onCreate` als in `onNewIntent` en ontsluit die
   via MethodChannel `nl.vdzon.robberts_assistent/launch`, met zowel een pull (`launchInfo` opvragen)
   als een push bij `onNewIntent`. Het verzamelen van extras is defensief en crasht niet op rare extras.
4. Er is een JUnit-test op de classificatielogica (nieuwe `android/app/src/test/...`-sourceset met een
   JUnit-dependency) die de gevallen uit criterium 2 dekt.
5. `POST /api/v1/app-launches` slaat een launch op en `GET /api/v1/app-launches?limit=50` geeft de
   laatste launches terug, nieuwste eerst; beide vereisen autorisatie en geven zonder geldig token
   dezelfde fout als de andere controllers.
6. Bij elke opgeslagen launch verschijnt precies één INFO-regel in het exacte formaat
   `APP_LAUNCH source=… platform=… referrer=… action=… categories=… extras=…`, op één regel,
   ook als de bron `UNKNOWN` is en ook als velden ontbreken.
7. Launches ouder dan 30 dagen zijn na een nieuwe opslag verwijderd; als het opschonen faalt,
   slaagt het opslaan alsnog.
8. `AppLaunchServiceTest` (opslaan, limiet/volgorde, 30-dagen-opschoning) en `AppLaunchControllerTest`
   (POST, GET, auth) zijn aanwezig en groen.
9. In de app wordt elke ontvangen launch gepost zodra er een sessie-token is; zonder token wordt hij
   stil overgeslagen en blijft de app normaal werken.
10. Bij een launch met `source == ASSISTANT` staat de Assistent-tab geselecteerd en is er meteen een
    nieuw gesprek (`AssistantScreen` zonder `conversationId`) in praatmodus geopend dat probeert te
    luisteren; bij elke andere bron is het gedrag ongewijzigd.
11. `AssistantScreen` heeft `startInVoiceMode` en `autoStartListening` (beide default `false`);
    `_startListening()` wordt alleen aangeroepen na afloop van `_initSpeech()` en alleen als
    spraak beschikbaar is. Is spraak niet beschikbaar, dan toont het scherm de bestaande foutmelding
    zonder te crashen.
12. Widget-tests tonen aan dat `AssistantScreen` in praatmodus opent bij `startInVoiceMode: true`
    en in chatmodus blijft bij `false`.
13. Alles wat native is, wordt op web met `kIsWeb` overgeslagen; de web-build breekt niet.
14. `mvn test` (backend), `flutter analyze` + `flutter test` (`robberts_assistent`) en de
    release-APK-build slagen.
15. Verdere verificatie is expliciet niet gewenst: er wordt geen Assistent-/Gemini-start nagebootst
    in CI, emulator of preview. De worklog/samenvatting vermeldt expliciet dat handmatig testen op
    Robberts telefoon de laatste stap is.

## Aannames

- **Weblaunches.** De web-app heeft geen MethodChannel; daar wordt bij het openen van `HomeScreen`
  één launch gepost met `platform = "web"` en `source = UNKNOWN`, zonder referrer/action/categories/extras.
  Zo blijft het `platform`-veld betekenisvol zonder native code op web.
- **Logformaat bij ontbrekende waarden.** Ontbrekende optionele waarden worden als `null` gelogd
  (`referrer=null`), lege lijsten/maps als lege waarde (`categories=`); newlines in waarden worden
  vervangen door een spatie zodat de regel altijd één regel blijft en `grep APP_LAUNCH` werkt.
- **Tijdstip en id.** De backend bepaalt `at` en `id`; de client stuurt ze niet mee. Zo blijft de
  sortering betrouwbaar ongeacht de kloktijd op het toestel.
- **`appVersion`.** De app stuurt de versie mee die al beschikbaar is in de bestaande
  update-/versielogica; kan die niet bepaald worden, dan blijft het veld `null` (geen blokkade).
- **Draaien van de Kotlin-unittest.** De Gradle-unittest wordt toegevoegd als
  `android/app/src/test/...` met een JUnit-dependency. De bestaande APK-workflow draait alleen
  `flutter test`; het toevoegen van een Gradle-teststap aan CI valt buiten deze story. Kan de test
  in de bouwomgeving niet uitgevoerd worden (geen Android SDK), dan is dat acceptabel zolang de
  backend-tests, `flutter test` en de APK-build slagen — conform de expliciete verificatiegrens
  in deze story.
- **Microfoonpermissie.** Er komt geen nieuwe dependency voor permissies bij: `RECORD_AUDIO` staat
  al in de manifest en `speech_to_text.initialize()` vraagt de runtime-permissie zelf; het resultaat
  daarvan is `_speechAvailable`, dat de bestaande foutafhandeling al aanstuurt.
- **Herhaalde starts.** Elke start wordt apart gelogd, ook een tweede start via `onNewIntent`;
  er wordt niet ontdubbeld.
- **Modulith.** `applaunch` is een nieuwe topmodule die alleen `auth` en `firebase` gebruikt,
  zodat `ModulithArchitectureTest` groen blijft.

<!-- test-feedback:start -->
## Test-feedback
## Testresultaat SF-1706

**Vangnet volledig groen:**
- `mvn -o test` → **385 tests, 0 failures, 0 errors, BUILD SUCCESS** (incl. `AppLaunchServiceTest` 8, `AppLaunchControllerTest` 2, `AppLaunchControllerAuthTest` 2, `ModulithArchitectureTest` groen)
- `flutter analyze` → *No issues found* · `flutter test` → **65/65 groen**

**E2E geverifieerd op preview `robberts-assistent-pr-44`** (bevestigd `head.sha = 40f6ab2` = branch-HEAD):
- POST/GET `/api/v1/app-launches`: server bepaalt id+tijd, nieuwste eerst, `limit` begrensd, onbekende/ontbrekende `source` → `UNKNOWN` zonder 400
- **AC6 hard bewezen** via `oc logs … | grep APP_LAUNCH`: precies één INFO-regel per launch, exact formaat, ontbrekende waarden als `null`, newlines → spaties (regel blijft greppable)
- **Web-launch E2E (Playwright):** app-start stuurt precies één `POST` met `platform=web`, geen JS-fouten, regel verschijnt in de pod-log → AC13 + web-aanname bevestigd
- Screenshots in `/work/screenshots/` (startscherm ongewijzigd bij niet-assistent-start; praatmodus-scherm)

### Bevinding → terug naar developer

**`MainActivity.onNewIntent` roept `setIntent(intent)` niet aan** — bij een warme start wordt een door Gemini meegestuurde referrer nooit gelezen.

- **Repro:** app draait in de achtergrond → "Hé Google, start Robberts assistent app" → `singleTop` levert de start in `onNewIntent`.
- **Verwacht:** `LaunchInfo.referrer` komt uit het nieuwe intent → `ASSISTANT` → praatmodus opent, en de `APP_LAUNCH`-regel bevat de echte referrer.
- **Werkelijk:** `Activity.getReferrer()` leest eerst `getIntent()` (`EXTRA_REFERRER`/`EXTRA_REFERRER_NAME`) en daarna pas de bij `attach()` vastgelegde `mReferrer`. Zonder `setIntent()` blijft `getIntent()` het *oorspronkelijke* intent — Flutters `FlutterActivity.onNewIntent` zet 'm ook niet (geverifieerd met `javap` op `flutter.jar`). `action`/`categories`/`extras` komen wél uit het nieuwe intent, dus de logregel mengt nieuwe intent-data met een oude referrer.
- **Fix:** één regel `setIntent(intent)` in `MainActivity.onNewIntent`. `AlarmActivity.kt` in dit repo doet dit al wél — het patroon staat er dus al.

De developer noteerde dit in de worklog als "aandachtspunt, geen blocker", maar de fixbare helft (de `EXTRA_REFERRER` van het *nieuwe* intent wordt weggegooid) is een concreet defect dat juist de diagnostische data corrumpeert waarvoor deze story bestaat — de vervolgtuning van `ASSISTANT_PACKAGES` leunt op die logregels. De `mReferrer`-fallback bij een warme start zonder `EXTRA_REFERRER` is wél een platformbeperking en blijft terecht een handmatige-telefoontest-observatie.

Alleen `docs/stories/worklog/SF-1704-worklog.md` is aangepast (testnotities); niets gecommit.

```json
{"agent_tips_update":[{"category":"tester","key":"robberts-assistent-oc-cli-works-on-preview-namespace","content":"De tester-sandbox heeft een werkende `oc`/`kubectl` met toegang tot de preview-namespace: `oc get pods -n robberts-assistent-pr-<n>` en `oc logs deploy/robberts-assistent-backend -n robberts-assistent-pr-<n> | grep <PATROON>` werken. Onmisbaar om logregel-acceptatiecriteria (exact formaat, precies één regel per actie) live te bewijzen i.p.v. alleen op de unittest te vertrouwen. Let op: een ArgoCD-rollover tijdens je run vervangt de pod, waardoor de in-memory preview-opslag leegloopt (en je testdata vanzelf opruimt) - `oc get pods` toont dan een tweede pod in ImagePullBackOff/Running."},{"category":"tester","key":"android-onnewintent-getreferrer-stale-without-setintent","content":"Bij Android-launch-detectie in robberts_assistent: `Activity.getReferrer()` leest eerst `getIntent()` (EXTRA_REFERRER/EXTRA_REFERRER_NAME) en pas daarna de bij attach() vastgelegde mReferrer. In `onNewIntent` blijft `getIntent()` het OORSPRONKELIJKE intent tenzij de activity zelf `setIntent(intent)` aanroept; Flutters FlutterActivity.onNewIntent doet dat NIET (te verifieren met `unzip /opt/flutter/bin/cache/artifacts/engine/android-arm64/flutter.jar` + `javap -c io/flutter/embedding/android/FlutterActivity.class`). Elke singleTop-activity die referrer/intent-data in onNewIntent leest heeft dus `setIntent(intent)` nodig - AlarmActivity.kt in dit repo doet het goed, gebruik die als referentie."},{"category":"tester","key":"flutter-web-preview-network-intercept-proves-launch-post","content":"Om te bewijzen dat de Flutter-web-app zelf een backend-call doet (niet alleen jouw curl), gebruik Playwright `page.on('request', ...)` met een filter op het endpoint plus `page.on('pageerror')`. Zo toonde SF-1706 dat het openen van de preview precies EEN POST /api/v1/app-launches met platform=web stuurt, zonder JS-fouten - sterker bewijs dan een grep op main.dart.js."}]}
```

{"phase":"test-rejected"}
<!-- test-feedback:end -->

## Eindsamenvatting

Ik heb `.task.md`, het worklog en de agent-rondes gelezen. Hieronder de eindsamenvatting.

---

# SF-1704 — App-start via Google Assistent/Gemini herkennen, loggen en direct in praatmodus openen

## Wat is er gebouwd

**1. Herkennen waar de app vandaan gestart is (Android)**
Nieuwe `LaunchSource.kt` in `robberts_assistent/android/.../`: bepaalt per app-start of die van de **Assistent**, de **launcher**, iets **anders** of een **onbekende** bron komt, op basis van het referrer-package. De vier bekende Google-packages (googlequicksearchbox, googleassistant, bard, gemini) en de bekende launchers staan als duidelijk uitbreidbare constante bovenaan. De classificatie is een pure functie zonder Android-afhankelijkheden, met een JUnit-test die alle vier de uitkomsten dekt. `MainActivity` bepaalt de startgegevens bij zowel een koude start (`onCreate`) als een warme start (`onNewIntent`) en geeft ze via een nieuw MethodChannel door aan de app. Het verzamelen van de meegestuurde intent-gegevens is bewust defensief: nooit crashen op rare extra's, waarden afgekapt en van newlines ontdaan.

**2. Loggen in de backend**
Nieuwe module `applaunch` (zelfde patroon als `watches`: poort + Firestore + in-memory fallback). `POST /api/v1/app-launches` slaat een start op — de **server** bepaalt tijd en id, de clientklok wordt niet vertrouwd — en `GET /api/v1/app-launches?limit=50` geeft de laatste starts terug, nieuwste eerst. Beide achter dezelfde autorisatie als de rest. Launches ouder dan 30 dagen worden bij elke opslag opgeruimd; faalt dat, dan slaagt het opslaan alsnog. Per opgeslagen start gaat er precies één regel de log in, uitleesbaar met:
`oc logs deploy/robberts-assistent-backend -n robberts-assistent | grep APP_LAUNCH`

**3. Gedrag in de app**
Elke app-start wordt fire-and-forget naar de backend gepost (zonder sessie-token stil overgeslagen — nooit een blokkade of foutmelding voor Robbert). Komt de start van de **Assistent**, dan springt de app naar de Assistent-tab en opent meteen een **nieuw gesprek in praatmodus dat al luistert**. Bij elke andere start verandert er niets. Is spraak niet beschikbaar of de microfoon geweigerd, dan verschijnt gewoon de bestaande melding met de mic-knop.

## Gemaakte keuzes

- **Web telt mee, zonder native code**: de web-app meldt één start met `platform=web` en bron `UNKNOWN`, zodat het platform-veld betekenisvol blijft.
- **Onbekende bron is geen fout**: een onbekende of ontbrekende bron wordt `UNKNOWN` in plaats van een 400 — juist bij een nog onbekende Gemini-referrer wil je de regel wél in de log hebben.
- **De detectielijst is expliciet voorlopig**: zodra uit de echte logs blijkt welk package Gemini gebruikt, is dat één regel bijwerken in `LaunchSource.kt`.
- **Bug uit de testronde verholpen**: bij een warme start werd de referrer van het nieuwe intent weggegooid, waardoor een Gemini-start als "gewone start" kon binnenkomen én de logregel oude en nieuwe gegevens mengde. Opgelost met `setIntent(intent)` in `onNewIntent` (zelfde patroon dat de alarm-activity al gebruikte).

## Wat is getest

- Backend `mvn test`: **385 tests groen**, inclusief de nieuwe tests op opslaan, volgorde/limiet, 30-dagen-opschoning, POST/GET en de auth-gate; modulegrenzen blijven bewaakt.
- App: `flutter analyze` schoon, `flutter test` **65/65 groen**, inclusief tests dat een assistent-start praatmodus opent en een gewone start niets verandert.
- **Live op de preview-omgeving**: POST/GET werken end-to-end, en het exacte logformaat is met echte pod-logs bewezen (precies één regel per start, altijd greppable). Ook de web-start is met een echte browsersessie geverifieerd.
- Screenshots van het ongewijzigde startscherm en van het praatmodus-scherm zijn bij de testronde vastgelegd.

## Bewust niet gedaan

- **Een Assistent-/Gemini-start nabootsen in CI, emulator of preview** — dat was expliciet buiten scope; wat Gemini precies meestuurt is alleen op een echt toestel zichtbaar.
- **Een app-scherm met de gelogde starts** — uitlezen gaat via de pod-logs.
- De Kotlin-unittest en de release-APK-build zijn niet in de bouwcontainer gedraaid (geen Android SDK); de APK-build loopt via de bestaande CI-workflow. Dit was vooraf als acceptabel afgesproken.
- Twee cosmetische reviewer-suggesties zijn bewust niet doorgevoerd (één ervan zou een acceptatiecriterium breken); ze staan genoteerd in het worklog.

## Laatste stap voor Robbert

**Handmatig testen op de telefoon is de afsluitende stap.** Start de app één keer normaal en één keer met "Hé Google, start Robberts assistent app" — zowel koud (app weggeswipet) als warm (app draait nog op de achtergrond) — en kijk daarna met `grep APP_LAUNCH` in de logs wat er is meegestuurd. Blijkt het Gemini-package nog niet in de lijst te staan, dan is dat een eenregelige vervolgwijziging. Let op: bij een warme start zonder meegestuurde referrer valt Android terug op de referrer van de vorige start — dat is een platformbeperking, geen fout in de app.
