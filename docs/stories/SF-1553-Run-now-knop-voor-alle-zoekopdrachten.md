# SF-1553 - Run now-knop voor alle zoekopdrachten

## Story

Run now-knop voor alle zoekopdrachten

<!-- refined-by-factory -->

## Samenvatting

Op het scherm met zoekopdrachten komt een extra knop bovenin waarmee je alle lopende
zoekopdrachten meteen laat controleren, in plaats van te wachten tot het vaste schema
weer aan de beurt is. Terwijl de controle loopt zie je dat er iets gebeurt en kun je niet
per ongeluk twee keer starten. Zodra alles gecontroleerd is, staat de bijgewerkte status
direct in de lijst. Gaat er iets mis, dan krijg je een duidelijke melding onderin beeld.
Zoekopdrachten die al gevonden zijn en daarom stilgezet, blijven ongemoeid.

## Scope

Backend (`robberts-assistent-backend/.../watches/`):
- `WatchRunner` krijgt naast `poll(now)` een tweede instapmethode (bv. `runNow(now)`) die de
  `WatchSchedule.isDue`-filtering overslaat en alle watches met `active == true` controleert
  via dezelfde bestaande private `check(watch, now)`-logica — die logica wordt hergebruikt,
  niet gedupliceerd of aangepast.
- Watches met `active == false` (o.a. alles wat al op `GEVONDEN` staat) worden overgeslagen.
- Gedrag per check blijft exact gelijk aan de geplande run: `status`/`statusDescription`/
  `lastCheckedAt` bijwerken via `repository.compareAndSet`, bij vondst `active = false`, en bij
  de eerste overgang naar `GEVONDEN` precies één push via `WatchPushNotifier` als
  `notifyOnFound` aanstaat. Een mislukte fetch/AI-beoordeling zet `status = ONBEKEND` met de
  bestaande melding en stopt de rest van de run niet.
- `WatchesController` krijgt `POST /api/v1/watches/run-now` met `authService.requireAuthorization
  (authorization)` zoals de andere endpoints; het endpoint draait de run synchroon af en geeft
  daarna de bijgewerkte `WatchesResponse` terug (zelfde vorm/opbouw als `GET`/`DELETE`, via de
  bestaande private `response()`-helper).
- Geen wijziging aan `Watch`, `WatchRepository`, `WatchSchedule`, `WatchEvaluator`,
  `WatchPageFetcher` of de bestaande `@Scheduled poll()`.

Frontend (`robberts_assistent/`):
- `ApiClient` krijgt een `runWatchesNow()`-methode die `POST /api/v1/watches/run-now` aanroept en
  de teruggegeven `watches`-lijst als `List<Watch>` parseert (zelfde parse-patroon als
  `listWatches()`), zodat er geen extra `GET` nodig is.
- `watches_screen.dart`: een extra `IconButton` in de AppBar naast de bestaande refresh-knop
  (bv. `Icons.play_circle_outline`) met een Nederlandse tooltip, die de run start.
- Tijdens de run: de knop is disabled (en de refresh-knop ook, om dubbel werk te voorkomen) en
  toont/vergezelt een voortgangsindicatie; de bestaande lijst blijft zichtbaar zodat de UI niet
  in een leeg laadscherm springt. Na afloop wordt de lijst met het resultaat van de call
  bijgewerkt.
- Bij een fout: een `SnackBar` met een duidelijke Nederlandse melding, zelfde patroon als
  `_add`/`_edit`/`_delete` in dit scherm; de knop wordt daarna weer bruikbaar.

Tests:
- `WatchRunnerTest` uitbreiden (zelfde fakes-opzet: `FakeFetcher`/`FakeEvaluator`/`FakePush`).
- `watches_screen_test.dart` uitbreiden (zelfde `_FakeApiClient`-opzet).

## Acceptance criteria

1. `POST /api/v1/watches/run-now` controleert alle watches met `active == true`, ongeacht
   `frequency` en `lastCheckedAt`, en geeft na afloop de bijgewerkte lijst terug in dezelfde
   JSON-vorm als `GET /api/v1/watches` (`{"watches":[...]}`).
2. Een watch met `active == false` wordt tijdens een run-now niet gefetcht en niet beoordeeld
   (fetcher/evaluator worden er niet voor aangeroepen) en zijn velden blijven ongewijzigd.
3. Een watch die tijdens de run als gevonden wordt beoordeeld, krijgt `status = GEVONDEN`,
   `active = false`, de beoordelingstekst als `statusDescription`, een bijgewerkte
   `lastCheckedAt`, en er gaat precies één push uit als `notifyOnFound` aanstaat.
4. Staat `notifyOnFound` uit, of stond de watch al op `GEVONDEN`, dan gaat er géén push uit.
5. Faalt de fetch of de AI-beoordeling voor één watch, dan krijgt die watch
   `status = ONBEKEND` met de bestaande "later opnieuw geprobeerd"-melding, en worden de
   overige watches in dezelfde run alsnog gecontroleerd.
6. Zonder geldige `Authorization`-header geeft het endpoint dezelfde afwijzing als de andere
   watches-endpoints (`authService.requireAuthorization`).
7. In de app staat op het scherm Zoekopdrachten naast de refresh-knop een "nu draaien"-knop;
   indrukken start één run.
8. Tijdens de run is die knop disabled en zichtbaar "bezig" (voortgangsindicatie); een tweede
   tik start geen tweede run. Na afloop is de knop weer bruikbaar en toont de lijst de
   teruggekomen statussen.
9. Faalt de call, dan verschijnt een `SnackBar` met een duidelijke foutmelding, blijft de
   bestaande lijst zichtbaar en is de knop weer bruikbaar.
10. `mvn test` (backend) en `flutter analyze` + `flutter test` (`robberts_assistent`) slagen,
    inclusief nieuwe tests voor punt 1–5 (`WatchRunnerTest`) en 7–9 (`watches_screen_test.dart`).

## Aannames

- De run is synchroon: de HTTP-request blijft open tot alle actieve watches gecontroleerd zijn
  (enkele seconden per watch). Er komt geen asynchrone job/statuspolling en geen extra
  timeout-configuratie; `ApiClient` kent nu ook geen client-side timeout.
- Er komt geen server-side lock tegen gelijktijdige runs: het bestaande
  `WatchRepository.compareAndSet`-patroon voorkomt al dubbele pushes en verloren updates bij
  overlap met de `@Scheduled poll()`. Het voorkomen van dubbele runs is een UI-verantwoordelijkheid.
- Een run-now wijzigt geen `frequency`/`active`-instellingen anders dan via de bestaande
  check-logica, en beïnvloedt het reguliere schema niet verder dan via de bijgewerkte
  `lastCheckedAt` (waardoor de eerstvolgende geplande check navenant opschuift — dit is
  bestaand `WatchSchedule.isDue`-gedrag en wordt niet aangepast).
- De controller mag `WatchRunner` rechtstreeks injecteren (zelfde module, geen
  `ModulithArchitectureTest`-grens) of via `WatchService` delegeren; dat is een
  implementatiekeuze van de developer.
- Zijn er geen actieve watches, dan is run-now een geldige no-op die simpelweg de (ongewijzigde)
  lijst teruggeeft; geen aparte foutmelding.
- Geen wijziging aan het aantal of de volgorde van de app-tabs, aan de FCM-deep-link
  (`data.type=watch`) of aan CLAUDE.md-documentatie buiten wat de documenter-rol doet.

## Eindsamenvatting

## Eindsamenvatting SF-1553 — Run now-knop voor alle zoekopdrachten

**Wat is gebouwd**

Op het scherm *Zoekopdrachten* staat nu naast de refresh-knop een "nu draaien"-knop (▶) waarmee alle lopende zoekopdrachten meteen gecontroleerd worden, zonder te wachten op het vaste schema.

- **Backend**: `WatchRunner.runNow(now)` controleert alle zoekopdrachten met `active == true`, ongeacht frequentie of laatste controlemoment, via exact dezelfde bestaande controle-logica als de geplande run. Nieuw endpoint `POST /api/v1/watches/run-now` (met dezelfde autorisatie als de andere watches-endpoints) draait de run synchroon af en geeft direct de bijgewerkte lijst terug in dezelfde JSON-vorm als `GET /api/v1/watches`.
- **App**: `ApiClient.runWatchesNow()` gebruikt het antwoord van de run rechtstreeks om de lijst bij te werken (geen extra ophaalronde). Tijdens de run zijn de nu-draaien-knop, de refresh-knop en de knop "Nieuwe zoekopdracht" uitgeschakeld en toont de knop een voortgangsindicatie; de bestaande lijst blijft zichtbaar. Bij een fout verschijnt een Nederlandse melding onderin ("Nu controleren mislukt: …") en is de knop weer bruikbaar.

**Keuzes**

- Controle-logica is hergebruikt, niet gedupliceerd: gedrag per zoekopdracht (status bijwerken, stilzetten bij een vondst, precies één push, foutafhandeling) is gegarandeerd identiek aan de geplande run.
- Reeds gevonden (inactieve) zoekopdrachten worden overgeslagen en blijven ongemoeid.
- De run is synchroon: de request blijft open tot alles gecontroleerd is. Geen achtergrondjob/statuspolling en geen server-side lock — dubbele runs worden in de UI voorkomen, en de bestaande atomische opslag voorkomt al dubbele meldingen bij overlap met het schema.
- Tijdens de review kwam één bug boven water (het scherm kon in een permanente laadspinner blijven hangen als je vlak na een verversing op de knop drukte). Die is opgelost én afgedekt met tests.

**Wat is getest**

- Backend `mvn test`: 333 tests groen (incl. 5 nieuwe run-now-tests voor AC 1–5).
- App `flutter analyze` schoon, `flutter test`: 50 tests groen (5 nieuwe tests voor AC 7–9 plus de spinner-regressie).
- End-to-end op de preview-omgeving: lege lijst → geldige no-op; twee tijdelijke zoekopdrachten werden in één run allebei gecontroleerd ongeacht frequentie, een falende controle stopte de run niet; in de UI leverde één klik exact één run op, waarna de nieuwe statussen zichtbaar waren. Testdata is opgeruimd.

**Bewust niet gedaan**

- Geen automatische test op de autorisatie van het nieuwe endpoint (er bestaat geen controller-test in deze module; het endpoint volgt exact het bestaande patroon, en op preview staat auth uit). Door reviewer én tester als niet-blokkerend beoordeeld.
- Geen asynchrone run, statuspolling, timeout-instelling of server-side lock.
- Geen wijziging aan het bestaande schema/poller, aan de datamodellen, aan de tabs of aan de push-deeplink.
