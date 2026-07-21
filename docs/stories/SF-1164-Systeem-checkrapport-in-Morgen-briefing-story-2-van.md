# SF-1164 - Systeem-checkrapport in Morgen-briefing (story 2 van 2)

## Story

Systeem-checkrapport in Morgen-briefing (story 2 van 2)

<!-- refined-by-factory -->

## Scope

Voegt een vijfde briefingsectie toe — het **systeem-checkrapport** — aan het bestaande
pluggable Morgen-briefing-raamwerk uit story 1 (`briefing.BriefingSectionProvider`-SPI). Geen
wijziging aan `BriefingService`/`BriefingController`/`BriefingScheduler` nodig; alleen een
nieuwe provider-implementatie (en een nieuwe AI-`ChatClient`, analoog aan
`WeekTasksSectionProvider`/`BriefingAiConfig`).

**Backend**
- Nieuwe `SystemStatusSectionProvider` (`briefing`-module), `order` na de bestaande vier secties
  (kite=0, agenda=10, weektaken=20, moestuin=30 → systeemstatus=40), zodat het rapport ónder de
  dag-punten uit story 1 verschijnt.
- Verzamelt per check ruwe data uit de bestaande modules:
  - **Zonnepanelen**: dummy-data (geen echte koppeling — placeholder, zelfde stijl als
    `GardenPlaceholderSectionProvider`).
  - **Backups**: dummy-data (nog te bouwen koppeling — placeholder).
  - **OpenShift-gezondheid**: hergebruikt de bestaande `OpenShiftHealthNightlyCheck.run()` (of
    rechtstreeks `OpenShiftClient.clusterHealth()`) — geen nieuwe gezondheidslogica.
  - **Robotmaaier**: `AutomowerClient.status()` — "in error/fout" wordt afgeleid uit
    `MowerStatus.errorCode != 0` en/of `state` in (`ERROR`, `FATAL_ERROR`, `ERROR_AT_POWER_UP`).
  - **Software Factory**: `SoftwareFactoryClient.stories()` — "aandacht nodig" bij stories met
    `error != null` en/of nog niet afgeronde/`merged == false` stories die op Robberts actie
    wachten (`myActions()` kan hiervoor eveneens gebruikt worden).
- Eén AI-aanroep (nieuwe, losse `ChatClient` zonder tools/historie, net als
  `weekTasksChatClient`) krijgt de ruwe data van alle vijf checks en bepaalt per check of er
  "aandacht nodig" is; retourneert een korte rapporttekst voor het scherm. Faalt de AI-call, dan
  valt de sectie terug op een neutrale/deterministische tekst (zelfde stille-fallback-patroon als
  `WeekTasksSectionProvider`) — geen crash, en deterministisch onder `RA_MOCK_AI` (bestaande
  `MockChatModel`-bean, geen nieuwe mock-schakelaar nodig).
- `shortSummary()` van deze provider levert **alleen** een tekst als minstens één check
  aandacht nodig heeft (bv. "⚠️ maaier in error"); anders `null`, zodat de bestaande
  `BriefingScheduler`-logica (`mapNotNull`) 'm automatisch overslaat in de 18:00-pushtekst —
  geen wijziging aan `BriefingScheduler` nodig.
- Volgt het bestaande stub/fallback-patroon van elke onderliggende koppeling (Stub-clients
  geven al foutloze/lege resultaten) — geen crash zonder secrets/koppelingen.

**Frontend**
- Geen scope in deze story — het bestaande "Morgen"-scherm rendert automatisch elke sectie uit
  `GET /api/v1/briefing`, dus de nieuwe sectie verschijnt zonder app-wijziging (zelfde generieke
  sectie-rendering als de vier bestaande secties).

## Acceptance criteria

- [ ] Een nieuwe `BriefingSectionProvider`-`@Component` (`briefing`-module) levert het
      systeem-checkrapport; `BriefingService`, `BriefingController` en `BriefingScheduler` zijn
      niet gewijzigd (SPI-patroon uit story 1 blijft intact).
- [ ] Het rapport verschijnt in `GET /api/v1/briefing` als sectie ná de vier bestaande secties
      (kite, agenda, weektaken, moestuin) en dus ook onderaan het "Morgen"-scherm.
- [ ] Het rapport dekt vijf checks: zonnepanelen (dummy), backups (dummy), OpenShift-gezondheid
      (via bestaande `openshift`-module), robotmaaier-status (via bestaande `automower`-module,
      niet in error/fout), en Software Factory (openstaande/error-stories via bestaande
      `softwarefactory`-module).
- [ ] Per check bepaalt een AI-aanroep of er "aandacht nodig" is (geen hardcoded drempel in
      code); bij een AI-fout valt de sectie stil terug op een neutrale tekst zonder te crashen.
- [ ] De 18:00-FCM-push bevat een systeemstatus-vermelding **alleen** als minstens één check
      aandacht nodig heeft (bv. "⚠️ maaier in error"); zijn alle checks in orde, dan blijft de
      systeemstatus buiten de pushtekst.
- [ ] Alles werkt zonder secrets/koppelingen via de bestaande stub/fallback-clients (geen
      crashloop) en is deterministisch testbaar onder `RA_MOCK_AI`/preview-omgevingen.
- [ ] Backend-tests dekken: de nieuwe provider (rapporttekst inclusief/exclusief
      aandacht-signalen per check-combinatie), de shortSummary()-aan/afwezigheid-logica, en een
      AI-fout-fallback — volgens hetzelfde testpatroon als `WeekTasksSectionProviderTest`/
      `KiteSectionProviderTest`.

## Aannames

- Het systeem-checkrapport wordt gebouwd als **één** nieuwe `BriefingSectionProvider`
  (bv. `SystemStatusSectionProvider`) die alle vijf checks bundelt tot één sectie/rapport, in
  plaats van vijf losse secties — dit sluit aan bij "het rapport" (enkelvoud) in de story-tekst
  en bij de wens van één korte push-vermelding voor het geheel.
- De AI-drempelbepaling gebeurt via één nieuwe, losse `ChatClient` (analoog aan
  `weekTasksChatClient`/`BriefingAiConfig`) die de ruwe checkdata van alle vijf checks in één
  keer krijgt en per check "aandacht nodig: ja/nee" + korte toelichting teruggeeft — geen aparte
  AI-call per check (voorkomt 5x AI-latency/kosten per briefing, zelfde overweging als de
  gekozen deterministische aanpak in story 1 voor de agenda-reminder-detectie, maar hier expliciet
  wél AI omdat de story dat vraagt).
- OpenShift-gezondheid wordt herbruikt via de bestaande `OpenShiftHealthNightlyCheck.run()`
  (of rechtstreeks `OpenShiftClient.clusterHealth()`) — geen nieuwe gezondheidslogica, geen
  afhankelijkheid van de nachtelijk-opgeslagen `NightlyCheckRepository`-historie (die geeft een
  verouderd resultaat t.o.v. een live check op briefing-moment).
- "Niet in error/fout" voor de maaier wordt afgeleid uit `MowerStatus.errorCode != 0` en/of
  `state` in (`ERROR`, `FATAL_ERROR`, `ERROR_AT_POWER_UP`); de developer mag deze exacte mapping
  verfijnen.
- "Stories die nog gedaan moeten worden of op error staan" voor Software Factory wordt afgeleid
  uit `SoftwareFactoryClient.stories()` (`error != null`, en/of niet-afgeronde/`merged == false`
  stories) — de developer bepaalt de precieze filtering, eventueel aangevuld met `myActions()`
  voor stories die op Robberts actie wachten.
- Zonnepanelen en backups blijven volledig dummy (vaste placeholder-tekst/-data), zoals expliciet
  in de story vermeld — geen nieuwe koppeling of `AppSecrets`-key in deze story.
- Er komt geen app-wijziging: het bestaande "Morgen"-scherm rendert secties generiek uit de
  briefing-response, dus de nieuwe sectie verschijnt zonder frontend-aanpassing.

## Eindsamenvatting

## Eindsamenvatting SF-1164 — Systeem-checkrapport in Morgen-briefing (story 2 van 2)

**Wat is gebouwd**
Een vijfde sectie in de dagelijkse Morgen-briefing: het systeem-checkrapport. Nieuwe `SystemStatusSectionProvider` (`briefing`-module, `@Component`, `order = 40`) verschijnt onderaan het bestaande sectierijtje (na kite, agenda, weektaken, moestuin) via de al bestaande `BriefingSectionProvider`-SPI uit story 1 — `BriefingService`, `BriefingController` en `BriefingScheduler` zijn niet aangeraakt.

De provider bundelt vijf checks tot één rapport:
- **Zonnepanelen** en **backups**: vaste dummy-tekst (bewust, geen koppeling in deze story).
- **OpenShift-gezondheid**: via de bestaande `OpenShiftClient.clusterHealth()`.
- **Robotmaaier**: via `AutomowerClient.status()` (state/errorCode).
- **Software Factory**: via `SoftwareFactoryClient.stories()`.

Per check is de aanroep individueel met `runCatching` afgeschermd, zodat een falende onderliggende koppeling nooit de hele sectie laat crashen.

**Belangrijkste keuze**
Eén nieuwe, losse `systemStatusChatClient`-bean (`BriefingAiConfig.kt`, zelfde patroon als `weekTasksChatClient`) krijgt de ruwe data van alle vijf checks in één AI-aanroep en bepaalt zelf, zonder hardcoded drempel in code, welke checks aandacht nodig hebben. De AI antwoordt in een vast formaat (`AANDACHT: <lijst>`/`AANDACHT: geen` + rapporttekst), uitgelezen door een losse, apart testbare `parseAiReply`-functie. Faalt de AI-call, dan valt de sectie stil terug op een neutrale tekst zonder aandachtspunten — geen crash. `shortSummary()` geeft alleen een tekst terug als de AI minstens één aandachtspunt meldt; anders `null`, zodat de bestaande push-logica (`mapNotNull` in `BriefingScheduler`) 'm automatisch overslaat in de 18:00-FCM-push.

**Getest**
- `mvn -o test`: 210/210 groen (volledige backend-suite, incl. `ModulithArchitectureTest` — geen module-grensschending).
- Nieuwe `SystemStatusSectionProviderTest`: rapporttekst/shortSummary per AI-antwoordcombinatie, AI-fout-fallback, individuele client-fouten (OpenShift/Automower/Software Factory) crashen niet, determinisme onder `MockChatModel`, losse tests voor `parseAiReply`.
- Live geverifieerd op preview `robberts-assistent-pr-15` onder `RA_MOCK_AI`: `GET /api/v1/briefing` toont de sectie (`"key":"system-status"`) als vijfde/laatste sectie met correcte fallback-tekst; screenshot bevestigt de "Systeemstatus"-sectie onderaan het "Morgen"-scherm.
- Geen bugs gevonden tijdens de testronde; alle acceptatiecriteria geverifieerd.

**Bewust niet gedaan**
- Geen frontend-codewijziging — het "Morgen"-scherm rendert secties generiek, dus deze sectie verschijnt automatisch zonder app-aanpassing.
- Geen echte koppeling voor zonnepanelen/backups (blijft dummy, zoals expliciet in de story).
- Geen nieuwe gezondheidslogica voor OpenShift; hergebruikt de bestaande client rechtstreeks in plaats van de nachtelijk-opgeslagen historie (voorkomt verouderde resultaten op briefing-moment).
