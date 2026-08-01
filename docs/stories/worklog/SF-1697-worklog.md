# SF-1697 - Worklog

Story-context bij eerste pickup:
Frequentiekeuze verwijderen en uurlijkse dagcontrole (08:00-22:00) implementeren

Verwijder het begrip controlefrequentie volledig en vervang de planning door één vaste regel, inclusief tests.

Backend (robberts-assistent-backend/src/main/kotlin/nl/vdzon/robbertsassistent/):
- watches/WatchSchedule.kt: isDue(watch, now) wordt: watch.active EN lokaal uur (Europe/Amsterdam) in 8..22 EN (lastCheckedAt == null OF >= 1 uur verstreken). Geen werkdag/weekend-onderscheid meer. Poll-interval (ra.watches.poll-interval-ms) en WatchRunner.runNow() (blijft isDue overslaan) ongewijzigd.
- watches/Watch.kt: enum WatchFrequency weg, veld frequency uit de data class.
- watches/WatchService.kt: parameter frequency uit create() en update(); overig gedrag van update() (reset naar NOG_NIET_GECONTROLEERD + active = true) ongewijzigd.
- watches/WatchesController.kt: frequency uit SaveWatchRequest en WatchResponse; endpoints, auth en run-now-flow ongewijzigd.
- watches/FirestoreWatchRepository.kt: frequency niet meer wegschrijven in toMap() en niet meer uitlezen in toWatch(). LET OP: de huidige toWatch() geeft null terug als frequency ontbreekt/onbekend is - die guard MOET weg, anders verdwijnen bestaande documenten stil uit de lijst. Documenten mét en zónder oud frequency-veld moeten allebei foutloos inlezen; geen migratie.
- assistant/ai/WatchTools.kt: @ToolParam frequency uit createWatch/updateWatch, helpers parseFrequency() en frequencyText() verwijderen, en de Nederlandse antwoordzinnen van listWatches/createWatch/updateWatch aanpassen zodat ze geen frequentiekeuze meer noemen (bijv. 'elk uur overdag'). Bestaand gedrag (id-prefix-match, validatiefout als leesbare zin, geen delete via chat) blijft gelijk. AiConfig/defaultTools niet wijzigen.

App (robberts_assistent/):
- lib/watches_screen.dart: dropdown 'Controlefrequentie' weg, inclusief _frequency, het frequency-veld op _WatchInput en het meesturen bij aanmaken/bewerken; geen frequentieweergave in de lijst.
- lib/api_client.dart: frequency-parameter uit createWatch()/updateWatch() en het frequency-veld uit het Watch-model + fromJson (m['frequency'] as String crasht anders zodra de backend het veld niet meer stuurt).

Tests (onderdeel van deze subtaak):
- WatchScheduleTest.kt: wel om 08:00 en 22:00, niet om 07:00/23:00/03:00, wel in het weekend, niet binnen een uur na lastCheckedAt, altijd false bij active == false.
- WatchRunnerTest.kt, WatchServiceTest.kt, assistant/ai/WatchToolsTest.kt.
- robberts_assistent/test/watches_screen_test.dart en robberts_assistent/test/home_screen_test.dart.

Controleer dat WatchFrequency nergens meer voorkomt. Sluit af met een zelf-review van de diff. mvn test (vanuit robberts-assistent-backend/) en flutter test + flutter analyze (vanuit robberts_assistent/) moeten groen zijn.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1698 (development) - uitgevoerd

Backend:
- `watches/Watch.kt`: enum `WatchFrequency` verwijderd en het veld `frequency` uit de data class
  `Watch` gehaald.
- `watches/WatchSchedule.kt`: `isDue` is nu een enkele regel zonder frequentie-onderscheid -
  actief + lokaal uur (Europe/Amsterdam) in `8..22` + (`lastCheckedAt == null` of >= 1 uur
  verstreken). Werkdag/weekend-onderscheid weg; `DayOfWeek`-import verwijderd.
- `watches/WatchService.kt`, `watches/WatchesController.kt` (`SaveWatchRequest`/`WatchResponse`):
  `frequency`-parameter/-veld weg; endpoints, auth en de run-now-flow ongewijzigd.
- `watches/FirestoreWatchRepository.kt`: `frequency` wordt niet meer weggeschreven in `toMap()`.
  De guard in `toWatch()` die `null` teruggaf bij een ontbrekend/onbekend `frequency`-veld is
  verwijderd - bestaande documenten met of zonder dat veld lezen nu allebei gewoon in (geen
  migratie; het oude veld blijft ongelezen staan tot een document opnieuw wordt opgeslagen).
- `assistant/ai/WatchTools.kt`: `@ToolParam frequency` uit `createWatch`/`updateWatch`, helpers
  `parseFrequency()`/`frequencyText()` weg, en de Nederlandse antwoordzinnen van
  `listWatches`/`createWatch`/`updateWatch` zeggen nu "elk uur overdag". `AiConfig`/`defaultTools`
  ongewijzigd. De KDoc van `WatchRunner.runNow()` verwees nog naar "frequentie" en is bijgewerkt.

App (`robberts_assistent/`):
- `lib/watches_screen.dart`: dropdown "Controlefrequentie" weg, inclusief `_frequency`, het
  `frequency`-veld op `_WatchInput` en het meesturen bij aanmaken/bewerken.
- `lib/api_client.dart`: `frequency`-parameter uit `createWatch()`/`updateWatch()` en het
  `frequency`-veld uit het `Watch`-model + `fromJson` (dat `m['frequency'] as String` zou anders
  gooien zodra de backend het veld niet meer meestuurt).

Tests:
- `WatchScheduleTest.kt` volledig herschreven: dagvenster 08:00 t/m 22:59 (niet om 07:00/23:00/
  03:00), weekend telt gewoon mee, minimaal een uur tussen twee controles, een verstreken uur
  buiten het venster maakt nog niets aan de beurt, en `active == false` geeft altijd `false`.
- `WatchRunnerTest.kt`, `WatchServiceTest.kt`, `assistant/ai/WatchToolsTest.kt` aangepast aan de
  nieuwe signaturen; de frequentie-vertaaltest is vervangen door een test dat de bevestigingen
  geen frequentiekeuze meer noemen.
- Nieuw `FirestoreWatchRepositoryTest.kt` (Mockito-mocks van Firestore/CollectionReference/
  DocumentReference/DocumentSnapshot): een document met en zonder oud `frequency`-veld leest
  foutloos in, en `save()` schrijft geen `frequency` meer weg.
- Nieuw `WatchesControllerTest.kt` (`@SpringBootTest` met random poort, zelfde patroon als
  `BriefingControllerTest`): `POST`/`PUT /api/v1/watches` zonder `frequency` in de body slaagt en
  de response bevat het veld niet.
- `robberts_assistent/test/watches_screen_test.dart` en `test/home_screen_test.dart` aangepast;
  het aanmaak-/bewerkdialoog wordt nu expliciet getoetst op de afwezigheid van een
  frequentie-dropdown, en de lijst op de afwezigheid van "Dagelijks"/"Kantooruren".

Vangnet:
- `mvn -o test` (vanuit `robberts-assistent-backend/`): 373 tests, 0 failures, 0 errors,
  BUILD SUCCESS.
- `flutter analyze` (vanuit `robberts_assistent/`): No issues found.
- `flutter test`: 61 tests, All tests passed.
