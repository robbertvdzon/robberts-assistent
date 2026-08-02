# Robbert's Assistent

Persoonlijke assistent van Robbert: één Kotlin/Spring-Boot-backend op OpenShift met
modulaire **skills**, en meerdere Flutter/Android-apps als kanalen ernaartoe. Dit bestand
is het instappunt voor een AI-agent — lees het eerst, en daarna de specifieke docs onderaan.

Taal in code-commentaar, docs, commits en UI: **Nederlands**.

---

## 1. Wat is dit?

Een backend die via skills allerlei taken doet (notities, wind/kite-check, reminders met
alarm, moestuin-foto-chat, dagelijkse Morgen-briefing en langdurige
websitezoekopdrachten) en die door apps + een AI-agent aangesproken wordt. De
AI-agent (OpenAI via Spring AI, met `@Tool`-functies) is tegelijk de belangrijkste
**test-harness**: bijna elke agent-skill is als tool aan de agent gehangen, dus je
test een hele keten met één zin ("zet een reminder over 10 min", "wanneer moet ik
naar de tandarts", "telegram me mijn vakanties"). Websitezoekopdrachten worden
bewust door hun eigen tool-loze AI-client beoordeeld.

Ontwerp-uitgangspunt: OpenShift blijft **stateless**; state leeft extern (Postgres/Neon,
Firestore, Firebase Storage). Elke externe koppeling zit achter een **port** met een
**stub/in-memory fallback**, zodat de app en tests groen draaien zónder secrets, en een
koppeling live gaat door enkel de secret te zetten (zelfde patroon als `effectiveMockAi`).

---

## 2. Repo-structuur

Mono-repo. GitHub Actions-workflows triggeren op hun eigen subfolder.

```
robberts-assistent/
├── robberts-assistent-backend/   ← Kotlin/Spring Boot/Spring Modulith backend
├── robberts_assistent/           ← Flutter app: briefings, chat, reminders en zoekopdrachten (web + APK)
├── groentetuin/                  ← Flutter app: moestuin-AI-chat (web op moestuin.vdzonsoftware.nl + APK)
├── notities/                     ← Flutter app: auto-save, rich text + lokale lettergrootte (APK)
├── wind/                         ← Flutter/native PoC: "Hey Google" App Actions → wind-antwoord (APK)
├── deploy/                       ← kustomize-manifests (base + preview-overlay) + sealed secret
├── docs/
│   ├── factory/                  ← factory-agent-context (specs, dev, deploy, agents/)
│   ├── stories/                  ← per-story worklogs (SF-xxxx)
│   ├── foundation-couplings.md   ← ontwerp + implementatieplan van de koppelingen-laag
│   ├── setup-guide-details.md    ← console-setup (Firebase/Google/Cloudflare) met concrete waarden
│   └── robbert-todo.md           ← status + handmatige stappen (Robbert)
├── .github/workflows/            ← per component: image-build + apk-build + backend-verify
└── CLAUDE.md                     ← dit bestand
```

---

## 3. Tech stack

- **Backend:** Kotlin, Spring Boot 3.5, **Spring Modulith**, Java 21, Maven. Spring AI
  (`spring-ai-openai`, handmatige bean-wiring) met **OpenAI gpt-5.5** (vision-capable).
  firebase-admin (Firestore + Cloud Storage). JdbcTemplate + Flyway.
- **Apps:** Flutter (stable), Dart `>=3.0.0 <4.0.0`. `wind/` heeft native Kotlin (App
  Actions-trampoline-activities). Web-apps draaien als nginx-container (Flutter web build).
- **Data:** **Firestore** (notities, reminders, alarms, langdurige zoekopdrachten,
  chat-conversaties, FCM-tokens — named database `robberts-assistent` in
  Google-project `tuinbewatering`); **Firebase Storage**
  (moestuin-foto's in map `moestuin/`, assistent-gespreksfoto's in map `assistent-chat/`, bucket
  `tuinbewatering.firebasestorage.app`). Geen SQL-database meer (Neon opgezegd).
- **Auth:** Google-login → eigen HMAC-sessie-token (allowlist `robbert@vdzon.com`).
- **Push:** Telegram (uitgaand) + FCM (`push`-module + app-kant, beide gebouwd —
  reminders/alarms, gevonden websitezoekopdrachten en de dagelijkse
  Morgen-briefing gebruiken 'm).
- **Deploy:** OpenShift single-node thuis, **GitOps via ArgoCD**, images op `ghcr.io`,
  externe toegang via **Cloudflare Tunnel**, secrets via **Sealed Secrets**.

---

## 4. Backend — Spring Modulith modules

Package-root `nl.vdzon.robbertsassistent`. Elke directe subpackage = een module; grenzen
worden afgedwongen door `ModulithArchitectureTest`. Koppelingen zitten achter een port met
fallback (zie §5).

| Module | Doel |
|---|---|
| `config` | `AppSecrets` + `AppSecretsLoader` (leest `secrets.env` lokaal, env-vars in prod). |
| `auth` | Google ID-token verifiëren → HMAC-sessie-token; `requireAuthorization()` gate. |
| `health` | `/healthz` (open) + `/api/v1/ping` (auth, testendpoint). |
| `notes` | Eén notitie-string in Firestore (document `notes/note`). Sinds SF-1808 daarnaast **versiegeschiedenis**: `NoteVersion(id, text, savedAt)` in de subcollectie `notes/note/versions` (velden `text` + `savedAt`, auto-id; in-memory fallback met UUID's), `NotesRepository` uitgebreid met `addVersion`/`latestVersions(limit)`/`version(id)`/`allVersions()`/`deleteVersion(id)`. `NotesService.update(text)` schrijft eerst de huidige tekst weg (ongewijzigd gedrag/returnwaarde) en bewaart daarna best-effort (`runCatching` + `logger.warn`, een fout laat de `PUT` niet falen) een versie — behalve als de tekst identiek is aan de **meest recente bestaande versie** (dus A → B → A levert wél drie versies op). Omdat dit in `NotesService` zit, levert ook een wijziging via de chat (`assistant/ai/NotesTools`) een versie op; `NotesTools` en `briefing/WeekTasksSectionProvider` zijn ongewijzigd. `NotesController` kreeg twee endpoints naast de bestaande (zelfde `authService.requireAuthorization(...)`): `GET /api/v1/notes/versions` (`{"versions":[{"id","savedAt"}]}`, nieuwste eerst, max 200, géén tekst) en `GET /api/v1/notes/versions/{id}` (`{"id","savedAt","text"}`, 404 bij een onbekend id); `savedAt` is ISO-8601 UTC. `NoteVersionCleanupScheduler` (`@Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam")`, hele run in `runCatching`, één INFO-regel met het aantal verwijderde versies) ruimt op via de pure `NoteVersionCleanup.idsToDelete(versions, now)`: alles binnen 7 dagen blijft, daarvóór blijft per kalenderdag (Europe/Amsterdam) alleen de laatste versie. |
| `summary` | Oorspronkelijke dagelijkse samenvatting (`GET /api/v1/summary`, incl. nightly-check-resultaten). Sinds de Morgen-briefing (SF-1163, zie `briefing` hieronder) is dit endpoint niet meer aangesloten op een app-scherm — `robberts_assistent` haalt "Vandaag" nu bij `briefing`. Nog niet opgeruimd/vervangen; behouden voor mogelijk hergebruik/opruiming in een latere story. |
| `briefing` | Dagelijkse **Morgen-briefing**: `BriefingSectionProvider`-SPI (net als `CouplingProbe`/`NightlyCheck` injecteert Spring automatisch `List<BriefingSectionProvider>`; een nieuwe sectie toevoegen raakt `BriefingService` niet) en `BriefingController` (`GET /api/v1/briefing`, auth, plus `POST /api/v1/briefing/agenda-reminder` voor de één-tap-reminder-actie). Sinds SF-1200 wordt de briefing **gecachet**: `BriefingCacheRepository` (Firestore/in-memory, zelfde patroon als `assistant.MemoryRepository` — precies één document, geen historie; gekozen door `BriefingStoreConfig`) bewaart de laatst opgebouwde `BriefingResponse` (incl. `updatedAt`, ISO-8601). Sinds SF-1275 zijn de "Upcoming"- en "Health check"-tabs onafhankelijk cachebaar: `BriefingService` houdt twee gescheiden caches aan (`@Qualifier("upcomingBriefingCache")` voor alle secties behalve `system-status`, `@Qualifier("healthBriefingCache")` uitsluitend voor `SystemStatusSectionProvider`), elk als los Firestore-document (`FirestoreBriefingCacheRepository`'s `documentId`: `current` resp. `health`, zie `BriefingStoreConfig`) met een eigen `updatedAt`. `BriefingService.currentUpcoming()`/`currentHealth()` leveren de eigen cache, of bouwen zonder te cachen live op als er nog geen cache is; `refreshUpcoming()`/`refreshHealth()` bouwen altijd live op en overschrijven alléén hun eigen cache — een refresh vanuit de ene tab raakt de andere niet. `BriefingController` ontsluit dit als `GET /api/v1/briefing` + `POST /api/v1/briefing/refresh` (Upcoming) en `GET /api/v1/briefing/health` + `POST /api/v1/briefing/health/refresh` (Health check), zodat er geen dubbele opbouwlogica is. Acht secties (oplopend `order`): `WeatherMapSectionProvider` (`order = -10`, dus bovenaan: sinds SF-1220/SF-1221 één statisch kaartbeeld van de kust IJmuiden–Egmond met daarop twee windpijlen voor morgen-Ochtend (07:00) en -Avond (19:00) — verticaal gestapeld aan de linkerkant van de kaart i.p.v. horizontaal verspreid — in verschillende kleuren (oranje/blauw), elk met windsnelheidslabel (kn) en een écht getekend weer-icoon (`java.awt`-vormen: zon/wolk/regen, geen `Font`/emoji meer — servers hebben vaak geen emoji-font), plus een legenda die kleur aan dagdeel koppelt en, sinds SF-1220/SF-1221, onderin de kaart een dag-breed (niet per-dagdeel) weersymbool en de hoog-/laagwatertijden van die dag (IJmuiden, via `tides.TideClient.forecast(...)`) als getekende tekst in een kader dat begrensd is op de kaartbreedte (`CoastMapImageBuilder.drawDaySummary()`; tekst wraait over meerdere regels i.p.v. het kader buiten het canvas te laten uitsteken — fix voor een bug uit de SF-1220-testronde), opgebouwd door `CoastMapImageBuilder.build(List<WindMapSlot>, dayWeatherCode, tideExtremes)` (één aanroep met beide dagdelen i.p.v. per-dagdeel) — `OsmCoastMapImageBuilder` haalt OpenStreetMap-tegels op met JDK-`java.awt`/`ImageIO`, geen betaalde kaarten-API of nieuwe dependency — uit `WindForecastClient` + `WeatherClient`; sinds SF-1296 haalt `OsmCoastMapImageBuilder` de (nooit veranderende) basiskaart-tegels nog maar maximaal één keer op: een in-memory cache, backed door de nieuwe `BaseMapStorage`-poort (Firebase Storage/in-memory, `BriefingStoreConfig`, zelfde patroon als `WeatherMapStorage`) zodat de cache een pod-herstart overleeft, en `drawOverlay()` tekent bij elke `build()`-aanroep op een verse kopie van die gecachete basiskaart in plaats van erop zelf — alleen de overlay wisselt per refresh (zie §9); het PNG gaat via `WeatherMapStorage` (Firebase Storage/in-memory, zelfde patroon als `assistant.PhotoStorage`, vaste sleutel `morgen`, dekt beide dagdelen) naar `GET /api/v1/briefing/weather-map/{slot}`, de sectie levert precies één `BriefingItem` met `imageUrl`; faalt de wind-/weervoorspelling, of ontbreekt data voor een van beide dagdelen, dan levert de sectie een foutmelding zonder de briefing te laten crashen (faalt alleen de getijvoorspelling, dan blijft de kaart wel opgebouwd, zonder getijtijden onderin)), `KiteSectionProvider` (`order = 0`, kite-kans voor morgen: per dagdeel `<label>: <emoji> <wind> kn (richting)`, aanlandige wind via `weather.WindForecastClient`, werkdag/feestdag/vakantie-onderscheid, 🟢/🟡/🔴), `BeachCycleSectionProvider` (`order = 5`, sinds SF-1192 een eigen kaart i.p.v. samengevoegd met kiten: per dagdeel een bolletje MET onderbouwing — wind (kn + richting), regen (mm of droog/nat) en getij (sinds SF-1220 alleen nog de nabijheid, `dichtbij laagwater`/`niet dichtbij laagwater` — de laagwatertijd zelf is verhuisd naar de weerkaart-sectie) — zodat het oordeel navolgbaar is), beide gebouwd op de gedeelde, niet-Spring `SlotAssessmentProvider` (in `KiteSectionProvider.kt`: dagdeel-/werkdag-/vakantielogica + `assessKite`/`assessBeachCycle`-beoordeling op basis van `WindForecastClient`, `WeatherClient` en laagwater via `tides`, zodat beide secties dezelfde netwerkcalls hergebruiken i.p.v. dupliceren), `AgendaSectionProvider` (afspraken komende 7 dagen, alle agenda's via `CalendarClient.eventsInRange`, reminder-status per afspraak — deterministische tijd-heuristiek i.p.v. een AI-call, zie klasse-KDoc — met `BriefingAction` om een ontbrekende reminder ~1u vooraf aan te maken), `WasteSectionProvider` (`order = 15`, sinds SF-1297 tussen agenda en weektaken: welke afvalbak(ken) de komende 7 dagen (vandaag t/m +6) buiten moeten, per ophaalmoment `dd-MM: <type>` uit de bestaande `waste.WasteClient.upcomingPickups()` — geen nieuwe koppeling, geen AI-call, deterministisch uit `WastePickup`-data; lege 7-dagen-lijst of een gezet `WasteSchedule.error` degradeert stil naar een neutrale foutmelding zonder de briefing te laten crashen (zelfde `runCatching`-patroon als `WeekTasksSectionProvider`); `shortSummary()` geeft alleen bij een ophaalmoment morgen "Zet vanavond de \<bak(ken)\> buiten" terug (meerdere types op één dag samengevoegd), anders `null` zodat de 18:00-push de sectie overslaat), `WeekTasksSectionProvider` (AI-samenvatting van reminders + notitie via een eigen `weekTasksChatClient`, `briefing.BriefingAiConfig`, stil-falende fallback), `GardenPlaceholderSectionProvider` (dummy-regel, zelfde stijl als `SummaryService`), `SystemStatusSectionProvider` (`order = 40`, systeem-checkrapport: bundelt zonnepanelen via `zonneplan.ZonneplanClient` (Zonneplan via Home Assistant), Time Machine-backups via `openshift.OpenShiftClient`'s `nodeMetrics.timeMachine` (echte sparsebundle-grootte/laatste-schrijfmoment i.p.v. de eerdere dummy-placeholder, zie `docs/nightly-checks.md`), OpenShift-gezondheid via `openshift.OpenShiftClient`, robotmaaier via `automower.AutomowerClient`, en Software Factory via `softwarefactory.SoftwareFactoryClient` tot ruwe per-check data (`buildChecks()` → `CheckData(heading, content)`); een eigen `systemStatusChatClient`, `briefing.BriefingAiConfig`, bepaalt per check "aandacht nodig" — geen hardcoded drempel in code — en levert de rapporttekst; stil-falende fallback bij een AI-fout of falende onderliggende client (`runCatching` per check), `shortSummary()` alleen niet-`null` als minstens één check aandacht nodig heeft. Sinds SF-1267/SF-1268 levert `section()` daarnaast de vijf ruwe `CheckData`-regels ongewijzigd als `BriefingSection.items` (`BriefingItem(text = content, heading = heading)`, gevuld ook als de AI-call faalt) — dit voedt de app-kant "Health check"-tab (zie §6) met de niet-AI-samengevatte per-check tekst, zonder de AI-beoordeling/`shortSummary()`/18:00-push-logica te wijzigen). Sinds SF-1275 filtert `softwareFactoryCheckData()` de Software Factory-check bovendien op relevantie: alleen stories met `error != null`, of met een gezette `phase` (`phase != null`) die nog niet gemerged zijn (`merged == false`), worden getoond — gemergede stories en stories zonder fase (nog niet gestart/gerefined) vervuilen de check niet meer; blijft na filteren niets over, dan toont de check "geen lopende of error-stories." i.p.v. een lege of ongefilterde opsomming. `shortSummary()` voor de 18:00-push zit bij `KiteSectionProvider`, `WasteSectionProvider` (sinds SF-1297) en `SystemStatusSectionProvider` (strandfietsen levert `null`, dus draagt niet bij aan de push). `Holidays`: algoritmische NL-feestdagberekening (Meeus/Jones/Butcher-Paasformule + afgeleiden), geen externe koppeling of hardcoded jaarlijkse lijst. `BriefingScheduler`: `@Scheduled(cron = "0 0 18 * * *", zone = "Europe/Amsterdam")` bouwt een korte pushtekst uit elke sectie's optionele `shortSummary()` en verstuurt via `PushService.sendToAll` (`data["type"] = "briefing"` voor de app-deep-link) — de systeemstatus-sectie draagt hieraan alleen bij als er aandacht nodig is, en blijft functioneel ongewijzigd door SF-1275 (bouwt los van beide caches op via de providers-lijst). `BriefingScheduler` (18:00-push) en `BriefingCacheScheduler` (sinds SF-1275 **uurlijks**, `@Scheduled(cron = "0 0 * * * *")`, was daarvoor dagelijks 17:30 — ververst nu zowel de Upcoming- als de Health check-cache, elk in een eigen `runCatching` zodat een falende refresh van de ene de andere niet blokkeert) zijn losse `@Scheduled`-jobs die elk hun eigen ding doen (push versus cache) — de 18:00-job bouwt zelf nog een keer op via `BriefingService`s providers en cachet niets. |
| `assistant` | Chat-assistent met persistente **gesprekken**: multi-turn, foto's (vision), zelf-verzonnen titel; conversaties in Firestore (`assistant-conversations`, `Conversation`/`FirestoreConversationRepository`, in-memory fallback), foto's via `PhotoStorage`/`FirebaseStoragePhotoStorage` (zelfde patroon als `gardenchat`). Gesprekken zijn te **archiveren/de-archiveren** (`archived`-veld, reversibel) en te **verwijderen** (hard delete incl. best-effort foto-opruiming); `ConversationRepository.listAll()` filtert/pagineert (`includeArchived`, `limit`, `offset`, gesorteerd op `updatedAt` descending). Daarnaast een gebruiker-breed, automatisch bijgewerkt **geheugen**: `MemoryRepository` (`current()`/`update(text)`, Firestore-collectie `assistant-memory` met één tekst-document, in-memory fallback) — één vrije-tekst-string in plaats van losse items. Na elke chat-beurt herschrijft een losse, stil falende AI-aanroep (`memoryChatClient`) de volledige geheugen-tekst op basis van de huidige tekst + de laatste uitwisseling; die tekst gaat als contextprefix mee in elke volgende chat-aanroep. Onder `RA_MOCK_AI` wordt de geheugen-update overgeslagen (deterministisch). `assistant/ai/`: `AiConfig` (ChatClients + model-keuze, incl. `memoryChatClient`), tools (`NotesTools`, `WindTools`, `WeatherTools`, `TideTools`, `AirQualityTools`, `NewsTools`, `WasteTools`, `AutomowerTools`, `StravaTools`, `SoftwareFactoryTools`, `OpenShiftTools`, `ReminderTools`, `CalendarTools`, `DocsTools`, `WatchTools`), `MockChatModel`. `WatchTools` (SF-1595) geeft de chat toegang tot de langdurige zoekopdrachten van `watches`: `listWatches()`, `createWatch(...)` en `updateWatch(...)` bovenop `watches.WatchService` — verwijderen kan bewust niet via de chat. Sinds SF-1711 kent `POST /api/v1/assistant/chat` de optionele multipart-parameter `voice` (boolean, `defaultValue = "false"`): staat die aan, dan voegt `AssistantService.chat(..., voice = true)` één extra `SystemMessage(VOICE_SYSTEM_PROMPT)` (`assistant/ai/AiConfig.kt`) aan de berichtenlijst toe met de spreektaal-instructie voor een voorgelezen antwoord — bewust géén request-level `.system(...)`, want dat zou de `defaultSystem(...)`/`SYSTEM_PROMPT` van `assistantChatClient` vervángen i.p.v. aanvullen. Zonder de vlag is de prompt exact als voorheen, dus bestaande clients (o.a. de `wind`-app) blijven ongewijzigd werken. |
| `reminders` | Reminder-model + repository-port (Firestore/in-memory), REST-controller, `@Scheduled ReminderScheduler` (due → `Notifier`). |
| `watches` | Langdurige zoekopdrachten: geauthenticeerde `GET`/`POST /api/v1/watches`, `PUT`/`DELETE /api/v1/watches/{id}` en (sinds SF-1553) `POST /api/v1/watches/run-now` (synchrone handmatige run over alle actieve opdrachten via `WatchRunner.runNow(now)`, dat de `isDue`-filtering overslaat maar dezelfde private `check(...)` hergebruikt), Firestore-collectie `watches` met in-memory fallback en één configureerbare fixed-delay-poller (`ra.watches.poll-interval-ms`, standaard 300000 ms). Een wijziging reset de opdracht naar actief en `NOG_NIET_GECONTROLEERD`, zodat ook een eerder gevonden opdracht opnieuw wordt beoordeeld. `WatchSchedule.isDue` kent sinds SF-1697 geen frequentiekeuze meer: een opdracht is aan de beurt als 'ie actief is, het lokale uur (Europe/Amsterdam) in `8..22` ligt (dus 08:00 t/m 22:59, ook in het weekend) en er ≥ 1 uur is verstreken sinds `lastCheckedAt` (of er nog nooit is gecontroleerd). `JdkWatchPageFetcher` zet maximaal 1 MB server-HTML om naar maximaal 20.000 tekens platte tekst; een eigen tool-loze `watchChatClient` beoordeelt de instructie. Fouten worden `ONBEKEND` en later opnieuw geprobeerd; een eerste vondst deactiveert de opdracht en verstuurt optioneel één FCM-push met `data.type=watch`. Pollresultaten en wijzigingen worden via `WatchRepository.compareAndSet` tegen het gelezen snapshot opgeslagen (atomische map-replace/Firestore-transactie), zodat overlappende polls niet dubbel pushen, een vondst geen wijziging overschrijft en een tijdens de controle verwijderde opdracht niet wordt hersteld. |
| `gardenchat` | Moestuin-AI-chat: multipart (tekst + foto's) → vision-AI; conversaties in Firestore, foto's in Firebase Storage; multi-turn. |
| `google` | `CalendarClient` + `DocsClient` (echt via OAuth refresh-token, of stubs) + `GoogleOAuthService`. |
| `weather` | `WeatherClient`: regen-/weersvoorspelling bij de moestuin (Luttik Cie 12, Heemskerk) via Open-Meteo (keyless, altijd echt); `StubWeatherClient` alleen voor tests. Plus `WindForecastClient`/`OpenMeteoWindForecastClient`: gestructureerde windvoorspelling (kn + graden) bij Wijk aan Zee voor de kite-sectie van `briefing` (i.p.v. de platte AI-tekst van `WindTools`); `StubWindForecastClient` voor tests, `WindForecastCouplingProbe` op het Koppelingen-scherm. Sinds SF-1621 delen beide Open-Meteo-clients de interne `ForecastFetcher` (zelfde package, `internal`): TTL-cache van 10 minuten op de ruwe respons-body (thread-veilig via double-checked locking, zelfde stijl als de basiskaart-cache in `OsmCoastMapImageBuilder`), retry van maximaal 3 pogingen met pauzes van 500 ms en 2000 ms bij netwerk-/IO-fout, HTTP 5xx en 429 (bij overige 4xx direct stoppen, per-poging-timeout van 10 s ongewijzigd), en last-known-good in geheugen tot 12 uur oud. `WeatherForecast`/`WindForecast` kregen daarvoor `fetchedAt: Instant? = null` en `stale: Boolean = false`. |
| `tides` | `TideClient`: getijvoorspelling (hoog-/laagwater, waterhoogte) bij IJmuiden buitenhaven via RWS WaterWebservices (keyless, altijd echt); `StubTideClient` alleen voor tests. |
| `airquality` | `AirQualityClient`: luchtkwaliteit/UV-index/pollen bij de moestuin via Open-Meteo Air-Quality-API (keyless, altijd echt); `StubAirQualityClient` alleen voor tests. |
| `news` | `NewsClient`: laatste nieuwskoppen via RSS (standaard NOS Algemeen, keyless, altijd echt); `StubNewsClient` alleen voor tests. |
| `waste` | `WasteClient`: afvalophaalkalender voor Robberts huisadres via de HVC Groep-API (keyless, altijd echt; postcode/huisnummer als constante, geen secret); `StubWasteClient` alleen voor tests. |
| `automower` | `AutomowerClient`: robotmaaier (Husqvarna Automower Connect API, `client_credentials`) — status + starten/parkeren; `RA_HUSQVARNA_APP_KEY`/`_APP_SECRET` bepalen echt vs. `StubAutomowerClient`. |
| `zonneplan` | `ZonneplanClient`: zonnepanelen-gezondheidscheck via de Zonneplan-integratie in Home Assistant (eigen thuis-cluster REST-API + long-lived token) — huidig vermogen (ter info) + dagopbrengst van gisteren (via de HA history-API, want er is geen losse "gisteren"-sensor). `ZonneplanCouplingProbe` meldt "niet ok" bij nagenoeg geen opbrengst gisteren (mogelijke storing). `RA_HOME_ASSISTENT_URL`/`_TOKEN` bepalen echt vs. `StubZonneplanClient`. |
| `strava` | `StravaClient`: Robberts trainingen via Strava API v3 (OAuth refresh-token, zelfde patroon als Google Agenda/Docs, `StravaOAuthService`); `RA_STRAVA_CLIENT_ID`/`_CLIENT_SECRET`/`_REFRESH_TOKEN` bepalen echt vs. `StubStravaClient`. |
| `softwarefactory` | `SoftwareFactoryClient`: bridge naar de software-factory-dashboard-backend (cluster-intern, `http://softwarefactory-dashboard-backend.software-factory`) — stories + actiepunten, via dezelfde REST-API als de software-factory-frontend. Logt zelf in met een Google ID-token (zelfde OAuth-client als de app-login, `googleClientId`, maar een eigen refresh-token) → sessie-token, gecachet. `RA_SOFTWAREFACTORY_CLIENT_SECRET`/`_REFRESH_TOKEN` bepalen echt vs. `StubSoftwareFactoryClient`. |
| `openshift` | `OpenShiftClient`: clustergezondheid (ClusterVersion/ClusterOperators) via de in-cluster ServiceAccount-token van de pod zelf (geen los secret — wel de expliciete vlag `RA_OPENSHIFT_HEALTH_ENABLED`, want de benodigde RBAC bestaat nog niet, zie `docs/nightly-checks.md`); `StubOpenShiftClient` anders. |
| `firebase` | `FirebaseProvider`: gedeelde FirebaseApp → named Firestore-db + Storage-bucket. |
| `notifier` | `Notifier`-port; `TelegramNotifier` (echt) of `LoggingNotifier` (fallback). |
| `push` | `PushService.sendToAll(title, body, data)`: FCM-push naar alle geregistreerde tokens (`FcmTokenStore`), no-op zonder Firebase/tokens; `data` gaat als extra FCM-data-payload mee (bv. `"type" to "briefing"`) zodat de app op basis daarvan kan deep-linken. `PushController` (token-registratie), `FcmCouplingProbe`. |
| `applaunch` | **App-start-logging** (SF-1704, diagnostisch): `AppLaunch` (+ enum `AppLaunchSource` `ASSISTANT/LAUNCHER/OTHER/UNKNOWN`), `AppLaunchRepository`-poort met `FirestoreAppLaunchRepository` (collectie `app-launches`) / `InMemoryAppLaunchRepository` via `AppLaunchStoreConfig` — exact het patroon van `watches/WatchStoreConfig`, in-memory fallback zonder Firebase. `AppLaunchService` bepaalt zélf `id` (UUID) en `at` (`Instant.now()`, clienttijd wordt niet vertrouwd), geeft `recent(limit = 50)` nieuwste eerst terug (begrensd op `MAX_LIMIT = 200`) en ruimt bij elke opslag best-effort alles ouder dan 30 dagen op (`runCatching` + `logger.warn`; een falende opschoning laat het opslaan niet mislukken). Per opgeslagen launch gaat er precies één slf4j-INFO-regel uit, op één regel: `APP_LAUNCH source=… platform=… referrer=… action=… categories=a,b extras=k=v;k=v` (ontbrekend = `null`, leeg = lege waarde, newlines → spatie), uit te lezen met `oc logs deploy/robberts-assistent-backend -n robberts-assistent \| grep APP_LAUNCH` — dát is bewust de enige uitleesweg, er is geen app-scherm voor. `AppLaunchController`: `POST /api/v1/app-launches` + `GET /api/v1/app-launches?limit=50`, beide achter `authService.requireAuthorization(...)`; een onbekende/ontbrekende `source` wordt `UNKNOWN` (geen 400), een leeg `platform` wordt `onbekend`. De module gebruikt alleen `auth` en `firebase`. |
| `couplings` | `CouplingProbe`-SPI + `CouplingsService`: elke module registreert een `@Component` die `CouplingProbe` implementeert (id/naam/omschrijving/configured/mode/test); Spring injecteert automatisch `List<CouplingProbe>`. Voedt het "Koppelingen"-scherm in de app — een nieuwe koppeling toevoegen betekent alleen een nieuwe `CouplingProbe`-implementatie in de eigen module, geen wijziging hier of in de app. |
| `nightlychecks` | `NightlyCheck`-SPI + `NightlyCheckScheduler`/`NightlyChecksService`: net als `couplings`, maar voor achtergrondchecks — elke module registreert een `@Component` met een eigen cron-schema; resultaten (met historie) in Firestore/in-memory. Voedt de "Nachtchecks"-tab in de app + `summary.SummaryService` (dat endpoint heeft sinds de Morgen-briefing (SF-1163) geen app-consument meer, zie de `summary`-rij hieronder). Sinds SF-1164 heeft de Morgen-briefing ook een eigen, live (niet nachtelijk-historisch) systeem-checkrapport, zie de `briefing`-rij (`SystemStatusSectionProvider`) — dat gebruikt bewust een live check i.p.v. `NightlyCheckRepository`-historie. Zie `docs/nightly-checks.md`. |

Sinds SF-1564 heeft `BriefingSection` twee achterwaarts compatibele, optionele velden voor de
Vandaag-tegels: `status` (`GOED`, `LET_OP`, `NIET`) en `tileLabel`. Oude cache-JSON zonder deze
velden blijft deserialiseren. Kite en strandfietsen kiezen uit hetzelfde `AssessmentResult` als
hun detailtekst het gunstigste dagdeel (groen vóór geel vóór rood, bij gelijkstand het vroegste),
zonder extra broncalls. Afval leidt tekst en tegel uit dezelfde planning af en rekent vandaag/
morgen expliciet volgens `Europe/Amsterdam`; een bronfout levert bij alle drie geen tegelvelden.

De gedeelde `ChatModel` voedt meerdere doelgerichte `ChatClient`-beans.
`assistantChatClient` is `@Primary` en heeft alle tools; `gardenChatClient`
ondersteunt vision met een eigen prompt. Andere modules gebruiken gekwalificeerde,
tool-loze clients voor afgebakende beoordelingen, waaronder
`watches.watchChatClient`.

---

## 5. Koppelingen + het stub/fallback-patroon

Elke koppeling is actief zodra de bijbehorende secret gezet is; anders fallback. Config via
`AppSecrets` (keys hieronder). In prod komen deze uit de **Sealed Secret** via `envFrom`. Elke
koppeling registreert ook een `CouplingProbe` (`@Component` in de eigen module, zie §4/`couplings`)
zodat 'ie automatisch op het "Koppelingen"-scherm verschijnt.

| Koppeling | Actief bij | Fallback |
|---|---|---|
| OpenAI (chat + vision) | `RA_OPENAI_API_KEY` | `MockChatModel` (deterministisch) |
| Telegram (Notifier) | `RA_TELEGRAM_BOT_TOKEN` + `RA_TELEGRAM_CHAT_ID` | `LoggingNotifier` |
| Firestore + Storage | `RA_FIREBASE_CREDENTIALS_JSON`(/`_FILE`) + `RA_FIREBASE_PROJECT_ID` (+ `RA_FIREBASE_DATABASE_ID`, `RA_FIREBASE_STORAGE_BUCKET`) | in-memory |
| Google Agenda + Docs | `RA_GOOGLE_OAUTH_CLIENT_ID` + `_SECRET` + `_REFRESH_TOKEN` | `StubCalendarClient` / `StubDocsClient` |
| Google-login | `RA_GOOGLE_CLIENT_ID` (audience) | n.v.t. (vereist) |
| Automower (Husqvarna) | `RA_HUSQVARNA_APP_KEY` + `_APP_SECRET` | `StubAutomowerClient` |
| Zonnepanelen (Zonneplan via Home Assistant) | `RA_HOME_ASSISTENT_URL` + `_TOKEN` | `StubZonneplanClient` |
| Strava | `RA_STRAVA_CLIENT_ID` + `_CLIENT_SECRET` + `_REFRESH_TOKEN` | `StubStravaClient` |
| Software Factory | `RA_SOFTWAREFACTORY_CLIENT_SECRET` + `_REFRESH_TOKEN` | `StubSoftwareFactoryClient` |
| OpenShift-gezondheid | `RA_OPENSHIFT_HEALTH_ENABLED=true` (RBAC nog te zetten, zie `docs/nightly-checks.md`) | `StubOpenShiftClient` |

Firebase-credentials: **`_JSON`** (inhoud, voor prod/sealed) of **`_FILE`** (pad, lokaal). De
selector-configs vangen init-fouten af en vallen terug op in-memory (geen crashloop).
Preview-omgevingen blanken `RA_FIREBASE_PROJECT_ID` → schrijven niet naar de echte Firestore.

---

## 6. Apps

- **`robberts_assistent/`** — bottom-navigatie met 4 tabs: **Vandaag**, **Assistent**,
  **Taken** (het bestaande Herinneringen-scherm) en **Meer**. Vandaag
  (`summary_screen.dart`, was "Morgen"/"Samenvatting"): dagelijkse briefing met alle secties van
  `briefing` (weerkaart, kite/strandfiets, agenda komende 7 dagen met per-afspraak een
  reminder-aanmaak-actie waar nodig, AI-weektakensamenvatting, moestuin-placeholder) **behalve**
  de systeemstatus-sectie, opgehaald via `ApiClient.getBriefing()` (`GET /api/v1/briefing`,
  client-side gefilterd op `section.key != 'system-status'`). Een tik op de dagelijkse
  18:00-FCM-push (`data['type'] == 'briefing'`) sluit eventuele routes boven de app-shell en opent
  deze tab automatisch (`FcmService.deepLinkTarget`, afgehandeld in `home_screen.dart`). Onder
  Meer staat **"Health check"** (`health_check_screen.dart`): toont uitsluitend
  de systeemstatus-sectie, per onderdeel (Zonnepanelen/Backups/OpenShift/Robotmaaier/Software
  Factory) een kop (`BriefingItem.heading`, nieuw optioneel veld) met daaronder de ruwe, niet-AI-
  samengevatte statustekst (`BriefingItem.text`, per regel als bullet) — alles via
  `SelectableText` zodat de tekst kopieerbaar is. De AI-"aandacht nodig"-beoordeling voor de
  18:00-push (`SystemStatusSectionProvider.shortSummary()`) blijft ongewijzigd op de
  AI-samenvatting gebaseerd; alleen deze tab toont de ruwe per-check data. Sinds SF-1274/SF-1275
  is Health check volledig ontkoppeld van Upcoming: `ApiClient.getHealthCheck()`/
  `refreshHealthCheck()` praten met de eigen `GET /api/v1/briefing/health` +
  `POST /api/v1/briefing/health/refresh`-endpoints (i.p.v. de `GET /api/v1/briefing` die Upcoming
  gebruikt), en het scherm toont een eigen "Bijgewerkt om ..."-regel (uit het eigen `updatedAt`)
  met een reload-knop ernaast (spinner tijdens laden, niet opnieuw indrukbaar) — zelfde
  `_refresh()`/`_refreshing`/`_buildHeaderRow()`-patroon als `summary_screen.dart`, maar met een
  eigen cache: verversen op Health check raakt Vandaag's `updatedAt` niet en omgekeerd. Beide
  caches worden daarnaast backend-side elk uur automatisch ververst (zie `briefing`-module hierboven).
  Vandaag toont direct onder dat tijdstip maximaal de eerste drie secties met een bekende status
  en niet-leeg `tileLabel` als even brede tegels in backendvolgorde. De tegel toont icoon, titel,
  label en status met kleur én woord; de volledige combinatie is ook toegankelijk uitgesproken en
  activeerbaar. Eén tegeldetail kan tegelijk openstaan. Getegelde secties verdwijnen uit de vaste
  kaartenlijst; statussecties na de limiet en secties zonder betrouwbare tegeldata blijven daar
  wel staan. Onbekende statuswaarden worden client-side veilig als `null` geparseerd.
  Ook **"Zoekopdrachten"** staat onder Meer en toont de titel en actuele statusomschrijving van alle
  langdurige zoekopdrachten en ondersteunt aanmaken, bewerken en verwijderen, plus (sinds
  SF-1553) een "nu draaien"-knop in de AppBar die alle actieve opdrachten meteen laat controleren. Titel, webadres, zoekinstructie
  en pushvoorkeur worden afzonderlijk ingevoerd (sinds SF-1697 geen frequentiekeuze meer). Een FCM-push met `data.type=watch`
  opent een verse Zoekopdrachten-route; de briefing-deeplink kiest Vandaag en Assistent blijft de
  standaardtab. Daarnaast: chat-assistent met
  persistente, benoemde gesprekken (gesprekkenlijst → chatscherm, foto's via camera/galerij, net
  als `groentetuin`). Het chat-invoerveld is sinds SF-1732 multiline: het start op één regel, groeit
  mee tot vijf regels en scrollt daarna intern; Enter voegt een nieuwe regel toe (versturen gaat
  uitsluitend via de send-knop rechts) en foto- en send-knop blijven onderaan uitgelijnd terwijl het
  veld groeit. Sinds SF-1767 kan in dat veld ook een **afbeelding uit het klembord** worden geplakt
(Android/Gboard, via `ContentInsertionConfiguration` met `image/png`/`image/jpeg`): die komt als
gewone bijlage in de pending-strook — zelfde `_attach(...)`-route als camera/galerij — en gaat mee
bij het versturen; zonder bruikbare afbeelding op het klembord verschijnt alleen een korte melding.
In de webversie blijft plakken tekst-only. In `conversations_screen.dart`: de eerste 10 (niet-gearchiveerde) gesprekken
  direct zichtbaar, oudere onder een uitklapbare "Ouder"-sectie; swipe-links (`flutter_slidable`)
  toont Archiveren/Verwijderen (verwijderen met bevestigingsdialoog); een AppBar-toggle laat
  gearchiveerde gesprekken alsnog zien. Plus Koppelingen-, Nachtchecks- en **Geheugen**-schermen
  (`memory_screen.dart`: één groot bewerkbaar tekstveld met de volledige geheugen-tekst,
  auto-save net als `notities/lib/notes_editor_screen.dart`) bereikbaar via
  `more_screen.dart`. De app gebruikt een centraal rustig licht thema, een teal-wit robotlogo en
  gedeelde statuspillen die betekenis altijd met kleur én tekst tonen; briefing-statusemoji's
  worden client-side vertaald zonder API-wijziging. Sinds SF-1704 meldt de app **elke start** bij de
  backend (`ApiClient.logAppLaunch` → `POST /api/v1/app-launches`, fire-and-forget; zonder
  sessie-token stil overgeslagen) en opent 'ie bij een start via Google Assistent/Gemini meteen een
  nieuw gesprek in praatmodus: `lib/launch_source.dart` (`LaunchSourceService.lastLaunch`,
  `ValueNotifier`-patroon van `FcmService.deepLinkTarget`) leest het native MethodChannel
  `nl.vdzon.robberts_assistent/launch` (alleen als `!kIsWeb`; op web precies één launch met
  `platform = "web"`, `source = UNKNOWN`), en `home_screen.dart` selecteert bij
  `source == ASSISTANT` de Assistent-tab en pusht meteen een `AssistantScreen` zónder
  `conversationId` met `startInVoiceMode: true, autoStartListening: true`. Bij elke andere bron
  verandert er niets. Sinds SF-1711 is praatmodus een **doorlopend gesprek**: luisteren → versturen
  → antwoord uitspreken → automatisch opnieuw luisteren, tot een stopconditie (stop-/mic-knop,
  wisselen naar chatmodus, `dispose`, spraakfout, chat-API-fout, of 2 opeenvolgende rondes zonder
  verstane spraak). De spraakroute stuurt daarbij `voice: true` mee naar
  `ApiClient.assistantChat(...)`, zodat het voorgelezen antwoord kort en in spreektaal is; de
  getypte route stuurt de vlag niet en blijft even uitgebreid als voorheen. Google-login (web:
  GIS-knop, mobiel: `signIn()`). Web op OpenShift
  (`robberts-assistent.vdzonsoftware.nl`) + APK.
- **`groentetuin/`** — moestuin-AI-chat: login → foto's maken/kiezen + tekst → vision-antwoord,
  multi-turn. `ApiClient.gardenChat` (multipart). Web op `moestuin.vdzonsoftware.nl` + APK.
  App-id blijft `nl.vdzon.groentetuin` (interne naam ≠ publieke host "moestuin").
- **`notities/`** — één auto-opslaande notitie, Google-login. Alleen APK. Sinds SF-1801 een
  **donker thema** (zwart, witte tekst, ook op het inlogscherm) en een **WYSIWYG-editor**
  (`flutter_quill`) met precies vijf vaste opmaakknoppen: Vet, Cursief, Onderstreept,
  Opsomming en Opmaak wissen. Sinds SF-1809 staan in de horizontaal scrollbare balk ook A− en A+
  (`Lettergrootte verkleinen`/`Lettergrootte vergroten`): alleen de bewerkbare notitietekst en
  bulletmarkeringen schalen lokaal van 12 t/m 28 pt in stappen van 2 pt (standaard 16 pt). De via
  `SharedPreferences` bewaarde voorkeur wordt vóór de notitie geladen en blijft na uitloggen of
  herstart behouden; de wijziging raakt het Quill-document en de save-flow niet. De notitie wordt
  nog steeds als **platte markdown-tekst** opgeslagen via het ongewijzigde `PUT /api/v1/notes` —
  conversie in
  `lib/markdown_delta.dart` (`markdownToDelta()`/`deltaToMarkdown()`), mapping uitsluitend
  `**vet**`, `*cursief*`, `<u>onderstreept</u>` en `- ` voor bullets; al het andere (kopjes,
  tabellen, links, code, lege regels) blijft letterlijke tekst, zodat door de assistent
  toegevoegde tekst ongeschonden blijft. Autosave (10s debounce, direct bij `paused`/`inactive`
  en best-effort bij `dispose`), de handmatige Opslaan-knop, de statusregel en Uitloggen zijn
  ongewijzigd. Sinds SF-1808 staan links in diezelfde opmaakbalk een **Ongedaan maken**
  (`Icons.undo`) en **Opnieuw** (`Icons.redo`)-knop op Quills eigen undo-historie
  (`controller.hasUndo`/`hasRedo` → uitgegrijsd als er niets te doen valt; direct na het laden dus
  beide uit, undo maakt de notitie nooit leeg), en opent de AppBar-actie **Versies**
  (`Icons.history`) een eigen route (`lib/note_versions_screen.dart`) met de eerdere versies uit
  `GET /api/v1/notes/versions` — per regel NL datum/tijd in lokale tijd (`vandaag 11:30` /
  `gisteren 11:30` / `ma 28 jul 09:05`) via een eigen mini-helper `formatVersionMoment()`, dus
  géén `intl`-dependency. Tikken opent een alleen-lezen weergave (`SelectableText` met de platte
  markdown) met de knop **Terugzetten** + bevestigingsdialoog; terugzetten vervangt de
  editorinhoud via `controller.replaceText(0, document.length, …)` — een bewerking op het
  bestáánde document, zodat de undo-historie en het `changes`-abonnement intact blijven en de
  normale debounce-autosave 'm als nieuwe versie opslaat.
- **`wind/`** — PoC "Hey Google" → App Actions → native trampoline (TTS + notificatie), praat
  met de backend-chat-assistent voor het windantwoord. Alleen APK.

Web-apps praten same-origin via nginx-proxy `/api/ → robberts-assistent-backend:80` (geen
CORS). APK's praten met `API_BASE_URL=https://robberts-assistent.vdzonsoftware.nl`.

---

## 7. Deploy (GitOps)

- CI (`.github/workflows/`): per component een image-build (backend + per web-app) die naar
  `ghcr.io` pusht en `deploy/base/kustomization.yaml` bumpt; per app een apk-build →
  GitHub Release (vaste tag). `backend-verify.yml` draait `mvn test` op PR + main.
- **ArgoCD** (repo `robberts-infrastructure`) synct `deploy/base` naar namespace
  `robberts-assistent` (prod) en spint per open PR een **preview** op (`deploy/overlays/preview`,
  `robberts-assistent-pr-<n>`, met `RA_PREVIEW_SKIP_GOOGLE_AUTH=true` + `RA_MOCK_AI=true`).
- **Secrets:** `deploy/base/sealed-secret-robberts-assistent.yaml` (Sealed Secrets). Nieuwe
  keys toevoegen met `kubeseal --merge-into` (cert: `robberts-infrastructure/.../cluster-cert.pem`)
  of `deploy/seal-secrets.sh`. Alleen versleutelde vorm in git.
- **Externe host:** Cloudflare Tunnel; nieuwe hostnames handmatig in Cloudflare Zero Trust.

Snelle route deze fase: rechtstreeks op `main` (prod), verifiëren via prod. Story-werk gaat
via de software-factory (branch, worklog in `docs/stories/`, PR → preview).

---

## 8. Conventies

- **Tests:** backend `mvn test` (vanuit `robberts-assistent-backend/`); `ModulithArchitectureTest`
  bewaakt module-grenzen. Apps: `flutter test` + `flutter analyze`.
- **Nieuwe skill:** module = nieuwe subpackage; nieuwe agent-capability = een `@Tool` in
  `assistant/ai/` geregistreerd in `AiConfig`. Koppeling = port + fallback + `AppSecrets`-key
  (indien niet keyless) + een `CouplingProbe`-`@Component` (zie `couplings.CouplingProbe`) —
  dat laatste is voldoende om op het "Koppelingen"-scherm te verschijnen, geen andere wijziging
  nodig.
- **Commits/branches:** Nederlands; factory gebruikt branch-prefix `ai/` en story-worklogs.

---

## 9. Huidige status (juli 2026)

Gebouwd + gedeployed: backend-fundament (auth, notes, summary, assistant + tools), reminders
+ scheduler, moestuin-AI-chat, Google Agenda/Docs (code), Firebase (Firestore + Storage),
Telegram-notifier; apps robberts_assistent + groentetuin/moestuin live met Google-login.

Nieuw (SF-1119): de assistent-chat in `robberts_assistent` is omgebouwd van één stateless
vraag/antwoord-lijst naar persistente, benoemde **gesprekken** (`POST /api/v1/assistant/chat`,
`GET /api/v1/assistant/conversations(/{id})`, `GET /api/v1/assistant/photos/{id}`), analoog aan
`gardenchat`: Firestore-opslag (`assistant-conversations`, in-memory fallback), zelf-verzonnen
titel na de eerste uitwisseling (deterministische placeholder onder `RA_MOCK_AI`), en
foto-ondersteuning (camera/galerij, vision) via een eigen `PhotoStorage` (map `assistent-chat/`
in Firebase Storage). Het oude `POST /api/v1/assistant/message` is vervallen; de native
`wind`-app roept nu `/api/v1/assistant/chat` aan (altijd zonder `conversationId`, dus telkens
een nieuw kortstondig gesprek).

Nieuw (SF-1141): gesprekken zijn te **archiveren/de-archiveren en verwijderen**
(`PATCH .../{id}/archive|unarchive`, `DELETE /api/v1/assistant/conversations/{id}`), en
`GET /api/v1/assistant/conversations` ondersteunt `includeArchived`/`limit`/`offset`-paginatie
(app: eerste 10 direct, oudere onder "Ouder", swipe-acties via `flutter_slidable`). Daarnaast een
automatisch bijgewerkt, gebruiker-breed **geheugen** (`GET/PUT /api/v1/assistant/memory`,
Firestore-collectie `assistant-memory`): na elke chat-beurt herschrijft een losse AI-aanroep de
volledige geheugen-tekst, die vervolgens als context meegaat in latere gesprekken; app-scherm
`memory_screen.dart` via "Meer" → "Geheugen".

Nieuw (SF-1149): het geheugen is omgezet van een lijst losse `MemoryItem`s naar **één
vrije-tekst-string** per gebruiker (`MemoryRepository.current()`/`update(text)`, zelfde
Firestore/in-memory-fallback). De endpoints zijn vereenvoudigd tot `GET`/`PUT
/api/v1/assistant/memory` (oude `POST`/`PUT .../{id}`/`DELETE .../{id}` zijn vervallen); de
AI-aanroep na elke chat-beurt krijgt de huidige geheugen-tekst + de laatste uitwisseling mee en
retourneert de volledige nieuwe tekst (geen reconciliatie tegen losse items meer). Het
"Geheugen"-scherm toont nu één groot multiline tekstveld met auto-save (10s debounce + save bij
app-pauze, zelfde patroon als `notities/lib/notes_editor_screen.dart`) i.p.v. een lijst met
toevoeg/bewerk/verwijder-dialogen.

**Live in prod, end-to-end geverifieerd** met echte creds: Firestore (reminders + chat),
Firebase Storage (foto's), Telegram-notifier, echte Google Agenda/Docs (OAuth), vision-chat.

Historische valkuil (opgelost): met firebase-admin erbij crashte de backend op de
**`alpine`**-base met SIGSEGV in gRPC's `netty-tcnative` (BoringSSL is voor glibc gebouwd, niet
musl) → CrashLoopBackOff, waardoor de oude pod bleef draaien en koppelingen op fallback leken te
staan. Opgelost door een **glibc-base** (`eclipse-temurin:21-jre`, zie backend-`Dockerfile`).
Les: een SIGSEGV in native code omzeilt de Java-fail-safe; check `kubectl get pods`/pod-logs.

App-kant gebouwd: **reminders/alarms-scherm**, **FCM-ontvangst** (google-services.json in `.keys/`),
en een **native wekker** — een échte alarm-ervaring i.p.v. alleen een notificatie. De Flutter-laag
(`lib/alarm_scheduler.dart`) rekent de eerstvolgende voorkomens uit en geeft ze via een MethodChannel
(`nl.vdzon.robberts_assistent/alarm`) door aan native Kotlin (`android/app/src/main/kotlin/.../alarm/`):
`AlarmScheduling` plant ze in met `AlarmManager.setAlarmClock` (altijd exact, geen SCHEDULE_EXACT_ALARM
nodig, overleeft Doze), `AlarmReceiver` → `AlarmService` (foreground-service, speelt eenmalig een
2 minuten durend, oplopend piepgeluid (`res/raw/alarm_beep.wav`, sinds SF-1247 — was voorheen een
oneindig lussende systeem-ringtoon via `RingtoneManager`) + trillen, sinds SF-1254 pas 30 seconden
ná het geluid (voorheen gelijktijdig)) toont een full-screen `AlarmActivity` over het lockscreen
met **Sluit** en **Snooze**; `BootReceiver` herplant na reboot (persistentie in SharedPreferences).

Nieuw (SF-1163, story 1 van 2): dagelijkse **Morgen-briefing**. Nieuwe `briefing`-module met een
pluggable `BriefingSectionProvider`-SPI (zelfde stijl als `CouplingProbe`/`NightlyCheck` — een
nieuwe sectie toevoegen raakt `BriefingService` niet), vier secties (kite-/strandfietskans voor morgen incl. wind in kn via
een nieuwe gestructureerde windbron (`weather.WindForecastClient`, i.p.v. de AI-tekst van
`WindTools`), agenda komende 7 dagen over alle agenda's met per-afspraak reminder-status en een
één-tap-aanmaak-actie, AI-weektakensamenvatting, moestuin-placeholder), een algoritmische
NL-feestdagenberekening (`Holidays`, geen externe koppeling/hardcoded lijst) en een dagelijkse
18:00 (Europe/Amsterdam) `@Scheduled`-job die via de bestaande `PushService` een korte
samenvattingspush stuurt. `CalendarClient` kreeg `eventsInRange` (7-dagen-tijdvenster,
multi-agenda) en behoudt nu het "hele dag"-kenmerk van events (voorheen verloren in de parsing) —
nodig voor vakantiedetectie. App-kant: de bestaande "Samenvatting"-tab in `robberts_assistent` is
hernoemd/ingevuld tot **"Morgen"** (geen nieuwe/7e tab); een tik op de briefing-push opent 'm via
een nieuwe deep-link (`FcmService.deepLinkTab`, FCM-`data['type'] == 'briefing'`) — hiervoor kreeg
`PushService.sendToAll` een optionele `data`-parameter. Het oude `GET /api/v1/summary`-endpoint
(`summary`-module) heeft hierdoor geen app-consument meer (zie §4).

Nieuw (SF-1164, story 2 van 2): vijfde briefingsectie **systeemstatus/-checkrapport**
(`SystemStatusSectionProvider`, `order = 40`, dus onderaan het "Morgen"-scherm na kite/agenda/
weektaken/moestuin) — geen wijziging aan `BriefingService`/`BriefingController`/
`BriefingScheduler` nodig (SPI-patroon uit story 1 volstond). Bundelt vijf checks tot ruwe data:
zonnepanelen (dummy), backups (dummy), OpenShift-gezondheid (live via
`openshift.OpenShiftClient.clusterHealth()`, niet via de nachtelijk-opgeslagen
`NightlyCheckRepository`-historie), robotmaaier (`automower.AutomowerClient.status()`,
error/state-afleiding) en Software Factory (`softwarefactory.SoftwareFactoryClient.stories()`).
Een nieuwe, losse `systemStatusChatClient` (`briefing.BriefingAiConfig`, zelfde patroon als
`weekTasksChatClient`) bepaalt volledig zelf per check of er "aandacht nodig" is (geen hardcoded
drempel in code) en levert de rapporttekst; faalt de AI-call of een onderliggende client, dan valt
de sectie stil terug op een neutrale tekst zonder te crashen (`runCatching` per check + rond de
AI-aanroep). `shortSummary()` geeft alleen een `⚠️ ...`-tekst terug als minstens één check
aandacht nodig heeft, zodat de bestaande 18:00-pushtekst-logica (`mapNotNull`) 'm bij een
"alles-in-orde"-status automatisch overslaat. Geen frontend-wijziging: het "Morgen"-scherm
rendert secties generiek uit `GET /api/v1/briefing`.

Nieuw (SF-1192): de gecombineerde 'Kiten / strandfietsen'-briefingsectie is gesplitst in twee
losse kaarten, beide bovenaan de briefing (boven Agenda) — `KiteSectionProvider` (`order = 0`)
en de nieuwe `BeachCycleSectionProvider` (`order = 5`). Kiten toont per dagdeel
`<label>: <emoji> <wind> kn (richting)`; strandfietsen toont per dagdeel een bolletje MET
onderbouwing (wind, regen, getij) zodat het oordeel navolgbaar is — voorheen propte
`KiteSectionProvider` beide activiteiten op één regel. De gedeelde dagdeel-/werkdag-/
vakantielogica en beoordelingslogica (`assessKite`/`assessBeachCycle`) is uit `KiteSectionProvider`
getrokken naar een interne, niet-Spring `SlotAssessmentProvider` (nog steeds in
`KiteSectionProvider.kt`) zodat beide `@Component`-providers dezelfde dataproviders
(`WindForecastClient`, `WeatherClient`, `TideClient`, `CalendarClient`) en netwerkcalls
hergebruiken. `shortSummary()` voor de 18:00-push blijft alleen bij kiten (strandfietsen levert
`null`). App-kant: `summary_screen.dart` kreeg alleen een extra icoon (`beach` → `Icons.pedal_bike`)
— secties renderen al generiek, dus geen verdere wijziging nodig.

Nieuw (SF-1199/SF-1200): de 'Morgen'-briefing wordt nu **gecachet in Firestore** i.p.v. bij elke
`GET /api/v1/briefing` opnieuw live opgebouwd. Een nieuwe `BriefingCacheScheduler` bouwt 'm
dagelijks om 17:30 (Europe/Amsterdam, een half uur vóór de bestaande 18:00-push) op en slaat 'm
samen met een `updatedAt`-tijdstip op via `BriefingCacheRepository`
(Firestore/in-memory-fallback, zelfde patroon als `assistant.MemoryRepository`); `GET
/api/v1/briefing` levert die gecachete versie (of bouwt live op zónder te cachen als er nog geen
cache is), en het nieuwe `POST /api/v1/briefing/refresh` (zelfde auth) forceert een live
her-opbouw + cache-overschrijving. `BriefingResponse` kreeg er zo een `updatedAt`-veld bij. Een
nieuwe zevende sectie **weerkaart** (`WeatherMapSectionProvider`, `order = -10`, dus bovenaan,
boven kiten/strandfietsen) genereert twee statische kaartbeelden (kust IJmuiden–Egmond, via
OpenStreetMap-tegels + JDK-`java.awt`/`ImageIO`, geen betaalde kaarten-API) voor morgenochtend en
-middag, elk met windrichtingspijl, windsnelheid (kn) en weer-icoon; de PNG's worden bij elke
cache-refresh opnieuw gegenereerd, opgeslagen via `WeatherMapStorage` (Firebase
Storage/in-memory) en ontsloten via `GET /api/v1/briefing/weather-map/{ochtend|middag}`.
`BriefingItem` kreeg een optioneel `imageUrl`-veld zodat de app een item als afbeelding kan
renderen i.p.v. platte tekst — bestaande secties zonder afbeelding blijven ongewijzigd. App-kant:
`summary_screen.dart` toont bovenin een "Bijgewerkt om ..."-regel (uit `updatedAt`) met een
reload-knop ernaast (spinner tijdens laden, niet opnieuw indrukbaar) die `/refresh` aanroept —
los van de bestaande pull-to-refresh (die de cache ophaalt via de gewone `GET`); `BriefingItem`s
met `imageUrl` renderen als `Image.network` (met auth-header).

Nieuw (SF-1206): de weerkaart-sectie levert nu **één gecombineerd kaartbeeld** i.p.v. twee losse
beelden voor ochtend en middag. `CoastMapImageBuilder.build(...)` neemt een `List<WindMapSlot>`
(label, kleur, windsnelheid, -richting, weathercode) en tekent beide dagdelen in één aanroep:
twee windpijlen in verschillende kleuren (oranje = ochtend, blauw = middag), elk met een
windsnelheidslabel (kn), plus een legenda die kleur aan dagdeel koppelt. De oude emoji-gebaseerde
`weatherIcon()` (via `java.awt`-`Font`, op de server niet zichtbaar — leeg blokje) is vervangen
door een echt getekend icoontje (`java.awt`-vormen: cirkel voor zon, ellipsen voor een wolk, plus
regendruppellijntjes bij regen/onweer), één per dagdeel bij de bijbehorende pijl.
`WeatherMapSectionProvider` levert nu precies één `BriefingItem` met `imageUrl`, opgeslagen via
`WeatherMapStorage` onder de ene vaste sleutel `morgen` (i.p.v. `ochtend`/`middag`); `GET
/api/v1/briefing/weather-map/{slot}` serveert alleen nog die sleutel, de oude
`ochtend`/`middag`-sleutels zijn vervallen. Geen frontend-wijziging nodig (`summary_screen.dart`
rendert `imageUrl` al generiek).

Nieuw (SF-1220/SF-1221): de weerkaart-sectie is op vier punten aangepast. **Layout**: de twee
windpijlen staan nu verticaal gestapeld aan de linkerkant van de kaart i.p.v. horizontaal over de
breedte verspreid (`drawOverlay` in `CoastMapImageBuilder.kt`), elk nog steeds met eigen kleur,
windsnelheidslabel en weer-icoon. **Dagdelen**: het tweede dagdeel heet nu "Avond" (19:00) i.p.v.
"Middag" (14:00) — analoog aan de dagdeel-indeling van `KiteSectionProvider`/
`SlotAssessmentProvider` (Ochtend 07:00 + Avond 19:00). **Weersaanduiding + getijtijden onderin**:
onderaan de kaart staat nu een dag-breed (niet per-dagdeel) weersymbool en de hoog-/
laagwatertijden van die dag (IJmuiden), opgehaald via het nieuwe `tides.TideClient.forecast(...)`-
gebruik in `WeatherMapSectionProvider` (`tomorrowTideExtremes()`, stil-falend bij een
getij-fout — de kaart blijft dan gewoon opgebouwd zonder getijtijden) en getekend met
`java.awt`-vormen/tekst in `CoastMapImageBuilder.drawDaySummary()` (geen emoji/`Font`-glyphs).
`CoastMapImageBuilder.build(...)` kreeg er daarvoor twee parameters bij: `dayWeatherCode` en
`tideExtremes`. De testronde van SF-1220 vond hierbij een bug (het kader met weer-icoon +
getijtekst werd breder dan het canvas bij 3+ getijmomenten, waardoor de tekst aan beide randen
werd afgesneden); SF-1221 loste dit op door `boxWidth` te begrenzen op de kaartbreedte en de tekst
greedy over meerdere regels te verdelen (`wrapTideLines()`) i.p.v. het kader te laten uitsteken.
**Tekst opschonen**: `BeachCycleSectionProvider.tideText()` toont per dagdeel niet langer een
laagwatertijd (`laagwater om HH:MM`), alleen nog de nabijheid ("dichtbij laagwater"/"niet dichtbij
laagwater") — de tijd staat nu op de weerkaart. De rating/beoordelingslogica (`assessBeachCycle`)
is ongewijzigd; `KiteSectionProvider` toonde al geen getij-tekst. Geen frontend-wijziging
(`summary_screen.dart` rendert de sectie al generiek).

Nieuw (SF-1227): cache-bust voor de weerkaart-afbeelding op de "Morgen"-tab. Na een refresh
(reload-knop → `POST /api/v1/briefing/refresh`, of de dagelijkse 17:30-cache gevolgd door een
gewone `GET /api/v1/briefing`) toonde `summary_screen.dart` soms nog de oude PNG, omdat
`imageUrl` een vaste URL is en Flutter's `Image.network`-`ImageCache` daarop keyed. Elk
`BriefingItem.imageUrl` krijgt nu client-side een `?v=<epoch-seconden>`-query-param aangehangen
op basis van `BriefingData.updatedAt` (`_cacheBustedImageUrl()` in `summary_screen.dart`) —
generiek voor elk item met een `imageUrl`, niet hardcoded op de weerkaart-sectie. Backend-kant,
ter versteviging: `GET /api/v1/briefing/weather-map/{slot}` (`BriefingController.kt`) geeft nu een
`Cache-Control: no-cache`-header mee. Geen wijziging aan `BriefingItem`/`BriefingResponse`-
datamodel.

Nieuw (SF-1247): het alarmgeluid van de native Android-wekker (`AlarmService.kt` in
`robberts_assistent/`) speelt niet langer de oneindig lussende systeem-alarmringtoon
(`RingtoneManager`) af, maar eenmalig een vast, gebundeld audiobestand van 2 minuten
(`res/raw/alarm_beep.wav`): een zachte piep op t=0, stilte tot t=10, en daarna elke 10 seconden
een steeds luidere piep tot het einde (t=120). `startAlarmSound()` gebruikt nu
`MediaPlayer.create(this, R.raw.alarm_beep, audioAttributes, AudioManager.AUDIO_SESSION_ID_GENERATE)`
met `isLooping = false`, binnen dezelfde `runCatching`-foutafhandeling als voorheen; er is bewust
géén `OnCompletionListener` toegevoegd, dus als het geluid na 2 minuten vanzelf stopt, blijven
trilling, foreground-notificatie en de full-screen `AlarmActivity` gewoon actief totdat de
gebruiker Sluit of Snooze kiest — ongewijzigd t.o.v. voorheen. Het audiobestand is
**ongecomprimeerd `.wav`** i.p.v. `.ogg`/`.mp3` (mono 16-bit PCM, 11025 Hz, ±2,5 MB), omdat de
sandbox waarin het is gegenereerd geen compressie-tooling (ffmpeg/lame/oggenc/sox) had; zie
`docs/stories/worklog/SF-1247-worklog.md` voor hoe het is gegenereerd (Python-stdlib-script). Rest
van `AlarmService.kt` (dismiss/snooze-flow, wakelock, resource-vrijgave in
`stopEverything()`/`onDestroy()`) ongewijzigd.

Nieuw (SF-1254): het trillen bij een afgaand alarm (`AlarmService.kt`) start niet langer
gelijktijdig met het geluid, maar pas **30 seconden** na `ACTION_START` — zodat het geluid
eerst de kans krijgt om te wekken. `ACTION_START` plant `startVibration()` nu in via een
instance-`Handler(Looper.getMainLooper())` + `Runnable` (`vibrationHandler.postDelayed
(startVibrationRunnable, VIBRATION_DELAY_MS)`, vaste companion-constante `VIBRATION_DELAY_MS
= 30_000L`) i.p.v. het direct aan te roepen; geluid (`startAlarmSound()`) en het full-screen
`AlarmActivity`-scherm starten ongewijzigd direct. `stopEverything()` (gebruikt door
`ACTION_DISMISS`, `ACTION_SNOOZE` en `onDestroy()`) roept eerst `vibrationHandler
.removeCallbacks(startVibrationRunnable)` aan, naast de bestaande `vibrator?.cancel()`, zodat
een nog niet afgevuurde vertraagde trilling niet alsnog start nadat het alarm al gestopt is.
Trillingspatroon (`VibrationEffect.createWaveform` met `longArrayOf(0, 800, 600)`, repeat=0) en
overige service-logica (notificatie, wakelock, dismiss/snooze) ongewijzigd. Geen wijziging aan
`AlarmReceiver`, `AlarmScheduling`, `BootReceiver`, `AlarmActivity` of de Flutter-laag
(`alarm_scheduler.dart`); geen instrumentatietest toegevoegd, handmatige verificatie op
toestel/emulator volstaat (zelfde aanpak als SF-1247).

Nieuw (SF-1267/SF-1268): de "Morgen"-tab in `robberts_assistent` is gesplitst in twee tabs op de
bottom-navigatie (nu 5 i.p.v. 4): **"Upcoming"** (zelfde plek/index als voorheen "Morgen",
`summary_screen.dart`, zelfde `SummaryScreen`, dus de bestaande 18:00-FCM-briefing-deep-link
blijft hiernaartoe wijzen) toont alle briefingsecties behalve systeemstatus, en de nieuwe
**"Health check"**-tab (`health_check_screen.dart`) toont uitsluitend de systeemstatus-sectie,
per onderdeel (Zonnepanelen/Backups/OpenShift/Robotmaaier/Software Factory) met een eigen kop en
de ruwe, niet-AI-samengevatte statusregel(s) als selecteerbare bullets (`SelectableText`) i.p.v.
de AI-samengevatte alinea. Backend: `briefing.BriefingItem` kreeg een optioneel `heading`-veld;
`SystemStatusSectionProvider` bouwt de vijf per-check statusregels nu eerst als gestructureerde
`CheckData(heading, content)` op en geeft die ongewijzigd door als `BriefingSection.items` — de
AI-inputtekst en de bestaande AI-"aandacht nodig"-beoordeling/`shortSummary()`/18:00-pushtekst
zijn functioneel ongewijzigd; geen nieuw backend-endpoint, de Health check-tab hergebruikt
`GET /api/v1/briefing`.

Nieuw (SF-1274/SF-1275): Upcoming en Health check zijn volledig ontkoppeld op het gebied van
caching/verversen, en de refresh-frequentie is verhoogd. **Backend** (`briefing`-module):
`BriefingService` bouwt nu twee onafhankelijke responses met elk een eigen cache en `updatedAt` —
`currentUpcoming()`/`refreshUpcoming()` (alle secties behalve `system-status`) en
`currentHealth()`/`refreshHealth()` (uitsluitend `system-status`, via
`providers.filterIsInstance<SystemStatusSectionProvider>()`) — elk met een eigen
`@Qualifier`-gekwalificeerde `BriefingCacheRepository`-bean (`upcomingBriefingCache` /
`healthBriefingCache`, `BriefingStoreConfig`) en een eigen Firestore-document
(`FirestoreBriefingCacheRepository`'s `documentId`: `current` resp. `health`). `BriefingController`
kreeg twee nieuwe endpoints naast de bestaande `GET /api/v1/briefing` + `POST
/api/v1/briefing/refresh` (die nu alleen de Upcoming-cache raken): `GET /api/v1/briefing/health` +
`POST /api/v1/briefing/health/refresh` voor de Health check-cache. `BriefingCacheScheduler` ververst
niet langer dagelijks om 17:30, maar **elk uur** (`@Scheduled(cron = "0 0 * * * *")`) beide caches,
elk in een eigen `runCatching` zodat een falende refresh van de ene de andere niet blokkeert. De
dagelijkse 18:00-FCM-push (`BriefingScheduler`) is functioneel ongewijzigd gebleven — die bouwt al
rechtstreeks via de providers-lijst op, los van beide caches. Daarnaast filtert
`SystemStatusSectionProvider.softwareFactoryCheckData()` nu op relevantie: alleen stories met
`error != null`, of met een gezette `phase` die nog niet gemerged is (`phase != null && !merged`),
worden getoond; gemergede stories en stories zonder fase vervallen, en blijft na filteren niets
over dan toont de check "geen lopende of error-stories." **Frontend**: `ApiClient` kreeg
`getHealthCheck()`/`refreshHealthCheck()` tegen de nieuwe endpoints; `health_check_screen.dart`
laadt daarmee i.p.v. via `getBriefing()` en kreeg, analoog aan `summary_screen.dart`, een eigen
"Bijgewerkt om ..."-regel + reload-knop (spinner tijdens laden, niet opnieuw indrukbaar).
`summary_screen.dart` zelf is ongewijzigd.

Nieuw (SF-1296): `OsmCoastMapImageBuilder` haalt de OSM-basiskaart van de kust IJmuiden–Egmond
niet langer bij elke `build()`-aanroep opnieuw op (voorheen elk uur via `BriefingCacheScheduler`
+ bij elke reload-knop op de Upcoming-tab) — alleen de overlay (windpijlen, weer-icoon,
getijtijden) verandert per refresh, de basiskaart zelf niet. `build()` haalt de basiskaart nu uit
een `@Volatile`-in-memory-cache (thread-veilig gevuld via double-checked locking rond
`baseMapLock`, zodat de uurlijkse scheduler en een handmatige reload elkaar niet dubbel laten
fetchen); bij een cache-miss wordt eerst de nieuwe opslag-poort `BaseMapStorage` geraadpleegd
(`FirebaseStorageBaseMapStorage` — Firebase Storage, pad `briefing-weather-map/basemap.png`, of
`InMemoryBaseMapStorage` als fallback zonder Firebase-config, bean-selectie in
`BriefingStoreConfig`, exact hetzelfde patroon als `WeatherMapStorage`) en pas als laatste
redmiddel doet `fetchMap()` de echte OSM-tile-HTTP-calls, waarna het resultaat in beide caches
wordt bewaard — zo overleeft de basiskaart een pod-herstart zonder alle tegels opnieuw op te
halen. `drawOverlay()` tekent voortaan altijd op een verse kopie van de gecachete basiskaart
(nieuwe `copyOf()`) i.p.v. op de gedeelde instantie, zodat overlay-tekeningen van een vorige
refresh niet in de volgende blijven doorschemeren en de cache schoon blijft. Geen TTL/invalidatie
toegevoegd (expliciet optioneel in de story — de basiskaart van dit vaste kustgebied wordt
verondersteld voor onbepaalde tijd stabiel). `CoastMapImageBuilder`-interface (`build(slots,
dayWeatherCode, tideExtremes): ByteArray`), `WeatherMapSectionProvider`, de weerkaart-endpoints
(`GET /api/v1/briefing/weather-map/{slot}`, `POST /api/v1/briefing(/health)/refresh`) en de
overige briefing-caching zijn ongewijzigd — deze story raakt alleen de basiskaart-ophaal-/
hergebruik-laag binnen `CoastMapImageBuilder.kt`. Geen frontend-wijziging.

Nieuw (SF-1297): achtste briefingsectie **afval** (`WasteSectionProvider`, `order = 15`, dus
tussen Agenda (10) en Weektaken (20) op de "Upcoming"-tab) — geen wijziging aan
`BriefingService`/`BriefingController`/`BriefingSectionProvider`/`BriefingScheduler` of
frontend nodig (SPI-patroon volstond, zelfde als bij eerdere sectie-toevoegingen). Gebruikt de
bestaande, keyless `waste.WasteClient.upcomingPickups()` (geen nieuwe koppeling): `section()`
filtert `WasteSchedule.pickups` tot vandaag t/m +6 dagen en toont per ophaalmoment een regel
`dd-MM: <type>`; een lege 7-dagen-lijst of een gezet `WasteSchedule.error` degradeert stil naar
een neutrale foutmelding (`runCatching`, zelfde beschermende patroon als
`WeekTasksSectionProvider`/`SystemStatusSectionProvider`), zonder de hele briefing te laten
crashen. Geen AI-call nodig — de tekst is deterministisch uit `WastePickup`-data opgebouwd.
`shortSummary()` geeft alleen bij een ophaalmoment morgen "Zet vanavond de \<bak(ken)\> buiten"
terug (meerdere types op dezelfde dag samengevoegd in één zin), anders `null`, zodat
`BriefingScheduler`'s bestaande `mapNotNull`-patroon de sectie in de 18:00-push overslaat.

Nieuw (SF-1553): **"nu draaien" voor alle zoekopdrachten**. `WatchRunner` kreeg naast de
`@Scheduled poll(now)` een tweede instap `runNow(now)`: die slaat de `WatchSchedule.isDue`-
filtering over en controleert álle opdrachten met `active == true` via dezelfde bestaande private
`check(watch, now)` — gedrag per opdracht is dus exact gelijk aan een geplande run
(`compareAndSet`-update van `status`/`statusDescription`/`lastCheckedAt`, bij vondst
`active = false` + precies één push als `notifyOnFound` aanstaat, bij een fetch-/AI-fout
`ONBEKEND` zonder de rest van de run te stoppen). Inactieve opdrachten — waaronder alles wat al
op `GEVONDEN` staat — worden overgeslagen en blijven ongewijzigd. `WatchesController` ontsluit dit
als `POST /api/v1/watches/run-now` (zelfde `authService.requireAuthorization`), draait de run
**synchroon** af en geeft daarna de bijgewerkte `WatchesResponse` terug via de bestaande private
`response()`-helper, zodat de app geen extra `GET` nodig heeft. Geen wijziging aan `Watch`,
`WatchRepository`, `WatchSchedule`, `WatchEvaluator`, `WatchPageFetcher` of `poll()`; bewust géén
server-side lock tegen gelijktijdige runs (het bestaande `compareAndSet`-patroon dekt overlap met
de poller af, dubbele runs voorkomen is UI-werk). App-kant: `ApiClient.runWatchesNow()` en in
`watches_screen.dart` een extra AppBar-`IconButton` (`Icons.play_circle_outline`, tooltip "Alle
zoekopdrachten nu controleren") naast de refresh-knop; tijdens de run (`_running`) toont die knop
een `CircularProgressIndicator` en zijn run-, reload- én toevoegknop uitgeschakeld — de bestaande
lijst blijft zichtbaar i.p.v. in een leeg laadscherm te springen. `_runNow()` deelt de
`_loadSequence`-teller met `_load()` zodat een oudere call nieuwere gegevens niet overschrijft en
het scherm niet in de laadspinner blijft hangen; bij een fout een `SnackBar` ("Nu controleren
mislukt: …") en de knop is daarna weer bruikbaar.

Nieuw (SF-1563): `robberts_assistent` heeft een rustig, uitsluitend licht thema met teal accent,
witte randkaarten zonder schaduw, het nieuwe teal-witte robotbeeldmerk en gedeelde statuspillen
die status altijd met kleur én woord tonen. De bottom-navigation telt vier bestemmingen:
**Vandaag**, **Assistent**, **Taken** en **Meer**; Assistent blijft het startscherm. Health check,
Zoekopdrachten, Koppelingen, Nachtchecks, Geheugen en Updates zijn losse routes onder Meer.
Briefing-pushes sluiten een eventueel geopende Meer-route en openen Vandaag; watch-pushes openen
een verse Zoekopdrachten-route. De backend en API-contracten zijn niet gewijzigd: de app vertaalt
de bestaande 🟢/🟡/🔴-briefingtekst client-side naar woordelijke pillen.

Nieuw (SF-1564): `BriefingSection` ondersteunt optioneel `status` (`GOED`, `LET_OP`, `NIET`) en
`tileLabel`, met `null`-defaults voor oude cachedata en niet-statussecties. Kite en strandfietsen
vatten hun gunstigste beoordeelde dagdeel samen; afval geeft op basis van dezelfde opgehaalde
zevendagenplanning `LET_OP` voor vandaag/morgen en anders `GOED`, met Amsterdamse daggrenzen.
Bronfouten geven bewust geen tegel. De Vandaag-tab toont maximaal de eerste drie geldige
statussecties als even brede, afgekorte en semantisch bedienbare tegels met exacte groen/geel/rode
statuskleuren en woordelijke betekenis. Een tik toont één volledig detail onder de rij; getegelde
secties worden niet dubbel als vaste kaart getoond en alle overige secties blijven zichtbaar. Er
zijn geen nieuwe endpoints, cachelagen of databronnen en de 18:00-push is ongewijzigd.

Nieuw (SF-1595): de assistent-chat kan de **langdurige zoekopdrachten (watches)** lezen, aanmaken
en aanpassen. Nieuwe `assistant/ai/WatchTools.kt` (`@Component` met `watches.WatchService` als
constructor-dependency, exact in de stijl van `ReminderTools.kt`: Nederlandse `@Tool`-descriptions,
`@ToolParam` per argument, korte Nederlandse zin als returnwaarde, geen exceptions naar buiten) met
drie tools: `listWatches()` (alle opdrachten via `WatchService.list()` — actieve eerst, daarna op
titel — per regel titel, url, zoekinstructie, frequentie, status + `statusDescription`, actief
ja/nee, `lastCheckedAt` met dezelfde `EEEE d MMMM HH:mm`/`Europe/Amsterdam`-formatter als
`ReminderTools` ("nog niet gecontroleerd" bij `null`) en de eerste 8 tekens van het id; lege lijst →
één vriendelijke melding), `createWatch(title, url, instruction, frequency, notifyOnFound)`
(`frequency` is vrije tekst: `kantooruren` → `KANTOORUREN`, `dagelijks` → `DAGELIJKS`,
case-insensitive, leeg/onbekend → `DAGELIJKS`; `notifyOnFound` default `true`) en `updateWatch(id,
...)` (zoekt op het begin van het id met `startsWith` over `WatchService.list()`, net als
`deleteReminder`; alleen meegegeven velden wijzigen, de rest wordt overgenomen van de bestaande
watch omdat `WatchService.update` alle velden verwacht; geen match → "Geen zoekopdracht gevonden met
id ..."; de bevestiging vermeldt expliciet dat de opdracht weer actief is en opnieuw gecontroleerd
wordt, want `update` reset `status` naar `NOG_NIET_GECONTROLEERD` en `active` naar `true`). Een
`WatchValidationException` (ongeldige URL, lege titel/instructie) wordt opgevangen en als leesbare
Nederlandse foutmelding teruggegeven i.p.v. als tool-fout. **Verwijderen kan bewust niet via de
chat** — dat blijft het Zoekopdrachten-scherm. `WatchTools` is als parameter toegevoegd aan
`AiConfig.assistantChatClient(...)` en meegegeven in `defaultTools(...)`; de system-prompt noemt de
nieuwe mogelijkheid. Geen wijziging aan de `watches`-module, de REST-API of de Flutter-apps — wat via
de chat wordt aangemaakt/gewijzigd verschijnt ongewijzigd in het bestaande Zoekopdrachten-scherm.

Nieuw (SF-1621): de briefing toont niet langer meteen "Kon Open-Meteo niet ophalen (HTTP 503)" bij
een incidentele storing van de weerdienst. Beide Open-Meteo-clients (`OpenMeteoWeatherClient`,
`OpenMeteoWindForecastClient`) delegeren het ophalen aan de nieuwe, `internal`
`weather/ForecastFetcher.kt` en doen zelf alleen nog parsen + afkappen op `hours`:
**retry** (maximaal 3 pogingen met ~0,5 s en ~2 s pauze, bij netwerk-/IO-fout, HTTP 5xx en 429;
bij overige 4xx precies 1 call, geen retry), **last-known-good** (faalt alles, dan de laatst
geslaagde respons met `error == null` en een verouderd-markering, mits jonger dan 12 uur — anders
de bestaande foutmelding met ongewijzigde tekst) en een **TTL-cache van 10 minuten** op de ruwe
respons-body, zodat één briefing-opbouw nog maar 1 weer-call + 1 wind-call doet i.p.v. 3 + 3. De
ruwe body wordt gecachet i.p.v. de geparste voorspelling, zodat het "vanaf nu"-filter ook bij een
cachehit tegen de actuele tijd gebeurt. Per definitief mislukte aanroep gaat er precies één
`logger.warn` met statuscode of foutmelding uit. `WeatherForecast`/`WindForecast` kregen twee
optionele velden (`fetchedAt: Instant? = null`, `stale: Boolean = false`) — `stale` is alleen waar
bij een last-known-good-teruggave, niet bij een verse call of een TTL-cachehit — dus alle stubs en
bestaande aanroepen compileren ongewijzigd. `now`, `sleeper` en `retryDelaysMs` zijn
constructorparameters met productiedefault (geen `Clock`-bean), zodat TTL, de 12-uursgrens en de
pauzes in tests bestuurbaar zijn zonder echte wachttijd. Briefingkant: `SlotAssessmentProvider`
geeft het oudste verouderde ophaalmoment van wind/weer mee in `AssessmentResult.Ok.staleSince`, en
`KiteSectionProvider`, `BeachCycleSectionProvider` en `WeatherMapSectionProvider` tonen bij
verouderde data de normale inhoud plus `(gegevens van HH:MM)` (Europe/Amsterdam; bij de weerkaart
achter de tekst van het bestaande item, omdat de sectietekst daar leeg is). Ongewijzigd:
de interfaces `WeatherClient`/`WindForecastClient` en hun stubs, de `CouplingProbe`s, de tegels
(`status`/`tileLabel`), `shortSummary()`/de 18:00-push, de briefing-cache, de PNG-opbouw van de
weerkaart (die gebeurt ook op last-known-good-data), alle API-contracten en de apps. Gevolg dat
bewust geaccepteerd is: de "test"-knop op het Koppelingen-scherm kan binnen de TTL of vanuit
last-known-good slagen terwijl Open-Meteo op dat moment onbereikbaar is. De cache/last-known-good
is puur in-memory: na een pod-herstart is er geen last-known-good tot de eerste geslaagde call.
Bekend aandachtspunt (niet opgelost, kandidaat voor een vervolgstory): een definitieve mislukking
wordt niet gecachet, dus bij een echte storing doorloopt elke sectie opnieuw de volledige
retry-reeks — functioneel correct (dezelfde last-known-good), maar de reload-knop en de uurlijkse
scheduler kunnen dan traag worden.

Nieuw (SF-1697): het begrip **controlefrequentie** bij langdurige zoekopdrachten is volledig
verdwenen. `WatchFrequency` (`KANTOORUREN`/`DAGELIJKS`) bestaat niet meer en het veld `frequency`
is uit `Watch`, `WatchService.create/update`, `SaveWatchRequest`/`WatchResponse` en de
Firestore-mapping gehaald. `WatchSchedule.isDue(watch, now)` is teruggebracht tot één regel:
`watch.active` **en** het lokale uur (Europe/Amsterdam) in `8..22` **en** (`lastCheckedAt == null`
**of** ≥ 1 uur verstreken). Dus elke actieve opdracht wordt overdag maximaal uurlijks
gecontroleerd — eerste beurt vanaf 08:00, laatste vanaf 22:00 (een controle kan tot en met 22:59
starten), ook in het weekend; tussen 23:00 en 07:59 gebeurt er niets. Het werkdag-/
weekend-onderscheid is weg. Ongewijzigd: het poll-interval (`ra.watches.poll-interval-ms`,
standaard 300000 ms — het effectieve ritme blijft dus de combinatie van vijfminuten-poller en de
1-uur-regel, een controle valt niet exact op het hele uur), `WatchRunner.runNow()` (slaat `isDue`
bewust over, dus "nu draaien" werkt ook buiten 08:00–22:00), `WatchEvaluator`,
`WatchPageFetcher`, `WatchRepository`, de push-afhandeling en de REST-URL's. **Firestore-migratie
is niet nodig**: `FirestoreWatchRepository.toMap()` schrijft `frequency` niet meer weg en de
`null`-guard in `toWatch()` (die een document zonder/met onbekende `frequency` stil oversloeg) is
verwijderd, zodat bestaande documenten mét én zónder dat veld gewoon inlezen; het oude veld blijft
ongelezen staan tot een document opnieuw wordt opgeslagen. Bestaande opdrachten worden niet
gereset (status, `lastCheckedAt` en `active` blijven staan). **Chat**: `assistant/ai/WatchTools`
heeft geen `frequency`-parameter meer op `createWatch`/`updateWatch` (helpers `parseFrequency()`/
`frequencyText()` weg) en de antwoordzinnen van `listWatches`/`createWatch`/`updateWatch` noemen
geen frequentiekeuze meer, maar "elk uur overdag"; `AiConfig`/`defaultTools` ongewijzigd. **App**
(`robberts_assistent`): de dropdown "Controlefrequentie" is uit het aanmaak-/bewerkdialoog van
`watches_screen.dart` verdwenen en `api_client.dart` stuurt/leest `frequency` niet meer (de oude
`m['frequency'] as String` zou gooien zodra de backend het veld niet meer meestuurt). Er is bewust
géén API-versionering of achterwaartse compatibiliteit voor een meegestuurd `frequency`-veld — app
en backend gaan in dezelfde release mee; een oude client die het veld tóch meestuurt krijgt geen
fout, het veld wordt genegeerd.

Nieuw (SF-1704): **app-start via Google Assistent/Gemini herkennen, loggen en direct in praatmodus
openen**. Drie delen. **Android native** (`robberts_assistent/android/app/src/main/kotlin/nl/vdzon/
robberts_assistent/`): nieuw `LaunchSource.kt` met `enum LaunchSourceType { ASSISTANT, LAUNCHER,
OTHER, UNKNOWN }`, `data class LaunchInfo(source, referrer, action, categories, extras)` (+ `toMap()`
voor het channel) en een **pure** `LaunchSource.classify(referrer: String?)` zonder Android-classes:
referrer `null`/leeg → `UNKNOWN`, een package uit `ASSISTANT_PACKAGES`
(`com.google.android.googlequicksearchbox`, `…apps.googleassistant`, `…apps.bard`, `…apps.gemini` —
constante bovenaan, expliciet bedoeld om bij te stellen zodra de echte logs bekend zijn) →
`ASSISTANT`, een package dat op `.launcher` eindigt of in de bekende-launcherlijst staat →
`LAUNCHER`, de rest → `OTHER`. `LaunchSource.from(activity, intent)` verzamelt referrer/action/
categories/extras defensief (`runCatching` per key, `toString()`, newlines weg, waarde afgekapt op
200 tekens, max. 50 keys — nooit crashen op rare extra-typen). `MainActivity` bepaalt de `LaunchInfo`
in `onCreate` (vóór `super.onCreate`, zodat Dart 'm niet eerder kan opvragen dan hij bestaat) én in
`onNewIntent`, en ontsluit 'm via een derde MethodChannel `nl.vdzon.robberts_assistent/launch`:
**pull** (`launchInfo`, dekt de koude start) + **push** (`invokeMethod("launchInfo", …)` bij
`onNewIntent`). `onNewIntent` roept daarbij eerst `setIntent(intent)` aan — zonder die regel blijft
`Activity.getReferrer()` de `EXTRA_REFERRER` van de kóúde start lezen (Flutters `FlutterActivity`
zet 'm zelf niet), waardoor de logregel nieuwe intent-data met een oude referrer zou mengen; zelfde
patroon als `alarm/AlarmActivity.kt`. Nieuwe unittest-sourceset `android/app/src/test/…/
LaunchSourceTest.kt` + `testImplementation("junit:junit:4.13.2")` dekt alle vier de
`classify`-uitkomsten. **Backend**: nieuwe Modulith-module `applaunch` (zie §4) met
`POST`/`GET /api/v1/app-launches` en precies één `APP_LAUNCH …`-INFO-regel per opgeslagen launch —
uitlezen gaat bewust via `oc logs … | grep APP_LAUNCH`, er komt geen app-scherm voor de gelogde
launches. **Flutter**: `lib/launch_source.dart`, `ApiClient.logAppLaunch(...)`, de launch-listener in
`home_screen.dart` en twee nieuwe optionele parameters op `AssistantScreen` (`startInVoiceMode`,
`autoStartListening`, beide default `false`) — `_startListening()` wordt pas aan het eind van
`_initSpeech()` aangeroepen en alleen als spraak beschikbaar is en de widget nog `mounted` is, dus
bij een geweigerde microfoonpermissie toont het scherm gewoon de bestaande foutmelding. Geen nieuwe
permissie-dependency (`RECORD_AUDIO` staat al in de manifest, `speech_to_text.initialize()` vraagt
de runtime-permissie zelf) en geen wijziging aan de `assistant`-backendmodule of de chatflow.
Bekende aandachtspunten (niet opgelost, bewust): (1) een warme start *zonder* `EXTRA_REFERRER` valt
terug op de bij `attach()` vastgelegde `mReferrer` van de koude start — Android-platformbeperking,
observatiepunt voor de telefoontest; (2) `LaunchSourceTest` draait nergens automatisch (er is geen
Gradle-wrapper in `robberts_assistent/android` en de APK-workflow draait alleen `flutter test`);
(3) `launchChannel` wordt niet opgeruimd bij engine-detach (cosmetisch, geen crash); (4) de
`APP_LAUNCH`-regel hangt aan een geslaagde opslag — zie je niets bij het greppen, kijk dan ook naar
Firestore-fouten in dezelfde logs. **Deze story is bewust niet volledig automatisch te testen**: wat
Gemini als referrer/extras meestuurt is alleen op een echt toestel te zien, dus handmatig testen op
Robberts telefoon (één normale start + één "Hé Google, start Robberts assistent app", daarna
`grep APP_LAUNCH`) is de laatste stap; blijkt het Gemini-package niet in `ASSISTANT_PACKAGES` te
staan, dan is dat één regel bijwerken in `LaunchSource.kt`.

Nieuw (SF-1711): **praatmodus is een doorlopend gesprek + korte spreektaal-antwoorden**. Twee
kanten. **Backend** (`assistant`): `POST /api/v1/assistant/chat` kreeg de optionele multipart-param
`voice` (`@RequestParam(required = false, defaultValue = "false")`), doorgegeven aan
`AssistantService.chat(conversationId, text, uploads, voice = false)`. Staat de vlag aan, dan gaat
er één extra `SystemMessage` mee met de nieuwe top-level `VOICE_SYSTEM_PROMPT` in
`assistant/ai/AiConfig.kt` (hardop voorgelezen ⇒ vlotte spreektaal, maximaal 2 korte zinnen, geen
opsommingen/markdown/kopjes/URL's/emoji/tabellen, geen inleiding vooraf, getallen en eenheden
uitspreekbaar geschreven, alleen langer bij een expliciet verzoek om details of een lijst). Bewust
een extra `SystemMessage` in de berichtenlijst i.p.v. een request-level `.system(...)`: dat laatste
zou de `defaultSystem(...)`/`SYSTEM_PROMPT` van `assistantChatClient` vervángen — nu blijven beide
system-boodschappen in de prompt staan (in `AssistantServiceTest` aangetoond op
`prompt.instructions`). `SYSTEM_PROMPT` zelf is ongewijzigd; zonder de vlag is de prompt exact als
voorheen, dus bestaande clients (`wind`-app) blijven werken. Ongewijzigd: opslag van vraag/antwoord,
titelgeneratie, geheugen-update, tools en alle overige endpoints. **Frontend**
(`robberts_assistent`): `ApiClient.assistantChat(...)` kreeg de optionele parameter `voice`
(default `false`) die alleen bij `true` als multipart-veld `voice` meegaat; alleen de spraakroute
(`_send(..., speakReply: true)`) zet 'm, de getypte route niet. `assistant_screen.dart` heeft nu de
lus luisteren → versturen → uitspreken → opnieuw luisteren: het uitspreken is afwachtbaar
(`awaitSpeakCompletion(true)`), de spraakherkenning wordt expliciet gestopt vóór het spreken, en er
wordt niet geluisterd tijdens versturen/wachten. Stopcondities: stop-/mic-knop, wisselen naar
chatmodus, `dispose`, spraakfout, chat-API-fout en 2 opeenvolgende rondes zonder verstane spraak
(`_maxSilentRounds`, dan gewoon terug naar de mic-knop, zonder foutmelding). Een `_loopGeneration`-
teller wordt bij elke stop opgehoogd zodat een antwoord dat pas dáárna klaar is met uitspreken de
lus niet alsnog herstart; de bestaande `_listening`-guard blijft intact. Voor de tests zijn twee
smalle seams toegevoegd — `SpeechRecognizer`/`VoiceSpeaker` met `_PluginSpeechRecognizer`/
`_PluginVoiceSpeaker` als productiedefault, injecteerbaar via de nieuwe optionele
`AssistantScreen`-parameters `speech`/`speaker` (stijl `_FakeApiClient`), geen nieuwe dependency.
Eén bewuste UI-afwijking: de stop/mic-FAB is tijdens een lopende lus niet meer disabled zodra
`_busy` (alleen bij `_busy && !_loopActive`), anders is "handmatig stoppen tijdens het uitspreken"
niet uitvoerbaar. Bekende aandachtspunten (niet-blokkerend, uit review/testronde): (1) een láát
binnenkomende `done`/`notListening` van een vórige luistersessie zou in `_onSpeechStatus`
theoretisch een tweede sessie kunnen starten — de guard kijkt naar `_loopActive`/`_heardThisRound`/
`_busy`, niet naar de generatie; niet reproduceerbaar met de test-seam, kleinste fix is een
sessie-id-check; (2) `_speech.stop()` gebeurt pas ná het API-antwoord, vlak vóór het spreken —
tijdens het wachten leunt het scherm erop dat de plugin al gestopt is na een eindresultaat;
(3) stop je terwijl het antwoord nog onderweg is, dan wordt dat antwoord daarna nog één keer
uitgesproken, maar de lus herstart niet; (4) `voice=onzin` geeft HTTP 400 (Spring-boolean-conversie),
geen regressie. Echte microfoon/TTS is niet in CI na te bootsen (alleen de callback-/lus-logica is
getest), dus handmatige eindverificatie op Robberts telefoon (twee vragen achter elkaar zonder
opnieuw te tikken + kort voorgelezen antwoord) is de laatste stap.

Nieuw (SF-1732): **multiline chat-invoerveld**. Alleen frontend, alleen `_chatControls()` in
`robberts_assistent/lib/assistant_screen.dart`: de chat-`TextField` (`hintText: 'Typ een vraag…'`)
kreeg `minLines: 1`, `maxLines: 5`, `keyboardType: TextInputType.multiline` en
`textInputAction: TextInputAction.newline`, zodat het veld op één regel start, meegroeit tot vijf
regels en daarna intern scrollt (standaardgedrag van `TextField`, dus geen extra `ConstrainedBox`).
`onSubmitted: (_) => _sendTyped()` is van dit veld verwijderd — Enter voegt nu een nieuwe regel toe
in plaats van te versturen; de send-knop rechts is bewust de enige verstuurweg (géén Ctrl/Shift+Enter-
sneltoets toegevoegd). De omliggende `Row` kreeg `crossAxisAlignment: CrossAxisAlignment.end` zodat
de foto-knop links en de send-knop rechts onderaan uitgelijnd blijven terwijl het veld groeit.
Ongewijzigd: `_sendTyped()`/`_send(...)` (dat alleen `trim()` doet, dus interne newlines blijven
vanzelf behouden), de `_busy`-afhandeling, de `_pending`-bijlagenflow, de volledige spraakmodus
(`_Mode.voice`, spraaklus, TTS, `voice: true`) en het backend-/API-contract — het multipart-veld
`message` ondersteunde meerregelige tekst al. Nieuwe widget-test in
`robberts_assistent/test/assistant_screen_test.dart` leest `minLines`/`maxLines`/`keyboardType`/
`textInputAction` en de nu `null` zijnde `onSubmitted` af via `tester.widget<TextField>(...)` (geen
pixel-/hoogtemeting, want de gerenderde hoogte hangt van het thema af) en toont via het bestaande
`_FakeApiClient`-patroon aan dat tekst met newlines na een tik op de send-knop ongewijzigd — alleen
ge-`trim()`d — als `message` bij `assistantChat(...)` aankomt. Geen bevindingen uit review/testronde.

Nieuw (SF-1767): **afbeelding uit het klembord plakken in de assistent-chat**. Alleen frontend,
alleen `robberts_assistent/lib/assistant_screen.dart`: het chat-`TextField` in `_chatControls()`
kreeg een `contentInsertionConfiguration` (`ContentInsertionConfiguration`) met
`allowedMimeTypes: _pasteableMimeTypes` (nieuwe top-level constante `['image/png', 'image/jpeg']`,
gedeeld met de callback) en `onContentInserted: _onContentInserted`. Die nieuwe callback zet de
aangeboden `KeyboardInsertedContent` om naar een `XFile.fromData(bytes, path: …, name: …,
mimeType: content.mimeType)` en voedt 'm aan de bestaande `_attach(List<XFile>)`-flow — dus géén
tweede bijlagenroute: `_pending`, de pending-strook (`_pendingPreview()`) en `_send(...)` blijven
ongewijzigd, en een geplakte afbeelding gaat precies als een galerijfoto mee in het multipart-veld
`photos` van `POST /api/v1/assistant/chat`. De bestandsnaam wordt client-side gegenereerd als
`geplakt-<epoch-ms>.png`/`.jpg` (afgeleid van de mimetype) en gaat zowel als `name` als als `path`
mee, want `cross_file`'s io-implementatie negeert `name` en leidt de naam uit `path` af — met alleen
`name` zou de bestandsnaam op Android leeg zijn. Ontbrekende/lege `data` of een mimetype buiten
PNG/JPEG levert geen bijlage en geen exception, alleen één `SnackBar` ("Geen afbeelding op het
klembord", voorafgegaan door `hideCurrentSnackBar()` zodat ze niet stapelen, via
`ScaffoldMessenger.maybeOf`). Omdat `onContentInserted` synchroon is en `_attach` async, loopt het
Future bewust door via `unawaited(...)` (`dart:async` toegevoegd). Ongewijzigd: alle
SF-1732-eigenschappen van het veld (`minLines: 1`, `maxLines: 5`, `keyboardType`,
`textInputAction`, geen `onSubmitted`, `enabled: !_busy`) en de `CrossAxisAlignment.end` van de
omliggende `Row`, `_showAttachSheet()` (blijft 'Foto maken' + 'Uit galerij kiezen'), de praatmodus,
`pubspec.yaml`/`pubspec.lock` (geen nieuwe dependency — `cross_file` is al transitief aanwezig via
`image_picker`) en de hele backend. Drie nieuwe widget-tests in
`robberts_assistent/test/assistant_screen_test.dart` roepen
`contentInsertionConfiguration!.onContentInserted(...)` rechtstreeks aan met geldige 1×1-PNG-bytes
(willekeurige bytes laten `Image.memory` in de pending-strook falen) en tonen via het bestaande
`_FakeApiClient`-patroon (`lastPhotos`) aan dat de bijlage bij het verzenden meegaat, ook zonder
ingetypte tekst. Bewuste beperkingen: **alleen de Android-toetsenbordroute** (Gboard 'plakken');
`ContentInsertionConfiguration` wordt door Flutter-web/desktop niet aangeroepen, dus in de webversie
blijft plakken tekst-only (geen foutmelding, bestaand gedrag), en Ctrl+V-afbeeldingplakken zou een
extra klembord-package vergen. Geen compressie/verkleining van geplakte bytes (camera/galerij
gebruiken wél `imageQuality: 70`). Bekend, niet-blokkerend aandachtspunt uit review/testronde:
`contentType` krijgt `content.mimeType` ongewijzigd mee terwijl de filter op de lowercase-variant
vergelijkt, dus een IME die `IMAGE/PNG` stuurt levert die hoofdlettervariant als multipart-
`Content-Type` — onschadelijk, de backend leest de bytes. Het écht plakken is niet automatisch te
testen (vereist een fysiek toestel met Gboard); eindverificatie handmatig op Robberts telefoon
(screenshot → kopiëren → in de chat plakken → versturen).

Nieuw (SF-1801): **notities-app krijgt een donker thema en een WYSIWYG-opmaakbalk**. Alleen
`notities/`; geen backendwijziging, `notities/lib/api_client.dart` en het contract van
`GET`/`PUT /api/v1/notes` zijn ongemoeid, en `.github/workflows/notities-apk.yml` blijft
ongewijzigd (de nieuwe dependency vraagt geen extra CI-stap of platform-configuratie:
`quill_native_bridge_android` eist `minSdk 24`, precies de `flutter.minSdkVersion` die de app al
gebruikt). **Thema** (`lib/main.dart`): nieuw top-level `notitiesDarkTheme` met
`Brightness.dark`, `useMaterial3: true`, `scaffoldBackgroundColor: Colors.black`,
`ColorScheme.dark(surface: Colors.black)`, een donkere `AppBarTheme` met witte titel/iconen,
witte cursor/selectie en een grijze hint; de oude `colorSchemeSeed: Colors.amber` +
`scaffoldBackgroundColor: Colors.yellow` zijn weg. `_loginView()` is leesbaar op zwart gemaakt
(donkergrijze `Card`, wit `Icons.edit_note`, witte titel, `Colors.white70` voor de uitleg); de
teksten 'Notities' en 'Log in met Google om verder te gaan.' en de inlogflow zijn onveranderd.
**Editor** (`lib/notes_editor_screen.dart`): de kale `TextField` is vervangen door een
`QuillEditor` + `QuillController` (`flutter_quill ^11.5.1`, géén `flutter_quill_extensions`;
`FlutterQuillLocalizations.localizationsDelegates`/`supportedLocales` toegevoegd aan
`MaterialApp` en aan de widget-tests). Direct onder de AppBar een **zelfgebouwde** rij (dus geen
`QuillSimpleToolbar`) met precies vijf `IconButton`s met de tooltips `Vet`, `Cursief`,
`Onderstreept`, `Opsomming` en `Opmaak wissen` (die laatste haalt bold/italic/underline én de
bullet-opmaak van de selectie af); de rij zit in een `ListenableBuilder` op de controller zodat
de actieve staat (accentkleur + gevulde achtergrond) met de selectie meeloopt, en heeft
`ValueKey('opmaakbalk')` als testhaak. De placeholder 'Typ hier je notities…' blijft.
**Opslagformaat** (`lib/markdown_delta.dart`, nieuw, zonder Flutter-widget-afhankelijkheden dus
puur als unittest te draaien): laden is `getNotes()` → `markdownToDelta()` →
`Document.fromDelta`, opslaan altijd `deltaToMarkdown(document.toDelta())` via de bestaande
`api.saveNotes(...)` — er gaat dus **nooit** Delta-JSON naar `/api/v1/notes` (embeds worden
overgeslagen i.p.v. als JSON weggeschreven), wat `assistant/ai/NotesTools.kt` en
`briefing/WeekTasksSectionProvider.kt` intact houdt. Mapping en niets anders: bold `**tekst**`,
italic `*tekst*`, underline `<u>tekst</u>`, bullet = regel die met exact `- ` begint; alle
overige markup (`#`-kopjes, genummerde lijsten, `* `-bullets, inspringing, tabellen, links,
code) is platte tekst en gaat letterlijk heen en terug, lege regels blijven behouden en er wordt
niets ge-escaped. Inline wordt per regel geparseerd (markers lopen niet over regelgrenzen), een
`*`-reeks wordt **atomair** behandeld (`_starRunLength()`/`_findStarRun()`: een opener van lengte
1/2/3 wordt alleen door een reeks van precies die lengte gesloten, een reeks van 4+ is nooit een
marker), een niet-afgesloten marker of een leeg paar (`<u></u>`, `******`) blijft letterlijke
tekst, en schrijven gebeurt genest in een vaste volgorde (underline buiten, dan bold, dan italic
→ `<u>***tekst***</u>`) waarbij aaneengesloten segmenten met hetzelfde kenmerk één markerpaar
delen. Quill's interne afsluitende newline wordt afgeknipt, zodat
`deltaToMarkdown(markdownToDelta(s)) == s` byte-identiek geldt voor notities zonder opmaak.
Autosave (10s debounce, nu gevoed door `document.changes` — het abonnement wordt pas ná het
initiële laden gezet zodat laden geen save triggert), directe save bij `paused`/`inactive`,
best-effort save in `dispose()` (tekst opgehaald vóór `_controller.dispose()`), statusregel
('Opgeslagen' / 'Opslaan mislukt: …'), force-save-knop, Uitloggen, laadspinner en foutmelding
zijn ongewijzigd. Verificatie: `flutter test` in `notities/` → 34 groen, `flutter analyze` →
"No issues found!". Bekende, bewust geaccepteerde aandachtspunten: (1) staat in een handmatig
aangeleverde notitie bold/italic *buiten* underline (`**<u>x</u>**`), dan wordt die bij de
eerste open+opslaan-cyclus genormaliseerd naar de canonieke nestvolgorde (`<u>**x**</u>`) —
semantisch identiek en vanaf cyclus 2 stabiel; in zeldzame gevallen komt daarbij een letterlijk
sterretje naast een hernestte marker te staan, wat zonder escapen (door de story verboden) niet
op te lossen is; (2) een geplakte afbeelding/embed verdwijnt stil bij het opslaan, zonder
melding; (3) `flutter build apk --release` is in de sandbox niet uitvoerbaar (geen Android SDK),
dus de APK-workflow op `main` is de eerste echte bevestiging; (4) `notities/pubspec.lock` legt
nu `dart >=3.12.0` / `flutter >=3.44.0` vast terwijl `pubspec.yaml` `sdk: ^3.9.0` declareert —
met `channel: stable` in CI prima, een oudere Flutter zou op de lockfile stuklopen.

Nieuw (SF-1808): **notities krijgen undo/redo, versiegeschiedenis en nachtelijk opruimen**. Drie
delen. **App-editor** (`notities/lib/notes_editor_screen.dart`): links in de bestaande
zelfgebouwde opmaakbalk (`ValueKey('opmaakbalk')`) staan nu twee extra `IconButton`s met de
tooltips `Ongedaan maken` (`Icons.undo`) en `Opnieuw` (`Icons.redo`), gevoed door de historie die
`QuillController` zelf al bijhoudt (`undo()`/`redo()`, enabled-state uit `hasUndo`/`hasRedo`, dus
`onPressed: null` → uitgegrijsd); de bestaande `ListenableBuilder` op de controller verzorgt het
herteken. Omdat het document nog steeds gezet wordt vóór het `document.changes`-abonnement, staat
het initiële laden niet in de historie — één keer undo na openen maakt de notitie dus nooit leeg.
Undo/redo is een gewone documentwijziging en gaat dus via de bestaande debounce-autosave, geen
aparte save-route. Er is bewust **geen** Ctrl+Z-sneltoets toegevoegd. **Backend** (`notes`-module,
zie §4): elke `PUT /api/v1/notes` (en dus ook een wijziging via `NotesTools`) bewaart een
`NoteVersion` in de nieuwe Firestore-subcollectie `notes/note/versions`, tenzij de tekst identiek
is aan de meest recente bestaande versie; het document `notes/note` en het opslagformaat (platte
markdown) zijn ongewijzigd, net als `GET`/`PUT /api/v1/notes`, `NotesTools` en
`WeekTasksSectionProvider`. Twee nieuwe, auth-gated endpoints: `GET /api/v1/notes/versions`
(`id` + `savedAt`, nieuwste eerst, max 200, zonder tekst) en `GET /api/v1/notes/versions/{id}`
(met tekst, 404 bij onbekend id). `NoteVersionCleanupScheduler` draait elke nacht om 03:30
(Europe/Amsterdam) en verwijdert wat de pure `NoteVersionCleanup.idsToDelete(versions, now)`
aanwijst: binnen 7 dagen blijft alles, daarvóór per kalenderdag alleen de laatste versie — één
INFO-regel per run met het aantal verwijderde versies (`oc logs
deploy/robberts-assistent-backend -n robberts-assistent`). **App-versiescherm**
(`notities/lib/note_versions_screen.dart`, nieuw + `api_client.dart`'s `listNoteVersions()`/
`getNoteVersion(id)` en het type `NoteVersionSummary`): AppBar-actie `Versies` (`Icons.history`)
opent een eigen route met de versielijst (spinner, foutmelding, lege-lijstmelding), per regel
NL datum/tijd in lokale tijd via de eigen helper `formatVersionMoment()` (`vandaag 11:30` /
`gisteren 11:30` / `ma 28 jul 09:05`) — **geen nieuwe dependency**, dus geen `intl`. Tikken opent
een alleen-lezen weergave (`SelectableText` met de platte markdown) met de knop `Terugzetten` +
bevestigingsdialoog (`Annuleren` / `Ja, terugzetten`); terugzetten vervangt de editorinhoud met
`controller.replaceText(0, document.length, …)` — bewust de vólledige lengte inclusief de
afsluitende newline (die draagt in Quill de blok-opmaak van de laatste regel) en bewust een
bewerking op het bestaande document i.p.v. `_controller.document = …`, zodat de undo-historie en
het changes-abonnement intact blijven: het terugzetten is met de undo-knop ongedaan te maken en de
gewone autosave slaat het daarna als nieuwe versie op. Buiten scope gehouden: diff-weergave,
versies benoemen/pinnen/verwijderen vanuit de app, en versiegeschiedenis in `robberts_assistent`
of de chat. Verificatie: `mvn test` → 405 groen (incl. `ModulithArchitectureTest`, `NotesToolsTest`);
`flutter test` in `notities/` → 44 groen; `flutter analyze` → "No issues found!"; live E2E op de
PR-preview (versie per save, geen dubbel bij identieke tekst, volgorde, 404). Bekende,
niet-blokkerende aandachtspunten: (1) de `Versies`-actie is ook actief tijdens `_loading` en na een
mislukte `_load()` — zet je dán een versie terug, dan staat de tekst wel in het document maar is er
nog geen `changes`-abonnement, dus de autosave pikt het niet op (kleinste fix: `onPressed:
(_loading || _error != null) ? null : _openVersions`); (2) `NotesService.update` doet per save een
extra `latestVersions(1)`-query op Firestore, dus elke 10 seconden autosave — functioneel correct,
alleen leesverbruik; (3) faalt één `deleteVersion`, dan stopt de opruimrun (alles zit in één
`runCatching`) met alleen een WARN-regel; de volgende nacht ruimt het alsnog op; (4)
`formatVersionMoment` bepaalt vandaag/gisteren via `Duration.inDays`, waardoor op de dag ná de
zomertijdovergang versies van gisteren één keer per jaar als `vandaag HH:MM` gelabeld worden
(cosmetisch; tijd en volgorde blijven correct); (5) de APK is in de sandbox niet te bouwen — de
`notities-apk.yml`-workflow op `main` is de eerste echte bevestiging.

Nieuw (SF-1809): **de lettergrootte van de bewerkbare notitie is lokaal instelbaar**. Alleen
`notities/lib/notes_editor_screen.dart` en de bijbehorende widgettests zijn gewijzigd; backend,
API-contract, `markdown_delta.dart`, dependencies en versie-/save-logica zijn ongemoeid. De
zelfgebouwde balk bevat vóór undo/redo twee toegankelijke knoppen met zichtbare labels A− en A+ en
de tooltips `Lettergrootte verkleinen` en `Lettergrootte vergroten`. De beschikbare waarden zijn
vast 12, 14, 16, 18, 20, 22, 24, 26 en 28 pt; zonder geldige voorkeur is 16 pt de standaard en op
de grenzen is de betreffende knop disabled. De hele balk zit in een horizontale
`SingleChildScrollView`, zodat ook een 280-pixelbrede viewport niet overloopt. De voorkeur staat
onder `notes_editor_font_size` in de al aanwezige `shared_preferences` en wordt vóór `getNotes()`
gelezen. Een ontbrekende of niet-gehele/ongeldige tussenwaarde valt terug op 16 pt; waarden buiten
het bereik worden op 12/28 begrensd. `QuillEditorConfig.customStyles` overschrijft uitsluitend de
fontgrootte van Quills `paragraph`, `lists` en `leading`: gewone en vet/cursief/onderstreepte tekst,
lijsttekst en bulletmarkering schalen daardoor samen, zonder Delta-attributen te wijzigen. Een
druk op A−/A+ herbouwt alleen de weergave en schrijft de lokale voorkeur; dat markeert de notitie
niet dirty, plant geen autosave en verandert bij handmatig opslaan geen byte van de markdown. De
alleen-lezen versieweergave, AppBar, status en overige knoppen schalen bewust niet mee. Verificatie:
gerichte editortests 21/21 groen, volledige `flutter test` 50/50 groen, `flutter analyze` zonder
issues en `flutter build bundle --release` geslaagd; geen APK-/previewtest omdat de ARM64-sandbox
geen Android SDK heeft en `notities/` APK-only is.

---

## 10. Meer detail

- `docs/factory/README.md` — index van de factory-docs.
- `docs/factory/functional-spec.md` / `technical-spec.md` — functionele/technische afspraken.
- `docs/factory/development.md` — lokaal bouwen/testen; `deployment.md` — deploy-flow + config.
- `docs/foundation-couplings.md` — ontwerp + gefaseerd implementatieplan van de koppelingen.
- `docs/koppelingen-ideeen.md` — kandidaat-koppelingen (ideeën + status) voor uitbreiding.
- `docs/nightly-checks.md` — nightly-check-framework, de OpenShift-gezondheidscheck (incl. nog
  te zetten RBAC), en ideeën voor toekomstige checks (tuin-water, kiten, zonnepanelen, agenda).
- `docs/setup-guide-details.md` — console-setup met concrete waarden (project `tuinbewatering`).
- `PLAN.md` — oorspronkelijke visie (apps, "Hey Google"-aanpak).
