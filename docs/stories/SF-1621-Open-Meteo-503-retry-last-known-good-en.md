# SF-1621 - Open-Meteo 503: retry, last-known-good en calls delen

## Story

Open-Meteo 503: retry, last-known-good en calls delen

<!-- refined-by-factory -->

## Samenvatting

De briefing laat regelmatig "Kon Open-Meteo niet ophalen (HTTP 503)" zien bij Weerkaart,
Kiten en Strandfietsen. Die 503 komt van de weerdienst zelf en is meestal binnen een
seconde weer over. Nu is één mislukte poging meteen zichtbaar als foutmelding, en per
briefing worden onnodig veel weer-opvragingen gedaan, waardoor de kans op zo'n melding
zich vermenigvuldigt.

Na deze story probeert de backend een mislukte opvraging automatisch een paar keer
opnieuw, en valt hij anders terug op de laatst opgehaalde voorspelling met een kort
zinnetje erbij dat de gegevens van een eerder tijdstip zijn. Ook delen de drie secties
dezelfde opgehaalde gegevens, zodat er nog maar twee opvragingen per briefing uitgaan.
Alleen als er echt niets bruikbaars is, verschijnt nog de foutmelding.

## Scope

Backend-only, module `weather/` plus de drie briefingsecties in `briefing/`. Geen
frontend-wijziging, geen nieuw endpoint, geen nieuwe dependency (`pom.xml` blijft
ongewijzigd — retry, TTL-cache en last-known-good worden handmatig geïmplementeerd, in de
stijl van de bestaande caches in `GoogleOAuthService` en `CoastMapImageBuilder`).

In scope:

1. **Retry met backoff** in `OpenMeteoWeatherClient` en `OpenMeteoWindForecastClient`:
   maximaal 3 pogingen per aanroep, met ~0,5 s en ~2 s pauze ertussen. Opnieuw proberen
   bij een netwerk-/IO-fout, bij HTTP 5xx en bij HTTP 429. Bij overige 4xx direct
   stoppen zonder retry. De bestaande per-poging-timeout van 10 s blijft.
2. **Last-known-good** per client: de laatst succesvol opgehaalde (volledige, niet
   afgekapte) voorspelling wordt samen met het ophaalmoment in geheugen bewaard. Falen
   alle pogingen, dan wordt die bewaarde voorspelling teruggegeven i.p.v. een fout,
   mits het ophaalmoment niet ouder is dan 12 uur. Anders (geen bewaarde waarde, of
   ouder dan 12 uur) de huidige foutmelding, ongewijzigd van tekst.
3. **Delen van calls** via een TTL-cache van 10 minuten in dezelfde twee clients: een
   tweede aanroep binnen de TTL doet geen HTTP-call maar levert de gecachete
   voorspelling. Omdat beide clients een vaste URL ophalen en het `hours`-argument
   client-side afkappen, wordt de volledige voorspelling gecachet en per aanroep
   afgekapt. De cache moet thread-veilig zijn (uurlijkse scheduler en de reload-knop
   kunnen tegelijk lopen).
4. **Signaleren van verouderde data**: `WeatherForecast` en `WindForecast` krijgen twee
   optionele velden met `null`/`false`-default (o.a. het ophaalmoment en een
   "verouderd"-markering), zodat bestaande constructie-aanroepen en tests ongewijzigd
   blijven compileren. Alleen een last-known-good-teruggave (punt 2) telt als verouderd;
   een verse call en een TTL-cachehit niet.
5. **Toonbaar maken** in `WeatherMapSectionProvider`, `KiteSectionProvider` en
   `BeachCycleSectionProvider`: bij verouderde data de normale inhoud tonen met de
   toevoeging `(gegevens van HH:MM)` (Europe/Amsterdam). `SlotAssessmentProvider` geeft
   die markering mee in `AssessmentResult.Ok` zodat Kiten en Strandfietsen 'm allebei
   kunnen tonen.
6. **Logging**: na alle mislukte pogingen één `logger.warn` per client-aanroep, met
   statuscode of foutmelding, volgens de bestaande slf4j-conventie in de backend.

Buiten scope: `OpenMeteoAirQualityClient`, `TideClient`, `CalendarClient` en het
samenvoegen van de dubbele `SlotAssessmentProvider`-instanties/tide-/agenda-calls; de
briefing-cache (`BriefingCacheRepository`, uurlijkse `BriefingCacheScheduler`) en de
18:00-push blijven ongewijzigd; de app en alle API-contracten blijven ongewijzigd.

## Acceptance criteria

1. Een aanroep van `WeatherClient.hourlyForecast(...)` / `WindForecastClient.hourlyForecast(...)`
   die HTTP 503 krijgt, wordt automatisch opnieuw geprobeerd tot maximaal 3 pogingen in
   totaal; slaagt de tweede of derde poging, dan levert de aanroep normale data met
   `error == null` en zonder verouderd-markering.
2. Bij een HTTP 4xx anders dan 429 wordt niet geretryd (precies 1 HTTP-call) en blijft
   de huidige foutafhandeling gelden. Bij 429 en bij netwerk-/IO-fouten wordt wél
   geretryd.
3. Na een geslaagde ophaling en daarna een aanroep waarbij alle pogingen falen, levert
   de client de eerder opgehaalde voorspelling met `error == null` en met de
   verouderd-markering + het oorspronkelijke ophaalmoment.
4. Is de bewaarde voorspelling ouder dan 12 uur, dan levert de client géén data maar de
   bestaande foutmelding (`Kon Open-Meteo (-wind) niet ophalen (HTTP <code>).` c.q. de
   exception-variant) — tekst ongewijzigd t.o.v. vandaag.
5. Twee aanroepen binnen 10 minuten resulteren in precies 1 HTTP-call; een aanroep na het
   verlopen van de TTL doet weer een nieuwe HTTP-call. Het `hours`-argument blijft per
   aanroep correct afkappen, ook bij een cachehit (kleiner `hours` na groter `hours`
   levert de juiste kortere lijst).
6. Eén volledige briefing-opbouw (`BriefingService`, alle sectieproviders achter elkaar)
   veroorzaakt maximaal 1 HTTP-call naar de Open-Meteo-weer-URL en 1 naar de
   Open-Meteo-wind-URL, i.p.v. 3 + 3.
7. Bij verouderde data bevat de tekst van de secties Weerkaart, Kiten en Strandfietsen de
   normale inhoud plus `(gegevens van HH:MM)`; bij verse of TTL-gecachete data staat die
   toevoeging er niet. Zijn wind- en weerdata allebei verouderd met verschillende
   ophaalmomenten, dan wordt het oudste van de twee getoond.
8. Is er geen bruikbare last-known-good, dan tonen de drie secties exact de huidige
   foutmelding zoals vandaag.
9. Een definitief mislukte ophaalpoging levert precies één warning-logregel per
   client-aanroep, met statuscode of foutmelding erin.
10. Nieuwe/uitgebreide unittests dekken minimaal: retry bij 503 (incl. het aantal
    pogingen), teruggeven van last-known-good bij falen, verlopen daarvan na 12 uur, geen
    HTTP-call binnen de TTL, geen retry bij 4xx, en de `(gegevens van HH:MM)`-toevoeging
    in minstens één sectieprovider.
11. `mvn test` in `robberts-assistent-backend/` is groen; bestaande tests
    `OpenMeteoWeatherClientTest`, `OpenMeteoWindForecastClientTest`,
    `KiteSectionProviderTest`, `BeachCycleSectionProviderTest`,
    `WeatherMapSectionProviderTest`, `BriefingServiceTest` en
    `ModulithArchitectureTest` blijven slagen zonder inhoudelijke aanpassing van hun
    verwachtingen (behalve waar een sectie bewust een extra tekstsuffix krijgt).
12. De testsuite wordt niet merkbaar trager: de retry-pauzes en het "nu"-tijdstip (voor
    TTL en 12-uursgrens) zijn in tests instelbaar.

## Aannames

- Retry, TTL-cache en last-known-good komen in de twee Open-Meteo-clientklassen zelf te
  zitten, niet in een gedeelde wrapper of in de briefingsecties. Daarmee profiteren
  automatisch ook `WeatherTools`, `WeatherCouplingProbe` en `WindForecastCouplingProbe`
  ervan zonder wijziging; de interfaces `WeatherClient`/`WindForecastClient` en hun
  stubs blijven ongewijzigd.
- Gevolg daarvan: de "test"-knop op het Koppelingen-scherm kan binnen de TTL of vanuit
  last-known-good een geslaagd resultaat melden terwijl Open-Meteo op dat moment
  onbereikbaar is. Dat wordt geaccepteerd — het weerspiegelt dat de briefing wél data
  heeft — en de probes worden niet aangepast.
- De cache/last-known-good is puur in-memory (geen Firestore/Firebase Storage); na een
  pod-herstart is er geen last-known-good tot de eerste geslaagde call.
- Om de 12-uursgrens en de TTL testbaar te maken worden het tijdstip ("nu") en de
  retry-pauzes injecteerbaar gemaakt via constructorparameters met een productiedefault,
  net zoals `httpClient` dat nu al is; er wordt geen `Clock`-bean in de hele module
  geïntroduceerd.
- HTTP wordt in de tests gefaket via een eigen `java.net.http.HttpClient`-testdouble die
  `send(...)` overschrijft (geen nieuwe testdependency, geen echte lokale server).
  Bestaat er tijdens implementatie een blokkade hiervoor, dan is een
  `com.sun.net.httpserver.HttpServer` op loopback een acceptabel alternatief.
- Worst case wordt een aanroep trager (3 × maximaal 10 s timeout + ~2,5 s pauze). Dat is
  acceptabel voor de uurlijkse cache-refresh en de handmatige reload-knop; er wordt geen
  aparte totaal-deadline of asynchrone opbouw toegevoegd.
- De `(gegevens van HH:MM)`-toevoeging komt alleen in de sectieteksten (voor Weerkaart in
  de tekst van het bestaande item, omdat de sectietekst daar leeg is). De
  18:00-pushtekst (`shortSummary()`) en de tegels (`status`/`tileLabel`) blijven
  ongewijzigd — verouderde data levert dus gewoon een tegel op, geen extra status.
- Verouderde data blokkeert het opbouwen van de weerkaart-PNG niet; die wordt normaal
  gegenereerd en opgeslagen op basis van de last-known-good-gegevens.
- Een TTL van 10 minuten en de uurlijkse `BriefingCacheScheduler` bijten elkaar niet: de
  uurlijkse refresh valt altijd buiten de TTL en haalt dus verse data op.

## Eindsamenvatting

Ik heb alle context (worklog, diff, review- en testbevindingen). Hier is de eindsamenvatting.

---

# SF-1621 — Open-Meteo 503: retry, last-known-good en calls delen

## Wat is gebouwd

De briefing toonde regelmatig "Kon Open-Meteo niet ophalen (HTTP 503)" bij Weerkaart, Kiten en Strandfietsen. Backend-only opgelost (16 bestanden, geen frontend-, endpoint- of dependency-wijziging):

- **Nieuwe `weather/ForecastFetcher.kt`** — één gedeelde ophaalstrategie voor beide Open-Meteo-clients:
  - **Retry**: max. 3 pogingen met pauzes van 500 ms en 2000 ms bij netwerk-/IO-fout, HTTP 5xx en 429; bij overige 4xx direct stoppen. Per-poging-timeout van 10 s blijft.
  - **TTL-cache van 10 minuten**, thread-veilig via double-checked locking op een `@Volatile`-veld (zelfde stijl als de basiskaart-cache in `OsmCoastMapImageBuilder`).
  - **Last-known-good**: falen alle pogingen, dan de laatst geslaagde respons met `stale = true`, mits jonger dan 12 uur; anders de bestaande foutmelding met ongewijzigde tekst.
  - Precies één `logger.warn` per definitief mislukte aanroep, met statuscode of foutmelding.
- **`OpenMeteoWeatherClient`/`OpenMeteoWindForecastClient`** doen nog alleen parse + afkappen op `hours` en delegeren het ophalen.
- **`WeatherForecast`/`WindForecast`** kregen `fetchedAt: Instant? = null` en `stale: Boolean = false` — alle bestaande aanroepen en stubs compileren ongewijzigd.
- **Briefingsecties**: `SlotAssessmentProvider` geeft `AssessmentResult.Ok.staleSince` mee (het oudste ophaalmoment van wind/weer); Kiten en Strandfietsen tonen `(gegevens van HH:MM)` (Europe/Amsterdam) op een eigen regel, `WeatherMapSectionProvider` achter de tekst van het bestaande item.

## Gemaakte keuzes

- **Ruwe respons-body gecachet** i.p.v. de geparste voorspelling, zodat het "vanaf nu"-filter in `parseForecast` ook bij een cache- of LKG-hit tegen de actuele tijd draait.
- **Strategie in de clients zelf**, niet in een wrapper of in de secties — daardoor profiteren `WeatherTools` en de CouplingProbes automatisch mee, zonder wijziging aan de interfaces of stubs.
- **`now`, `sleeper` en `retryDelaysMs` als constructorparameters met productiedefault** (geen `Clock`-bean), zodat tests geen echte wachttijd hebben.
- **Puur in-memory** cache/LKG (geen Firestore); na een pod-herstart is er geen LKG tot de eerste geslaagde call.
- **Tegels, `shortSummary()`/18:00-push en de PNG-opbouw ongewijzigd** — verouderde data is alleen zichtbaar in de sectietekst.

## Wat is getest

- `mvn -o test` in `robberts-assistent-backend/`: **359 tests, 0 failures, 0 errors**, build 47 s — geen merkbare vertraging. Bestaande testverwachtingen zijn niet aangepast.
- Nieuw: `FakeHttpClient` (testdouble op `java.net.http.HttpClient`, telt calls) en `WeatherCallSharingTest` — één opbouw van weerkaart + kiten + strandfietsen doet **1 weer-call en 1 wind-call** (was 3 + 3).
- Alle 12 acceptatiecriteria per stuk geverifieerd: retry-aantallen bij 503/429/IOException (3 calls) vs. 404 (1 call), LKG na 30 min met `stale == true`, vervallen na 13 uur met ongewijzigde fouttekst, cachehit met correct afkappen op `hours`, en de `(gegevens van HH:MM)`-toevoeging incl. "oudste van wind/weer wint".
- **E2E op preview `robberts-assistent-pr-42`**: `/healthz` 200, `GET /api/v1/briefing` 200 met alle secties, `POST /refresh` 3× achter elkaar telkens 200 (1,7–1,9 s) zonder 503-melding en zonder verouderd-suffix, weerkaart-PNG 200 (182 kB). Screenshot van de Vandaag-tab gemaakt.

## Bewust niet gedaan / aandachtspunten

- **Buiten scope gehouden** (conform story): `OpenMeteoAirQualityClient`, `TideClient`, `CalendarClient`, het samenvoegen van dubbele `SlotAssessmentProvider`-instanties, de briefing-cache en de 18:00-push.
- **Geen "recent mislukt"-cache** (gesignaleerd door reviewer én tester, niet blokkerend): bij een echte Open-Meteo-storing doorloopt elk van de drie secties opnieuw de volledige retry-reeks, dus tot 3 × (3 pogingen × 10 s + 2,5 s) per client per briefing-opbouw. Functioneel correct — alle drie tonen dezelfde last-known-good — maar de reload-knop of de uurlijkse scheduler kan dan minutenlang duren. AC6 is alleen in het slaagpad afgedwongen. **Kandidaat voor een vervolgstory.**
- `staleNotice()` toont alleen `HH:MM` zonder datum (story-conform), terwijl LKG tot 12 uur oud mag zijn — over een dagovergang is het tijdstip ambigu.
- Een `InterruptedException` tijdens de retry-pauze propageert uit `hourlyForecast(...)`; alleen relevant bij shutdown.
- De "test"-knop op het Koppelingen-scherm kan binnen de TTL of vanuit LKG succes melden terwijl Open-Meteo onbereikbaar is — expliciet geaccepteerd in de story-aannames.
