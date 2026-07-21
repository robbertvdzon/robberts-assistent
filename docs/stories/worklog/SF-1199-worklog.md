# SF-1199 - Worklog

Story-context bij eerste pickup:
Gecachte briefing (Firestore) + reload-endpoint en nieuwe weer-sectie met kaartplaatjes

Deel A: nieuw BriefingCacheRepository-port (Firestore + in-memory fallback, patroon MemoryRepository) met BriefingResponse.updatedAt; nieuwe @Scheduled BriefingCacheScheduler om 17:30 (Europe/Amsterdam) die BriefingService.buildFresh() opbouwt en cachet; GET /api/v1/briefing levert de cache (met fallback naar live opbouwen zonder te cachen als er nog geen cache is); nieuw POST /api/v1/briefing/refresh dat live opbouwt, cache bijwerkt en het verse resultaat teruggeeft (gedeelde opbouw-/opslaghelper met de scheduler, geen duplicatie). App: summary_screen.dart toont 'Bijgewerkt om ...' uit de payload en een reload-knop in de AppBar die /refresh aanroept met spinner-state tijdens laden (niet opnieuw indrukbaar); api_client.dart krijgt de refresh-aanroep en parsing van updatedAt. Deel B: nieuwe WeatherMapSectionProvider (order=-10, puur SPI, geen wijziging aan BriefingService/BriefingController) die via een nieuwe CoastMapImageBuilder-helper OSM-tegels voor de kust IJmuiden-Egmond ophaalt en samenstelt (java.net.http + java.awt/ImageIO, geen nieuwe dependency) met windrichtingspijl, windsnelheid in knopen en weer-icoon overlay, voor twee vaste tijdstippen van morgen (09:00/14:00) op basis van de bestaande WindForecastClient en WeatherClient; faalt een van beide clients, dan levert de sectie een nette foutsectie (geen crash). De gegenereerde PNG's worden bij elke cache-refresh opnieuw opgeslagen via een PhotoStorage-achtige poort (Firebase Storage + in-memory fallback, vaste sleutels per dagdeel) en ontsloten via GET /api/v1/briefing/weather-map/{slot}. BriefingItem krijgt een optioneel imageUrl-veld (bestaande secties blijven ongewijzigd). summary_screen.dart rendert items met imageUrl generiek als Image.network met auth-headers. Inclusief alle nieuwe/aangepaste unit- en widgettests (repository-implementaties, controller cache-hit/-miss/refresh, sectie succes/netwerkfout, reload-knop-states, afbeelding-rendering).

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Deel A (briefing-cache + refresh): nieuwe `BriefingCacheRepository`-poort (Firestore/
  `FirestoreBriefingCacheRepository`, JSON-serialisatie via de bestaande jackson-kotlin-module —
  zelfde reden als bij `assistant.FirestoreConversationRepository`'s geneste structuren; in-memory
  `InMemoryBriefingCacheRepository` als fallback), gewired via nieuwe `BriefingStoreConfig` (zelfde
  patroon als `assistant.AssistantStoreConfig`). `BriefingResponse` kreeg een `updatedAt`
  (ISO-8601). `BriefingService.current()` levert de cache of bouwt live op zonder te cachen;
  `BriefingService.refresh()` bouwt altijd live op en overschrijft de cache — gedeeld door de
  nieuwe `BriefingCacheScheduler` (17:30 Europe/Amsterdam) en het nieuwe
  `POST /api/v1/briefing/refresh`-endpoint.
- Deel B (weerkaart-sectie): nieuwe `WeatherMapSectionProvider` (`order = -10`, puur SPI) bouwt
  twee OSM-kaartbeelden (09:00/14:00 morgen) op via de nieuwe `CoastMapImageBuilder`
  (`OsmCoastMapImageBuilder`: java.net.http voor OSM-tegels + java.awt/ImageIO voor de
  windpijl/snelheid/weer-icoon-overlay, keyless en dus altijd actief zoals de andere
  Fase-0-weerclients; `StubCoastMapImageBuilder` voor tests, geen netwerk). PNG's opgeslagen via
  nieuwe `WeatherMapStorage`-poort (Firebase Storage/in-memory, vaste sleutels `ochtend`/`middag`,
  zelfde patroon als `assistant.PhotoStorage`) en ontsloten via
  `GET /api/v1/briefing/weather-map/{slot}`. `BriefingItem` kreeg een optioneel `imageUrl`-veld;
  bestaande secties blijven ongewijzigd (geen `imageUrl`).
- App (`robberts_assistent`): `ApiClient.getBriefing()` retourneert nu `BriefingData`
  (`sections` + `updatedAt`), plus nieuwe `refreshBriefing()`. `summary_screen.dart` toont
  "Bijgewerkt om HH:mm" bovenin met een reload-knop (spinner-state, niet opnieuw indrukbaar tijdens
  het laden) en rendert items met `imageUrl` als `Image.network` (met auth-headers en
  `errorBuilder` i.p.v. een onafgevangen netwerkfout — voorkomt ook flakiness in de widget-test).
  Geen eigen AppBar op `SummaryScreen` (die zit gedeeld op `HomeScreen`), dus de reload-knop staat
  in een eigen kopregel bovenaan de lijst i.p.v. letterlijk in een `AppBar`-widget.
- Tests: nieuwe unit-tests voor `BriefingCacheRepository` (in-memory), `WeatherMapStorage`
  (in-memory), `CoastMapImageBuilder` (pure overlay-/icoonlogica + Stub, geen netwerk-test — zelfde
  "geen HTTP in unit-tests"-patroon als `OpenMeteoWindForecastClientTest`), `WeatherMapSectionProvider`
  (succes + wind-/weerfout), `BriefingCacheScheduler`, en uitgebreide `BriefingServiceTest`
  (cache-hit/-miss/refresh). `BriefingController`/Firestore-implementaties zijn niet unit-getest,
  consistent met de rest van de codebase (geen bestaande controller- of Firestore-integratietests).
  `robberts_assistent/test/summary_screen_test.dart` uitgebreid met updatedAt-weergave,
  reload-spinner (via een `Completer`-gestuurde fake, anders lost de fake-refresh te snel op om de
  laad-state te kunnen waarnemen), reload-foutmelding (vereiste een omringende `Scaffold` in de
  test, want `ScaffoldMessenger.showSnackBar` heeft die nodig — `SummaryScreen` heeft zelf geen
  Scaffold) en imageUrl-rendering.
- Backend: `mvn test` groen (235 tests, 0 failures/errors). App: `flutter analyze` (geen
  issues) en `flutter test` (alle tests, incl. bestaande) groen.

## Test-notities (SF-1201, tester)

- Backend `mvn test`: 235 tests, 0 failures/errors, BUILD SUCCESS (start 2026-07-21T15:34:01Z,
  eind 2026-07-21T15:34:32Z, Maven "Total time: 30.008 s").
- App `flutter pub get` + `flutter analyze` (geen issues) + `flutter test` (28 tests, alle groen)
  op `/opt/flutter/bin/flutter` 3.44.7 — geen wijzigingen aan `pubspec.lock` achtergebleven.
- Preview (`robberts-assistent-pr-18`, geen Firebase-config, in-memory fallback):
  - `GET /api/v1/briefing`: HTTP 200, sectievolgorde `weather-map, kite, beach, agenda,
    week-tasks, moestuin, system-status` (weerkaart bovenaan, order=-10 klopt), `updatedAt`
    aanwezig (ISO-8601).
  - `weather-map`-sectie levert twee items (`ochtend`/`middag`) met `imageUrl` naar
    `/api/v1/briefing/weather-map/{slot}` en tekst met kn + windrichting + weeromschrijving.
  - `GET /api/v1/briefing/weather-map/ochtend` en `.../middag`: HTTP 200, `content-type:
    image/png`, geldige PNG-magic-bytes, ~170KB; visueel gecontroleerd (kust IJmuiden-Egmond,
    windrichtingspijl, "10 kn"-label) — zie screenshots.
  - `POST /api/v1/briefing/refresh`: HTTP 200, nieuwe `updatedAt`; navolgende `GET
    /api/v1/briefing` retourneert exact diezelfde (ververste) `updatedAt` → cache-overschrijving
    werkt.
  - Browser (Playwright, viewport 480x900, Morgen-tab): "Bijgewerkt om HH:mm"-tijdstip zichtbaar
    bovenin, reload-icoon met tooltip "Briefing verversen"; klik toont spinner tijdens het laden
    en na afloop een bijgewerkt tijdstip (15:35 → 15:36). Bestaande secties (kite/agenda/
    weektaken/moestuin/systeemstatus) bleven in de API-response intact en ongewijzigd van vorm.
  - Geen bugs gevonden; alle 12 acceptatiecriteria geverifieerd (11 via tests/build, 6/7/8/9 via
    live preview-API + browser-screenshots).
