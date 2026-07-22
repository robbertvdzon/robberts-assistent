# SF-1227 - Weerkaart Morgen-tab: cache-bust zodat verse windpijl/richting laadt

## Story

Weerkaart Morgen-tab: cache-bust zodat verse windpijl/richting laadt

<!-- refined-by-factory -->

## Scope

De weerkaart-afbeelding op de "Morgen"-tab (`SummaryScreen` in `robberts_assistent`) blijft na een refresh de oude, gecachete PNG tonen omdat `imageUrl` (`/api/v1/briefing/weather-map/morgen`) een vaste URL is: zowel Flutter's `Image.network` (ImageCache, keyed op URL) als eventuele HTTP-caches blijven de eerder opgehaalde afbeelding hergebruiken.

Fix in `robberts_assistent/lib/summary_screen.dart`: hang aan elke `imageUrl` uit een `BriefingItem` de `updatedAt`-timestamp van de omvattende `BriefingData` (`_data!.updatedAt`) als query-param, bv. `?v=<epoch-seconds>`, zodat de URL bij elke nieuwe cache (reload-knop → `/refresh`, of de dagelijkse 17:30-cache gevolgd door een gewone `GET`) verandert en Flutter's `ImageCache` de afbeelding opnieuw ophaalt.

Aanvullend (optioneel, ter versteviging): `Cache-Control: no-cache` response-header op `GET /api/v1/briefing/weather-map/{slot}` in `BriefingController.kt`, zodat ook een eventuele browser/HTTP-cache (bv. bij de web-variant van de app) niet stiekem een oude versie hergebruikt.

Geen backend-datamodel-wijziging nodig: `BriefingItem.imageUrl` blijft ongewijzigd de relatieve pad-string; de cache-bust-parameter wordt uitsluitend client-side toegevoegd bij het opbouwen van de `Image.network`-URL.

## Acceptance criteria

- Na het indrukken van de reload-knop (`POST /api/v1/briefing/refresh`) toont de weerkaart-sectie de nieuw gegenereerde afbeelding (herkenbaar aan windpijl-richting/-snelheid), niet meer de eerder getoonde versie — geverifieerd doordat de gerenderde `Image.network`-URL na een refresh een andere query-param-waarde heeft dan ervoor.
- Na de dagelijkse 17:30-cache-refresh (backend) en een daaropvolgende gewone app-load/pull-to-refresh (`GET /api/v1/briefing`) toont de app eveneens de nieuwste kaart, niet een eerder in het `ImageCache` bewaarde versie.
- Bestaande secties zonder `imageUrl` (kite, strandfiets, agenda, weektaken, moestuin) tonen ongewijzigd gedrag — de cache-bust raakt alleen items met een `imageUrl`.
- Bestaande widget-tests (`summary_screen_test.dart`) blijven slagen of zijn aangepast om de query-param-toevoeging te verdisconteren.
- (Indien de optionele `Cache-Control`-header wordt toegevoegd) `GET /api/v1/briefing/weather-map/{slot}` retourneert een `Cache-Control: no-cache`-response-header; bestaande backend-tests voor dit endpoint blijven slagen.

## Aannames

- `BriefingData.updatedAt` (al aanwezig in `api_client.dart`) is voldoende granulariteit (seconden) om elke refresh een unieke query-param-waarde te geven; er is geen apart per-afbeelding-versienummer nodig.
- De cache-bust wordt generiek toegepast op elk `BriefingItem.imageUrl` (niet hardcoded op de weerkaart-sectie specifiek), zodat toekomstige secties met afbeeldingen hetzelfde gedrag automatisch krijgen.
- Geen wijziging nodig aan `WeatherMapSectionProvider.kt`/`BriefingItem`/`BriefingResponse`-datamodel in de backend; dit is primair een frontend-fix, met de `Cache-Control`-header als kleine, losstaande backend-aanvulling.

## Eindsamenvatting

Alles duidelijk genoeg om de eindsamenvatting te schrijven.

## Eindsamenvatting SF-1227: Weerkaart Morgen-tab — cache-bust zodat verse windpijl/richting laadt

**Probleem:** de weerkaart-afbeelding op de "Morgen"-tab bleef na een refresh de oude, gecachete PNG tonen, omdat `imageUrl` een vaste URL was — zowel Flutter's `ImageCache` als eventuele HTTP-caches hergebruikten de eerder opgehaalde afbeelding.

**Gebouwd:**
- **Frontend** (`robberts_assistent/lib/summary_screen.dart`): nieuwe helper `_cacheBustedImageUrl(imageUrl, updatedAt)` hangt `?v=<epoch-seconden>` (of `&v=...`) aan elk `BriefingItem.imageUrl`. Generiek toegepast op elk item met een afbeelding, niet hardcoded voor de weerkaart — toekomstige secties met afbeeldingen krijgen dit gedrag automatisch. `_buildItemRow` kreeg `updatedAt` als extra parameter.
- **Backend** (`BriefingController.kt`, module `briefing`): `GET /api/v1/briefing/weather-map/{slot}` stuurt nu ook een `Cache-Control: no-cache`-header mee, als extra versteviging tegen browser-/HTTP-caching (optionele deel uit de story, wél gedaan).

**Keuzes:**
- Cache-bust puur client-side, op basis van de al aanwezige `updatedAt` (secondegranulariteit) — geen apart per-afbeelding-versienummer, geen wijziging aan `BriefingItem`/`BriefingResponse`-datamodel of `WeatherMapSectionProvider.kt` (bevestigt de aannames uit de story).

**Getest:**
- Backend: `mvn -o test` 242/242 groen, incl. nieuwe `BriefingControllerTest` (verifieert de `Cache-Control: no-cache`-header).
- Frontend: `flutter test` 29/29 groen (incl. aangepaste + nieuwe test in `summary_screen_test.dart`), `flutter analyze` schoon.
- Story-brede test op de preview-omgeving (`robberts-assistent-pr-22`): drie opeenvolgende refreshes gaven drie verschillende `updatedAt`-waarden; alleen de weerkaart-sectie heeft een `imageUrl` (overige secties `null`, dus ongewijzigd gedrag bevestigd). Browser-E2E (Playwright) bevestigde dat de querystring van de afbeelding-URL na een klik op de reload-knop daadwerkelijk verandert (`?v=1784708807` → `?v=1784708870`), met screenshots vóór/na.
- Geen bugs gevonden; alle acceptatiecriteria gedekt.

**Bewust niet gedaan:** geen wijziging aan het backend-datamodel — de fix is primair client-side, zoals in de scope was aangenomen.
