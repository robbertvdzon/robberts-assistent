# SF-1491 - Worklog

Story-context bij eerste pickup:
Backend watches-module + Flutter Watches-tab

Bouw de volledige watches-feature: backend-module (model, repository port met Firestore/in-memory, service, controller, scheduler met AI-check, push bij GEVONDEN) en Flutter-app (zesde tab, watches_screen.dart met lijst/aanmaken/verwijderen, ApiClient-extensie). Inclusief unit-tests voor de backend.

Stappenplan:
[x]: read issue and target docs
[x]: implement backend watches-module (model, repository, service, controller, scheduler, AI-config)
[x]: implement Flutter watches-tab (watches_screen.dart, api_client extensie, home_screen update)
[x]: write backend unit tests
[x]: run backend tests (mvn test) — 315 tests, 0 failures
[x]: run flutter tests/analyze — 36 tests, all passed, no issues

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Docs gelezen: reminders-module als patroon, briefing voor AI-config, push voor notificaties.

Backend watches-module (8 nieuwe bestanden in `nl.vdzon.robbertsassistent.watches`):
- `Watch.kt`: model met `id`, `title`, `url`, `instruction`, `frequency` (KANTOORUREN/DAGELIJKS), `status` (ONBEKEND/GEVONDEN/NIET_GEVONDEN), `statusText`, `lastChecked`, `active`
- `WatchRepository.kt`: interface voor opslag
- `InMemoryWatchRepository.kt`: in-memory fallback (ConcurrentHashMap)
- `FirestoreWatchRepository.kt`: Firestore-impl (collectie `watches`)
- `WatchRepositoryConfig.kt`: bean-selectie (zelfde patroon als reminders)
- `WatchesService.kt`: CRUD-operaties
- `WatchesController.kt`: REST-endpoints `GET/POST/DELETE /api/v1/watches`
- `WatchAiConfig.kt`: losse `watchChatClient`-bean (geen tools, zelfde ChatModel als assistantChatClient)
- `WatchScheduler.kt`: @Scheduled poller met frequentie-regels, AI-beoordeling, push bij GEVONDEN, eigen `htmlToPlainText()`
- `ApiModels.kt`: request/response DTOs

Backend tests (2 nieuwe bestanden in `test/.../watches`):
- `WatchesServiceTest.kt`: 5 tests voor CRUD-operaties
- `WatchSchedulerTest.kt`: 6 tests voor AI-response parsing, htmlToPlainText, mock-modus

Flutter app (`robberts_assistent`):
- `watches_screen.dart`: nieuw scherm met lijst, FAB voor aanmaken, delete met bevestiging
- `api_client.dart`: `listWatches()`, `createWatch()`, `deleteWatch()`, `Watch`-model toegevoegd
- `home_screen.dart`: Watches-tab op index 4 (vóór Meer), 6 tabs totaal
- `test/home_screen_test.dart`: test aangepast voor 6 tabs i.p.v. 5
