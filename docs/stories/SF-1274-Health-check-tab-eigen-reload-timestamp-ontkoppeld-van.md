# SF-1274 - Health check-tab: eigen reload + timestamp, ontkoppeld van Upcoming, uurlijkse auto-update; Software Factory alleen lopende/error-stories

## Story

Health check-tab: eigen reload + timestamp, ontkoppeld van Upcoming, uurlijkse auto-update; Software Factory alleen lopende/error-stories

<!-- refined-by-factory -->

## Scope

De "Upcoming"- en "Health check"-tabs in `robberts_assistent` worden ontkoppeld van elkaar op het gebied van caching, verversen en automatische updates, en de Software Factory-check in het systeemstatusrapport toont voortaan alleen relevante (lopende/error-)stories.

**1. Health check-tab: eigen reload + timestamp**
`health_check_screen.dart` krijgt, analoog aan `summary_screen.dart`, een reload-knop bovenin (spinner tijdens laden, niet opnieuw indrukbaar zolang een refresh loopt) die een refresh-actie aanroept, en een "Bijgewerkt om ..."-regel bovenin op basis van het eigen `updatedAt`-tijdstip van de systeemstatus-data.

**2. Onafhankelijke caching/refresh voor Upcoming en Health check**
Backend en frontend worden zo aangepast dat een refresh van Upcoming de Health check-data niet raakt en andersom. Beide houden een eigen `updatedAt`. Ontwerpvrijheid voor de implementatie (bv. een eigen cache + eigen refresh-endpoint voor de systeemstatus-sectie naast de bestaande briefing-cache, of het opsplitsen van de bestaande briefing-cache in twee onafhankelijk verversbare delen) — zolang het resultaat is: Upcoming toont alle briefingsecties behalve system-status, Health check toont uitsluitend system-status, en beide zijn onafhankelijk verversbaar met een eigen laatst-bijgewerkt-tijdstip.

**3. Uurlijkse automatische update**
Zowel de Upcoming- als de Health check-data wordt voortaan elk uur automatisch ververst (i.p.v. alleen dagelijks om 17:30). De handmatige reload-knoppen blijven daarnaast beschikbaar. De bestaande dagelijkse 18:00-FCM-briefingpush (`BriefingScheduler`) blijft functioneel ongewijzigd — die bouwt nu al rechtstreeks via de `BriefingSectionProvider`-lijst op, los van de cache, en wordt door deze wijziging niet geraakt.

**4. Software Factory-check: alleen lopende/error-stories**
`SystemStatusSectionProvider.softwareFactoryCheckData()` toont niet langer alle stories, maar filtert op: story heeft `error != null`, OF story heeft een gezette `phase` (`phase != null`) én is nog niet gemerged (`merged == false`). Gemergede stories en stories zonder gezette fase (nog niet gestart/gerefined) worden niet getoond. Blijft de lijst na filtering leeg, dan toont de check een nette regel ("geen lopende of error-stories") in plaats van een lege of volledige opsomming.

## Acceptance criteria

- `health_check_screen.dart` toont bovenin een "Bijgewerkt om ..."-regel op basis van het (eigen) `updatedAt`-tijdstip van de systeemstatus-data, en een reload-knop met spinner-status tijdens het laden die niet opnieuw ingedrukt kan worden terwijl een refresh loopt.
- Een refresh vanuit de Health check-tab bouwt/ververst alléén de systeemstatus-data en laat de Upcoming-cache (en diens `updatedAt`) ongewijzigd; omgekeerd laat een refresh vanuit Upcoming de Health check-data (en diens `updatedAt`) ongewijzigd.
- Upcoming blijft alle briefingsecties tonen behalve `system-status`; Health check blijft uitsluitend de `system-status`-sectie tonen (ongewijzigd t.o.v. de bestaande filtering in `health_check_screen.dart`/`summary_screen.dart`).
- Zowel de Upcoming- als de Health check-data wordt automatisch elk uur ververst (nieuwe/aangepaste `@Scheduled`-job(s) in de `briefing`-module), naast de bestaande dagelijkse 17:30-verversing (of vervanging daarvan door de uurlijkse job — geen dubbele opbouwlogica).
- De dagelijkse 18:00-FCM-push (`BriefingScheduler.sendDailyPush()`) blijft functioneel ongewijzigd: zelfde inhoud-opbouw via `BriefingSectionProvider.shortSummary()`, zelfde pushtekst-logica, geen wijziging aan gedrag.
- `SystemStatusSectionProvider.softwareFactoryCheckData()` toont alleen stories met `error != null` of (`phase != null` en `merged == false`); gemergede stories en stories met `phase == null` worden weggelaten.
- Als er na filtering geen stories overblijven, toont de Software Factory-check een duidelijke "geen lopende of error-stories"-regel i.p.v. een lege of ongefilterde lijst.
- De bestaande AI-"aandacht nodig"-beoordeling (voor de 18:00-push) en de overige vier checks (zonnepanelen, backups, OpenShift, robotmaaier) in `SystemStatusSectionProvider` blijven functioneel ongewijzigd.
- Backend: `mvn test` slaagt, inclusief uitgebreide/aangepaste tests in `SystemStatusSectionProviderTest` (Software Factory-filter, met en zonder resterende stories) en tests voor de nieuwe/aangepaste cache-/refresh-/scheduler-logica in de `briefing`-module.
- Frontend: `flutter test` en `flutter analyze` slagen voor `robberts_assistent`, inclusief tests/aanpassingen voor de nieuwe reload-knop/timestamp op de Health check-tab.
- Nederlandstalige code-comments, UI-teksten en commits, conform de repo-conventie.

## Aannames

- De exacte technische invulling van de cache-splitsing (aparte cache + refresh-endpoint voor system-status vs. twee-delen-splitsing van de bestaande briefing-cache) is aan de developer, zoals de issue-description expliciet aangeeft; deze story schrijft alleen het waarneembare gedrag voor (onafhankelijke refresh, eigen `updatedAt` per tab).
- Voor de uurlijkse auto-update wordt aangenomen dat dit een achtergrond-scheduler in de backend is (net als de bestaande 17:30-job), niet een client-side pollingmechanisme in de Flutter-app — consistent met het bestaande cache-patroon waarbij de app alleen leest/handmatig ververst.
- De bestaande dagelijkse 17:30-cache-refresh (`BriefingCacheScheduler`) mag vervangen worden door de nieuwe uurlijkse job(s), zolang het resultaat (elk uur verse data, dagelijkse 18:00-push ongewijzigd) hetzelfde is; er is geen harde eis dat 17:30 als apart moment blijft bestaan.
- "Lopende/openstaande fase" voor de Software Factory-filter is exact zoals in de issue-description gedefinieerd: `phase` is gezet (niet `null`/leeg) én `merged == false`. Er wordt geen aanvullende betekenis aan specifieke fase-waarden (bv. `"done"` zonder merge) toegekend dan deze letterlijke regel.
- Er wordt geen nieuw permanent backend-endpoint verwacht bovenop wat nodig is voor de cache-splitsing/refresh (bv. geen apart endpoint voor uitsluitend de systeemstatus-timestamp) — dat volgt uit de gekozen ontwerpoplossing van punt 2.
- `docs/factory/`-documentatie wordt niet als losstaand acceptatiecriterium bijgewerkt in deze story, aangezien die al als verouderd/niet-authoritatief bekendstaat (zie bestaande agent-tip `docs-factory-stale`) en niet direct dit issue raakt.

## Eindsamenvatting

Ik heb voldoende context uit `.task.md` en het worklog. Nu de eindsamenvatting.

## Eindsamenvatting SF-1274 — Health check-tab: eigen reload + timestamp, ontkoppeld van Upcoming, uurlijkse auto-update; Software Factory alleen lopende/error-stories

**Wat is gebouwd**

- **Health check-tab ontkoppeld van Upcoming.** Backend: de gedeelde briefing-cache is gesplitst in twee onafhankelijke caches (`upcomingBriefingCache` en `healthBriefingCache`), elk met een eigen `updatedAt`, eigen Firestore-document en eigen endpoints — bestaande `GET /api/v1/briefing` + `POST /api/v1/briefing/refresh` blijven voor Upcoming, nieuw zijn `GET /api/v1/briefing/health` + `POST /api/v1/briefing/health/refresh` voor Health check. Frontend: `health_check_screen.dart` laadt nu via deze nieuwe endpoints en kreeg een eigen reload-knop (spinner tijdens laden, niet opnieuw indrukbaar) en een "Bijgewerkt om ..."-regel, naar hetzelfde patroon als `summary_screen.dart` (die zelf ongewijzigd bleef).
- **Uurlijkse automatische update.** De dagelijkse 17:30-cron is vervangen door een uurlijkse job die beide caches ververst (elk in een eigen foutafhandeling, zodat een falende sectie de andere cache niet blokkeert). De dagelijkse 18:00-FCM-push blijft functioneel ongewijzigd — bevestigd doordat die rechtstreeks via de sectie-providers bouwt, los van beide caches (expliciet geverifieerd, geen diff op dat bestand).
- **Software Factory-check gefilterd.** Toont voortaan alleen stories met een fout, of met een gezette fase die nog niet gemerged is. Blijft er niets over, dan verschijnt een nette "geen lopende of error-stories"-melding in plaats van een lege of volledige lijst.

**Gemaakte keuzes**
- De cache is gesplitst in twee volledig gescheiden caches/endpoints (i.p.v. één cache in tweeën op te delen), zodat refreshes elkaar gegarandeerd niet raken.
- De bestaande dagelijkse cache-cron is vervangen door de uurlijkse job in plaats van er een tweede naast te zetten, om dubbele opbouwlogica te vermijden — conform de ruimte die de story daarvoor liet.

**Getest**
- Backend: volledige `mvn test`-suite groen (291 tests), inclusief uitgebreide tests voor de gesplitste cache/scheduler en het nieuwe Software Factory-filter (met én zonder resterende stories).
- Frontend: `flutter analyze` en `flutter test` groen (36 tests), inclusief nieuwe tests voor de reload-knop, spinner-status en timestamp op de Health check-tab.
- Extra end-to-end-verificatie op de live preview: onafhankelijke `updatedAt`'s bevestigd via de echte API, en browser-verificatie (Playwright) dat beide tabs visueel en functioneel losstaan, met screenshots van de reload-flow.

**Bewust niet gedaan**
- `docs/factory/`-documentatie niet bijgewerkt (buiten scope per de story-aannames; volgt in de losse documentatie-subtaak).
- `summary_screen.dart` niet aangeraakt — blijft functioneel ongewijzigd, zoals vereist.

Geen openstaande vragen of blockers vanuit review/test.
