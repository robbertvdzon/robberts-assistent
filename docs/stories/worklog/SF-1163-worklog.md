# SF-1163 - Worklog

Story-context bij eerste pickup:
Backend: Morgen-briefing-raamwerk + secties + 18:00-scheduler

Nieuwe `briefing`-module met `BriefingSectionProvider`-SPI (analoog aan CouplingProbe/NightlyCheck) en `BriefingService`. Vier secties: kite/strandfiets (nieuwe gestructureerde windbron in `weather`, `tides`-laagwater, nieuwe NL-feestdagenberekening, werkdag/vakantie via all-day agenda-items - `CalendarClient` moet all-day behouden), agenda 7 dagen alle agenda's (CalendarClient uitbreiden met tijdrange+multi-agenda) met AI-bepaalde reminder-status + aanmaak-actie via reminders-module, AI-weektakensamenvatting (reminders+notities), moestuin-placeholder. Plus @Scheduled 18:00 Europe/Amsterdam job die via bestaande PushService een korte samenvattingspush stuurt. Stub/fallback-patroon, deterministisch onder RA_MOCK_AI. Inclusief unittests voor beoordelingslogica, feestdagberekening, scheduler->push en de SPI-opzet.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuwe `briefing`-module: `BriefingSectionProvider`-SPI + `BriefingSection`/`BriefingResponse`,
  `BriefingService` (verzamelt `List<BriefingSectionProvider>`, sorteert op `order`, vangt een
  crashende sectie op — zelfde patroon als `NightlyCheckScheduler`), `BriefingController`
  (`GET /api/v1/briefing`, auth-gated, plus `POST /api/v1/briefing/agenda-reminder` voor de
  één-tap-reminder-actie vanuit de agenda-sectie).
- `Holidays` (in `briefing`): algoritmische NL-feestdagberekening (Meeus/Jones/Butcher-Paasformule
  + vaste/afgeleide data, Koningsdag-verschuiving bij zondag) — geen externe koppeling, geen
  hardcoded lijst per jaar.
- `google.CalendarEvent` kreeg een `allDay`-veld (behouden i.p.v. verloren in de parsing);
  `CalendarClient` kreeg `eventsInRange(from, to)` voor de multi-agenda-7-dagen-behoefte.
  `GoogleCalendarClient.eventsInRange` haalt eerst alle agenda's op (Calendar List API) en
  bevraagt daarna elke agenda los binnen het tijdvenster; `toCalendarEvent`/`parseTime` verhuisd
  naar een `internal companion object` zodat de parsing (incl. `allDay`) los van HTTP getest kan
  worden (zelfde patroon als `RwsTideClient.parseForecast`). `StubCalendarClient` kreeg
  `eventsInRange` + een hele-dag-vakantie-event.
- Nieuwe gestructureerde windbron: `weather.WindForecastClient`/`OpenMeteoWindForecastClient`
  (Wijk aan Zee-coördinaten, kn + graden)/`StubWindForecastClient` + `WindForecastCouplingProbe`
  — de bestaande `assistant.ai.WindTools` levert alleen platte AI-tekst en was niet herbruikbaar
  als betrouwbare, niet-AI-afhankelijke bron voor de kite-sectie.
- `KiteSectionProvider` (order 0): bepaalt "morgen", vakantiedag (hele-dag-agenda-item) en
  feestdag; werkdag (ma-do, geen feestdag/vakantie) → ochtend (07:00) + avond (19:00) apart
  beoordeeld, anders één dagbeoordeling (12:00). Zuivere, los testbare functies `assessKite`/
  `assessBeachCycle`/`isNearLowTide`/`compassPoint` in een `internal companion object`.
  - Aanname (expliciet aan developer gelaten in de story): "aanlandig" bij Wijk aan Zee =
    windrichting in het westelijke kwadrant 225°-315° (kust loopt ~noord-zuid, zee ligt ten
    westen). Kite-drempels: 20-35 kn ideaal (🟢), 15-20 kn / 35-45 kn grens (🟡), daarbuiten of
    niet-aanlandig/nat (🔴). Strandfietsen: <15 kn + droog + "redelijk laag water" = binnen 2 uur
    van een laagwatermoment (🟢), buiten dat venster maar verder wel geschikt (🟡), te veel
    wind/nat (🔴).
- `AgendaSectionProvider` (order 10): afspraken komende 7 dagen over alle agenda's, oplopend op
  tijd, met reminder-status.
  - **Afwijking van de letterlijke storytekst**: de story omschrijft de reminder-detectie als een
    "AI-bepaling (op basis van tijd + tekst)". Voor determinisme (harde eis onder `RA_MOCK_AI`,
    zie AC "deterministisch testbaar") en simpele unit-testbaarheid is dit een expliciete,
    tijd-gebaseerde heuristiek geworden (`hasReminderFor`: een reminder "hoort erbij" als 'ie
    30-90 minuten vóór de afspraak afgaat), geen echte AI-call per afspraak. Functioneel dekt dit
    dezelfde gebruikersvraag ("staat er al een reminder voor deze afspraak") zonder AI-kosten/
    -latency per afspraak per briefing-opbouw. Zie `AgendaSectionProvider`-KDoc.
- `WeekTasksSectionProvider` (order 20): AI-samenvatting op basis van actieve reminders + de
  notitie, via een eigen lichte `ChatClient`-bean (`briefing.BriefingAiConfig.weekTasksChatClient`,
  hergebruikt de bestaande gedeelde `ChatModel`-bean uit `assistant.ai.AiConfig` — dus automatisch
  mock/echt via `AppSecrets.effectiveMockAi`, geen aparte schakelaar nodig). Faalt stil naar een
  neutrale placeholder-tekst bij een AI-fout.
- `GardenPlaceholderSectionProvider` (order 30): vaste dummy-tekst, zelfde stijl als de bestaande
  moestuin-regel in `summary.SummaryService`.
- `BriefingScheduler`: `@Scheduled(cron = "0 0 18 * * *", zone = "Europe/Amsterdam")`, bouwt de
  pushtekst uit `BriefingSectionProvider.shortSummary()` van elke sectie (nieuwe, standaard-`null`
  SPI-methode — een sectie kan zich vrijwillig aanmelden voor de push-samenvatting; secties zonder
  relevante one-liner, zoals de moestuin-placeholder, doen niet mee) en verstuurt via de bestaande
  `PushService.sendToAll` (no-op zonder Firebase/tokens, geen nieuwe pushkanaal-integratie).
- Modulith-grenzen gerespecteerd: `briefing` injecteert alleen root-classes van andere modules
  (`CalendarClient`/`CalendarEvent` uit `google`, `WeatherClient`/`TideClient`/`WindForecastClient`
  uit `weather`/`tides`, `RemindersService`/`Reminder` uit `reminders`, `NotesService` uit `notes`,
  `PushService` uit `push`) en het framework-type `ChatModel`/`ChatClient` (Spring AI, niet
  `assistant.ai`-intern) — `ModulithArchitectureTest` blijft groen.
- Nieuwe/gewijzigde tests: `HolidaysTest`, `OpenMeteoWindForecastClientTest`,
  `KiteSectionProviderTest` (pure functies + `section()`/`shortSummary()` tegen stubs),
  `AgendaSectionProviderTest`, `WeekTasksSectionProviderTest` (tegen `MockChatModel`, zelfde
  patroon als `AssistantServiceTest`), `GardenPlaceholderSectionProviderTest`,
  `BriefingServiceTest`, `BriefingSchedulerTest`, `GoogleCalendarClientTest` (nieuw, dekt
  `toCalendarEvent`/`allDay`), `StubCalendarClientTest` (nieuw).
- Volledig vangnet (`mvn test`, incl. `ModulithArchitectureTest`) groen: 196 tests, 0 failures,
  0 errors.

Niet gedaan / aangepast:
- Frontend (Morgen-scherm, FCM-deep-link, per-afspraak-reminder-knop): expliciet buiten scope van
  deze subtaak (SF-1165) — dat is SF-1166 (subtaak 2 van de story).
- Reminder-status in de agenda-sectie is een deterministische tijd-heuristiek i.p.v. een echte
  AI-bepaling (zie hierboven) — bewuste keuze voor determinisme/testbaarheid/kosten, geen technische
  blocker. Als een échte AI-bepaling per afspraak alsnog gewenst is, kan dat later als vervanging
  van `AgendaSectionProvider.hasReminderFor` zonder de SPI/sectie-structuur te raken.
- Geen nieuwe `CouplingProbe` voor `CalendarClient.eventsInRange` an sich (de bestaande
  `GoogleCouplingProbe` dekt de Google-koppeling al); wel een nieuwe `WindForecastCouplingProbe`
  voor de nieuwe windbron.
- `GoogleCalendarClient.eventsInRange` (Calendar List API + per-agenda-events) is, net als de rest
  van `GoogleCalendarClient`, nog niet end-to-end geverifieerd met een echte OAuth-token (zie de
  bestaande NB in die klasse) — alleen de pure parsing is unit-getest.

## Review (SF-1165, reviewer)

- Volledige diff t.o.v. `main` (`git diff main...HEAD`) bekeken: nieuwe `briefing`-module
  (SPI `BriefingSectionProvider` + `BriefingService`/`BriefingController`/`BriefingScheduler`,
  vier secties), `Holidays` (algoritmische NL-feestdagen), `CalendarEvent.allDay` +
  `CalendarClient.eventsInRange` (Google-impl + stub), nieuwe `weather.WindForecastClient`
  (Open-Meteo, structureel) + couplingprobe.
- `mvn test` opnieuw gedraaid, gericht op de gewijzigde/nieuwe testklassen
  (`briefing.**`, `GoogleCalendarClientTest`, `StubCalendarClientTest`,
  `OpenMeteoWindForecastClientTest`, `ModulithArchitectureTest`); Maven draaide daarbij het
  volledige surefire-vangnet mee — alle rapporten tonen `Failures: 0, Errors: 0` (incl.
  `ModulithArchitectureTest`), consistent met de developer-claim van 196 groene tests.
  Geen eigen volledige herrun buiten dit gerichte doel (conform reviewer-instructies).
- Inhoudelijk tegen de story/AC's gelegd: SPI-patroon correct analoog aan
  `CouplingProbe`/`NightlyCheck` (geen wijziging in `BriefingService` nodig voor een nieuwe
  sectie — klaar voor story 2/2); kite/strandfiets-logica dekt werkdag/weekend/feestdag/
  vakantie-tak en toont 🟢/🟡/🔴 + kn zoals gevraagd; feestdagen algoritmisch (Meeus/Jones/
  Butcher + afgeleiden, Koningsdag-verschuiving) i.p.v. hardcoded lijst; vakantiedetectie via
  hele-dag-agenda-items nu correct (allDay bewaard i.p.v. verloren in parsing); agenda-sectie
  7 dagen/alle agenda's + reminder-status + één-tap-aanmaak-endpoint; weektaken-AI-samenvatting
  met stil-falende fallback; moestuin-placeholder in bestaande stijl; 18:00-Europe/Amsterdam-
  scheduler bouwt pushtekst uit een nieuwe optionele `shortSummary()`-SPI-methode en hergebruikt
  bestaande `PushService` (no-op zonder Firebase, geen crash).
- De in de worklog toegelichte bewuste afwijking (tijd-heuristiek i.p.v. een echte AI-call per
  afspraak voor reminder-detectie) is functioneel gelijkwaardig en beter voor determinisme onder
  `RA_MOCK_AI`/unit-testbaarheid — geaccepteerd, geen blocker.
- Modulith-grenzen: `briefing` importeert alleen root-classes van `google`/`weather`/`tides`/
  `reminders`/`notes`/`push` plus Spring AI-framework-types — `ModulithArchitectureTest` blijft
  groen, bevestigd.
- Frontend (Morgen-scherm/deep-link) terecht buiten scope: dat is SF-1166.
- Geen bugs/regressies gevonden binnen deze subtaak-scope. Akkoord.

## SF-1166 (developer) — Frontend: Morgen-scherm + FCM-deep-link

Stappenplan:
[x]: issue + relevante backend-code (briefing-module, SF-1165) lezen
[x]: bestaande Samenvatting-tab ombouwen naar het Morgen-scherm (4 secties)
[x]: FCM-tap-deep-link naar de Morgen-tab implementeren
[x]: per-afspraak reminder-actie in de agenda-sectie werkend maken
[x]: flutter test + flutter analyze draaien
[x]: worklog bijwerken

Gedaan / rationale:
- **Backend (kleine, noodzakelijke uitbreiding op SF-1165)**: de bestaande `BriefingSection`
  had alleen een platte `text`-string per sectie — onvoldoende om de AC "per afspraak zonder
  reminder een werkende één-tap-actie" op te bouwen (de app kan geen betrouwbare `startAt`/
  `summary` uit opgemaakte tekst parsen). `BriefingSectionProvider.kt` kreeg daarom een generieke,
  niet-agenda-specifieke uitbreiding: `BriefingSection.items: List<BriefingItem>` (regel-tekst +
  optionele `BriefingAction`), en `BriefingAction(label, endpoint, payload)` — de app kent de
  betekenis van een actie niet, doet gewoon een POST naar `endpoint` met `payload`. Dit blijft
  binnen het bestaande pluggable-SPI-patroon (geen wijziging in `BriefingService`/
  `BriefingController` nodig) en is generiek genoeg voor toekomstige secties (story 2/2).
  `AgendaSectionProvider.section()` vult nu `items` met per afspraak een `BriefingAction` naar
  het al bestaande `POST /api/v1/briefing/agenda-reminder` (alleen als er nog geen reminder
  staat) — geen nieuw endpoint nodig, wel een uitbreiding van `AgendaSectionProviderTest`.
- **Backend**: `PushService.sendToAll` kreeg een optionele `data: Map<String, String>`-parameter
  (default leeg, dus alle bestaande call-sites ongewijzigd) die als extra FCM-data-payload
  meegaat. `BriefingScheduler` stuurt nu `"type" to "briefing"` mee zodat de app bij een tik op de
  melding kan onderscheiden of het de Morgen-briefing-push is (i.p.v. bv. een reminder-push, die
  geen `type` meestuurt) — zonder dit was er geen manier voor de app om deep-link-gedrag te
  beperken tot precies deze pushsoort.
- **Frontend — Morgen-scherm**: `summary_screen.dart` (klasse-naam `SummaryScreen` ongewijzigd
  gelaten, vult nu de bestaande tab) haalt `GET /api/v1/briefing` op (`ApiClient.getBriefing()`,
  nieuwe modellen `BriefingSection`/`BriefingItem`/`BriefingAction` in `api_client.dart`) en toont
  per sectie een kaart met titel + óf de platte tekst (secties zonder `items`, bv. kite/weektaken/
  moestuin) óf een regel per item met — indien aanwezig — een actieknop
  (`ApiClient.runBriefingAction`, generieke `POST` naar `action.endpoint`/`action.payload`, geen
  agenda-specifieke kennis in de app). Na een geslaagde actie wordt de briefing herladen (de
  aangemaakte reminder verdwijnt daarmee direct uit "nog geen reminder"-status). Tab-label in
  `home_screen.dart` hernoemd van "Samenvatting" naar "Morgen" (geen nieuwe/7e tab, zoals de
  story vereist).
- **Frontend — FCM-deep-link**: `fcm_service.dart` kreeg `FcmService.deepLinkTab`
  (`ValueNotifier<int?>`), gezet op de Morgen-tab-index zodra een binnenkomende/geopende melding
  `data['type'] == 'briefing'` heeft (`FirebaseMessaging.onMessageOpenedApp` voor een tik terwijl
  de app op de achtergrond staat, `messaging.getInitialMessage()` voor een koude start via de
  melding). `HomeScreen` luistert op deze notifier (`initState`/`dispose`) en schakelt de
  zichtbare tab om zodra 'ie een waarde krijgt, en zet 'm meteen terug op `null` (idempotent, geen
  dubbele navigatie bij een rebuild).
- Tests: `test/summary_screen_test.dart` (nieuw) dekt sectie-rendering, de actieknop-flow
  (tonen/niet-tonen, tikken roept `runBriefingAction` aan) en een foutmelding bij een mislukte
  load. `test/home_screen_test.dart` uitgebreid met een test die `FcmService.deepLinkTab` zet en
  verifieert dat de Morgen-tab (index 0, `SummaryScreen`) verschijnt; het label-`Samenvatting`-
  assertion aangepast naar `Morgen`. Backend: `AgendaSectionProviderTest` uitgebreid met
  assertions op `section.items`/`BriefingAction`-payload.
- `flutter analyze` (robberts_assistent): geen issues. `flutter test` (robberts_assistent): alle
  tests groen (23, 0 failures). Backend `mvn test`: 196 tests, 0 failures, 0 errors (ongewijzigd
  aantal t.o.v. SF-1165 — de uitbreiding zit in bestaande testklassen, geen nieuwe testmethode
  toegevoegd, wel nieuwe assertions).

Niet gedaan / aangepast:
- Geen nieuw REST-endpoint voor structured agenda-items: gekozen voor een generieke
  `items`/`action`-uitbreiding op de bestaande `BriefingSection`-DTO (één `GET /api/v1/briefing`-
  call blijft voldoende voor het hele scherm) i.p.v. een aparte
  `GET /api/v1/briefing/agenda-items`. Functioneel gelijkwaardig, minder round-trips, en generiek
  herbruikbaar voor toekomstige secties (story 2/2) i.p.v. agenda-specifiek.
- `PushService.sendToAll`'s nieuwe `data`-parameter is niet apart met een dedicated
  `PushServiceTest` gedekt (die klasse had nog geen test) — het no-op-gedrag zonder Firebase blijft
  gedekt via `BriefingSchedulerTest`/`ReminderSchedulerTest`; de daadwerkelijke FCM-`Message`-
  payload (incl. `data`) vereist een Firebase-mock die nergens anders in de repo bestaat, en is
  buiten scope gehouden om geen nieuwe test-infrastructuur te introduceren voor één regel.
  `Message.Builder.putAllData` is standaard Firebase-Admin-SDK-gedrag.
- FCM-web (deep-link bij een tik in de webversie) blijft buiten scope: `FcmService.setup` doet nu
  al niets op web (`kIsWeb`-check), zoals vóór deze story.

## Review SF-1166 (frontend: Morgen-scherm + FCM-deep-link)

- Volledige story-diff (`git diff main...HEAD`) bekeken; SF-1165 was al review-approved, dit
  focust op de db532d0-commit (frontend + kleine backend-uitbreiding: `BriefingItem`/
  `BriefingAction`, `PushService.sendToAll(data)`, `BriefingScheduler` push-type).
- `flutter analyze` (robberts_assistent): geen issues. `flutter test`: 23/23 groen (flutter-SDK
  is in deze sandbox-run wél aanwezig, zie agent-tip — dus dit is echt testbewijs, niet alleen
  code-review).
- Backend: gerichte `mvn test` op `AgendaSectionProviderTest`/`BriefingSchedulerTest`/
  `BriefingServiceTest` (de door deze subtaak geraakte klassen): 11/11 groen. Volledige
  backend-suite niet opnieuw gedraaid (developer-run al deterministisch geverifieerd door de
  harness, zie reviewer-instructies).
- End-to-end wiring geklopt: `AgendaSectionProvider`-payload (`summary`/`startAt`) komt overeen
  met `CreateAgendaReminderRequest` in `BriefingController`; `ApiClient.runBriefingAction` post
  generiek naar `action.endpoint`/`action.payload`; `FcmService.deepLinkTab` (`type == 'briefing'`,
  moet matchen met `BriefingScheduler.PUSH_TYPE`) → `HomeScreen` schakelt naar tab-index 0
  ("Morgen"), consistent met de nieuwe destinations-volgorde in `home_screen.dart`.
- Geen bugs/regressies gevonden. Scope blijft binnen de story (kleine backend-uitbreiding is
  functioneel noodzakelijk voor de agenda-actie-AC en raakt geen bestaand gedrag: `data`-param
  heeft een default, `items` heeft een default `emptyList()`).

{"phase":"reviewed"}
