# SF-1534 - Worklog

Story-context bij eerste pickup:
Watches-feature implementeren

Nieuwe watches-module (datamodel, repository met Firestore/in-memory fallback, controller, scheduler met isDue-logica, watchChatClient, WatchCouplingProbe) + frontend (api_client uitbreiden, watches_screen.dart, 6e tab in home_screen, FCM deep-link type=watch). Schrijf unittests voor isDue-logica en repository.

Stappenplan:
[x]: read issue and target docs
[x]: Backend: Watch datamodel + WatchRepository + Firestore/in-memory impl
[x]: Backend: WatchesController (CRUD endpoints)
[x]: Backend: WatchScheduler + isDue-logica + watchChatClient
[x]: Backend: htmlToPlainText + page fetcher
[x]: Backend: WatchCouplingProbe
[x]: Backend: Unit tests voor isDue en repository
[x]: Frontend: ApiClient uitbreiden met watch-endpoints
[x]: Frontend: watches_screen.dart
[x]: Frontend: 6e tab in home_screen + FCM deep-link
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Issue en docs gelezen, patronen bekeken (ReminderRepository, BriefingAiConfig, CouplingProbe).

## Implementatie

### Backend (`watches`-module)
Nieuwe module met dezelfde structuur als `reminders`:
- `Watch.kt`: datamodel met enums `WatchFrequency` (KANTOORUREN/DAGELIJKS) en `WatchStatus` (ONBEKEND/GEVONDEN/NIET_GEVONDEN)
- `WatchRepository.kt` + `InMemoryWatchRepository.kt` + `FirestoreWatchRepository.kt`: zelfde Firestore/in-memory fallback-patroon
- `WatchRepositoryConfig.kt`: bean-selectie op basis van Firebase-config
- `WatchesService.kt`: CRUD + status-updates
- `WatchesController.kt`: REST-endpoints (GET/POST/DELETE /api/v1/watches, PATCH /{id}/toggle)
- `WatchAiConfig.kt`: losse `watchChatClient` (tool-loos, BriefingAiConfig-patroon) met system-prompt voor GEVONDEN/NIET GEVONDEN-beoordeling
- `WatchPageFetcher.kt`: haalt pagina op en converteert HTML naar platte tekst (eigen `htmlToPlainText()` kopie, ModulithArchitectureTest-bewaking)
- `WatchScheduler.kt`: `@Scheduled(fixedDelayString = "${ra.watches.poll-interval-ms:300000}")` pollt actieve watches; pure `isDue()`-functie bepaalt of nu gecheckt moet worden; bij transitie NIET_GEVONDEN→GEVONDEN push + deactiveer
- `WatchCouplingProbe.kt`: verschijnt op Koppelingen-scherm, configured = !effectiveMockAi

### Unit tests
- `WatchSchedulerIsDueTest.kt`: 12 tests voor isDue-logica (KANTOORUREN ma-vr 09-17 elk uur, DAGELIJKS 24h interval)
- `WatchRepositoryTest.kt`: 10 tests voor InMemoryWatchRepository (save/findById/all/activeWatches/delete/toggle)
- `WatchPageFetcherTest.kt`: 14 tests voor htmlToPlainText (script/style verwijdering, entity-decoding, whitespace-compressie, max-length)

### Frontend (`robberts_assistent`)
- `api_client.dart`: Watch-model + listWatches/createWatch/toggleWatch/deleteWatch methodes
- `watches_screen.dart`: lijst met watches, FAB voor nieuwe watch, swipe-acties (flutter_slidable) voor pauzeren/verwijderen met bevestigingsdialoog
- `home_screen.dart`: 6e tab (vóór Meer) met Watches-icoon (visibility)
- `fcm_service.dart`: deep-link type=watch → tab index 4

### Flutter tests
- `home_screen_test.dart`: aangepast van 5 naar 6 tabs, test voor Watches-tab + FCM deep-link

## Testresultaten
- Backend: 339 tests, 0 failures, BUILD SUCCESS
- Flutter: 38 tests, 0 failures, All tests passed!
