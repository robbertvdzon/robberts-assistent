# SF-1553 - Worklog

Story-context bij eerste pickup:
Run-now voor alle actieve zoekopdrachten (backend + app)

Backend: voeg in WatchRunner een tweede instapmethode runNow(now) toe die WatchSchedule.isDue overslaat en alle watches met active == true door de bestaande private check(watch, now) haalt; check() zelf niet wijzigen of dupliceren, zodat status/statusDescription/lastCheckedAt-update, compareAndSet, active=false bij vondst, precies één push bij de eerste overgang naar GEVONDEN (alleen bij notifyOnFound) en de ONBEKEND-afhandeling bij een mislukte fetch/AI-call identiek blijven; een falende watch stopt de run niet. Voeg POST /api/v1/watches/run-now toe aan WatchesController met authService.requireAuthorization zoals de andere endpoints; draai de run synchroon en geef daarna via de bestaande private response()-helper de bijgewerkte WatchesResponse terug. Geen wijziging aan Watch, WatchRepository, WatchSchedule, WatchEvaluator, WatchPageFetcher of de @Scheduled poll(). Frontend (robberts_assistent): ApiClient.runWatchesNow() die het nieuwe endpoint aanroept en de watches-lijst parseert met hetzelfde patroon als listWatches(); in watches_screen.dart een extra IconButton in de AppBar naast refresh (bv. Icons.play_circle_outline, Nederlandse tooltip) met een _running-vlag die tijdens de run beide AppBar-knoppen disabled maakt en een voortgangsindicatie toont terwijl de bestaande lijst zichtbaar blijft; na afloop de lijst met het resultaat bijwerken (let op de bestaande _loadSequence-guard), bij een fout een SnackBar met duidelijke Nederlandse melding en de knop weer bruikbaar. Schrijf zelf de tests: WatchRunnerTest uitbreiden met de bestaande fakes en watches_screen_test.dart uitbreiden via _FakeApiClient. Voer daarna zelf een reviewslag uit en zorg dat mvn test (backend) en flutter analyze + flutter test (robberts_assistent) slagen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## Wat is er gedaan

### Backend (`robberts-assistent-backend/.../watches/`)
- `WatchRunner.runNow(now = Instant.now())` toegevoegd: filtert op `active == true` (i.p.v.
  `WatchSchedule.isDue`) en roept per watch dezelfde bestaande private `check(watch, now)` aan.
  `check()` is ongewijzigd, dus compareAndSet-gedrag, `active = false` bij vondst, precies één push
  bij de eerste overgang naar `GEVONDEN` (alleen bij `notifyOnFound`) en de `ONBEKEND`-afhandeling
  bij een mislukte fetch/AI-call zijn identiek aan de geplande run. Een falende watch stopt de run
  niet, omdat `check()` de fout zelf afvangt.
- `WatchesController`: `POST /api/v1/watches/run-now` met `authService.requireAuthorization(...)`,
  injecteert `WatchRunner` rechtstreeks (zelfde module), draait de run synchroon en geeft daarna de
  bestaande private `response()`-helper terug (`{"watches":[...]}`, zelfde vorm als `GET`/`DELETE`).
- Geen wijziging aan `Watch`, `WatchRepository`, `WatchSchedule`, `WatchEvaluator`,
  `WatchPageFetcher` of de `@Scheduled poll()`.

### Frontend (`robberts_assistent/`)
- `ApiClient.runWatchesNow()`: `POST /api/v1/watches/run-now` en parseert `watches` met hetzelfde
  patroon als `listWatches()` — geen extra `GET` nodig na de run.
- `watches_screen.dart`: extra AppBar-`IconButton` (`Icons.play_circle_outline`, tooltip
  "Alle zoekopdrachten nu controleren") naast de refresh-knop. Nieuwe `_running`-vlag zet tijdens de
  run beide AppBar-knoppen op `onPressed: null` en vervangt het play-icoon door een kleine
  `CircularProgressIndicator`; de bestaande lijst blijft zichtbaar (er wordt bewust geen `_loading`
  gezet, dus geen leeg laadscherm). `_runNow()` haakt in op de bestaande `_loadSequence`-guard zodat
  een oudere `_load()` het run-resultaat niet overschrijft. Bij een fout: `SnackBar`
  "Nu controleren mislukt: ..." (zelfde patroon als `_add`/`_edit`/`_delete`), knop daarna weer
  bruikbaar.

### Tests (zelf geschreven)
- `WatchRunnerTest` +5 tests: alle actieve watches ongeacht frequentie/`lastCheckedAt` (expliciet
  geverifieerd dat `WatchSchedule.isDue` voor géén van beide waar is), inactieve watch niet
  gefetcht/beoordeeld en ongewijzigd, vondst → `GEVONDEN` + `active = false` + precies één push
  (ook bij een tweede run-now geen tweede push), geen push zonder `notifyOnFound`, en een falende
  watch → `ONBEKEND` terwijl de overige watches alsnog gecontroleerd worden.
- `watches_screen_test.dart` +3 tests: knop start één run en toont de bijgewerkte lijst; tijdens de
  run voortgangsindicatie + beide knoppen disabled + tweede tik start geen tweede run + lijst blijft
  zichtbaar + na afloop weer bruikbaar (via `Completer`, geen `pumpAndSettle` op een hangende
  future); fout → `SnackBar` met behoud van de lijst en herbruikbare knop.

## Testresultaat
- Backend: `rm -rf target && mvn -o test` → 333 tests, 0 failures, 0 errors, 0 skipped
  (`WatchRunnerTest`: 14 tests groen). `mvn clean` kan niet offline, vandaar `rm -rf target`.
- App: `flutter analyze` → "No issues found!"; `flutter test` → 48 tests, all passed.

## Review (SF-1554, reviewer) — afgekeurd, 1 bug

Eigen gerichte verificatie: `mvn -o test -Dtest='Watch*Test,ModulithArchitectureTest'` → 30 tests
groen; `BriefingControllerTest` (@SpringBootTest) groen, dus de nieuwe `WatchRunner`-injectie in
`WatchesController` wiret. `flutter analyze` schoon; `flutter test test/watches_screen_test.dart`
→ 10 tests groen.

- [bug] `watches_screen.dart` `_runNow()` verhoogt `_loadSequence`, maar zet zelf nooit
  `_loading = false`. Een op dat moment lopende `_load()` slaat door de sequence-guard zijn
  `finally`-reset over, waardoor `_loading` permanent `true` blijft: de lijst verdwijnt achter de
  grote laadspinner en komt ook ná de run niet terug (alleen een handmatige refresh herstelt het).
  Bereikbaar tijdens de initiële load, na een FCM-reload (`didUpdateWidget`) en vlak na
  toevoegen/bewerken/verwijderen (die `await _load()` doen) — de AppBar-knoppen zijn daar telkens
  enabled. Botst met AC 8/9 en is een regressie t.o.v. de situatie vóór deze story.
  Fix-richting: in de `finally` van `_runNow()` ook `_loading = false` zetten, of run-now
  blokkeren zolang `_loading` waar is.
- [suggestie] FAB "Nieuwe zoekopdracht" blijft tijdens de run enabled terwijl `_add` intern
  `await _load()` doet en zo het runresultaat kan wegdrukken.
- [info] AC 6 (auth) heeft geen test, maar er bestaat geen `WatchesControllerTest` in deze module;
  het endpoint volgt exact het bestaande `requireAuthorization`-patroon. Geen blocker.
- [info] Backend-kant is conform scope: `runNow()` hergebruikt de ongewijzigde private `check()`,
  geen wijziging aan `poll()`/`Watch`/`WatchRepository`/`WatchSchedule`/`WatchEvaluator`/
  `WatchPageFetcher`; AC 1–5 zijn met 5 nieuwe `WatchRunnerTest`-tests gedekt.

## Herstelronde na review (SF-1554)

Stappenplan:
[x]: reviewbevinding reproduceren en analyseren
[x]: bug fixen in `watches_screen.dart`
[x]: suggestie (FAB tijdens run) meenemen
[x]: tests uitbreiden en vangnet draaien

### Bug (blocker) — permanente laadspinner
`_runNow()` hoogde `_loadSequence` op terwijl er nog een `_load()` liep; die `_load()` sloeg
daardoor zijn `finally { _loading = false }` over en het scherm bleef in de grote laadspinner
hangen. Twee-laags fix in `watches_screen.dart`:
- `_runNow()` start niet meer zolang `_loading` waar is (`if (_running || _loading) return;`), en
  de nu-draaien-knop is in de AppBar disabled zolang de lijst laadt
  (`onPressed: (_running || _loading) ? null : _runNow`). Zo kan de teller niet meer opgehoogd
  worden onder een lopende `_load()`.
- Als vangnet zet de `finally` van `_runNow()` ook zelf `_loading = false`, maar alleen als deze
  run nog de nieuwste is (`loadSequence == _loadSequence`) — zodat een intussen gestarte `_load()`
  (bv. via `didUpdateWidget`/FCM-reload) zijn eigen laadstatus houdt.

### Suggestie uit de review
De FAB "Nieuwe zoekopdracht" is tijdens een run disabled (`onPressed: _running ? null : _add`),
omdat `_add` intern `await _load()` doet en zo het runresultaat kon wegdrukken.

### Tests
`watches_screen_test.dart` +2 tests:
- FAB is enabled vóór de run, disabled tijdens de run en daarna weer enabled.
- Nu-draaien is disabled zolang de eerste load loopt, een tik start dan geen run, de load rondt
  gewoon af (lijst zichtbaar, geen permanente spinner) en daarna werkt nu-draaien wél.

### Testresultaat herstelronde
- App: `flutter analyze` → "No issues found!"; `flutter test` → 50 tests, all passed.
- Backend: `rm -rf target && mvn -o test` → 333 tests, 0 failures, 0 errors, 0 skipped
  (backend ongewijzigd in deze ronde).

## Review SF-1554 (ronde 2) — akkoord

Volledige story-diff t.o.v. `main` beoordeeld (`git diff main...HEAD`): `WatchRunner.runNow`,
`POST /api/v1/watches/run-now`, `ApiClient.runWatchesNow()`, `watches_screen.dart` + tests.

- Scope gerespecteerd: `Watch`, `WatchRepository`, `WatchSchedule`, `WatchEvaluator`,
  `WatchPageFetcher` en de `@Scheduled poll()` zijn ongewijzigd; `runNow` hergebruikt de
  ongewijzigde private `check(watch, now)`, dus compareAndSet/push/ONBEKEND-gedrag is identiek.
- De laadspinner-regressie uit ronde 1 is opgelost en getest; de `_loadSequence`-teller wordt
  niet meer onder een lopende `_load()` opgehoogd.
- Eigen verificatie in deze review-sandbox:
  - Backend gericht: `mvn -o test -Dtest='Watch*Test,ModulithArchitectureTest,BriefingControllerTest'`
    → WatchRunnerTest 14, WatchServiceTest 7, WatchScheduleTest 4, WatchEvaluatorTest 2,
    WatchPageFetcherTest 2, ModulithArchitectureTest 1, BriefingControllerTest 1 — 0 failures/errors.
    (De extra `WatchRunner`-injectie in `WatchesController` wiret aantoonbaar in een echte
    Spring-context.)
  - App: `flutter analyze` → "No issues found!"; `flutter test` → 50 tests, all passed.
- [info] AC 6 (auth) heeft geen automatische test; er bestaat geen `WatchesControllerTest` in deze
  module en het endpoint volgt exact het bestaande `requireAuthorization`-patroon. Geen blocker.

## Test SF-1555 (story-brede test) — akkoord

Volledig vangnet in deze tester-sandbox:

- Backend `mvn -o test` (in `robberts-assistent-backend/`), start `2026-07-31T16:05:49Z`,
  eind `2026-07-31T16:06:17Z`, exitcode 0 — surefire-totaal **333 tests, 0 failures, 0 errors,
  0 skipped** (incl. `WatchRunnerTest` 14 tests, waaronder de 5 nieuwe run-now-cases:
  "controleert alle actieve watches ongeacht frequentie en laatste controle",
  "slaat inactieve watches over en laat ze ongewijzigd", "met een vondst deactiveert en pusht
  precies eenmaal", "zonder meldingsvoorkeur deactiveert zonder push", "gaat na een mislukte
  watch door met de overige watches" → AC1–AC5).
- App `flutter analyze` → "No issues found!"; `flutter test` → **50 tests, all passed**
  (`watches_screen_test.dart` dekt AC7–AC9: één run per tik, knop bezig/disabled + geen tweede
  run, FAB uit tijdens de run, SnackBar bij fout met zichtbare lijst, geen hangende spinner).

E2E op preview `robberts-assistent-pr-38` (frontend-proxy, `RA_PREVIEW_SKIP_GOOGLE_AUTH`):

- Lege lijst: `POST /api/v1/watches/run-now` → HTTP 200 `{"watches":[]}` (no-op, geen fout) — AC1.
- Twee tijdelijke watches aangemaakt (`SF1555-tmp-ok`, `SF1555-tmp-badhost`, beide `active`,
  frequenties `DAGELIJKS`/`KANTOORUREN`, nooit eerder gecontroleerd): één `run-now` gaf HTTP 200
  met dezelfde JSON-vorm als `GET`, beide watches gecontroleerd ongeacht frequentie/`lastCheckedAt`,
  beide met `status = ONBEKEND` + "Controle mislukt; de opdracht wordt later opnieuw geprobeerd."
  en bijgewerkte `lastCheckedAt` — AC1 + AC5 (een falende watch stopt de run niet).
  (De preview-pod heeft geen internet-egress, dus ook de "goede" URL faalt; het GEVONDEN-pad is
  op preview niet forceerbaar onder `RA_MOCK_AI` — gedekt door `WatchRunnerTest`.)
- UI: op het scherm Zoekopdrachten staat naast de refresh-knop de nieuwe "nu draaien"-knop
  (`Icons.play_circle_outline`); één klik levert via Playwright-netwerk-intercept exact één
  `POST /api/v1/watches/run-now` op, daarna toont de lijst de teruggekomen statussen — AC7.
  Screenshots: `SF-1555-zoekopdrachten-run-now-knop.png`, `SF-1555-run-bezig.png`,
  `SF-1555-na-run.png`. De run duurde <0,5 s, dus de spinner is niet op het screenshot te
  vangen; die staat (AC8) wel in de widgettests.
- Testdata opgeruimd: beide watches verwijderd (HTTP 200), `GET /api/v1/watches` weer `{"watches":[]}`.
- AC6 (auth) is niet apart geautomatiseerd en op preview niet te testen (auth staat uit); het
  endpoint roept als eerste `authService.requireAuthorization(authorization)` aan, identiek aan
  de andere watches-endpoints. Geen blocker (zelfde constatering als de reviewer).

Geen code/tests/infra gewijzigd door de tester.
