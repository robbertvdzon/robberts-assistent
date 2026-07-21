# SF-1199 - Morgen-tab: gecachte briefing in Firestore + weer-sectie met kaartplaatjes

## Story

Morgen-tab: gecachte briefing in Firestore + weer-sectie met kaartplaatjes

<!-- refined-by-factory -->

## Scope

Uitbreiding van de 'Morgen'-briefing (backend module `briefing` + app `robberts_assistent/lib/summary_screen.dart`) met twee onafhankelijke delen:

**A. Briefing cachen in Firestore + reload-knop**
- Nieuw repository-port (Firestore + in-memory fallback, zelfde patroon als `MemoryRepository`/`ConversationRepository`) dat de volledige briefing + ophaal-timestamp opslaat.
- Nieuwe `@Scheduled`-job (Europe/Amsterdam, cron `0 30 17 * * *`) die dagelijks om 17:30 (een half uur vóór de bestaande 18:00-push) `BriefingService.current()` opbouwt en samen met de timestamp opslaat.
- `GET /api/v1/briefing` levert standaard de gecachte briefing (incl. de timestamp waarop die is opgehaald); is er nog geen cache, dan bouwt de aanroep 'm live op (zonder die live-build zelf te cachen).
- Nieuw endpoint `POST /api/v1/briefing/refresh`: bouwt de briefing live opnieuw op, overschrijft de cache + timestamp, en retourneert het verse resultaat.
- `BriefingResponse` krijgt een `updatedAt`-tijdstip (ISO-8601) zodat de app kan tonen wanneer de getoonde data is opgehaald.
- App: bij openen toont het scherm direct de gecachte data ("Bijgewerkt om ..." zichtbaar bovenin); een reload-knop in de AppBar roept `/refresh` aan en toont tijdens het laden een spinner op de knop (los van de bestaande pull-to-refresh, die de cache ophaalt).

**B. Nieuwe weer-sectie boven kite + strandfiets**
- Nieuwe `BriefingSectionProvider` (`order = -10`), dus bovenaan, boven `KiteSectionProvider` (0) en `BeachCycleSectionProvider` (5).
- Backend genereert twee statische kaartbeelden (kust IJmuiden t/m Egmond, via OpenStreetMap-tegels, geen betaalde Google Static Maps API) — één voor de ochtend, één voor de middag van morgen — elk met daaroverheen een windrichtingspijl, de windsnelheid in knopen en een weer-icoon (zon / regenwolk / onweerswolk), opgebouwd uit `weather.WindForecastClient` en `weather.WeatherClient` (bestaande clients, kust/Wijk aan Zee).
- De gegenereerde PNG's worden bij elke cache-refresh (17:30-job en handmatige `/refresh`) opnieuw gegenereerd en opgeslagen (zelfde opslag-patroon als bestaande foto's: Firebase Storage met in-memory fallback) en zijn ophaalbaar via een eigen endpoint (bv. `GET /api/v1/briefing/weather-map/{ochtend|middag}`); `BriefingItem` krijgt een optioneel `imageUrl`-veld zodat de app de afbeelding kan renderen zonder dat bestaande secties zonder afbeelding hoeven te wijzigen.
- `summary_screen.dart` rendert de weer-sectie generiek: toont een item met `imageUrl` als afbeelding (via `Image.network`, met auth-header net als andere API-calls) i.p.v. platte tekst.

## Acceptance criteria

1. Er bestaat een nieuw repository-port voor de briefing-cache (Firestore-implementatie + in-memory fallback, zelfde stub/fallback-conventie als elders); geen crash bij ontbrekende Firebase-config.
2. Een nieuwe `@Scheduled`-job draait dagelijks om 17:30 (Europe/Amsterdam), bouwt de briefing op en slaat 'm samen met een timestamp op in de cache; een falende sectie of AI-call crasht de job niet (zelfde `runCatching`-patroon als `BriefingService`/`BriefingScheduler`).
3. `GET /api/v1/briefing` retourneert de gecachte briefing + `updatedAt` als die bestaat; zonder cache bouwt het endpoint live op (zonder crash) en levert een `updatedAt` van "nu".
4. `POST /api/v1/briefing/refresh` (auth vereist, zelfde als het bestaande `GET /api/v1/briefing`) bouwt de briefing live op, overschrijft de cache + timestamp en retourneert het verse resultaat.
5. Een nieuwe `WeatherMapSectionProvider` (`order = -10`) staat bovenaan het "Morgen"-scherm, boven kiten en strandfietsen, zonder wijziging aan `BriefingService`/`BriefingController` (SPI-patroon).
6. De sectie levert twee kaartbeelden (ochtend/middag van morgen) van de kust IJmuiden–Egmond, elk met windrichtingspijl, windsnelheid in knopen en een passend weer-icoon, gebaseerd op `WindForecastClient` en `WeatherClient`; bij een netwerkfout in een van beide clients faalt de sectie stil (nette foutsectie, geen crash van de hele briefing).
7. De kaartbeelden zijn via een eigen backend-endpoint ophaalbaar (of als data meegegeven in de briefing-response) en worden bij elke cache-refresh opnieuw gegenereerd — geen betaalde kaarten-API gebruikt.
8. `summary_screen.dart` toont de nieuwe weer-sectie (twee afbeeldingen) en toont zichtbaar de "Bijgewerkt om ..."-timestamp van de (gecachte) briefing.
9. Een reload-knop bovenin het "Morgen"-scherm roept de refresh-actie aan; tijdens het laden toont de knop een spinner/laad-animatie en is niet opnieuw indrukbaar.
10. Bestaande secties (kite, strandfiets, agenda, weektaken, moestuin, systeemstatus) blijven ongewijzigd renderen — geen breuk in de generieke sectie-rendering voor secties zonder afbeelding.
11. `mvn test` (backend) en `flutter analyze` + `flutter test` (robberts_assistent) slagen.
12. Alle nieuwe code/commentaar/UI-teksten zijn in het Nederlands.

## Aannames

- De kaartbeelden worden ontsloten via een los backend-endpoint (analoog aan `GET /api/v1/assistant/photos/{id}`) met een `imageUrl`-veld op `BriefingItem`, in plaats van base64 inline in de JSON-response — houdt de briefing-payload klein en herbruikt het bestaande foto-opslagpatroon (Firebase Storage / in-memory fallback).
- Kaartbeeld-generatie gebruikt de standaard JDK-graphics (`java.awt`/`ImageIO`) voor het samenstellen van OSM-tegels + overlay-iconen; geen nieuwe Maven-dependency nodig.
- "Ochtend" en "middag" worden geconcretiseerd als twee vaste tijdstippen (bv. 09:00 en 14:00) van morgen, in lijn met de bestaande dagdeel-conventie in `KiteSectionProvider`/`SlotAssessmentProvider`.
- De cache bevat precies één document (laatste stand), net als `MemoryRepository` — geen historie van eerdere briefings.
- De 17:30-job en de `/refresh`-actie hergebruiken dezelfde opbouw- en opslaglogica (geen dubbele implementatie).
- OpenStreetMap-tegel-gebruik blijft binnen normaal, niet-geautomatiseerd bulkgebruik (twee kaarten per dag via de geplande job + incidentele refreshes); geen aparte tile-cache-laag is vereist voor deze story.
- Live-opgebouwde briefings via de fallback-route (GET zonder bestaande cache) worden niet automatisch alsnog gecachet — alleen de 17:30-job en expliciete `/refresh` schrijven naar de cache.

## Eindsamenvatting

{"phase":"summarized"}
