# SF-1296 - Weerkaart: OSM-basiskaart eenmalig ophalen en hergebruiken

## Story

Weerkaart: OSM-basiskaart eenmalig ophalen en hergebruiken

<!-- refined-by-factory -->

## Scope

`OsmCoastMapImageBuilder` (`robberts-assistent-backend/.../briefing/CoastMapImageBuilder.kt`) haalt momenteel bij élke `build()`-aanroep (elk uur via `BriefingCacheScheduler`, plus bij elke reload-knop op de Upcoming-tab) opnieuw alle OSM-tegels van de kust IJmuiden–Egmond op via `fetchMap()`. Deze basiskaart verandert nooit; alleen de overlay (windpijlen, weer-icoon, getijtijden, getekend door `drawOverlay()`) wisselt per refresh. Deze story zorgt dat de basiskaart maximaal één keer wordt opgehaald en daarna hergebruikt, om onnodige belasting van de gratis OSM-tile-server te voorkomen.

Aanpak:
- `OsmCoastMapImageBuilder` cachet de opgebouwde basiskaart-`BufferedImage` in het geheugen (lazy: eerste `build()`-aanroep na opstarten vult de cache).
- Bij een cache-hit wordt `fetchMap()` overgeslagen; `drawOverlay()` tekent op een **kopie** van de gecachete basiskaart, nooit op de gedeelde gecachete instance, zodat de cache niet vervuild raakt met overlay-tekeningen van een vorige refresh.
- De basiskaart wordt daarnaast weggeschreven naar Firebase Storage (zelfde patroon als `WeatherMapStorage`/`FirebaseStorageWeatherMapStorage`, in-memory fallback zonder Firebase-configuratie), zodat de cache een pod-herstart overleeft en niet na elke herstart opnieuw alle tegels ophaalt.
- Bij opstarten (of bij een cache-miss in het geheugen) probeert de builder eerst de basiskaart uit deze opslag te laden vóór 'ie `fetchMap()` aanroept.
- Een optionele TTL/invalidatie-mechanisme voor de basiskaart-cache (bijvoorbeeld: na X dagen alsnog een verse fetch forceren) — geen harde eis, wel gewenst als eenvoudig in te bouwen is (bv. een `updatedAt`-tijdstip meeslaan in de opslag).
- Overige weerkaart-logica (overlay-tekenlogica, getij-tekst, `WeatherMapSectionProvider`, de endpoints `GET /api/v1/briefing/weather-map/{slot}` en `POST /api/v1/briefing(/health)/refresh`) blijft ongewijzigd.

## Acceptance criteria

- `OsmCoastMapImageBuilder.build()` roept `fetchMap()` (de OSM-tile-HTTP-calls) niet meer aan zodra er al een basiskaart in de cache zit (in-memory of, na herstart, geladen uit de externe opslag) — geverifieerd met een unit/integratietest die aantoont dat een tweede `build()`-aanroep geen nieuwe tile-fetch triggert.
- De overlay (windpijlen, weer-icoon, legenda, dag-samenvatting/getijtijden) wordt bij elke `build()`-aanroep vers getekend en reflecteert de op dat moment meegegeven `slots`/`dayWeatherCode`/`tideExtremes` — twee opeenvolgende `build()`-aanroepen met verschillende invoer leveren verschillende PNG's op, zonder dat overlay-elementen van de eerste aanroep in de tweede blijven "doorschemeren" (de gedeelde basiskaart-cache blijft ongewijzigd/schoon).
- De basiskaart wordt weggeschreven naar een externe opslag (Firebase Storage, met in-memory fallback zonder Firebase-config, zelfde patroon als `WeatherMapStorage`) zodat een herstart van de backend niet opnieuw alle OSM-tegels hoeft op te halen wanneer een eerder gecachete basiskaart beschikbaar is.
- Bestaande tests (`CoastMapImageBuilderTest`, `WeatherMapStorageTest`, `WeatherMapSectionProviderTest`) en `StubCoastMapImageBuilder` blijven werken, eventueel aangevuld/aangepast waar nodig voor de nieuwe caching-laag.
- `WeatherMapSectionProvider`, de weerkaart-endpoints en de overige briefing-caching (Upcoming/Health check, `BriefingCacheScheduler`) zijn functioneel ongewijzigd — deze story raakt alleen de basiskaart-ophaal-/hergebruik-laag binnen `CoastMapImageBuilder.kt`.

## Aannames

- "Herstart overleeft" betekent: de basiskaart wordt persistent opgeslagen (Firebase Storage), niet dat er een gegarandeerde TTL/invalidatie verplicht is — een eenvoudig TTL-mechanisme mag worden toegevoegd als het zonder noemenswaardige complexiteit past, maar is geen harde acceptatie-eis.
- Een tegel-set die eenmaal is opgehaald wordt verondersteld stabiel genoeg (OSM-kaartdata van dit vaste kustgebied verandert nauwelijks) om zonder expliciete invalidatie voor onbepaalde tijd te hergebruiken, tenzij de developer een pragmatische TTL toevoegt.
- De caching-laag zit volledig in `OsmCoastMapImageBuilder` (en een nieuwe opslag-component ernaast, analoog aan `WeatherMapStorage`); er is geen wijziging nodig aan de `CoastMapImageBuilder`-interface zelf (`build(slots, dayWeatherCode, tideExtremes): ByteArray` blijft ongewijzigd) of aan de aanroepers ervan (`WeatherMapSectionProvider`).

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summarized"}
