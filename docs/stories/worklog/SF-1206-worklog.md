# SF-1206 - Worklog

Story-context bij eerste pickup:
Weerkaart combineren tot één beeld met twee gekleurde windpijlen en echte weericonen

Pas briefing.CoastMapImageBuilder (interface + OsmCoastMapImageBuilder + StubCoastMapImageBuilder) aan zodat build(...) de wind-/weerdata van ochtend én middag in één aanroep krijgt en drawOverlay() twee windpijlen in verschillende kleuren (bv. oranje=ochtend, blauw=middag) tekent, elk met een windsnelheidslabel (kn) en een klein legendaatje. Vervang de emoji-gebaseerde weatherIcon()-functie (java.awt Font, niet zichtbaar op server) door een functie die een écht getekend icoontje (java.awt-vormen: cirkel voor zon, ellipsen voor wolk, wolk+regendruppellijntjes voor regen) tekent, één per dagdeel bij de bijbehorende pijl, gebaseerd op de WMO-weathercode. Pas WeatherMapSectionProvider aan zodat die één build(...)-aanroep doet met beide dagdelen, het PNG opslaat onder één vaste sleutel ('morgen') via WeatherMapStorage, en precies één BriefingItem met imageUrl='/api/v1/briefing/weather-map/morgen' teruggeeft (i.p.v. twee). Werk de KDoc in WeatherMapStorage.kt, FirebaseStorageWeatherMapStorage.kt en BriefingController.kt bij naar de ene sleutel. Houd de bestaande nette foutafhandeling intact (geen crash bij ontbrekende wind-/weervoorspelling of ontbrekende data voor een dagdeel). Werk als onderdeel van dit ontwikkelwerk ook de bestaande unit-tests bij: CoastMapImageBuilderTest (drawOverlay-test en icoon-tests naar de nieuwe signatuur/tekenlogica), WeatherMapSectionProviderTest (exact één item/imageUrl i.p.v. twee, storage-sleutel 'morgen'), WeatherMapStorageTest (sleutel-naamgeving).

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `CoastMapImageBuilder`: interface `build(...)` neemt nu een `List<WindMapSlot>` (label, kleur,
  speedKn, directionDeg, weatherCode) i.p.v. losse parameters voor één dagdeel. `drawOverlay`
  tekent per slot een eigen windpijl (in de slot-kleur) met windsnelheidslabel, plaatst de pijlen
  horizontaal verdeeld over het beeld zodat ze bij twee dagdelen niet overlappen, tekent een nieuw
  `drawWeatherIcon` (java.awt-vormen: gevulde cirkel voor zon, ellipsen voor een wolk, plus
  regendruppellijntjes bij regen/onweer — geen `Font`/emoji meer) boven elke pijl, en tekent een
  legenda linksboven die kleur aan dagdeel-label koppelt. De oude emoji-`weatherIcon()` is vervangen
  door `weatherCategory()` (alleen nog gebruikt in tests/documentatie, geen tekenfunctie meer).
  `StubCoastMapImageBuilder` volgt dezelfde nieuwe signatuur (nog steeds een vaste 8x8 dummy-PNG,
  geen netwerk-call).
- `WeatherMapSectionProvider`: bouwt nu windrichting/snelheid/weercode voor ochtend (09:00, oranje)
  en middag (14:00, blauw) op, roept `coastMapImageBuilder.build(...)` één keer aan met beide
  dagdelen, slaat het resultaat op onder de vaste sleutel `morgen` (i.p.v. `ochtend`/`middag`) en
  levert precies één `BriefingItem` met `imageUrl = "/api/v1/briefing/weather-map/morgen"` en een
  tekst die beide dagdelen samenvat (`"Ochtend: ... · Middag: ..."`). Ontbreekt de wind-/weerdata
  voor één van beide dagdelen, dan valt die stilzwijgend weg (`mapNotNull`); zijn beide dagdelen
  onbeschikbaar, dan levert de sectie (zoals voorheen) een foutmelding zonder items — geen crash.
- `WeatherMapStorage`/`FirebaseStorageWeatherMapStorage`/`BriefingController`: alleen KDoc
  bijgewerkt naar de ene sleutel (`morgen`); de opslag-interface zelf was al generiek op een
  willekeurige string-sleutel, dus geen signatuurwijziging nodig.
- Tests bijgewerkt: `CoastMapImageBuilderTest` (nieuwe `WindMapSlot`-signatuur, test dat er
  daadwerkelijk oranje/blauwe pixels getekend worden voor beide dagdelen, `weatherCategory`-tests
  i.p.v. `weatherIcon`-emoji-tests), `WeatherMapSectionProviderTest` (precies één item/imageUrl op
  `.../weather-map/morgen`, storage-sleutel `morgen`), `WeatherMapStorageTest` (sleutel `morgen`
  i.p.v. `ochtend`/`middag` — de storage-klasse zelf bleef ongewijzigd generiek).
- Volledig vangnet gedraaid: `mvn test` vanuit `robberts-assistent-backend/` — exit code 0, alle
  tests (incl. `ModulithArchitectureTest`) groen. Geen frontend-wijziging nodig (app rendert
  `imageUrl` al generiek per `BriefingItem`, zoals in de story-aannames vermeld).
