# SF-1206 - Morgen-tab: één weerkaart met twee gekleurde windpijlen + zichtbaar weer-icoon

## Story

Morgen-tab: één weerkaart met twee gekleurde windpijlen + zichtbaar weer-icoon

<!-- refined-by-factory -->

## Scope
Pas de Weerkaart-sectie van de Morgen-briefing aan (backend module `briefing`: `WeatherMapSectionProvider` + `CoastMapImageBuilder`, incl. `WeatherMapStorage` en het `GET /api/v1/briefing/weather-map/{slot}`-endpoint in `BriefingController`).

Huidige situatie: er worden twee losse kaartbeelden gegenereerd (ochtend 09:00 en middag 14:00), elk met één blauwe windpijl, windsnelheid (kn) en een weer-icoon dat als emoji via `java.awt`-`Font` wordt getekend en daardoor op de server niet zichtbaar is (leeg blokje).

Gewenste situatie:
1. Eén kaartbeeld (kust IJmuiden–Egmond) met twee windpijlen erover: ochtend en middag, in verschillende kleuren (oranje = ochtend, blauw = middag), met een klein legendaatje dat aangeeft welke kleur bij welk dagdeel hoort.
2. Per pijl de bijbehorende windsnelheid (kn) als label.
3. Het emoji-weer-icoon vervangen door een echt getekend icoontje (`java.awt`-vormen: zon/cirkel, wolk, wolk met regendruppels) — altijd zichtbaar, geen fontafhankelijkheid. Eén weer-icoon per dagdeel (bij elke pijl), gebaseerd op de WMO-weathercode zoals nu bepaald in `weatherIcon()`.

Aanpassingen elders die hieruit volgen:
- `CoastMapImageBuilder.build(...)` (en `drawOverlay`) krijgt de data van beide dagdelen in één aanroep (bv. een lijst/twee sets van `speedKn`/`directionDeg`/`weatherCode`) en levert één gecombineerd PNG terug; `StubCoastMapImageBuilder` en de bestaande `drawOverlay`-test worden hierop aangepast.
- `WeatherMapSectionProvider` levert nu één `BriefingItem` met `imageUrl` (in plaats van twee); `WeatherMapStorage` en `GET /api/v1/briefing/weather-map/{slot}` gaan naar één sleutel (bv. `morgen`) in plaats van `ochtend`/`middag`.
- De app (`summary_screen.dart`) rendert `imageUrl` generiek — geen frontend-wijziging nodig.
- Nette foutafhandeling blijft intact: bij ontbrekende wind-/weervoorspelling geen crash van de briefing, zoals nu.

## Acceptance criteria
- De Weerkaart-sectie van `GET /api/v1/briefing` levert exact één `BriefingItem` met één `imageUrl` (in plaats van twee items/afbeeldingen voor ochtend en middag).
- `GET /api/v1/briefing/weather-map/{slot}` serveert één PNG onder één sleutel (bv. `morgen`); de oude sleutels `ochtend`/`middag` zijn vervallen.
- Het opgehaalde PNG toont op één kaartbeeld van de kust IJmuiden–Egmond: twee windpijlen (ochtend en middag) in duidelijk verschillende kleuren, elk met een windsnelheidslabel (kn) ernaast, plus een legenda die de kleur-dagdeel-koppeling toont.
- Elke windpijl heeft een eigen, écht getekend weer-icoon (java.awt-vormen: zon, wolk, wolk-met-regen — geen tekst/emoji-glyph) gebaseerd op de WMO-weathercode van dat dagdeel.
- Faalt de wind- of weervoorspelling (of ontbreekt data voor een van beide dagdelen), dan crasht de briefing niet en toont de sectie een nette foutmelding, net als nu.
- `CoastMapImageBuilder`-interface en `StubCoastMapImageBuilder` zijn aangepast op de nieuwe, gecombineerde aanroep-signatuur; de bestaande `drawOverlay`-test (`CoastMapImageBuilderTest`) is bijgewerkt naar het nieuwe gedrag (twee pijlen/iconen/legenda in één beeld).
- `mvn test` slaagt, inclusief `ModulithArchitectureTest` en de aangepaste tests in `WeatherMapSectionProviderTest`/`WeatherMapStorageTest`/`CoastMapImageBuilderTest`.

## Aannames
- De exacte kleuren (oranje/blauw) en exacte icoon-vormgeving (grootte, plaatsing) zijn een implementatiedetail van de developer, zolang ochtend/middag visueel duidelijk te onderscheiden zijn en het weer-icoon altijd zichtbaar is zonder tekst/emoji.
- De nieuwe opslagsleutel wordt `morgen` (enkelvoud, dekt beide dagdelen in één beeld); een andere naam is akkoord zolang consistent toegepast in `WeatherMapStorage`, `BriefingController` en `WeatherMapSectionProvider`.
- `FirebaseStorageWeatherMapStorage` (Firebase-pad `briefing-weather-map/`) wordt op dezelfde manier meegewijzigd naar de ene sleutel; er is geen migratie van bestaande opgeslagen PNG's nodig (cache wordt bij eerstvolgende refresh gewoon opnieuw opgebouwd).
- Geen frontend-wijziging nodig in `robberts_assistent` (`summary_screen.dart` rendert `imageUrl` al generiek per `BriefingItem`).

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summary-finished"}
