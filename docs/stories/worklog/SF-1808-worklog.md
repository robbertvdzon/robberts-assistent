# SF-1808 - Worklog

Story-context bij eerste pickup:
Notities: undo/redo, versiegeschiedenis en nachtelijk opruimen

Implementeer de hele story in één stap, inclusief tests.

Backend (robberts-assistent-backend/.../notes/):
- Nieuw versietype (id, text, savedAt: Instant). Breid NotesRepository uit met: versie toevoegen, nieuwste versies met limiet (nieuwste eerst), versie op id, alle versies (voor opruimen) en verwijderen op id. Implementeer volledig in zowel FirestoreNotesRepository (subcollectie notes/note/versions met velden text + savedAt, auto-id) als InMemoryNotesRepository (lijst + UUID), zodat tests zonder Firebase draaien. Document notes/note (veld text) blijft ongewijzigd de huidige tekst.
- NotesService.update(text): eerst huidige tekst wegschrijven (bestaand gedrag/returnwaarde), daarna versie bewaren, behalve als de tekst identiek is aan de meest recente bestaande versie; zonder bestaande versies altijd wegschrijven. Versie-opslag is best-effort (runCatching + logger.warn), een fout mag de PUT niet laten falen.
- NotesController: GET /api/v1/notes/versions (id + savedAt, nieuwste eerst, max 200, geen tekst) en GET /api/v1/notes/versions/{id} (id + savedAt + text, 404 bij onbekend id), beide met authService.requireAuthorization(authorization). ISO-8601 UTC.
- Nieuwe @Component-scheduler in de notes-module, @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam"), stijl van briefing/BriefingCacheScheduler (hele run in runCatching, warn bij falen). Selectielogica in een pure functie (lijst versies + now -> te verwijderen ids): alles binnen 7 dagen blijft, van oudere blijft per kalenderdag (Europe/Amsterdam) alleen de laatste versie. Eén INFO-logregel met het aantal verwijderde versies.
- Raak NotesTools.kt en WeekTasksSectionProvider.kt niet aan; houd de notes-module binnen zijn bestaande module-afhankelijkheden (ModulithArchitectureTest).

App (notities/):
- api_client.dart: listNoteVersions() en getNoteVersion(id) via het bestaande authHeaders()/_throwOnError-patroon.
- notes_editor_screen.dart: twee IconButtons links in de bestaande opmaakbalk (ValueKey('opmaakbalk')) met tooltips 'Ongedaan maken' (Icons.undo) en 'Opnieuw' (Icons.redo), gevoed door controller.undo()/redo() en hasUndo/hasRedo (onPressed: null => uitgegrijsd); bestaande ListenableBuilder verzorgt herteken. Document blijft gezet vóór het changes-abonnement, zodat laden niet undo-baar is.
- AppBar-actie 'Versies' (Icons.history) opent een eigen route met de versielijst (laadspinner, foutmelding, lege-lijstmelding), per regel NL datum/tijd in lokale tijd via een kleine eigen helper (vandaag 11:30 / gisteren 11:30 / ma 28 jul 09:05) - GEEN nieuwe dependency. Tikken opent een alleen-lezen weergave (selecteerbare platte tekst) met knop 'Terugzetten' + bevestigingsdialoog; terugzetten vervangt de editorinhoud via replaceText op het bestaande document (niet via `_controller.document = ...`), zodat undo-historie en changes-abonnement intact blijven en de normale debounce-autosave het als nieuwe versie opslaat.
- Opslagformaat blijft platte markdown; markdown_delta.dart ongewijzigd.

Tests (hoort bij deze subtaak):
- Backend: versie bij update, geen dubbele versie bij identieke tekst, endpoints (data, volgorde, limiet, 404), pure opruimfunctie op vaste tijdstippen.
- App: widget-tests voor undo/redo (aanwezigheid, disabled na laden, werking na wijziging) en de terugzet-flow inclusief bevestiging.
- Draai mvn test in robberts-assistent-backend/ en flutter test + flutter analyze in notities/; alles groen.

Sluit af met een zelf-review van de diff en werk docs/stories/worklog/SF-1808-worklog.md bij.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## Backend (`notes`-module)

- Nieuw `NoteVersion(id, text, savedAt: Instant)` (`NoteVersion.kt`).
- `NotesRepository` uitgebreid met `addVersion`, `latestVersions(limit)`, `version(id)`,
  `allVersions()` en `deleteVersion(id)`; volledig geïmplementeerd in
  `InMemoryNotesRepository` (UUID's, `asReversed()` vóór het stabiele sorteren zodat twee
  saves binnen dezelfde milliseconde tóch nieuwste-eerst blijven) en
  `FirestoreNotesRepository` (subcollectie `notes/note/versions`, velden `text` +
  `savedAt` als Firestore-timestamp, auto-id, `orderBy(savedAt, DESC)`). Het document
  `notes/note` zelf is ongemoeid.
- `NotesService.update(text)`: eerst `repository.update(text)` (ongewijzigd gedrag en
  returnwaarde), daarna een versie — behalve als de tekst identiek is aan de meest recente
  bestaande versie. Alles rond de versie zit in `runCatching` + `logger.warn`, dus een
  falende versie-opslag laat de `PUT` niet mislukken. `now` is een constructorparameter met
  productiedefault (`Instant::now`) zodat tijdstippen in tests bestuurbaar zijn; Spring
  instantieert de service gewoon (bevestigd door de bootende `NotesControllerTest`).
- `NotesController`: `GET /api/v1/notes/versions` (id + `savedAt` ISO-8601 UTC, nieuwste
  eerst, max 200, geen tekst) en `GET /api/v1/notes/versions/{id}` (404 bij onbekend id),
  beide via `authService.requireAuthorization(...)`.
- `NoteVersionCleanup` (pure `object`, geen klok/Firestore) + `NoteVersionCleanupScheduler`
  (`@Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam")`, hele run in
  `runCatching`, één INFO-regel met het aantal verwijderde versies). Regel: binnen 7 dagen
  blijft alles, daarvóór per kalenderdag (Europe/Amsterdam) alleen de laatste versie.
- `NotesTools.kt` en `WeekTasksSectionProvider.kt` zijn niet aangeraakt; het opslagformaat
  van de notitie zelf is ongewijzigd.

## App (`notities/`)

- `api_client.dart`: `listNoteVersions()` + `getNoteVersion(id)` via het bestaande
  `authHeaders()`/`_throwOnError`-patroon, plus het datatype `NoteVersionSummary`.
- `notes_editor_screen.dart`: `Ongedaan maken` (`Icons.undo`) en `Opnieuw` (`Icons.redo`)
  links in de bestaande opmaakbalk, gevoed door `controller.hasUndo`/`hasRedo` (dus
  uitgegrijsd als er niets te doen valt). Het document wordt nog steeds gezet vóór het
  `changes`-abonnement, dus het initiële laden staat niet in de undo-historie.
  AppBar-actie `Versies` (`Icons.history`) opent de nieuwe route; komt daar tekst uit
  terug, dan vervangt `_restore()` de inhoud met `controller.replaceText(0,
  document.length, markdownToDelta(...), ...)` — een bewerking op het bestaande document,
  dus undo-historie en changes-abonnement blijven intact en de gewone debounce-autosave
  pikt het op. Bewust de *volledige* lengte inclusief afsluitende newline: die newline
  draagt in Quill de blok-opmaak (bullet) van de laatste regel.
- `note_versions_screen.dart` (nieuw): versielijst (spinner/fout/lege-lijstmelding) met
  `formatVersionMoment()` — eigen mini-helper met NL dag-/maandafkortingen en
  `vandaag`/`gisteren`, dus geen `intl`-dependency erbij. Tikken opent een alleen-lezen
  weergave (`SelectableText` met de platte markdown) met knop `Terugzetten` en een
  bevestigingsdialoog (`Annuleren` / `Ja, terugzetten`).

## Tests / verificatie

- Backend: `NotesServiceTest` (versie per update, geen dubbel bij identieke tekst, lege
  eerste versie, versie op id + null, 200-limiet nieuwste-eerst, best-effort bij een
  falende versie-opslag), `NotesControllerTest` (+`NotesControllerAuthTest`: endpoints,
  volgorde, geen tekst in het overzicht, 404, 401 zonder token), `NoteVersionCleanupTest`
  (pure functie op vaste tijdstippen, incl. de Amsterdamse dag-grens) en
  `NoteVersionCleanupSchedulerTest` (verwijdert de juiste ids, exacte INFO-logregel via een
  logback `ListAppender`, en crasht niet bij een falende repository).
- `rm -rf target && mvn -o test` in `robberts-assistent-backend/`:
  **405 tests, 0 failures, 0 errors, BUILD SUCCESS** (incl. `ModulithArchitectureTest`,
  `NotesToolsTest`).
- `flutter test` in `notities/`: **44 tests groen** (undo/redo-aanwezigheid, disabled na
  laden + undo maakt de notitie niet leeg, werking na een wijziging, versielijst,
  alleen-lezen weergave, annuleren én bevestigen van terugzetten, undo-baarheid van het
  terugzetten, autosave erna, en behoud van bullet-/vet-opmaak bij terugzetten).
- `flutter analyze` in `notities/`: **No issues found!**

## Aandachtspunten

- De opmaakbalk hertekent op `_controller`-notificaties (echte invoer gaat via
  `controller.replaceText`) én op de `setState` in `_onChanged`. Een testwijziging
  rechtstreeks op `document` notificeert de controller niet, dus in widget-tests zijn er
  twee `pump()`s nodig voordat de undo-knop enabled is — vastgelegd in de tests.
- De APK zelf is in deze sandbox niet te bouwen (geen Android SDK); de
  `notities-apk.yml`-workflow op `main` is de eerste echte bevestiging.
