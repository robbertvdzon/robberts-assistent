# SF-1200 - Review-notities

Reviewer-run (effort: medium) tegen `main...ai/SF-1199` (volledige story-diff, developer-commit
`1f92a88`).

## Bevindingen

- Backend `mvn test`: zelf opnieuw gedraaid ter verificatie — 235 tests, 0 failures/errors,
  inclusief `ModulithArchitectureTest` (module-grenzen intact ondanks nieuwe
  `BriefingController`-afhankelijkheid op `WeatherMapStorage`).
- App: `flutter analyze` (geen issues) en volledige `flutter test`-suite (alle bestanden, incl.
  `summary_screen_test.dart`) zelf gedraaid in deze sandbox (flutter 3.44.7 aarwezig) — alles
  groen. Dit is dus echt testbewijs, geen "structureel niet uitvoerbaar"-uitzondering nodig.
- Acceptatiecriteria 1 t/m 12 nagelopen tegen de diff: cache-poort + Firestore/in-memory-
  implementatie, 17:30-scheduler, `current()`/`refresh()`-scheiding, `updatedAt`, nieuwe
  `WeatherMapSectionProvider` (`order = -10`, boven kite/strandfiets), OSM-kaartbeeld-opbouw met
  windpijl/snelheid/icoon, stille foutafhandeling per client, `weather-map/{slot}`-endpoint,
  app-header met "Bijgewerkt om ..." + reload-knop met spinner-state — allemaal aanwezig en
  overeenkomstig de story. Reload-knop staat niet letterlijk in een `AppBar` (gedeelde AppBar zit
  op `HomeScreen`-niveau) maar in een eigen kopregel — functioneel gelijkwaardig, zoals de
  developer ook zelf meldt; geen blocker.
- [suggestie] `summary_screen.dart` rendert de weerkaart-afbeeldingen via `Image.network(url,
  headers: ...)`. De bestaande foto-viewer in de assistant-chat (`ApiClient.fetchAssistantPhoto`,
  zie `api_client.dart:172-173`) vermijdt bewust "een kale `Image.network`-URL" voor auth-gated
  content en haalt de bytes zelf op (`http.get` + eigen widget). Op recente Flutter-web-builds
  (CanvasKit/Skwasm, geen HTML-renderer meer) zou `headers` op `Image.network` intussen wel
  gerespecteerd moeten worden, maar dat is in deze sandbox niet te verifiëren (geen browser). Geen
  blocker: bij een falende/ongeautoriseerde fetch valt de nieuwe code netjes terug op
  `errorBuilder` (icoon i.p.v. crash), dus in het ergste geval ontbreekt alleen de afbeelding op
  web. Aanbevolen: bij de volgende story handmatig verifiëren in de web-build
  (`robberts-assistent.vdzonsoftware.nl`) of de weerkaarten daadwerkelijk laden i.p.v. het
  fouticoon te tonen.
- [info] `GET /api/v1/briefing/weather-map/{slot}` geeft `slot` ongevalideerd door aan
  `WeatherMapStorage.load`. Voor Firestore/Firebase Storage is dit geen path-traversal-risico
  (objectsleutels zijn vlak, geen bestandssysteem-semantiek) en de in-memory-variant is een simpele
  map-lookup; een onbekende sleutel levert gewoon 404. Geen wijziging nodig.

## Conclusie

Story-diff (deel A: briefing-cache + refresh-endpoint; deel B: weerkaart-sectie met
kaartbeelden) is inhoudelijk correct, goed getest (backend + Flutter, beide echt gedraaid) en
consistent met de bestaande stub/fallback- en SPI-conventies. Geen blockers gevonden.
