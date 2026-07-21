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
