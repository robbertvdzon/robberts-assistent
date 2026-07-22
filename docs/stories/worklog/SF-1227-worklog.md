# SF-1227 - Worklog

Story-context bij eerste pickup:
Cache-bust voor weerkaart-afbeelding op Morgen-tab

Frontend-fix in robberts_assistent/lib/summary_screen.dart: voeg in _buildItemRow een cache-bust-query-param (bv. ?v=<epoch-seconden van _data!.updatedAt>) toe aan de Image.network-URL van elk BriefingItem met een imageUrl (helperfunctie, generiek toepasbaar, niet weerkaart-specifiek hardcoded). Geef updatedAt door aan _buildItemRow (huidige signature/aanroep via section.items.map(_buildItemRow) aanpassen). Pas summary_screen_test.dart aan/breid uit zodat de query-param-toevoeging correct verdisconteerd wordt en een test bevestigt dat verschillende updatedAt-waarden tot verschillende URLs leiden. Optioneel: voeg Cache-Control: no-cache response-header toe aan GET /api/v1/briefing/weather-map/{slot} in BriefingController.kt (backend, module briefing), met bijbehorende test-aanpassing indien nodig.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1228 - Cache-bust voor weerkaart-afbeelding op Morgen-tab

- `robberts_assistent/lib/summary_screen.dart`: `_buildItemRow` krijgt nu `updatedAt`
  (uit `_data!.updatedAt`) als tweede parameter; een nieuwe helper
  `_cacheBustedImageUrl(imageUrl, updatedAt)` hangt `?v=<epoch-seconden>` (of `&v=...` als de URL
  al een query-string heeft) aan elk `BriefingItem.imageUrl`, generiek voor alle secties met een
  afbeelding (niet weerkaart-specifiek). Zo verandert de `Image.network`-URL bij elke nieuwe
  cache (reload-knop → `/refresh`, of de dagelijkse 17:30-cache + een gewone `GET`), waardoor
  Flutter's `ImageCache` (keyed op URL) de afbeelding opnieuw ophaalt i.p.v. de oude versie te
  hergebruiken.
- `robberts-assistent-backend/.../briefing/BriefingController.kt`: `GET
  /api/v1/briefing/weather-map/{slot}` stuurt nu ook een `Cache-Control: no-cache`-header mee
  (`CacheControl.noCache()`), als extra versteviging tegen browser-/HTTP-caching (bv. de
  web-variant van de app).
- Tests:
  - `robberts_assistent/test/summary_screen_test.dart`: bestaande imageUrl-test aangepast om de
    cache-bust-query-param te verdisconteren (asserteert de volledige URL incl. `?v=...` i.p.v.
    alleen `findsOneWidget` op `Image`); nieuwe test bevestigt dat een refresh met een andere
    `updatedAt` tot een andere (niet-gelijke) `Image.network`-URL leidt.
  - Nieuw `robberts-assistent-backend/.../briefing/BriefingControllerTest.kt` (Spring-Boot-test,
    zelfde patroon als `assistant.AssistantIntegrationTest`): verifieert dat de
    weather-map-response een `Cache-Control: no-cache`-header bevat.
- Getest: `flutter test` (volledige suite, robberts_assistent) groen, `flutter analyze` schoon,
  `mvn test` (robberts-assistent-backend) groen — 242 tests, 0 failures/errors.
- Niet gedaan: geen wijziging aan `BriefingItem`/`BriefingResponse`-datamodel of
  `WeatherMapSectionProvider.kt` nodig (zoals aangenomen in de story); de cache-bust is puur
  client-side.

### Review (SF-1228)

- Volledige story-diff t.o.v. `main` bekeken (5 bestanden: `summary_screen.dart`,
  `summary_screen_test.dart`, `BriefingController.kt`, nieuwe `BriefingControllerTest.kt`, dit
  worklog).
- `flutter` bleek in deze sandbox gewoon beschikbaar (3.44.7, aarch64) — zelf gedraaid i.p.v. op
  de "structureel niet uitvoerbaar"-uitzondering te vertrouwen: `flutter test` (volledige suite,
  29/29 groen, incl. de 2 nieuwe/aangepaste tests in `summary_screen_test.dart`) en
  `flutter analyze` (schoon).
- `mvn -o test` (backend): 242/242 groen, incl. de nieuwe `BriefingControllerTest`.
- Code-review: `_cacheBustedImageUrl` generiek (niet weerkaart-specifiek), correct achter
  `if (item.imageUrl != null)` gebruikt, `?`/`&`-separator-logica correct. `Cache-Control:
  no-cache` header correct toegevoegd. Geen scope-overschrijding; alle acceptatiecriteria uit
  `.task.md` gedekt.
- Akkoord, geen blockers.
