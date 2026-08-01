# SF-1697 - Zoekopdrachten: frequentie-keuze weg, altijd elk uur van 08:00 t/m 22:00

## Story

Zoekopdrachten: frequentie-keuze weg, altijd elk uur van 08:00 t/m 22:00

<!-- refined-by-factory -->

## Samenvatting

Bij zoekopdrachten kun je nu nog kiezen tussen "Dagelijks" en "Kantooruren". Die keuze verdwijnt
helemaal. Voortaan wordt elke actieve zoekopdracht gewoon elk uur gecontroleerd overdag: de
eerste controle om 08:00 en de laatste om 22:00 (Nederlandse tijd), ook in het weekend. Tussen
23:00 en 07:00 gebeurt er niets.

Het invulscherm voor een zoekopdracht heeft dus één veld minder, en ook via de chat-assistent
kun je geen frequentie meer opgeven. Bestaande zoekopdrachten blijven gewoon werken; hun oude
frequentie-instelling wordt genegeerd.

## Scope

Backend (`robberts-assistent-backend/src/main/kotlin/nl/vdzon/robbertsassistent/`):

- `watches/Watch.kt`: enum `WatchFrequency` verwijderen en het veld `frequency` uit de data class
  `Watch` halen.
- `watches/WatchSchedule.kt`: `isDue(watch, now)` wordt één regel zonder frequentie-onderscheid:
  `watch.active` **en** het lokale uur (`Europe/Amsterdam`) ligt in `8..22` **en**
  (`lastCheckedAt == null` **of** er is ≥ 1 uur verstreken sinds `lastCheckedAt`). Geen
  werkdag/weekend-onderscheid meer.
- `watches/WatchService.kt`: parameter `frequency` uit `create()` en `update()` halen. Overig
  gedrag van `update()` (reset naar `NOG_NIET_GECONTROLEERD` + `active = true`) ongewijzigd.
- `watches/WatchesController.kt`: veld `frequency` uit `SaveWatchRequest` en `WatchResponse`
  halen. Endpoints, auth en de `run-now`-flow ongewijzigd.
- `watches/FirestoreWatchRepository.kt`: `frequency` niet meer wegschrijven in `toMap()` en niet
  meer uitlezen in `toWatch()`. Belangrijk: de huidige `toWatch()` geeft `null` terug als
  `frequency` ontbreekt of onbekend is — na deze wijziging mag een document mét of zónder
  `frequency`-veld gewoon inlezen. Geen migratie; het oude veld blijft ongelezen in Firestore
  staan tot een document opnieuw wordt opgeslagen.
- `assistant/ai/WatchTools.kt`: de `@ToolParam frequency` uit `createWatch`/`updateWatch` halen,
  de helpers `parseFrequency()` en `frequencyText()` verwijderen, en de Nederlandse
  antwoordzinnen van `listWatches`/`createWatch`/`updateWatch` aanpassen zodat ze geen
  "dagelijks"/"elk uur tijdens kantooruren" meer noemen (bijv. "elk uur overdag").
  `AiConfig`/`defaultTools` hoeven niet te wijzigen.

Frontend (`robberts_assistent/`):

- `lib/watches_screen.dart`: de dropdown "Controlefrequentie" (Dagelijks / Kantooruren) weg,
  inclusief `_frequency`, het `frequency`-veld op `_WatchInput` en het meesturen ervan bij
  aanmaken/bewerken. Eventuele weergave van de frequentie in de lijst weg.
- `lib/api_client.dart`: de `frequency`-parameter uit `createWatch()`/`updateWatch()` en het
  `frequency`-veld uit het `Watch`-model + `fromJson` halen (nodig: `m['frequency'] as String`
  gooit anders een fout zodra de backend het veld niet meer meestuurt).

Tests bijwerken/uitbreiden:

- `WatchScheduleTest.kt`, `WatchRunnerTest.kt`, `WatchServiceTest.kt`,
  `assistant/ai/WatchToolsTest.kt`
- `robberts_assistent/test/watches_screen_test.dart` en `robberts_assistent/test/home_screen_test.dart`

Buiten scope: het poll-interval van `WatchRunner` (`ra.watches.poll-interval-ms`, default 300000)
blijft ongewijzigd, evenals `WatchRunner.runNow()` (blijft `isDue` overslaan), `WatchEvaluator`,
`WatchPageFetcher`, `WatchRepository`, de push-afhandeling en de URL's van de REST-endpoints.

## Acceptance criteria

1. `WatchFrequency` bestaat niet meer in de codebase; `Watch`, `WatchService.create/update`,
   `SaveWatchRequest` en `WatchResponse` hebben geen `frequency`-veld/-parameter meer.
2. `WatchSchedule.isDue` geeft `true` voor een actieve, nooit-gecontroleerde zoekopdracht om
   08:00 en om 22:00 lokale tijd, en `false` om 07:00, 23:00 en 03:00 — ongeacht de dag van de
   week.
3. `isDue` geeft `true` in het weekend binnen 08:00–22:00 (het oude werkdag-onderscheid is weg).
4. `isDue` geeft `false` als er minder dan een uur is verstreken sinds `lastCheckedAt`, en `true`
   zodra er ≥ 1 uur is verstreken (mits binnen 08:00–22:00 en actief).
5. `isDue` geeft altijd `false` voor een zoekopdracht met `active == false`, ook binnen
   08:00–22:00.
6. Een `POST`/`PUT /api/v1/watches` zonder `frequency` in de body slaagt; de response bevat geen
   `frequency`-veld.
7. `FirestoreWatchRepository` leest een bestaand document mét een oud `frequency`-veld foutloos in
   (geen `null`/overgeslagen watch) en schrijft bij opslaan geen `frequency` meer weg.
8. De chat-tools `createWatch` en `updateWatch` hebben geen `frequency`-parameter meer; hun
   antwoordzinnen en die van `listWatches` noemen geen frequentiekeuze meer. Bestaand gedrag
   (id-prefix-match, validatiefout als leesbare zin, geen delete via chat) blijft gelijk.
9. Het aanmaak-/bewerkdialoog in `watches_screen.dart` toont geen frequentieveld meer; aanmaken en
   bewerken werken onveranderd en de lijst toont nergens nog een frequentie.
10. `mvn test` (vanuit `robberts-assistent-backend/`) en `flutter test` + `flutter analyze`
    (vanuit `robberts_assistent/`) draaien groen, met de hierboven genoemde uitgebreide tests.

## Aannames

- "Laatste controle om 22:00" wordt gelezen als: het lokale uur ligt in `8..22`, dus een controle
  kan nog tot en met 22:59 starten. Vanaf 23:00 tot en met 07:59 gebeurt er niets.
- Er is geen datamigratie in Firestore nodig; het oude `frequency`-veld blijft daar staan tot een
  document opnieuw wordt opgeslagen en wordt verder genegeerd.
- Bestaande zoekopdrachten worden door deze wijziging niet gereset: hun status, `lastCheckedAt` en
  `active` blijven staan; alleen het controleritme verandert.
- Het effectieve ritme blijft bepaald door de combinatie van de vijfminuten-poller en de
  1-uur-regel; een controle valt dus niet exact op het hele uur.
- De "nu draaien"-knop blijft alle actieve zoekopdrachten direct controleren, ook buiten
  08:00–22:00 (`runNow` slaat `isDue` bewust over).
- Geen API-versionering of achterwaartse compatibiliteit voor een meegestuurd `frequency`-veld:
  app en backend gaan in dezelfde release mee.

## Eindsamenvatting

# Eindsamenvatting SF-1697 — Zoekopdrachten: frequentiekeuze weg, altijd elk uur van 08:00 t/m 22:00

## Wat is er gebouwd

De instelling "Controlefrequentie" (Dagelijks / Kantooruren) bestaat niet meer. Elke actieve zoekopdracht wordt voortaan volgens één vaste regel gecontroleerd: **elk uur overdag, van 08:00 tot en met 22:59 (Nederlandse tijd), alle dagen van de week** — dus ook in het weekend. Tussen 23:00 en 07:59 gebeurt er niets.

- **Backend:** `WatchFrequency` en het veld `frequency` zijn volledig verdwenen uit het model, de service, de REST-request/response en de Firestore-opslag. `WatchSchedule.isDue` is teruggebracht tot één regel: actief + lokaal uur in 8..22 + minstens een uur sinds de vorige controle.
- **Chat-assistent:** `createWatch` en `updateWatch` hebben geen frequentie-parameter meer; de Nederlandse antwoordzinnen van `listWatches`/`createWatch`/`updateWatch` zeggen nu "elk uur overdag".
- **App:** de dropdown in het aanmaak-/bewerkdialoog is weg, de lijst toont nergens nog een frequentie, en het `frequency`-veld is uit het `Watch`-model en de API-client gehaald.

## Belangrijke keuzes

- **Geen datamigratie in Firestore.** Het oude `frequency`-veld blijft ongelezen in bestaande documenten staan tot ze opnieuw worden opgeslagen. Cruciaal detail: de oude leescode gooide een watch weg als `frequency` ontbrak of onbekend was — die guard is verwijderd, zodat documenten mét én zónder het veld gewoon inlezen en geen bestaande zoekopdracht stil uit de lijst verdwijnt.
- **"Laatste controle om 22:00"** is geïnterpreteerd als: een controle kan nog starten tot en met 22:59.
- **Bestaande zoekopdrachten worden niet gereset** — status, `lastCheckedAt` en `active` blijven staan; alleen het ritme verandert.
- **"Nu draaien" blijft ongewijzigd:** die knop controleert alle actieve opdrachten direct, ook buiten 08:00–22:00.
- Het effectieve ritme blijft de combinatie van de vijfminuten-poller en de 1-uur-regel; een controle valt dus niet exact op het hele uur.

## Wat is getest

- **Backend `mvn test`:** 373 tests groen (0 failures/errors). `WatchScheduleTest` dekt het dagvenster (08:00 en 22:00 wel; 07:00, 23:00 en 03:00 niet), weekend, de 1-uursregel en inactieve opdrachten. Nieuw toegevoegd: `FirestoreWatchRepositoryTest` (oud document mét en zónder `frequency` leest foutloos in; opslaan schrijft het veld niet meer weg) en `WatchesControllerTest` (POST/PUT zonder `frequency` slaagt, response bevat het veld niet).
- **App:** `flutter analyze` zonder issues, `flutter test` 61 tests groen.
- **E2E op de preview-omgeving (PR #43):** POST/PUT zonder `frequency` → HTTP 200 zonder `frequency` in de response; een oude client die nog wél `frequency` meestuurt krijgt geen fout (veld wordt genegeerd); `run-now` werkt onveranderd; testdata is opgeruimd.
- **UI-screenshots:** aanmaak- en bewerkdialoog zonder frequentieveld, lijst zonder frequentie.

## Bewust niet gedaan

- Geen wijziging aan het poll-interval (`ra.watches.poll-interval-ms`, 300000 ms), `WatchRunner.runNow()`, de evaluator/page-fetcher, de push-afhandeling of de REST-URL's.
- Geen API-versionering of achterwaartse compatibiliteit voor de frontend: app en backend gaan in dezelfde release mee.
- `WatchTools` is niet via de preview-chat end-to-end getest — die draait met `RA_MOCK_AI=true` en roept per ontwerp geen tools aan; afgedekt via unit-tests plus een grep die bevestigt dat frequentie nergens meer in productiecode voorkomt.
- Eén cosmetisch punt uit de review is bewust blijven staan: een scheve inspringing in `test/watches_screen_test.dart` (geen format-check in CI).

Geen openstaande bevindingen; de story is klaar voor documentatie en merge.
