# SF-1296 - Worklog

Story-context bij eerste pickup:
Basiskaart-cache in OsmCoastMapImageBuilder

Splits OsmCoastMapImageBuilder.build() zodat fetchMap() (OSM-tile-HTTP) alleen bij een cache-miss draait: houd een in-memory cache van de opgehaalde basiskaart-BufferedImage bij, en persisteer 'm daarnaast via een nieuwe opslag-poort (bv. BaseMapStorage, analoog aan WeatherMapStorage/FirebaseStorageWeatherMapStorage/InMemoryWeatherMapStorage in briefing/WeatherMapStorage.kt en FirebaseStorageWeatherMapStorage.kt) zodat de cache een pod-herstart overleeft; wire de nieuwe opslag-bean in BriefingStoreConfig.kt volgens hetzelfde Firebase-vs-in-memory-selectiepatroon. Zorg dat drawOverlay() nooit meer op de gedeelde/gecachete BufferedImage zelf tekent maar op een kopie (nieuwe BufferedImage + Graphics2D.drawImage van de cache erin) vóór het overlay-tekenwerk, zodat overlay-elementen van een vorige refresh niet doorschemeren in de volgende. Overweeg een eenvoudige optionele TTL (bv. een updatedAt-tijdstip) voor het forceren van een verse fetch na verloop van tijd, maar alleen als dit zonder noemenswaardige complexiteit past - geen harde eis. Zorg voor thread-veilige cache-vulling bij gelijktijdige aanroepen (scheduler + reload-knop). CoastMapImageBuilder-interface, WeatherMapSectionProvider, de weerkaart-endpoints, WeatherMapStorage (de opslag van de afgeronde overlay-PNG) en BriefingCacheScheduler blijven ongewijzigd. Schrijf/actualiseer unit tests in CoastMapImageBuilderTest (cache-hit slaat fetchMap over, twee build()-aanroepen met verschillende slots/dayWeatherCode/tideExtremes leveren verschillende PNG's op zonder dat de gecachete basiskaart wordt gemuteerd) en voeg zo nodig een test toe voor de nieuwe opslag-component (analoog aan WeatherMapStorageTest).

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1298 - Basiskaart-cache in OsmCoastMapImageBuilder (developer)

- Nieuwe `briefing.BaseMapStorage`-poort (analoog aan `WeatherMapStorage`): `InMemoryBaseMapStorage`
  (fallback) + `FirebaseStorageBaseMapStorage` (Firebase Storage, pad
  `briefing-weather-map/basemap.png`, los bestand naast het overlay-PNG).
- `BriefingStoreConfig` kreeg een `baseMapStorage`-bean volgens hetzelfde
  Firebase-vs-in-memory-selectiepatroon als `weatherMapStorage`.
- `OsmCoastMapImageBuilder` cachet de opgehaalde basiskaart-`BufferedImage` nu in het geheugen
  (`@Volatile` + `synchronized`-double-checked-locking rond een `baseMapLock`, zodat de uurlijkse
  scheduler en een handmatige reload-knop elkaar niet kunnen dubbel laten fetchen). Bij een
  in-memory cache-miss wordt eerst `BaseMapStorage.load()` geprobeerd (overleeft een pod-herstart);
  pas als dat ook leeg is, doet `fetchMap()` de echte OSM-tile-HTTP-calls, waarna het resultaat
  zowel in het geheugen als in `BaseMapStorage` wordt bewaard.
- `build()` tekent de overlay nooit meer op de gedeelde/gecachete instantie: een nieuwe `copyOf()`
  maakt eerst een losse `BufferedImage`-kopie (via `Graphics2D.drawImage`) waarop `drawOverlay()`
  tekent, zodat overlay-elementen van een vorige refresh niet doorschemeren in de volgende en de
  cache schoon blijft.
- Geen TTL/invalidatie toegevoegd — expliciet optioneel in de story/aannames, en de basiskaart van
  dit vaste kustgebied is stabiel; toevoegen zou een tijdstip-veld door de storage-laag heen moeten
  meesleuren voor weinig waarde.
- `OsmCoastMapImageBuilder` is `open` gemaakt en `fetchMap()` is `internal open` zodat een
  test-subclass (`CountingCoastMapImageBuilder` in `CoastMapImageBuilderTest`) 'm kan overriden om
  zonder netwerk-call fetch-aanroepen te tellen — het bestaande "geen HTTP-call in unit-tests"-
  patroon (zie testklasse-KDoc) blijft zo intact.
- Nieuwe/aangepaste tests: `BaseMapStorageTest` (nieuw, analoog aan `WeatherMapStorageTest`) en drie
  nieuwe tests in `CoastMapImageBuilderTest` (tweede `build()` fetcht niet opnieuw; een nieuwe
  builder-instantie met gevulde opslag fetcht niet opnieuw ("herstart"); overlay op een kopie —
  verschillende invoer geeft een andere PNG zonder dat de vorige overlay in de nieuwe doorschemert).
  Interface, `WeatherMapSectionProvider`, de weerkaart-endpoints, `WeatherMapStorage` en
  `BriefingCacheScheduler` zijn ongewijzigd (`OsmCoastMapImageBuilder` wordt overal als
  Spring-`@Component` geïnjecteerd, geen aanroeper construeert 'm handmatig met de oude
  no-arg-constructor).
- Vangnet: `mvn test` in `robberts-assistent-backend/` — 297 tests, 0 failures, 0 errors, BUILD
  SUCCESS.

## SF-1299 - Story-brede test (tester)

- Code-review van `CoastMapImageBuilder.kt`/`BaseMapStorage.kt`/`FirebaseStorageBaseMapStorage.kt`/
  `BriefingStoreConfig.kt` tegen de acceptatiecriteria: basiskaart-cache met double-checked locking,
  `build()` tekent op een `copyOf()`-kopie i.p.v. de gecachete instantie, `BaseMapStorage`
  (Firebase Storage + in-memory fallback) volgt exact het `WeatherMapStorage`-patroon, geen
  wijziging aan de `CoastMapImageBuilder`-interface of aanroepers — komt overeen met de story-scope.
- `mvn test` opnieuw gedraaid (niet alleen op ontwikkelaars-woord vertrouwd): start `2026-07-26
  14:43:25Z`, eind `14:43:53Z` — 297 tests, 0 failures, 0 errors, BUILD SUCCESS. Specifiek
  gecontroleerd: `CoastMapImageBuilderTest` (13 tests, incl. de 3 nieuwe cache-tests: tweede
  `build()` fetcht niet opnieuw, nieuwe instantie met gevulde opslag fetcht niet opnieuw
  ("herstart"), overlay op kopie laat geen doorschemering zien), `BaseMapStorageTest` (3 tests),
  `WeatherMapSectionProviderTest` (6 tests) en `WeatherMapStorageTest` (3 tests) — allemaal groen.
  `ModulithArchitectureTest` ook groen (module-grenzen niet geraakt).
- Live preview-verificatie op `robberts-assistent-frontend-robberts-assistent-pr-31` (via de
  frontend-nginx-proxy, zie eerdere tester-tip): `GET /api/v1/briefing` → HTTP 200 met
  `weather-map`-sectie + `imageUrl`; `POST /api/v1/briefing/refresh` → HTTP 200 (tweemaal
  aangeroepen, ~2,3s de tweede keer, geen fouten); `GET
  /api/v1/briefing/weather-map/morgen` → HTTP 200, geldige PNG (181.750 bytes). Bevestigt dat de
  basiskaart-caching-laag de bestaande weerkaart-functionaliteit end-to-end niet breekt.
- Geen frontend-/UI-wijziging in deze story (uitsluitend backend `briefing`-module) — geen
  screenshot-verplichting van toepassing.
- Geen bugs gevonden. Geen tijdelijke testdata aangemaakt (alleen leesoperaties/curl-GETs en twee
  refresh-POSTs die al bestaand gedrag triggeren, geen cleanup nodig).
