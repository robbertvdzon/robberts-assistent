# SF-1489 - Worklog

Story-context bij eerste pickup:
Zoekopdrachten (watches): backend-module + app-tab

Langdurige zoekopdrachten: laat de assistent een webpagina in de gaten houden (bv. "is dit
uitverkochte product weer op voorraad?") en geef een seintje zodra het zover is. Nieuwe
Spring-Modulith-module `watches` in de backend + een nieuw tabblad "Zoekopdrachten" in
`robberts_assistent`.

## Stappenplan

- [x] Issue + factory-docs (`development.md`, `technical-spec.md`) en de bestaande patronen
      (`reminders`, `briefing.BriefingAiConfig`, `schedules_screen.dart`) gelezen
- [x] Backend-module `nl.vdzon.robbertsassistent.watches` gebouwd (model, repository-poort +
      Firestore/in-memory, service met check, AI-config, scheduler, REST-controller)
- [x] Frontend: `watches_screen.dart`, `ApiClient`-sectie + `Watch`-model, nieuwe tab op index 4
      in `home_screen.dart`, `watch`-deep-link in `fcm_service.dart`
- [x] Zelf tests geschreven: backend (frequentie-/aan-de-beurt-logica, statusparsing, push bij de
      omslag, repository-bean-selectie, foutafhandeling per watch) en app (widget-test voor
      `watches_screen.dart` + uitgebreide `home_screen_test.dart`)
- [x] Vangnet gedraaid: `mvn clean test` (backend, incl. `ModulithArchitectureTest`),
      `flutter test` + `flutter analyze` (`robberts_assistent`)
- [x] Worklog bijgewerkt

## Gedaan / waarom

**Backend — nieuwe module `watches`** (leunt alleen op `firebase`, `push` en `auth`):

- `Watch.kt`: model + `WatchFrequency` (`KANTOORUREN`/`DAGELIJKS`) met een tolerante
  `fromName`-conversie voor API/Firestore-invoer.
- `WatchRepository` + `InMemoryWatchRepository` + `FirestoreWatchRepository` (collectie `watches`,
  doc-id = watch-id); `WatchRepositoryConfig` kiest ertussen exact volgens
  `ReminderRepositoryConfig` (in-memory zonder Firebase-config, `runCatching`-fallback bij een
  init-fout zodat de app nooit crasht).
- `WatchesService`: CRUD + één check — pagina ophalen met de JDK-`HttpClient` (User-Agent +
  timeout, redirects volgen), `htmlToPlainText` binnen de module (bewuste duplicatie: de variant in
  `assistant/ai/WindTools` is `internal` en `ModulithArchitectureTest` bewaakt de grens), daarna
  instructie + paginatekst naar de tool-loze `watchChatClient`. Het antwoord wordt defensief
  geparsed (`parseAssessment`): regel 1 `GEVONDEN`/`NIET GEVONDEN`, regel 2 de statuszin; een
  niet-herkend of leeg antwoord levert `found = false` met een neutrale/ruwe status. Daardoor is
  `RA_MOCK_AI`/preview deterministisch en pushvrij, zónder een eigen mock-schakelaar.
- Push uitsluitend bij de omslag "niet gevonden" → "gevonden" en alleen bij `pushOnFound`: één
  `PushService.sendToAll(title, status, {"type": "watch"})`. Bij die omslag gaat de watch daarna op
  `active = false` (afgerond), zodat er geen herhaalde meldingen komen.
- `WatchScheduler`: `@Scheduled(fixedDelayString = "${ra.watches.poll-interval-ms:300000}")` met de
  pure, los testbare `isDue(watch, now, zone)` — KANTOORUREN = hoogstens één keer per klokuur op
  ma t/m vr in de uren 09:00 t/m 16:59 (Europe/Amsterdam), DAGELIJKS = hoogstens één keer per
  kalenderdag; gepauzeerd = nooit. Elke check zit in een eigen `runCatching`, en `check()` vangt
  netwerk-/HTTP-/AI-fouten zelf af tot alleen een `lastError` op díe watch (vorige status blijft
  staan, geen push, rest gaat door).
- `WatchesController`: `GET`/`POST /api/v1/watches`, `PUT`/`DELETE /api/v1/watches/{id}` en
  `POST /api/v1/watches/{id}/check`, alles achter `authService.requireAuthorization(...)` in de
  stijl van `RemindersController`; pauzeren/hervatten loopt via `active` op de `PUT`.

Testbaarheid: `WatchesService` is `open` met een `internal open fetchPageText(...)` en
`sendFoundPush(...)`, zodat tests netwerk en push kunnen vervangen zonder `java.net.http.HttpClient`
na te maken — hetzelfde patroon als `briefing.OsmCoastMapImageBuilder.fetchMap`, waarvoor in deze
repo al precedent is (en er is géén precedent voor het mocken van `HttpClient`, zie
`HvcWasteClientTest`). De `httpClient` blijft daarnaast een constructor-parameter met default,
net als bij `assistant.ai.WindTools`.

**Frontend — `robberts_assistent`**:

- `watches_screen.dart`: lijst met per opdracht titel, laatste status, laatste controlemoment +
  frequentie (en "gepauzeerd"), een eventuele foutmelding, en een groene markering + vinkje bij
  gevonden. Per rij "nu controleren" (spinner op die rij), pauzeren/hervatten en verwijderen met
  bevestigingsdialoog; aanmaken/bewerken via `WatchDialog` (titel, URL, instructie, frequentie,
  push-schakelaar) in de stijl van `schedules_screen.dart`.
- `ApiClient`: eigen `// -- Zoekopdrachten --`-sectie + `Watch`-modelklasse met `fromJson`, in de
  stijl van `Reminder`.
- `home_screen.dart`: nieuwe tab **Zoekopdrachten op index 4**, vóór "Meer" (dat naar 5 schuift);
  beide parallelle lijsten (IndexedStack-children én `NavigationBar.destinations`) bijgewerkt.
  Index 0 (Upcoming) en het default-tabblad (`_tab = 2`) zijn ongewijzigd, dus de bestaande
  briefing-deep-link blijft werken.
- `fcm_service.dart`: tweede tak in `_handleTap` voor `data['type'] == 'watch'` → tabindex 4.

**Tests (zelf geschreven)**

- Backend: `WatchSchedulerTest` (aan-de-beurt-logica incl. weekend- en uurranden 08:59/09:00/
  16:59/17:00, één keer per klokuur resp. per kalenderdag, gepauzeerd; plus `pollDue` die de niet
  aan de beurt zijnde watches overslaat en waarbij een falende watch de rest niet blokkeert),
  `WatchesServiceTest` (CRUD, statusparsing herkend/niet-herkend/leeg, `htmlToPlainText`,
  precies één push bij de omslag en géén tweede/ongewijzigd-gevonden push, geen push zonder
  `pushOnFound`, mock-AI = niet gevonden zonder push, falende fetch/AI zet alleen `lastError`),
  `WatchRepositoryConfigTest` (bean-selectie). `ModulithArchitectureTest` bewaakt de nieuwe grens.
- App: `watches_screen_test.dart` (10 widget-tests) + `home_screen_test.dart` uitgebreid met het
  nieuwe aantal/de volgorde van tabs en de `watch`-deep-link naar index 4.

## Resultaat vangnet

- `mvn test` (backend, na `rm -rf target` — `mvn clean` kan niet offline, de clean-plugin zit niet
  in de lokale repo): 333 tests, 0 failures, 0 errors, BUILD SUCCESS, incl.
  `ModulithArchitectureTest` en de 29 nieuwe `watches`-tests.
- `flutter test` (`robberts_assistent`): 48 tests, alles groen.
- `flutter analyze` (`robberts_assistent`): No issues found.
