# SF-1621 - Worklog

Story-context bij eerste pickup:
Retry, last-known-good en TTL-cache in de Open-Meteo-clients + verouderd-melding in de briefingsecties

Implementeer in weather/OpenMeteoWeatherClient en weather/OpenMeteoWindForecastClient dezelfde ophaalstrategie: (a) TTL-cache van 10 minuten op de volledige voorspelling, per aanroep afgekapt op 'hours', thread-veilig (stijl CoastMapImageBuilder); (b) retry met maximaal 3 pogingen en pauzes van ~0,5s en ~2s bij netwerk-/IO-fout, HTTP 5xx en 429, geen retry bij overige 4xx, bestaande 10s per-poging-timeout blijft; (c) last-known-good in geheugen met ophaalmoment, teruggegeven met error==null plus verouderd-markering als alle pogingen falen en de waarde jonger is dan 12 uur, anders de huidige foutmelding met ongewijzigde tekst; (d) precies één slf4j logger.warn per definitief mislukte aanroep met statuscode of foutmelding. Voeg aan WeatherForecast en WindForecast twee optionele velden toe (ophaalmoment + verouderd-vlag) met null/false-default zodat bestaande aanroepen en tests blijven compileren. Maak 'nu' en de retry-pauzes injecteerbaar via constructorparameters met productiedefault (geen Clock-bean). Laat SlotAssessmentProvider de verouderd-markering (oudste ophaalmoment van wind/weer) meegeven in AssessmentResult.Ok, en laat KiteSectionProvider, BeachCycleSectionProvider en WeatherMapSectionProvider (in de tekst van het bestaande item) bij verouderde data de normale inhoud tonen met de toevoeging '(gegevens van HH:MM)' in Europe/Amsterdam; bij verse of TTL-gecachete data geen toevoeging, en zonder bruikbare last-known-good exact de huidige foutmelding. Interfaces WeatherClient/WindForecastClient, de stubs, de CouplingProbes, shortSummary()/de 18:00-push, status/tileLabel, de briefing-cache en de frontend blijven ongewijzigd; de weerkaart-PNG wordt ook bij verouderde data normaal opgebouwd. Schrijf de unittests mee: retry bij 503 incl. aantal pogingen, geen retry bij 4xx, wel bij 429, last-known-good bij falen, verlopen na 12 uur, geen HTTP-call binnen de TTL, correct afkappen op 'hours' bij een cachehit, en de '(gegevens van HH:MM)'-toevoeging in minstens één sectieprovider; fake HTTP met een eigen java.net.http.HttpClient-testdouble (fallback: com.sun.net.httpserver.HttpServer op loopback), zonder nieuwe testdependency. Sluit af met een eigen review-stap en zorg dat mvn test in robberts-assistent-backend/ groen is, inclusief de bestaande OpenMeteo*ClientTests, Kite-/BeachCycle-/WeatherMapSectionProviderTest, BriefingServiceTest en ModulithArchitectureTest.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1628 (development)

### Implementatie

- **Nieuw: `weather/ForecastFetcher.kt`** (internal). Eén gedeelde ophaalstrategie voor beide
  Open-Meteo-clients, omdat ze allebei één vaste URL ophalen en client-side op `hours` afkappen:
  - TTL-cache van 10 minuten met double-checked locking rond een `@Volatile`-veld (zelfde stijl als
    de basiskaart-cache in `OsmCoastMapImageBuilder`), zodat de uurlijkse scheduler en de
    reload-knop niet dubbel fetchen.
  - Retry: maximaal 3 pogingen met pauzes van 500 ms en 2000 ms, bij netwerk-/IO-fout, HTTP 5xx en
    429. Bij overige 4xx direct stoppen. De per-poging-timeout van 10 s blijft.
  - Last-known-good: falen alle pogingen, dan de laatst geslaagde respons met `stale = true`, mits
    jonger dan 12 uur; anders de bestaande foutmelding (tekst ongewijzigd).
  - Precies één `logger.warn` per definitief mislukte aanroep, met statuscode of foutmelding.
  - Bewust de **ruwe respons-body** gecachet i.p.v. de geparste voorspelling, zodat het
    "vanaf nu"-filter in `parseForecast` ook bij een cache-/last-known-good-hit tegen de actuele
    tijd gebeurt.
- **`OpenMeteoWeatherClient` / `OpenMeteoWindForecastClient`** doen nu alleen nog parse + afkappen
  en delegeren het ophalen aan `ForecastFetcher`. `now`, `sleeper` en `retryDelaysMs` zijn extra
  constructorparameters met productiedefault (geen `Clock`-bean); Spring vult ze niet in, net als
  de bestaande `httpClient`-default — geverifieerd doordat de `@SpringBootTest`-contexten
  (`BriefingControllerTest`, `AssistantIntegrationTest`) gewoon booten.
- **`WeatherForecast` / `WindForecast`** kregen `fetchedAt: Instant? = null` en
  `stale: Boolean = false`; alle bestaande aanroepen/stubs compileren ongewijzigd. `stale` is
  alleen waar bij een last-known-good-teruggave, niet bij een verse call of TTL-cachehit.
- **Briefingsecties**: `SlotAssessmentProvider` geeft `AssessmentResult.Ok.staleSince` mee (het
  oudste ophaalmoment van de verouderde wind-/weerbronnen, via de gedeelde helpers
  `oldestStaleMoment()`/`staleNotice()` in `KiteSectionProvider.kt`). Kiten en strandfietsen zetten
  `(gegevens van HH:MM)` (Europe/Amsterdam) op een eigen regel onder de normale tekst;
  `WeatherMapSectionProvider` hangt 'm achter de tekst van het bestaande item, omdat de sectietekst
  daar leeg is. Tegels (`status`/`tileLabel`), `shortSummary()`/de 18:00-push, de PNG-opbouw en de
  frontend zijn ongewijzigd.

### Tests

- Nieuw `weather/FakeHttpClient.kt` (test): testdouble op `java.net.http.HttpClient` die geplande
  antwoorden teruggeeft en `send`-aanroepen telt — geen nieuwe testdependency, geen lokale server.
- `OpenMeteoWeatherClientTest`/`OpenMeteoWindForecastClientTest`: retry bij 503 inclusief het
  aantal pogingen en de pauzes, geen retry bij 4xx, wel bij 429 en netwerkfout, last-known-good bij
  falen, vervallen na 12 uur, geen HTTP-call binnen de TTL, nieuwe call na de TTL, en correct
  afkappen op `hours` bij een cachehit. Tijd en pauzes zijn geïnjecteerd, dus geen wachttijd.
- Nieuw `briefing/WeatherCallSharingTest`: één opbouw van weerkaart + kiten + strandfietsen doet
  precies 1 weer-call en 1 wind-call (was 3 + 3).
- `Kite-`, `BeachCycle-` en `WeatherMapSectionProviderTest`: de `(gegevens van HH:MM)`-toevoeging
  bij verouderde data (incl. "oudste van wind/weer telt") én de afwezigheid ervan bij verse data.

### Resultaat

`rm -rf target && mvn -o test` in `robberts-assistent-backend/`: **Tests run: 359, Failures: 0,
Errors: 0** — BUILD SUCCESS. Bestaande verwachtingen zijn niet aangepast.

### Review SF-1628 (reviewer)

Akkoord. Volledige story-diff t.o.v. `main` beoordeeld (16 bestanden, alleen backend + worklog);
alle 12 acceptatiecriteria terug te vinden in code én test. Eigen gerichte verificatie:
`mvn -o test -Dtest='OpenMeteoWeatherClientTest,OpenMeteoWindForecastClientTest,
KiteSectionProviderTest,BeachCycleSectionProviderTest,WeatherMapSectionProviderTest,
WeatherCallSharingTest,BriefingServiceTest,ModulithArchitectureTest,BriefingControllerTest'` —
groen, 0 failures/errors; `BriefingControllerTest` boot de volledige Spring-context, dus de extra
Kotlin-default-constructorparameters (`now`/`sleeper`/`retryDelaysMs`) wiren gewoon.

Aandachtspunten (niet blokkerend, meegegeven voor een eventuele vervolgstory):

- [suggestie] Er wordt geen "recent mislukt"-status gecachet. Bij een echte Open-Meteo-storing
  doet elk van de drie weersecties opnieuw de volledige retry-reeks (3 pogingen + ~2,5 s pauze),
  ook als er al een last-known-good is teruggegeven — dus ~3× de worst case per briefing-opbouw
  per client, i.p.v. de 1× die de story-aanname beschrijft. Functioneel correct (de secties tonen
  dezelfde LKG-data), alleen trager bij de reload-knop en de uurlijkse scheduler. Op te lossen
  door bij een definitieve mislukking het faalmoment kort te onthouden en binnen de TTL direct
  LKG/fout terug te geven.
- [info] `staleNotice()` toont alleen `HH:MM` zonder datum (story-conform), terwijl een
  last-known-good tot 12 uur oud mag zijn; over een dagovergang is het tijdstip dus ambigu.
- [info] Verouderde data raakt bewust niet de tegels (`status`/`tileLabel`) en de 18:00-push —
  conform de story-aannames, maar dus geen signaal richting gebruiker buiten de sectietekst.
