# SF-1274 - Worklog

Story-context bij eerste pickup:
Health check-tab ontkoppelen van Upcoming + Software Factory-filter

Backend (briefing-module): splits de gedeelde BriefingCacheRepository/BriefingService in twee onafhankelijke caches met elk een eigen updatedAt - Upcoming (alle BriefingSectionProvider's behalve SystemStatusSectionProvider) en Health check (uitsluitend system-status), elk met een eigen GET/POST-refresh-endpoint in BriefingController (bestaande /api/v1/briefing + /refresh voor Upcoming, nieuwe analoge endpoints voor de health-check-cache). Vervang de dagelijkse 17:30-cron (BriefingCacheScheduler) door een uurlijkse job die beide caches ververst; laat BriefingScheduler (18:00-FCM-push, bouwt al buiten de cache om) ongewijzigd en verifieer dat expliciet. Pas SystemStatusSectionProvider.softwareFactoryCheckData() aan zodat alleen stories met error != null of (phase != null en merged == false) getoond worden, met een nette 'geen lopende of error-stories'-regel als na filtering niets overblijft; schrijf/breid hiervoor SystemStatusSectionProviderTest uit. Frontend (robberts_assistent/lib): api_client.dart krijgt methodes voor de nieuwe health-check-cache-endpoints; health_check_screen.dart laadt daarmee i.p.v. via getBriefing(), en krijgt een reload-knop (spinner tijdens laden, niet opnieuw indrukbaar) + 'Bijgewerkt om ...'-header analoog aan summary_screen.dart se _refresh()/_refreshing/_buildHeaderRow-patroon; summary_screen.dart blijft functioneel ongewijzigd. Nederlandstalige code-comments/UI-teksten/commits.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1275 — Health check-tab ontkoppelen van Upcoming + Software Factory-filter

**Backend (`briefing`-module):**
- `BriefingService` bouwt nu twee onafhankelijke responses: `currentUpcoming()`/`refreshUpcoming()`
  (alle secties behalve `system-status`) en `currentHealth()`/`refreshHealth()` (uitsluitend
  `system-status`, geïdentificeerd via `providers.filterIsInstance<SystemStatusSectionProvider>()`).
  Elk gebruikt een eigen `BriefingCacheRepository` (`@Qualifier("upcomingBriefingCache")` /
  `("healthBriefingCache")`), zodat een refresh van de ene de andere cache/`updatedAt` niet raakt.
- `FirestoreBriefingCacheRepository` kreeg een `documentId`-parameter (`current` vs. `health`) zodat
  beide caches als losse Firestore-documenten worden bewaard; `BriefingStoreConfig` registreert twee
  gekwalificeerde beans (in-memory-fallback ongewijzigd per bean).
- `BriefingController`: bestaande `GET /api/v1/briefing` + `POST /api/v1/briefing/refresh` praten nu
  met de Upcoming-cache; nieuwe `GET /api/v1/briefing/health` + `POST /api/v1/briefing/health/refresh`
  met de Health check-cache.
- `BriefingCacheScheduler`: cron van dagelijks 17:30 naar uurlijks (`0 0 * * * *`), ververst nu beide
  caches (elk in een eigen `runCatching`, zodat een falende sectie in de ene de andere niet blokkeert).
  `BriefingScheduler` (dagelijkse 18:00-FCM-push) is niet aangeraakt — die bouwt al rechtstreeks via de
  providers-lijst op, los van beide caches; expliciet geverifieerd door de code te lezen (geen
  referentie naar `BriefingCacheRepository`/`BriefingService`-cache-methodes) en door
  `BriefingCacheSchedulerTest`/`BriefingServiceTest` gescheiden te houden van
  `BriefingSchedulerTest` (ongewijzigd, blijft groen).
- `SystemStatusSectionProvider.softwareFactoryCheckData()`: filtert nu op `error != null` of
  (`phase != null && !merged`); lege lijst na filteren → "geen lopende of error-stories." i.p.v. de
  oude "geen stories gevonden."/volledige opsomming. AI-beoordeling/`shortSummary()` en de overige
  vier checks blijven ongewijzigd.
- Tests uitgebreid: `BriefingServiceTest`, `BriefingCacheSchedulerTest` (nu met een losse
  `SystemStatusSectionProvider`-instance voor de Health check-cache) en
  `SystemStatusSectionProviderTest` (twee nieuwe tests voor het Software Factory-filter, met en
  zonder resterende stories).

**Frontend (`robberts_assistent/lib`):**
- `api_client.dart`: nieuwe `getHealthCheck()`/`refreshHealthCheck()` tegen de nieuwe
  `/api/v1/briefing/health(/refresh)`-endpoints, zelfde `BriefingData`-model als de bestaande
  Upcoming-calls.
- `health_check_screen.dart`: laadt nu via `getHealthCheck()` i.p.v. `getBriefing()`, kreeg een eigen
  `_refresh()`/`_refreshing`/`_buildHeaderRow()` (reload-knop met spinner, niet opnieuw indrukbaar
  tijdens een lopende refresh, "Bijgewerkt om ..."-regel) — zelfde patroon als `summary_screen.dart`.
  `summary_screen.dart` zelf is niet gewijzigd.
- `health_check_screen_test.dart` uitgebreid met reload-knop-/spinner-/foutmelding-/timestamp-tests,
  analoog aan `summary_screen_test.dart`.

**Getest:**
- Backend: `mvn test` vanuit `robberts-assistent-backend/` — groen (0 failures, 0 errors).
- Frontend: `flutter analyze` en `flutter test` vanuit `robberts_assistent/` — groen, geen issues.
- `pubspec.lock` ongewijzigd gelaten na `flutter pub get` (geen ongerelateerde dependency-bumps).

**Niet gedaan / bewust buiten scope:**
- Geen wijziging aan `docs/factory/`-documentatie (expliciet buiten scope per de Aannames-sectie van
  de story).
- Geen wijziging aan `summary_screen.dart` (blijft functioneel ongewijzigd, zoals gevraagd).

## Review (SF-1275)

- Volledige story-diff t.o.v. `main` bekeken (backend `briefing`-module + frontend
  `health_check_screen.dart`/`api_client.dart` + bijbehorende tests).
- Zelf uitgevoerd in de reviewer-sandbox (flutter én mvn zijn hier beide bruikbaar):
  - `mvn -o test` (volledige backend-suite): **291 tests, 0 failures, 0 errors**.
  - `flutter analyze` in `robberts_assistent/`: **geen issues**.
  - `flutter test` in `robberts_assistent/`: **36 tests, alle groen**.
- Geverifieerd dat `BriefingScheduler` (18:00-FCM-push) en `BriefingSchedulerTest` ongewijzigd zijn
  gebleven (`git diff main...HEAD` op beide bestanden is leeg), en dat `summary_screen.dart`
  eveneens ongewijzigd is — beide expliciete acceptatiecriteria.
- `BriefingService`/`BriefingCacheScheduler`/`BriefingController`/`BriefingStoreConfig`/
  `FirestoreBriefingCacheRepository` correct gesplitst in twee onafhankelijke caches
  (Upcoming/Health), elk met eigen `updatedAt`, gedekt door `BriefingServiceTest`/
  `BriefingCacheSchedulerTest`.
- `SystemStatusSectionProvider.softwareFactoryCheckData()`-filter (`error != null` of `phase !=
  null && !merged`) komt exact overeen met de acceptatiecriteria; "geen lopende of error-stories."-
  fallback gedekt door een nieuwe test.
- Frontend `health_check_screen.dart`: reload-knop met spinner (niet opnieuw indrukbaar tijdens
  laden), "Bijgewerkt om ..."-header, foutafhandeling via snackbar — consistent met het bestaande
  `summary_screen.dart`-patroon (`_refresh`/`_refreshing`/`_buildHeaderRow`/`_formatTime`). Tests
  dekken spinner-state, timestamp en foutmelding.
- Geen bugs, regressies of scope-afwijkingen gevonden. Testdekking is volledig en groen (zowel
  door de developer gerapporteerd als hier zelf herhaald).
- Oordeel: akkoord, geen blockers.

## Test (SF-1276)

- Volledige story-diff t.o.v. `main` opnieuw doorgenomen (backend `briefing`-module + frontend
  `health_check_screen.dart`/`api_client.dart` + alle tests).
- `mvn -o test` (backend, volledige suite): **291 tests, 0 failures, 0 errors, BUILD SUCCESS**.
- `flutter analyze` (`robberts_assistent/`): **geen issues** (Flutter bleek in deze sandbox-run wél
  bruikbaar, in tegenstelling tot de eerdere aanname dat dit structureel niet zou kunnen).
- `flutter test` (`robberts_assistent/`): **36 tests, alle groen**, inclusief de nieuwe
  `health_check_screen_test.dart`-cases (reload-spinner, timestamp, foutmelding).
- End-to-end geverifieerd tegen de live preview (`robberts-assistent-pr-30`, via de
  frontend-proxy-route `SF_PREVIEW_URL/api/...`):
  - `GET /api/v1/briefing/health` en `GET /api/v1/briefing` leveren elk een eigen `updatedAt`.
  - `POST /api/v1/briefing/health/refresh` ververst alléén de Health check-`updatedAt`; een
    daaropvolgende `GET /api/v1/briefing` bleef ongewijzigd — onafhankelijkheid bevestigd op
    live API-niveau, niet alleen in unit tests.
  - Live Software Factory-check toont uitsluitend `SF-1274` (fase=in-progress, merged=false) —
    bevestigt het nieuwe filter ook met echte productie-achtige data.
- Browser-verificatie (Playwright/Chromium, viewport 480x900) op de preview-web-app:
  - Health check-tab toont "Bijgewerkt om ..." + reload-icoon; tijdens het verversen toont het
    icoon een niet-klikbare spinner (icoon zelf verdwijnt); na afloop update de timestamp
    (05:07 → 05:08) en komt het reload-icoon terug met tooltip "Systeemstatus verversen".
    Netwerklog bevestigt de aanroepen naar `/api/v1/briefing/health` (laden) en
    `/api/v1/briefing/health/refresh` (reload-klik).
  - Upcoming-tab toont alle briefingsecties (weerkaart, kiten, strandfietsen, agenda,
    week-taken) behalve systeemstatus, met een eigen, andere timestamp (05:09) — bevestigt de
    ontkoppeling ook visueel.
  - Screenshots opgeslagen in `/work/screenshots/` (`01_health_check.png`,
    `02_health_check_refreshing.png`, `03_health_check_refreshed.png`, `04_upcoming_tab.png`).
- Geen bugs of afwijkingen van de acceptatiecriteria gevonden.
- Oordeel: **tested**, geen blockers of openstaande vragen.
