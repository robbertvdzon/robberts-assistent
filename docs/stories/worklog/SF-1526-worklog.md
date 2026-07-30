# SF-1526 - Worklog

Story-context bij eerste pickup:
Watches-module (backend) + Zoekopdrachten-tab (frontend)

Backend: nieuwe module `watches` (Watch-model, WatchRepository-poort met Firestore/in-memory-implementatie zoals `reminders`, WatchesController voor CRUD op /api/v1/watches, WatchAiConfig met tool-loze watchChatClient naar het patroon van briefing.BriefingAiConfig, WatchPageFetcher met eigen htmlToPlainText()-kopie, WatchScheduler met één @Scheduled poller die per actieve watch de frequentie-logica (KANTOORUREN/DAGELIJKS) toepast, bij transitie naar GEVONDEN precies één PushService.sendToAll stuurt en active=false zet, en falende watches geïsoleerd afvangt). Frontend: ApiClient-CRUD-methoden voor /api/v1/watches, nieuw watches_screen.dart naar het patroon van schedules_screen.dart, en een nieuwe tab 'Zoekopdrachten' op index 4 in home_screen.dart (Meer schuift op naar index 5, overige tabs/indices en de briefing-deep-link blijven ongewijzigd). Inclusief unit tests voor de 'aan de beurt?'-tijdslogica en de defensieve AI-antwoord-parsing.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1527 - Watches-module (backend) + Zoekopdrachten-tab (frontend)

Backend (nieuwe module `watches`, patroon `reminders`/`briefing`):
- `Watch` (id/title/url/instruction/frequency/notifyOnFound/status/statusText/active/lastCheckedAt),
  `WatchFrequency` (KANTOORUREN/DAGELIJKS), `WatchStatus` (ONBEKEND/NIET_GEVONDEN/GEVONDEN).
- `WatchRepository`-poort + `InMemoryWatchRepository`/`FirestoreWatchRepository` (collectie
  `watches`) + `WatchRepositoryConfig` (kiest op `FirebaseProvider.isConfigured`, zelfde patroon
  als `reminders.ReminderRepositoryConfig`).
- `WatchesService` (create/list/update/delete/active/save); `update()` reactiveert de watch
  (`active = true`) en wist de vorige beoordeling (`status = ONBEKEND`, `lastCheckedAt = null`) —
  dit is de manier waarop "opnieuw aanpassen" (zie AC4) een gestopte watch weer laat pollen, er is
  geen apart activeer-knopje in de UI.
- `WatchesController`: `GET/POST /api/v1/watches`, `PUT/DELETE /api/v1/watches/{id}`, auth via
  `AuthService.requireAuthorization`, 400 bij lege velden of een ongeldige `frequency`-waarde.
- `WatchPageFetcher`: eigen `HttpClient`-fetch + eigen `htmlToPlainText()`-kopie (de variant in
  `assistant.ai.WindTools` is `internal`, dus niet herbruikbaar over modulegrenzen heen — bewaakt
  door `ModulithArchitectureTest`, expliciet genoemd in de story-scope). `open class`/`open fun`
  zodat `WatchSchedulerTest` een test-subklasse kan maken die de HTTP-call overslaat (patroon uit
  de agent-tips: `OsmCoastMapImageBuilder`).
- `WatchAiConfig.watchChatClient`: losse, tool-loze `ChatClient` (patroon
  `briefing.BriefingAiConfig`), systeemprompt vraagt exact twee regels
  (`GEVONDEN`/`NIET GEVONDEN` + statuszin).
- `WatchVerdict.parse()`: defensieve parsing — "NIET GEVONDEN" wordt eerst gecheckt (anders matcht
  de substring "GEVONDEN" ook een negatief antwoord), leeg/onverwacht antwoord (ook
  `MockChatModel` onder `RA_MOCK_AI`) valt terug op `ONBEKEND` zonder te crashen.
- `WatchScheduling.isDue()`: pure functie, los van Spring — KANTOORUREN test op ma-vr 09-17u +
  een ander uur-blok dan `lastCheckedAt`; DAGELIJKS test op een andere kalenderdag (beide in
  `Europe/Amsterdam`).
- `WatchScheduler` (`@Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")`):
  filtert actieve + due watches, haalt de pagina op, laat de AI beoordelen, en bij een transitie
  náár GEVONDEN (dus niet bij een poll die al GEVONDEN was) stuurt 'ie bij `notifyOnFound=true`
  precies één `PushService.sendToAll(..., data["type"]="watch")` en zet de watch op inactief.
  Fouten (fetch of AI) worden per watch met `runCatching` afgevangen en gelogd; `lastCheckedAt`
  blijft dan ongewijzigd zodat de volgende poll-ronde het gewoon opnieuw probeert — andere watches
  en de rest van de ronde blijven ongemoeid (AC5).
- Geen nieuwe `CouplingProbe`: watches hergebruiken de bestaande `ChatModel`-bean (mock/echt) en
  een kale `HttpClient`, geen nieuw secret — niet in scope volgens de story.
- Tests: `WatchSchedulingTest` (aan-de-beurt-tijdlogica, incl. weekend/kantooruren-grens/uurblok-
  wisseling), `WatchVerdictTest` (defensieve AI-antwoord-parsing, incl. `MockChatModel`-achtig
  antwoord), `WatchesServiceTest`, `WatchSchedulerTest` (transitie naar GEVONDEN + push + inactief,
  blijft actief zolang niet gevonden, slaat inactieve/niet-due watches over, isolatie van een
  falende pagina-ophaal of AI-call). Backend-vangnet: `mvn -o test` → 329 tests, 0 failures/errors,
  BUILD SUCCESS (incl. `ModulithArchitectureTest`).

Frontend (`robberts_assistent`):
- `ApiClient`: `Watch`/`WatchFrequency`/`WatchStatus`-modellen + `listWatches`/`createWatch`/
  `updateWatch`/`deleteWatch` tegen `/api/v1/watches`.
- `watches_screen.dart` (patroon `schedules_screen.dart` + het bevestig-dialoog-patroon uit
  `conversations_screen.dart`): lijst met titel + status-tekst (+ "gestopt" als `active=false`),
  FAB opent een aanmaak-dialoog (titel/url/instructie/frequentie-dropdown/pushmelding-switch), tik
  op een item opent hetzelfde dialoog vooraf ingevuld (bewerken), prullenbak-icoon vraagt eerst
  een bevestiging. `DropdownButtonFormField` staat op `isExpanded: true` + `TextOverflow.ellipsis`
  — zonder die twee overflowde de lange KANTOORUREN-label-tekst de smalle dialoogbreedte in
  widget-tests (RenderFlex-overflow, ontdekt tijdens het schrijven van de widget-test).
- `home_screen.dart`: nieuwe 6e tab "Zoekopdrachten" op index 4 (`Icons.search`), "Meer" schuift
  naar index 5; bestaande tabs/indices en de briefing-deep-link (`FcmService.deepLinkTab`, wijst
  naar index 0) ongewijzigd.
- Tests: `watches_screen_test.dart` (titel+status tonen, lege staat, aanmaken, bewerken
  vooraf-ingevuld, verwijderen-met-bevestiging) + `home_screen_test.dart` bijgewerkt (6 tabs i.p.v.
  5, "Zoekopdrachten"-label, fake `listWatches()`). `flutter analyze`: geen issues. `flutter test`:
  alle 41 tests groen (volledige suite, niet alleen de nieuwe/gewijzigde bestanden).

Niet gedaan / bewust buiten scope:
- Geen `WatchesControllerTest` (MockMvc) — bestaand patroon in deze repo heeft ook geen
  `RemindersControllerTest`; de service-/scheduler-/parsing-/tijdlogica is wel volledig getest.
- Geen `CouplingProbe` voor watches — geen nieuw secret/koppeling, dus niet van toepassing.
- Geen losse "activeren"-knop in de UI — reactiveren gebeurt impliciet via bewerken (zie boven),
  conform de story-aanname dat titel/url/instructie/frequentie/pushmelding de enige invoervelden
  van het dialoog zijn.

## Review (SF-1527)

Code-review tegen de volledige story-diff (`git diff main...HEAD`), plus gerichte verificatie:
- Backend: `mvn -o test -Dtest='Watch*Test,ModulithArchitectureTest'` → 26 tests groen, incl.
  module-grenzen bewaakt; extra `BriefingControllerTest` (bestaande `@SpringBootTest`) groen ter
  bevestiging dat de nieuwe beans (`WatchAiConfig`, `WatchesController`, ...) de Spring-context
  niet breken. Volledig backend-vangnet daarna herbevestigd: `mvn -o test` → 329 tests, 0
  failures/errors, BUILD SUCCESS.
- Frontend: `flutter analyze` (geen issues) en de volledige `flutter test`-suite (41/41 groen,
  inclusief `watches_screen_test.dart`/`home_screen_test.dart`) daadwerkelijk gedraaid in de
  reviewer-sandbox (flutter 3.44.x aanwezig) — geen blanco review nodig.
- Inhoudelijk tegen de AC's gecontroleerd: CRUD + validatie (`WatchesController`), transitie-naar-
  GEVONDEN met precies één push + `active=false` (`WatchScheduler`, getest), isolatie van fetch-/
  AI-fouten per watch, tab-index 4 met "Meer" naar 5 (`home_screen.dart`), bestaande tabs/briefing-
  deep-link ongewijzigd. Patronen (repository-poort, controller-stijl, AI-config) volgen
  consistent `reminders`/`briefing`. Geen blockers gevonden.

## Test (SF-1528)

- Backend-vangnet: `mvn -o test` opnieuw gedraaid (start 2026-07-30T04:39:54Z, eind
  2026-07-30T04:40:21Z) → 329 tests, 0 failures/errors, BUILD SUCCESS (incl.
  `ModulithArchitectureTest`). Een stacktrace in de output is verwacht/gelogd gedrag van de
  `WatchScheduler`-isolatietest (falende fetch/AI wordt met `runCatching` afgevangen en gelogd).
- Frontend-vangnet: `flutter pub get` + `flutter analyze` (geen issues) + `flutter test`
  (volledige suite) → 41/41 groen, inclusief `watches_screen_test.dart`/`home_screen_test.dart`.
  Flutter-SDK was dit keer bruikbaar in de sandbox.
- E2E op preview `robberts-assistent-pr-36`
  (`https://robberts-assistent-frontend-robberts-assistent-pr-36.apps.sno.lab.vdzon.com`):
  - `GET/POST/PUT/DELETE /api/v1/watches` via de frontend-proxy (geen auth-header nodig,
    `RA_PREVIEW_SKIP_GOOGLE_AUTH`): create → list toont de nieuwe watch → PUT wijzigt
    titel/instructie/frequentie/notifyOnFound → validatie geeft 400 bij ongeldige `frequency` en
    bij een lege `title` → DELETE + list bevestigt opruiming. Alle tijdelijke testdata
    (`tester-tijdelijk-aaltjes`, `tester-ui-check`) achteraf verwijderd, `GET /api/v1/watches`
    geeft weer `{"watches":[]}`.
  - Browser-screenshots (Playwright/Chromium, viewport 480x900,
    `screenshots/00-initial.png`..`04-edit-dialog.png`): bottom-nav toont 6 tabs met
    "Zoekopdrachten" op index 4 en "Meer" op index 5 (AC6); lege staat toont de verwachte tekst;
    aanmaakdialoog bevat titel/url/instructie/frequentie-dropdown/pushmelding-switch (AC1); de
    lijst toont titel + statustekst per zoekopdracht (AC2); tik op een item opent hetzelfde
    dialoog vooraf ingevuld (bewerken, AC2).
  - AC3 (poller haalt pagina op + AI-beoordeling) en AC4 (transitie naar GEVONDEN + precies één
    push + `active=false`) zijn op preview niet forceerbaar binnen de poll-intervaltijd/onder
    `RA_MOCK_AI` (geeft altijd een niet-`GEVONDEN`-uitkomst) — al eerder zo genoteerd voor de
    vergelijkbare SF-1489-poller; deze paden zijn wel volledig gedekt door
    `WatchSchedulerTest`/`WatchSchedulingTest`/`WatchVerdictTest` (backend-vangnet groen). AC5
    (isolatie van fouten) idem gedekt door `WatchSchedulerTest`.
- Geen bugs gevonden. Geen code/tests/infra gewijzigd; alleen dit worklog bijgewerkt en
  tijdelijke testdata (met cleanup).
