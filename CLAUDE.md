# Robbert's Assistent

Persoonlijke assistent van Robbert: één Kotlin/Spring-Boot-backend op OpenShift met
modulaire **skills**, en meerdere Flutter/Android-apps als kanalen ernaartoe. Dit bestand
is het instappunt voor een AI-agent — lees het eerst, en daarna de specifieke docs onderaan.

Taal in code-commentaar, docs, commits en UI: **Nederlands**.

---

## 1. Wat is dit?

Een backend die via skills allerlei taken doet (notities, wind/kite-check, reminders met
alarm, moestuin-foto-chat, dagelijkse Morgen-briefing) en die door apps + een AI-agent
aangesproken wordt. De AI-agent (OpenAI via Spring AI, met `@Tool`-functies) is tegelijk
de belangrijkste **test-harness**: bijna elke skill is als tool aan de agent gehangen, dus
je test een hele keten met één zin ("zet een reminder over 10 min", "wanneer moet ik naar
de tandarts", "telegram me mijn vakanties").

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
├── robberts_assistent/           ← Flutter app: dagelijkse Morgen-briefing + chat-assistent (web + APK)
├── groentetuin/                  ← Flutter app: moestuin-AI-chat (web op moestuin.vdzonsoftware.nl + APK)
├── notities/                     ← Flutter app: één auto-opslaande notitie (APK)
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
- **Data:** **Firestore** (notities, reminders, alarms, chat-conversaties, FCM-tokens — named
  database `robberts-assistent` in Google-project `tuinbewatering`); **Firebase Storage**
  (moestuin-foto's in map `moestuin/`, assistent-gespreksfoto's in map `assistent-chat/`, bucket
  `tuinbewatering.firebasestorage.app`). Geen SQL-database meer (Neon opgezegd).
- **Auth:** Google-login → eigen HMAC-sessie-token (allowlist `robbert@vdzon.com`).
- **Push:** Telegram (uitgaand) + FCM (`push`-module + app-kant, beide gebouwd — reminders/alarms
  en de dagelijkse Morgen-briefing gebruiken 'm).
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
| `notes` | Eén notitie-string in Firestore (document `notes/note`). |
| `summary` | Oorspronkelijke dagelijkse samenvatting (`GET /api/v1/summary`, incl. nightly-check-resultaten). Sinds de Morgen-briefing (SF-1163, zie `briefing` hieronder) is dit endpoint niet meer aangesloten op een app-scherm — `robberts_assistent` haalt de "Morgen"-tab nu bij `briefing`. Nog niet opgeruimd/vervangen; behouden voor mogelijk hergebruik/opruiming in een latere story. |
| `briefing` | Dagelijkse **Morgen-briefing**: `BriefingSectionProvider`-SPI (net als `CouplingProbe`/`NightlyCheck` injecteert Spring automatisch `List<BriefingSectionProvider>`; een nieuwe sectie toevoegen raakt `BriefingService` niet) en `BriefingController` (`GET /api/v1/briefing`, auth, plus `POST /api/v1/briefing/agenda-reminder` voor de één-tap-reminder-actie). Zes secties (oplopend `order`): `KiteSectionProvider` (`order = 0`, kite-kans voor morgen: per dagdeel `<label>: <emoji> <wind> kn (richting)`, aanlandige wind via `weather.WindForecastClient`, werkdag/feestdag/vakantie-onderscheid, 🟢/🟡/🔴), `BeachCycleSectionProvider` (`order = 5`, sinds SF-1192 een eigen kaart i.p.v. samengevoegd met kiten: per dagdeel een bolletje MET onderbouwing — wind (kn + richting), regen (mm of droog/nat) en getij (laagwater-nabijheid + tijd) — zodat het oordeel navolgbaar is), beide gebouwd op de gedeelde, niet-Spring `SlotAssessmentProvider` (in `KiteSectionProvider.kt`: dagdeel-/werkdag-/vakantielogica + `assessKite`/`assessBeachCycle`-beoordeling op basis van `WindForecastClient`, `WeatherClient` en laagwater via `tides`, zodat beide secties dezelfde netwerkcalls hergebruiken i.p.v. dupliceren), `AgendaSectionProvider` (afspraken komende 7 dagen, alle agenda's via `CalendarClient.eventsInRange`, reminder-status per afspraak — deterministische tijd-heuristiek i.p.v. een AI-call, zie klasse-KDoc — met `BriefingAction` om een ontbrekende reminder ~1u vooraf aan te maken), `WeekTasksSectionProvider` (AI-samenvatting van reminders + notitie via een eigen `weekTasksChatClient`, `briefing.BriefingAiConfig`, stil-falende fallback), `GardenPlaceholderSectionProvider` (dummy-regel, zelfde stijl als `SummaryService`), `SystemStatusSectionProvider` (`order = 40`, systeem-checkrapport: bundelt zonnepanelen (dummy), backups (dummy), OpenShift-gezondheid via `openshift.OpenShiftClient`, robotmaaier via `automower.AutomowerClient`, en Software Factory via `softwarefactory.SoftwareFactoryClient` tot ruwe data; een eigen `systemStatusChatClient`, `briefing.BriefingAiConfig`, bepaalt per check "aandacht nodig" — geen hardcoded drempel in code — en levert de rapporttekst; stil-falende fallback bij een AI-fout of falende onderliggende client (`runCatching` per check), `shortSummary()` alleen niet-`null` als minstens één check aandacht nodig heeft). `shortSummary()` voor de 18:00-push blijft alleen bij `KiteSectionProvider` en `SystemStatusSectionProvider` (strandfietsen levert `null`, dus draagt niet bij aan de push). `Holidays`: algoritmische NL-feestdagberekening (Meeus/Jones/Butcher-Paasformule + afgeleiden), geen externe koppeling of hardcoded jaarlijkse lijst. `BriefingScheduler`: `@Scheduled(cron = "0 0 18 * * *", zone = "Europe/Amsterdam")` bouwt een korte pushtekst uit elke sectie's optionele `shortSummary()` en verstuurt via `PushService.sendToAll` (`data["type"] = "briefing"` voor de app-deep-link) — de systeemstatus-sectie draagt hieraan alleen bij als er aandacht nodig is. |
| `assistant` | Chat-assistent met persistente **gesprekken**: multi-turn, foto's (vision), zelf-verzonnen titel; conversaties in Firestore (`assistant-conversations`, `Conversation`/`FirestoreConversationRepository`, in-memory fallback), foto's via `PhotoStorage`/`FirebaseStoragePhotoStorage` (zelfde patroon als `gardenchat`). Gesprekken zijn te **archiveren/de-archiveren** (`archived`-veld, reversibel) en te **verwijderen** (hard delete incl. best-effort foto-opruiming); `ConversationRepository.listAll()` filtert/pagineert (`includeArchived`, `limit`, `offset`, gesorteerd op `updatedAt` descending). Daarnaast een gebruiker-breed, automatisch bijgewerkt **geheugen**: `MemoryRepository` (`current()`/`update(text)`, Firestore-collectie `assistant-memory` met één tekst-document, in-memory fallback) — één vrije-tekst-string in plaats van losse items. Na elke chat-beurt herschrijft een losse, stil falende AI-aanroep (`memoryChatClient`) de volledige geheugen-tekst op basis van de huidige tekst + de laatste uitwisseling; die tekst gaat als contextprefix mee in elke volgende chat-aanroep. Onder `RA_MOCK_AI` wordt de geheugen-update overgeslagen (deterministisch). `assistant/ai/`: `AiConfig` (ChatClients + model-keuze, incl. `memoryChatClient`), tools (`NotesTools`, `WindTools`, `WeatherTools`, `TideTools`, `AirQualityTools`, `NewsTools`, `WasteTools`, `AutomowerTools`, `StravaTools`, `SoftwareFactoryTools`, `OpenShiftTools`, `ReminderTools`, `CalendarTools`, `DocsTools`), `MockChatModel`. |
| `reminders` | Reminder-model + repository-port (Firestore/in-memory), REST-controller, `@Scheduled ReminderScheduler` (due → `Notifier`). |
| `gardenchat` | Moestuin-AI-chat: multipart (tekst + foto's) → vision-AI; conversaties in Firestore, foto's in Firebase Storage; multi-turn. |
| `google` | `CalendarClient` + `DocsClient` (echt via OAuth refresh-token, of stubs) + `GoogleOAuthService`. |
| `weather` | `WeatherClient`: regen-/weersvoorspelling bij de moestuin (Luttik Cie 12, Heemskerk) via Open-Meteo (keyless, altijd echt); `StubWeatherClient` alleen voor tests. Plus `WindForecastClient`/`OpenMeteoWindForecastClient`: gestructureerde windvoorspelling (kn + graden) bij Wijk aan Zee voor de kite-sectie van `briefing` (i.p.v. de platte AI-tekst van `WindTools`); `StubWindForecastClient` voor tests, `WindForecastCouplingProbe` op het Koppelingen-scherm. |
| `tides` | `TideClient`: getijvoorspelling (hoog-/laagwater, waterhoogte) bij IJmuiden buitenhaven via RWS WaterWebservices (keyless, altijd echt); `StubTideClient` alleen voor tests. |
| `airquality` | `AirQualityClient`: luchtkwaliteit/UV-index/pollen bij de moestuin via Open-Meteo Air-Quality-API (keyless, altijd echt); `StubAirQualityClient` alleen voor tests. |
| `news` | `NewsClient`: laatste nieuwskoppen via RSS (standaard NOS Algemeen, keyless, altijd echt); `StubNewsClient` alleen voor tests. |
| `waste` | `WasteClient`: afvalophaalkalender voor Robberts huisadres via de HVC Groep-API (keyless, altijd echt; postcode/huisnummer als constante, geen secret); `StubWasteClient` alleen voor tests. |
| `automower` | `AutomowerClient`: robotmaaier (Husqvarna Automower Connect API, `client_credentials`) — status + starten/parkeren; `RA_HUSQVARNA_APP_KEY`/`_APP_SECRET` bepalen echt vs. `StubAutomowerClient`. |
| `strava` | `StravaClient`: Robberts trainingen via Strava API v3 (OAuth refresh-token, zelfde patroon als Google Agenda/Docs, `StravaOAuthService`); `RA_STRAVA_CLIENT_ID`/`_CLIENT_SECRET`/`_REFRESH_TOKEN` bepalen echt vs. `StubStravaClient`. |
| `softwarefactory` | `SoftwareFactoryClient`: bridge naar de software-factory-dashboard-backend (cluster-intern, `http://softwarefactory-dashboard-backend.software-factory`) — stories + actiepunten, via dezelfde REST-API als de software-factory-frontend. Logt zelf in met een Google ID-token (zelfde OAuth-client als de app-login, `googleClientId`, maar een eigen refresh-token) → sessie-token, gecachet. `RA_SOFTWAREFACTORY_CLIENT_SECRET`/`_REFRESH_TOKEN` bepalen echt vs. `StubSoftwareFactoryClient`. |
| `openshift` | `OpenShiftClient`: clustergezondheid (ClusterVersion/ClusterOperators) via de in-cluster ServiceAccount-token van de pod zelf (geen los secret — wel de expliciete vlag `RA_OPENSHIFT_HEALTH_ENABLED`, want de benodigde RBAC bestaat nog niet, zie `docs/nightly-checks.md`); `StubOpenShiftClient` anders. |
| `firebase` | `FirebaseProvider`: gedeelde FirebaseApp → named Firestore-db + Storage-bucket. |
| `notifier` | `Notifier`-port; `TelegramNotifier` (echt) of `LoggingNotifier` (fallback). |
| `push` | `PushService.sendToAll(title, body, data)`: FCM-push naar alle geregistreerde tokens (`FcmTokenStore`), no-op zonder Firebase/tokens; `data` gaat als extra FCM-data-payload mee (bv. `"type" to "briefing"`) zodat de app op basis daarvan kan deep-linken. `PushController` (token-registratie), `FcmCouplingProbe`. |
| `couplings` | `CouplingProbe`-SPI + `CouplingsService`: elke module registreert een `@Component` die `CouplingProbe` implementeert (id/naam/omschrijving/configured/mode/test); Spring injecteert automatisch `List<CouplingProbe>`. Voedt het "Koppelingen"-scherm in de app — een nieuwe koppeling toevoegen betekent alleen een nieuwe `CouplingProbe`-implementatie in de eigen module, geen wijziging hier of in de app. |
| `nightlychecks` | `NightlyCheck`-SPI + `NightlyCheckScheduler`/`NightlyChecksService`: net als `couplings`, maar voor achtergrondchecks — elke module registreert een `@Component` met een eigen cron-schema; resultaten (met historie) in Firestore/in-memory. Voedt de "Nachtchecks"-tab in de app + `summary.SummaryService` (dat endpoint heeft sinds de Morgen-briefing (SF-1163) geen app-consument meer, zie de `summary`-rij hieronder). Sinds SF-1164 heeft de Morgen-briefing ook een eigen, live (niet nachtelijk-historisch) systeem-checkrapport, zie de `briefing`-rij (`SystemStatusSectionProvider`) — dat gebruikt bewust een live check i.p.v. `NightlyCheckRepository`-historie. Zie `docs/nightly-checks.md`. |

Twee `ChatClient`-beans: `assistantChatClient` (`@Primary`, met tools) en `gardenChatClient`
(`@Qualifier`, vision, eigen system-prompt).

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
| Strava | `RA_STRAVA_CLIENT_ID` + `_CLIENT_SECRET` + `_REFRESH_TOKEN` | `StubStravaClient` |
| Software Factory | `RA_SOFTWAREFACTORY_CLIENT_SECRET` + `_REFRESH_TOKEN` | `StubSoftwareFactoryClient` |
| OpenShift-gezondheid | `RA_OPENSHIFT_HEALTH_ENABLED=true` (RBAC nog te zetten, zie `docs/nightly-checks.md`) | `StubOpenShiftClient` |

Firebase-credentials: **`_JSON`** (inhoud, voor prod/sealed) of **`_FILE`** (pad, lokaal). De
selector-configs vangen init-fouten af en vallen terug op in-memory (geen crashloop).
Preview-omgevingen blanken `RA_FIREBASE_PROJECT_ID` → schrijven niet naar de echte Firestore.

---

## 6. Apps

- **`robberts_assistent/`** — eerste tab is nu **"Morgen"** (`summary_screen.dart`, was
  "Samenvatting"): dagelijkse briefing met de 4 secties van `briefing` (kite/strandfiets, agenda
  komende 7 dagen met per-afspraak een reminder-aanmaak-actie waar nodig, AI-weektakensamenvatting,
  moestuin-placeholder), opgehaald via `ApiClient.getBriefing()` (`GET /api/v1/briefing`). Een tik
  op de dagelijkse 18:00-FCM-push (`data['type'] == 'briefing'`) opent deze tab automatisch
  (`FcmService.deepLinkTab`, afgehandeld in `home_screen.dart`). Daarnaast: chat-assistent met
  persistente, benoemde gesprekken (gesprekkenlijst → chatscherm, foto's via camera/galerij, net
  als `groentetuin`). In `conversations_screen.dart`: de eerste 10 (niet-gearchiveerde) gesprekken
  direct zichtbaar, oudere onder een uitklapbare "Ouder"-sectie; swipe-links (`flutter_slidable`)
  toont Archiveren/Verwijderen (verwijderen met bevestigingsdialoog); een AppBar-toggle laat
  gearchiveerde gesprekken alsnog zien. Plus Koppelingen-, Nachtchecks- en **Geheugen**-schermen
  (`memory_screen.dart`: één groot bewerkbaar tekstveld met de volledige geheugen-tekst,
  auto-save net als `notities/lib/notes_editor_screen.dart`) bereikbaar via
  `more_screen.dart`. Google-login (web: GIS-knop, mobiel: `signIn()`). Web op OpenShift
  (`robberts-assistent.vdzonsoftware.nl`) + APK.
- **`groentetuin/`** — moestuin-AI-chat: login → foto's maken/kiezen + tekst → vision-antwoord,
  multi-turn. `ApiClient.gardenChat` (multipart). Web op `moestuin.vdzonsoftware.nl` + APK.
  App-id blijft `nl.vdzon.groentetuin` (interne naam ≠ publieke host "moestuin").
- **`notities/`** — één auto-opslaande notitie, Google-login. Alleen APK.
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
nodig, overleeft Doze), `AlarmReceiver` → `AlarmService` (foreground-service, loopende alarm-ringtoon +
trillen) toont een full-screen `AlarmActivity` over het lockscreen met **Sluit** en **Snooze**;
`BootReceiver` herplant na reboot (persistentie in SharedPreferences).

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
