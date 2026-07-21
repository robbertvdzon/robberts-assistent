# SF-1164 - Worklog

Story-context bij eerste pickup:
SystemStatusSectionProvider: systeem-checkrapport in de Morgen-briefing

Nieuwe `SystemStatusSectionProvider` (`briefing`-module, `@Component`, `order = 40`) die vijf checks bundelt tot één sectie: zonnepanelen (dummy), backups (dummy), OpenShift-gezondheid (via `OpenShiftClient.clusterHealth()`/`OpenShiftHealthNightlyCheck.run()`), robotmaaier (via `AutomowerClient.status()`, error/state-afleiding), Software Factory (via `SoftwareFactoryClient.stories()`/`myActions()`). Voeg een nieuwe losse `systemStatusChatClient`-bean toe aan `briefing/BriefingAiConfig.kt` (zelfde patroon als `weekTasksChatClient`, hergebruikt de bestaande `ChatModel`-bean, dus deterministisch onder `RA_MOCK_AI`) die per check bepaalt of er 'aandacht nodig' is en een korte rapporttekst teruggeeft. Implementeer een stille fallback (neutrale tekst, geen aandachtspunten) bij een AI-fout, en vang fouten van de onderliggende clients per check af zodat de sectie nooit crasht. `shortSummary()` geeft alleen een tekst terug als minstens één check aandacht nodig heeft, anders `null`. Geen wijziging aan `BriefingService`/`BriefingController`/`BriefingScheduler`. Schrijf bijbehorende unit tests (provider-rapporttekst per check-combinatie, shortSummary()-aan/afwezigheid, AI-fout-fallback), volgens het testpatroon van `WeekTasksSectionProviderTest`/`KiteSectionProviderTest`.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `briefing.SystemStatusSectionProvider` toegevoegd (`@Component`, `order = 40`): verzamelt per
  check alleen ruwe feiten (zonnepanelen/backups: vaste dummy-tekst; OpenShift via
  `OpenShiftClient.clusterHealth()`; robotmaaier via `AutomowerClient.status()`
  (`activityDescription`/`stateDescription`, errorCode, state); Software Factory via
  `SoftwareFactoryClient.stories()`), elk individueel met `runCatching` afgeschermd zodat een
  falende onderliggende client de sectie niet laat crashen.
- Nieuwe `systemStatusChatClient`-bean in `BriefingAiConfig.kt` (zelfde patroon als
  `weekTasksChatClient`, hergebruikt de bestaande `ChatModel`-bean → deterministisch onder
  `RA_MOCK_AI`). De AI krijgt een vast antwoordformaat opgelegd ("AANDACHT: <lijst>"/"AANDACHT:
  geen" op regel 1, gevolgd door de rapporttekst) zodat de code zelf geen hardcoded
  aandacht-drempel hoeft te bevatten — de AI bepaalt volledig zelf per check of er aandacht nodig
  is; `parseAiReply` (internal companion-functie, los testbaar) leest dat antwoord uit.
- Faalt de AI-call (exception, leeg antwoord) dan valt de sectie stil terug op een neutrale tekst
  zonder aandachtspunten (`shortSummary()` geeft dan `null`) — geen crash.
- `shortSummary()` geeft alleen een `⚠️ ...`-tekst terug als de AI minstens één aandachtspunt
  meldt, anders `null`; bestaande `BriefingScheduler.buildPushBody()` (`mapNotNull`) slaat 'm dan
  automatisch over — geen wijziging aan `BriefingService`/`BriefingController`/`BriefingScheduler`
  nodig.
- Nieuwe tests: `SystemStatusSectionProviderTest` (rapporttekst/shortSummary per AI-antwoord,
  AI-fout-fallback, individuele client-fouten (OpenShift/Automower/Software Factory) crashen de
  sectie niet, ruwe maaier-/netwerkfoutdata komt in de AI-prompt terecht, determinisme onder
  `MockChatModel`, en losse tests voor de pure `parseAiReply`-functie).
- Getest: `mvn -o test` — 210/210 groen (volledige backend-testsuite), incl.
  `ModulithArchitectureTest` (geen module-grensschending) en de vier bestaande
  `briefing`-testklassen. Geen frontend-wijziging nodig (generieke sectie-rendering, zie scope).

## Testronde (tester, SF-1174)

- `mvn -o test` (robberts-assistent-backend/): start 2026-07-21T07:58:17Z, eind 2026-07-21T07:58:42Z,
  Maven "Total time: 23.694 s" — 210/210 groen, 0 failures, 0 errors (incl. `ModulithArchitectureTest`
  en de nieuwe `SystemStatusSectionProviderTest`).
- Code-review diff (`SystemStatusSectionProvider.kt`, `BriefingAiConfig.kt`): geen wijziging aan
  `BriefingService`/`BriefingController`/`BriefingScheduler` (grep bevestigt geen diff). `order = 40`
  na kite(0)/agenda(10)/week-tasks(20)/moestuin(30).
  `runCatching` per onderliggende client (OpenShift/Automower/Software Factory) + stille AI-fallback
  (`FALLBACK_TEXT`, geen aandachtspunten) aanwezig zoals in de story vereist.
- Live preview (`robberts-assistent-pr-15`, `RA_MOCK_AI`): `GET /api/v1/briefing` toont de sectie
  `"key":"system-status","title":"Systeemstatus"` als vijfde/laatste sectie, met ruwe data van alle
  vijf checks (zonnepanelen/backups dummy, OpenShift-stub, Automower-stub, Software Factory met
  echte open stories) in de AI-prompt. `MockChatModel`'s echo-antwoord matcht niet het
  `AANDACHT:`-formaat → `parseAiReply` valt terug op de volledige tekst als rapport zonder
  aandachtspunten → `shortSummary()` = null (bevestigd deterministisch, geen crash zonder secrets).
- Screenshot (`screenshots/sf1164-morgen-tab.png`, `sf1164-systeemstatus-section.png`): "Morgen"-tab
  in `robberts_assistent`-webpreview toont de nieuwe "Systeemstatus"-sectie onderaan, na Kiten,
  Agenda, Deze week en Moestuin — conform AC. Geen frontend-codewijziging nodig/aanwezig (generieke
  sectie-rendering, zoals scope aangeeft).
- Geen bugs gevonden; alle acceptatiecriteria geverifieerd.
