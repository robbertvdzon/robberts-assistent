# Technical Spec

Architectuur, stack en codeconventies. Volledig overzicht + modulelijst: root `CLAUDE.md`.

## Stack & versies

- **Backend:** Kotlin, Spring Boot 3.5, **Spring Modulith**, Java 21, Maven. Spring AI
  (`spring-ai-openai` + `spring-ai-client-chat`, handmatige bean-wiring, geen
  auto-configuratie-starter) met **OpenAI gpt-5.5** (vision-capable). `firebase-admin`
  (Firestore + Cloud Storage). JdbcTemplate + Flyway. Package-root `nl.vdzon.robbertsassistent`.
- **Apps:** Flutter (stable), Dart `>=3.0.0 <4.0.0`. `wind/` daarnaast native Kotlin (App
  Actions-trampoline-activities, TTS, notificaties). Web-apps: Flutter web → nginx-container.
- **Android:** applicationId's `nl.vdzon.*` (o.a. `nl.vdzon.groentetuin`, `nl.vdzon.robberts_assistent`);
  gedeelde release-keystore (Google Sign-In hangt aan de SHA-1).

## Architectuur (backend)

- **Spring Modulith**: elke directe subpackage onder `robbertsassistent` is een module;
  `ModulithArchitectureTest` dwingt de grenzen af. Cross-module verwijzingen alleen naar types
  in de base-package van de andere module.
- **Koppelingen achter ports met fallback.** Een selector-`@Configuration` kiest per koppeling
  de echte implementatie (als de secret gezet is) of de fallback (stub/in-memory/mock).
  Voorbeelden: `Notifier` (Telegram/Logging), `ReminderRepository` + `ConversationRepository`
  (Firestore/in-memory), `PhotoStorage` (Firebase Storage/in-memory), `CalendarClient` +
  `DocsClient` (Google/stub). Firebase-init-fouten worden afgevangen → fallback, geen crashloop.
- **Pluggable SPI-patroon** voor uitbreidbare lijsten: een module definieert een interface
  (`CouplingProbe`, `NightlyCheck`, `BriefingSectionProvider`), elke leverende module registreert
  een `@Component`-implementatie ervan, en Spring injecteert automatisch de volledige
  `List<...>` in de verzamelende service. Nieuwe leverancier toevoegen ⇒ nieuwe `@Component`,
  geen wijziging in de verzamelende service.
- **Briefingstatuscontract:** `BriefingSection` heeft achterwaarts compatibele optionele velden
  `status` (`GOED`, `LET_OP`, `NIET`) en `tileLabel`; ontbrekende velden in bestaande
  Firestore-cache-JSON deserialiseren als `null`. Kite en strandfietsen kiezen uit hun ene
  `AssessmentResult` groen boven geel boven rood (bij gelijkstand het vroegste dagdeel). Afval
  leidt tekst en tegel af uit dezelfde eenmalig opgehaalde zevendagenplanning, met daggrenzen in
  `Europe/Amsterdam`. Bronfouten leveren bewust beide tegelvelden als `null`.
- **Weer-ophaalstrategie:** `weather/ForecastFetcher` (internal) is de gedeelde ophaallaag van
  `OpenMeteoWeatherClient` en `OpenMeteoWindForecastClient`; die clients parsen en kappen alleen
  nog af op `hours`. De fetcher combineert een TTL-cache van 10 minuten op de ruwe respons-body
  (thread-veilig via double-checked locking rond een `@Volatile`-veld, zelfde stijl als de
  basiskaart-cache in `OsmCoastMapImageBuilder`), retry (max. 3 pogingen, pauzes 500/2000 ms, bij
  IO-fout, 5xx en 429; overige 4xx zijn fataal; per-poging-timeout 10 s) en in-memory
  last-known-good tot 12 uur oud. Bij een last-known-good-teruggave zijn `WeatherForecast`/
  `WindForecast`'s optionele velden `fetchedAt` (`Instant?`) en `stale` (`Boolean`) gezet; een
  verse call en een TTL-cachehit zijn niet `stale`. Per definitief mislukte aanroep gaat er precies
  één `logger.warn` uit. `now`, `sleeper` en `retryDelaysMs` zijn constructorparameters met
  productiedefault (zelfde patroon als `httpClient`, geen `Clock`-bean), zodat tests tijd en
  pauzes sturen zonder wachttijd; HTTP wordt gefaket met een eigen `java.net.http.HttpClient`-
  testdouble (`FakeHttpClient`), zonder extra testdependency. `SlotAssessmentProvider` geeft het
  oudste verouderde ophaalmoment door in `AssessmentResult.Ok.staleSince`; de secties weerkaart,
  kiten en strandfietsen tonen dan `(gegevens van HH:MM)` in `Europe/Amsterdam`. Bewust niet
  gedaan: een "recent mislukt"-cache, zodat elke sectie bij een echte storing opnieuw de volledige
  retry-reeks doorloopt (functioneel correct, wel trager).
- **Config:** `AppSecrets` + `AppSecretsLoader` lezen `secrets.env` (lokaal) of env-vars (prod,
  uit de Sealed Secret via `envFrom`). Ontbrekende secret ⇒ fallback (zie `effectiveMockAi`).
- **AI-agent:** twee `ChatClient`-beans in `assistant/ai/AiConfig` — `assistantChatClient`
  (`@Primary`, met alle `@Tool`-beans) en `gardenChatClient` (`@Qualifier`, vision, eigen
  system-prompt). `MockChatModel` in preview/tests (deterministisch, geen kosten/netwerk).
  Andere modules kunnen een eigen lichte, tool-loze `ChatClient`-bean toevoegen die de gedeelde
  `ChatModel` hergebruikt (bv. `briefing.BriefingAiConfig.weekTasksChatClient` en
  `watches.WatchAiConfig.watchChatClient`), zodat mock/echt automatisch
  meeloopt met `AppSecrets.effectiveMockAi` zonder eigen schakelaar.
  Een per-request instructie komt erbij als extra `SystemMessage` in de berichtenlijst, niet als
  request-level `.system(...)` — dat laatste vervángt de `defaultSystem(...)` van de client. Zo
  werkt sinds SF-1711 de optionele multipart-param `voice` op `POST /api/v1/assistant/chat`
  (`defaultValue = "false"` ⇒ bestaande clients ongewijzigd): bij `true` gaat `VOICE_SYSTEM_PROMPT`
  (`assistant/ai/AiConfig.kt`) naast de bestaande `SYSTEM_PROMPT` mee voor een kort
  spreektaal-antwoord.
- **Data:** notities, reminders, langdurige zoekopdrachten (`watches`) + chat-conversaties
  (incl. `archived`-veld) + gebruiker-breed geheugen (`assistant-memory`) + gelogde app-starts
  (`app-launches`, 30 dagen bewaard) in Firestore (named
  database `robberts-assistent`, project `tuinbewatering`); moestuin-foto's in Firebase Storage
  (`tuinbewatering.firebasestorage.app`, map `moestuin/`).
- **Watches:** `GET`/`POST /api/v1/watches`, `PUT`/`DELETE /api/v1/watches/{id}` en
  `POST /api/v1/watches/run-now` zijn geauthenticeerd. `WatchStoreConfig` kiest de Firestore-collectie `watches` of
  `InMemoryWatchRepository`. Bewerken valideert dezelfde invoervelden als aanmaken (titel, URL,
  zoekinstructie, pushvoorkeur — sinds SF-1697 geen `frequency` meer, in request noch response) en
  reset de opdracht naar actief en `NOG_NIET_GECONTROLEERD`, zodat de gewijzigde criteria opnieuw
  gecontroleerd worden. `WatchRunner` gebruikt één fixed-delay poller
  (`ra.watches.poll-interval-ms`, standaard 300000 ms); de pure
  `WatchSchedule.isDue` rekent in `Europe/Amsterdam` en is sinds SF-1697 frequentie-loos:
  `active` **en** uur in `8..22` **en** (`lastCheckedAt == null` of ≥ 1 uur verstreken), zonder
  werkdag-/weekendonderscheid. `FirestoreWatchRepository` schrijft `frequency` niet meer weg en
  leest documenten mét en zónder dat oude veld foutloos in (geen migratie). Naast `poll(now)` heeft
  `WatchRunner` een `runNow(now)` die de `isDue`-filtering overslaat en alle
  opdrachten met `active == true` via dezelfde private `check(watch, now)`
  controleert (inactieve — waaronder alles op `GEVONDEN` — worden overgeslagen);
  `POST /api/v1/watches/run-now` draait die run synchroon af en geeft de
  bijgewerkte lijst terug. Er is bewust geen server-side lock tegen gelijktijdige
  runs: de `compareAndSet` hieronder dekt overlap met de poller af, dubbele runs
  voorkomen is een UI-verantwoordelijkheid. `JdkWatchPageFetcher`
  accepteert alleen succesvolle HTTP-responses, begrenst de HTML op 1.000.000
  bytes en de geëxtraheerde tekst op 20.000 tekens. `watchChatClient` heeft geen
  tools en `WatchAssessmentParser` accepteert alleen `GEVONDEN` of `NIET
  GEVONDEN` plus een omschrijving. Netwerk-, HTTP-, AI- en parsefouten worden
  als `ONBEKEND` opgeslagen en bij een volgende geplande beurt opnieuw
  geprobeerd. De chat-kant loopt via `assistant/ai/WatchTools` (`listWatches`,
  `createWatch`, `updateWatch` bovenop `WatchService`, geregistreerd in
  `AiConfig.defaultTools(...)`); die tools hergebruiken de bestaande service en
  validatie, vangen `WatchValidationException` af als Nederlandse tekst en
  zoeken een opdracht via het begin van het id (`startsWith`, zelfde patroon als
  `ReminderTools.deleteReminder`). Niet-meegegeven update-velden zijn optionele
  `@ToolParam(required = false)`-parameters met neutrale defaults en worden
  overgenomen van de bestaande watch, omdat `WatchService.update` alle velden
  verwacht. Verwijderen is bewust niet als tool ontsloten.
- **App-start-logging (`applaunch`):** `POST /api/v1/app-launches` en
  `GET /api/v1/app-launches?limit=50` zijn geauthenticeerd (`authService.requireAuthorization`,
  net als `WatchesController`). `AppLaunchStoreConfig` kiest de Firestore-collectie `app-launches`
  of `InMemoryAppLaunchRepository` — hetzelfde selector-patroon als `WatchStoreConfig`, met
  `runCatching` rond de Firestore-init. `AppLaunchService` bepaalt `id` (UUID) en `at`
  (`Instant.now()`, injecteerbaar als `now`-lambda voor tests) server-side, begrenst `limit` met
  `coerceIn(1, MAX_LIMIT = 200)` en ruimt bij elke opslag alles ouder dan `RETENTION` (30 dagen) op
  als best effort (`runCatching` + `logger.warn`; een falende opschoning mag het opslaan niet laten
  mislukken). Een onbekende/ontbrekende `source` wordt via `runCatching { valueOf(...) }` stil
  `UNKNOWN` in plaats van een 400, en een leeg `platform` wordt `onbekend`. De uitleesweg is bewust
  de slf4j-log: precies één INFO-regel per opgeslagen launch, op één regel, waarbij `null` letterlijk
  als `null` wordt gelogd, lege lijsten/maps als lege waarde en newlines door spaties worden
  vervangen zodat `grep APP_LAUNCH` altijd blijft werken. De module gebruikt alleen `auth` en
  `firebase`, zodat `ModulithArchitectureTest` groen blijft.
- **Launch-bron (Android/Flutter):** de classificatie zit in
  `robberts_assistent/android/.../LaunchSource.kt` als **pure** functie
  `classify(referrer: String?)` zonder Android-classes, juist zodat die met een gewone JUnit-test
  (`android/app/src/test/…/LaunchSourceTest.kt`, `testImplementation("junit:junit:4.13.2")`) te
  dekken is; de assistent- en launcher-packages staan als constante bovenaan en zijn bedoeld om
  bijgesteld te worden zodra de echte logs bekend zijn. Het verzamelen van intent-gegevens is
  defensief (`runCatching` per extra-key, `toString()`, newlines weg, waarde ≤ 200 tekens, ≤ 50
  keys). `MainActivity` (`singleTop`) bepaalt de `LaunchInfo` in `onCreate` (vóór `super.onCreate`)
  én in `onNewIntent`, en moet daar `setIntent(intent)` aanroepen vóór het uitlezen — `getReferrer()`
  leest eerst `EXTRA_REFERRER` uit `getIntent()`, en Flutters `FlutterActivity.onNewIntent` zet dat
  intent zelf niet (zelfde patroon als `alarm/AlarmActivity.kt`). Ontsluiting via MethodChannel
  `nl.vdzon.robberts_assistent/launch` met pull (`launchInfo`, dekt de koude start) én push
  (`invokeMethod` bij `onNewIntent`). Flutter-kant: `lib/launch_source.dart` biedt de laatste launch
  aan via een `ValueNotifier` in de stijl van `FcmService.deepLinkTarget` (na afhandeling weer
  `null`), leest het channel alleen als `!kIsWeb` en meldt op web één launch met `platform = "web"`
  / `source = UNKNOWN`. `LaunchSourceTest` draait niet in CI (er is geen Gradle-wrapper in
  `robberts_assistent/android` en de APK-workflow draait alleen `flutter test`) — bewust
  geaccepteerd. Restbeperking: bij een warme start *zonder* `EXTRA_REFERRER` valt `getReferrer()`
  terug op de `mReferrer` van de koude start; dat is niet in code op te lossen en blijft een
  observatiepunt voor de handmatige telefoontest.
- **Multiline chat-invoerveld (Flutter):** de chat-`TextField` in `_chatControls()` van
  `robberts_assistent/lib/assistant_screen.dart` gebruikt `minLines: 1` + `maxLines: 5` (bewust
  geen `maxLines: null` + `ConstrainedBox`; zelfde gedrag inclusief intern scrollen, minder code)
  met `TextInputType.multiline` en `TextInputAction.newline`. `onSubmitted` is van dit veld
  verwijderd — met `TextInputAction.newline` zou het toch niet meer voor Enter afgaan — en er is
  bewust geen sneltoets-alternatief (Ctrl/Shift+Enter); de send-knop is de enige verstuurweg. De
  omliggende `Row` staat op `CrossAxisAlignment.end`. `_sendTyped()` doet alleen `trim()`, dus
  interne newlines blijven behouden; het backend-contract (multipart-veld `message`) is ongewijzigd.
  De widget-test leest `minLines`/`maxLines`/`keyboardType`/`textInputAction`/`onSubmitted` af via
  `tester.widget<TextField>(...)` in plaats van via een pixel-/hoogtemeting, omdat de gerenderde
  hoogte van het thema afhangt.
- **Afbeelding plakken in het chat-invoerveld (Flutter):** hetzelfde `TextField` heeft sinds SF-1767
  een `ContentInsertionConfiguration` met `allowedMimeTypes: _pasteableMimeTypes` (top-level
  constante `['image/png', 'image/jpeg']`, gedeeld met de callback) en `onContentInserted:
  _onContentInserted`. Die callback zet de `KeyboardInsertedContent` om naar
  `XFile.fromData(bytes, path: …, name: …, mimeType: content.mimeType)` en voedt 'm aan de bestaande
  `_attach(List<XFile>)`-flow — bewust geen tweede bijlagenroute, zodat `_pending`,
  `_pendingPreview()` en `_send(...)` ongewijzigd blijven en het API-contract (multipart `photos` op
  `POST /api/v1/assistant/chat`) niet wijzigt. De bestandsnaam (`geplakt-<epoch-ms>.png`/`.jpg`,
  afgeleid van de mimetype) gaat zowel als `name` als als `path` mee: `cross_file`'s
  io-implementatie negeert `name` en leidt de naam uit `path` af, dus met alleen `name` zou de naam
  op Android leeg zijn. `onContentInserted` is synchroon terwijl `_attach` async is — het Future
  loopt bewust door via `unawaited(...)`. Ontbrekende/lege `data` of een andere mimetype: geen
  bijlage, geen exception, één `SnackBar` via `ScaffoldMessenger.maybeOf` +
  `hideCurrentSnackBar()`. Geen nieuwe dependency (`cross_file` is al transitief aanwezig via
  `image_picker`) en geen klembord-package, dus geen "Plakken uit klembord"-actie in
  `_showAttachSheet()`; `ContentInsertionConfiguration` is een IME-route, dus web/desktop
  (Ctrl+V-afbeelding) valt hier buiten. Geplakte bytes worden niet gecomprimeerd (camera/galerij
  gebruiken wél `imageQuality: 70`). De widget-tests roepen
  `contentInsertionConfiguration!.onContentInserted(...)` rechtstreeks aan met geldige
  1×1-PNG-bytes (ongeldige bytes laten `Image.memory` in de pending-strook falen); écht plakken via
  Gboard is alleen op een fysiek toestel te verifiëren. Restpunt (niet-blokkerend): `contentType`
  krijgt `content.mimeType` ongewijzigd mee terwijl de filter lowercased vergelijkt.
- **Doorluister-lus praatmodus (Flutter):** `robberts_assistent/lib/assistant_screen.dart` draait in
  `_Mode.voice` de lus luisteren → versturen → uitspreken → opnieuw luisteren. Het uitspreken is
  afwachtbaar (`awaitSpeakCompletion(true)`), de spraakherkenning wordt expliciet gestopt vóór het
  spreken en er wordt niet geluisterd tijdens versturen/wachten. Stoppen gebeurt bij de stop-/
  mic-knop, mode-wissel, `dispose`, spraakfout, chat-API-fout en na `_maxSilentRounds` (= 2)
  opeenvolgende rondes zonder verstane spraak; elke stop hoogt `_loopGeneration` op zodat een
  antwoord dat pas dáárna klaar is met uitspreken de lus niet alsnog herstart, naast de bestaande
  `_listening`-guard tegen dubbele sessies. Om dat in widget-tests te kunnen aansturen zijn er twee
  smalle seams — `SpeechRecognizer`/`VoiceSpeaker`, met de plugin-implementaties als
  productiedefault en injecteerbaar via de optionele `AssistantScreen`-parameters `speech`/`speaker`
  (stijl `_FakeApiClient`); geen nieuwe dependency. Alleen de spraakroute zet `voice: true` op
  `ApiClient.assistantChat(...)`. Echte microfoon/TTS wordt bewust niet nagebootst: getest is
  uitsluitend de callback-/lus-logica, eindverificatie gebeurt handmatig op toestel.
- **Rich-text-notitie met platte-tekst-opslag (Flutter, `notities/`):** sinds SF-1801 gebruikt
  `notities/lib/notes_editor_screen.dart` een `QuillEditor` + `QuillController`
  (`flutter_quill ^11.5.1`, géén `flutter_quill_extensions`;
  `FlutterQuillLocalizations.localizationsDelegates`/`supportedLocales` in `MaterialApp` en in de
  widget-tests) in plaats van een kale `TextField`, met een **zelfgebouwde** opmaakbalk (geen
  `QuillSimpleToolbar`) van precies vijf `IconButton`s met de tooltips `Vet`, `Cursief`,
  `Onderstreept`, `Opsomming`, `Opmaak wissen` — die tooltips zijn de afgesproken testhaak, naast
  `ValueKey('opmaakbalk')` op de rij. De opslaglaag blijft één platte markdown-string: laden gaat
  via `markdownToDelta()`, opslaan altijd via `deltaToMarkdown(document.toDelta())` naar de
  ongewijzigde `api.saveNotes(...)`, dus er belandt nooit Delta-JSON in `/api/v1/notes` (embeds
  worden overgeslagen) en `assistant/ai/NotesTools`/`briefing/WeekTasksSectionProvider` blijven
  werken. De conversie zit in `notities/lib/markdown_delta.dart` zonder
  Flutter-widget-afhankelijkheden (alleen `package:flutter_quill/quill_delta.dart`), dus puur als
  unittest te draaien. Mapping en niets anders: `**vet**`, `*cursief*`, `<u>onderstreept</u>`,
  bullet = regel met exact `- `; al het overige is platte tekst, niets wordt ge-escaped en lege
  regels blijven staan. Parsen gebeurt per regel; een `*`-reeks is atomair
  (`_starRunLength()`/`_findStarRun()`, opener van lengte 1/2/3 alleen gesloten door precies die
  lengte, 4+ nooit een marker), een niet-afgesloten marker of een leeg paar blijft letterlijk, en
  schrijven gebeurt genest in de vaste volgorde underline → bold → italic met één markerpaar over
  aaneengesloten segmenten met hetzelfde kenmerk. Quill's afsluitende newline wordt afgeknipt,
  zodat `deltaToMarkdown(markdownToDelta(s)) == s` byte-identiek geldt voor opmaakloze notities.
  Autosave wordt gevoed door `document.changes` (abonnement pas ná het initiële laden, anders
  triggert laden een save); `dispose()` haalt de tekst op vóór `_controller.dispose()`. Bewust
  geaccepteerd: bold/italic buiten underline in handmatig aangeleverde markdown wordt bij de
  eerste cyclus naar de canonieke nestvolgorde genormaliseerd (stabiel vanaf cyclus 2), en een
  geplakte embed verdwijnt stil bij het opslaan.
- **Lokale editorlettergrootte (Flutter, `notities/`, SF-1809):**
  `notes_editor_screen.dart` leest vóór `api.getNotes()` de bestaande `shared_preferences`-key
  `notes_editor_font_size`. De toegestane gehele waarden zijn 12 t/m 28 in stappen van 2, met 16
  als default; een ontbrekende, niet-gehele of ongeldige tussenwaarde valt terug op 16 en een
  waarde buiten bereik wordt begrensd op 12/28. Twee `IconButton`s met zichtbare tekst A−/A+ en
  tooltips `Lettergrootte verkleinen`/`Lettergrootte vergroten` schrijven de voorkeur zonder erop
  te wachten (`unawaited(setInt(...))`) en zijn op hun grens disabled. De hele zelfgebouwde balk is
  horizontaal scrollbaar. `QuillEditorConfig.customStyles` past alleen de fontgrootte van
  `DefaultStyles.paragraph`, `lists` en `leading` aan; Quill voegt die gedeeltelijke overrides met
  de overige defaults samen, waardoor inline vet/cursief/onderstreept en lijsttekst hun opmaak
  behouden en de aparte bullet-leading even groot schaalt. Er worden geen Delta-attributen
  gewijzigd: A−/A+ triggert dus geen `document.changes`, dirty-state, autosave of API-aanroep en
  handmatig opslaan blijft byte-identieke markdown leveren. AppBar, balk, statusmeldingen en de
  alleen-lezen versieweergave gebruiken hun bestaande grootte.
- **Notitie-versiegeschiedenis (backend `notes`, SF-1808):** `NoteVersion(id, text, savedAt:
  Instant)` in de Firestore-subcollectie `notes/note/versions` (velden `text` + `savedAt`,
  auto-id; `InMemoryNotesRepository` gebruikt UUID's en `asReversed()` vóór het stabiele sorteren,
  zodat twee saves binnen dezelfde milliseconde tóch nieuwste-eerst blijven). `NotesRepository`
  kreeg `addVersion`/`latestVersions(limit)`/`version(id)`/`allVersions()`/`deleteVersion(id)`, in
  beide implementaties volledig ingevuld zodat tests zonder Firebase draaien. `NotesService.update`
  schrijft eerst de huidige tekst weg (ongewijzigde returnwaarde) en bewaart daarna best-effort
  (`runCatching` + `logger.warn`; een falende versie-opslag mag de `PUT` niet laten mislukken) een
  versie, tenzij de tekst identiek is aan de laatste bestaande versie — vergelijking dus met de
  laatste *versie*, niet met de huidige notitietekst, zodat A → B → A drie versies oplevert.
  `now` is een constructorparameter met productiedefault (`Instant::now`), geen `Clock`-bean.
  Endpoints: `GET /api/v1/notes/versions` en `GET /api/v1/notes/versions/{id}` (`savedAt` als
  ISO-8601 UTC, 404 via `ResponseStatusException`), beide met hetzelfde
  `authService.requireAuthorization(...)` als de bestaande notes-endpoints. Opruimen zit in de
  **pure** `object NoteVersionCleanup.idsToDelete(versions, now)` (geen klok, geen Firestore, dus
  zonder wachttijd te testen; retentie 7 dagen, groepering per kalenderdag in `Europe/Amsterdam`,
  bij een gelijk tijdstip beslist het id zodat de uitkomst deterministisch is); de
  `NoteVersionCleanupScheduler` (`@Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam")`,
  stijl van `briefing/BriefingCacheScheduler`) doet alleen ophalen → functie → verwijderen en logt
  één INFO-regel met het aantal verwijderde versies. Het opruimen leest bewust álle versies
  (zonder de 200-limiet van het endpoint) en verwijdert per id — geen batch/paginatie, acceptabel
  omdat de taak dagelijks draait. Geen migratie nodig: bestaande installaties hebben simpelweg nog
  geen versies. `NotesTools`, `WeekTasksSectionProvider` en `GET`/`PUT /api/v1/notes` zijn
  ongewijzigd.
- **Undo/redo + versies terugzetten (Flutter, `notities/`, SF-1808):** de undo/redo-knoppen leunen
  volledig op de historie die `QuillController` zelf bijhoudt (`undo()`/`redo()`,
  `hasUndo`/`hasRedo`); er is geen eigen historie-stack en geen sneltoets. Terugzetten van een oude
  versie gebeurt met `controller.replaceText(0, document.length, markdownToDelta(...), ...)` — een
  bewerking op het bestaande document i.p.v. `_controller.document = …`, want een nieuw `Document`
  wist de undo-historie en vereist een nieuw `changes`-abonnement; bewust de volledige lengte
  inclusief Quills afsluitende newline, die de blok-opmaak (bullet) van de laatste regel draagt.
  De Nederlandse datum/tijd-weergave is een eigen mini-helper (`formatVersionMoment()` in
  `note_versions_screen.dart`, vaste dag-/maandafkortingen + `vandaag`/`gisteren` op
  `savedAt.toLocal()`) i.p.v. `intl` of een timezone-package — **geen nieuwe dependency**. De
  versielijst is een eigen `Navigator.push`-route, niet een dialoog, zodat de alleen-lezen weergave
  er als tweede route bovenop past. Testhaak: de opmaakbalk hertekent op controller-notificaties,
  dus een widget-test die rechtstreeks `document` wijzigt heeft twee `pump()`s nodig voordat de
  undo-knop enabled is.
- **Gelijktijdigheid watches:** een pollresultaat wordt met
  `WatchRepository.compareAndSet(expected, updated)` alleen opgeslagen wanneer
  de actuele opdracht nog exact gelijk is aan het gelezen snapshot. In-memory
  gebeurt dit atomisch met `ConcurrentHashMap.replace`; Firestore gebruikt een
  transactie. Ook een gebruikerswijziging gebruikt deze CAS. Daardoor kunnen overlappende pollers
  geen gevonden status terugdraaien of dubbele push versturen en kan een lopende controle een
  tussentijds gewijzigde of verwijderde opdracht niet overschrijven/herstellen. Alleen de winnende
  overgang naar `GEVONDEN` mag de optionele FCM-push met `data.type=watch`
  versturen.

## Web-apps

- `Dockerfile`: Flutter web build (met `--dart-define` voor `GOOGLE_CLIENT_ID`, `API_BASE_URL`,
  `SKIP_GOOGLE_AUTH`) → nginx-unprivileged. `nginx.conf` proxyt `/api/` same-origin naar
  `robberts-assistent-backend:80` (geen CORS) en serveert de Flutter-app.
- Google-login: web via de GIS-knop (`google_sign_in_web`), mobiel via `signIn()`. `ApiClient`
  ruilt het Google-ID-token in voor een sessie-token en stuurt dat als Bearer mee.
- `robberts_assistent` heeft een expliciet centraal light-`ColorScheme` en `CardTheme` in
  `lib/main.dart`. `AppLogo`, `SectionHeading` en `StatusPill` zijn de gedeelde bouwstenen voor
  respectievelijk het SVG-beeldmerk, sectiekoppen en toegankelijke statusweergave (kleur plus
  woord). `flutter_svg` rendert `assets/icon/logo.svg`; `flutter_launcher_icons` genereert de
  Android- en webiconen uit het overeenkomstige PNG.
- De Vandaag-tab parseert onbekende briefingstatussen als `null` en rendert maximaal drie geldige
  statussecties met een niet-leeg `tileLabel` als even brede tegels. De exacte bolkleuren zijn
  `#0CA30C`, `#FAB219` en `#D03B3B`; tegel-semantiek bevat statuswoord, titel, label en een
  uitvoerbare tikactie. Eén geselecteerde tegel toont zijn bestaande sectiekaart direct onder de
  rij en wordt zo nodig met `Scrollable.ensureVisible` in beeld gebracht; getegelde secties worden
  uit de permanente kaartenlijst gefilterd. Geldige statussecties na de limiet blijven gewone
  kaarten.
- Pushnavigatie gebruikt `FcmService.deepLinkTarget`: `briefing` gaat naar de hoofdbestemming
  Vandaag en sluit eerst routes boven de app-shell; `watch` pusht een verse
  `WatchesScreen`-route. Daarmee zijn deeplinks niet gekoppeld aan veranderlijke tab-indexen.

## Codeconventies

- Nederlands in commentaar/docs/commits/UI.
- Nieuwe skill = nieuwe module (subpackage) + evt. een `@Tool` in `assistant/ai/`, geregistreerd
  in `AiConfig`. Nieuwe koppeling = port + fallback + `AppSecrets`-key + secrets-documentatie.
- Match de bestaande stijl per module (JdbcTemplate voor Postgres, port-selector voor koppelingen).

## Bekende valkuilen

- **Modulith-grenzen:** een verweesde `.class` in `target/` (na hernoemen/verwijderen van een
  class) kan een dubbele bean geven — draai `mvn clean test` bij vreemde bean-conflicten.
- **Firebase-credentials in prod:** gebruik `RA_FIREBASE_CREDENTIALS_JSON` (inhoud), niet
  `_FILE` (pad bestaat niet in de container).
- **Sealed Secrets:** nieuwe keys mergen met `kubeseal --merge-into`; bij een verlopen
  `cluster-cert.pem` ontsleutelt de controller de secret niet en blijven koppelingen op fallback
  (cert verversen met `kubeseal --fetch-cert`).
- **Google-vision** weigert ongeldige/te kleine afbeeldingen (`image_parse_error`) — test met
  echte foto's.
