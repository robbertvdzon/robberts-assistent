# SF-1891 - Worklog

Story-context bij eerste pickup:
Backend: notities als meerdere documenten

Breid de notes-module uit van één notitie-string naar meerdere documenten (id, title, order, text; inhoud blijft platte markdown).

- NotesRepository + InMemoryNotesRepository + FirestoreNotesRepository: documentoperaties (lijst op order, aanmaken leeg onderaan, hernoemen, verwijderen incl. versies, volgorde herschrijven naar dichte posities 0..n-1, tekst lezen/schrijven per document) en alle versie-operaties met document-id. Firestore: notes/<docId> met velden title, order, text; versies in subcollectie notes/<docId>/versions (ongewijzigde vorm). De lijst-query pakt alleen documenten met een gezet title-veld.
- Lazy, idempotente migratie bij elke documenten-toegang (ook via de oude endpoints en de AI-tools): bestaan er al documenten met title, dan niets; anders wordt het bestaande notes/note het 'todo'-document (title='todo', order=0, bestaande text; lege tekst als het document ontbreekt). Subcollectie versions niet aanraken, notes/note nooit overschrijven.
- NotesService: documentmethodes; dubbel-detectie bij opslaan blijft qua gedrag gelijk maar per document (geen versie als de tekst identiek is aan de nieuwste versie van dát document), versie wegschrijven blijft best-effort.
- NotesController: nieuwe endpoints onder /api/v1/notes/documents zoals in de story-tabel (GET lijst, POST nieuw, PUT /order, PUT /{id}/title voor hernoemen, DELETE /{id}, GET /{id}, PUT /{id} voor tekst, GET /{id}/versions, GET /{id}/versions/{versionId}), alle achter authService.requireAuthorization(header). Let op de padvolgorde: /order vóór /{id}. Foutgedrag: onbekend document-/versie-id 404, lege of alleen-witruimte titel 400, dubbele titel (case-insensitive na trim) 409, verwijderen van het laatste document 409 (document blijft bestaan), titel max 60 tekens.
- Backwards compatibility: GET/PUT /api/v1/notes en GET /api/v1/notes/versions(/{id}) blijven ongewijzigd en werken op het standaarddocument (id 'note'). briefing/WeekTasksSectionProvider.kt niet wijzigen.
- NoteVersionCleanup.idsToDelete blijft ongewijzigd puur; NoteVersionCleanupScheduler loopt over alle documenten, past de regel per document toe en logt één INFO-regel met het totaal, hele run in één runCatching.
- assistant/ai/NotesTools.kt: tools voor documenten opsommen, document op naam lezen, document op naam overschrijven en nieuw document aanmaken, in de bestaande stijl. SYSTEM_PROMPT in AiConfig.kt bijwerken.
- Unittests: migratie, CRUD, volgorde, tekst + versies per document, 404/400/409, oude endpoints, cleanup over meerdere documenten, uitgebreide NotesToolsTest. mvn test volledig groen inclusief ModulithArchitectureTest.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1892 — Backend: notities als meerdere documenten (developer)

### Wat en waarom

- **Datamodel** (`notes/NoteDocument.kt`): `NoteDocument(id, title, order, text)` plus de constanten
  `DEFAULT_DOCUMENT_ID = "note"`, `DEFAULT_DOCUMENT_TITLE = "todo"` en `MAX_TITLE_LENGTH = 60`, en
  drie module-eigen fouttypes (`NoteDocumentNotFoundException`, `NoteTitleInvalidException`,
  `NoteDocumentConflictException`). Het standaarddocument hangt bewust aan het **id**, niet aan de
  titel — zo blijft het werken als Robbert 'todo' later hernoemt.
- **Repository-poort**: alle bestaande versie-methodes hebben er een `documentId` bij gekregen;
  `current()`/`update(text)` zijn vervangen door `document(id)`/`updateText(id, text)`. Nieuw:
  `documents()`, `createDocument`, `renameDocument`, `deleteDocument` (incl. versies), `updateOrder`
  en `createDefaultDocument` (de migratiestap). `InMemoryNotesRepository` kreeg een optionele
  `legacyText`-parameter zodat de migratie ("bestaande tekst blijft behouden") zonder Firestore te
  testen is.
- **Firestore-indeling**: `notes/<docId>` met `title`/`order`/`text`, versies ongewijzigd in
  `notes/<docId>/versions`. De lijst-query gebruikt `orderBy("title")` — Firestore laat documenten
  zónder dat veld automatisch weg, precies de eis dat een document zonder notitievelden nooit als
  notitie in de lijst belandt, en dat zonder samengestelde index. Sorteren op `order` gebeurt daarna
  in geheugen (het gaat om een handvol documenten). Schrijfacties gebruiken `SetOptions.merge()`,
  zodat de migratie de bestaande tekst nooit overschrijft en de subcollectie `versions` intact blijft.
- **Migratie** zit in `NotesService.ensureDocuments()` (`@Synchronized`) en wordt door élke
  documenten-toegang aangeroepen — dus ook via de oude endpoints, de briefing en de AI-tools. Zijn er
  al documenten mét titel, dan gebeurt er niets; twee keer draaien levert nooit twee 'todo's op.
- **Fouten**: `NotesService` blijft web-vrij en gooit de module-eigen excepties; `NotesController`
  vertaalt ze met `@ExceptionHandler` naar 404/400/409 (bewust een `ResponseEntity` teruggeven i.p.v.
  opnieuw gooien vanuit de handler). `NotesTools` vangt dezelfde excepties af en geeft een
  Nederlandse zin terug, nooit een exception.
- **Hernoemen** heeft een eigen pad `PUT .../{id}/title` (naast `PUT .../{id}` voor tekst), zoals in
  de story gemotiveerd; `PUT .../documents/order` staat als letterlijk pad vóór `/{id}`.
- **Backwards compatibility**: `GET`/`PUT /api/v1/notes` en `GET /api/v1/notes/versions(/{id})`
  werken op het standaarddocument. Is dat (later) verwijderd, dan valt de service terug op het eerste
  document in de volgorde, zodat `briefing/WeekTasksSectionProvider` (ongewijzigd) nooit op een 404
  stukloopt.
- **Cleanup**: `NoteVersionCleanup.idsToDelete` is ongewijzigd puur; de scheduler loopt over alle
  documenten, past de regel per document toe en logt één INFO-regel met het totaal, hele run in één
  `runCatching`.
- **Chat**: `NotesTools` kreeg `listNoteDocuments`, `getNoteDocument`, `updateNoteDocument` en
  `createNoteDocument` (naam-matching hoofdletter-ongevoelig: eerst exact, dan `startsWith`, precies
  één match nodig); `getNotes`/`updateNotes` blijven op het standaarddocument werken. De
  `SYSTEM_PROMPT` in `AiConfig.kt` noemt nu de meerdere notitiedocumenten.

### Verificatie

- `rm -rf target && mvn -o test` in `robberts-assistent-backend/`: **BUILD SUCCESS**, `Tests run: 433,
  Failures: 0, Errors: 0, Skipped: 0` (incl. `ModulithArchitectureTest`).
- Nieuwe/uitgebreide tests: `NotesServiceTest` (21) — migratie (leeg én met bestaande tekst/versies,
  idempotent), aanmaken/hernoemen/verwijderen/herordenen, tekst + versies per document,
  404/400/409-gevallen, oude API op 'todo' + terugval; `NotesControllerTest` (6) — documenten-lijst,
  volledige CRUD-flow incl. versies per document en de 404/400/409-statussen, oude endpoints;
  `NoteVersionCleanupSchedulerTest` (3) — opruimen over meerdere documenten met één INFO-regel;
  `NotesToolsTest` (11) — opsommen, lezen/overschrijven op naam, exact vóór beginstuk, onbekend en
  ambigu, aanmaken, ongeldige/dubbele titel.

### Bekende aandachtspunten

- Er is geen Firestore-emulator/mocktest voor `FirestoreNotesRepository` (die was er ook niet vóór
  deze story); de Firestore-indeling is alleen via code-review geverifieerd.
- `NotesService` doet per documenten-toegang één extra `documents()`-lees voor de migratiecheck —
  functioneel correct, wel wat extra Firestore-leesverbruik (net als de bestaande
  `latestVersions(1)`-check per save).
- De app-kant (`notities/`) is bewust ongewijzigd; die staat in subtaak SF-1893.

## SF-1892 — Review (reviewer)

**Uitkomst: akkoord.** Geen blockers; wel vier punten om mee te nemen (geen daarvan blokkeert
SF-1893/SF-1894).

### Geverifieerd

- Volledige suite zelf gedraaid op deze checkout: **433 tests, 0 failures, 0 errors**, inclusief
  `ModulithArchitectureTest`. `NotesControllerTest` is een `@SpringBootTest` en bewijst daarmee ook
  dat `NoteVersionCleanupScheduler(notesService, repository, now = …)` met zijn Kotlin-default-
  parameter gewoon wiret.
- Scope: alleen backend geraakt (`notes`, `assistant/ai/NotesTools.kt` + `SYSTEM_PROMPT`).
  `briefing/WeekTasksSectionProvider.kt` is ongewijzigd en leest via `current()` het
  standaarddocument — conform de story. `notities/` is niet aangeraakt (SF-1893).
- Acceptatiecriteria 1 t/m 7 en 12 nagelopen tegen de code: migratie (lazy, `@Synchronized`,
  idempotent, `SetOptions.merge()` zodat tekst én de subcollectie `versions` blijven staan),
  CRUD + herordenen, versies strikt per document, 404/400/409, ongewijzigde oude endpoints,
  cleanup over alle documenten met één INFO-regel, en de vier nieuwe AI-tools met
  exact-vóór-`startsWith`-matching en Nederlandse foutzinnen i.p.v. excepties.
- `@ExceptionHandler`-methodes geven een `ResponseEntity` terug (niet opnieuw gooien) — correct.
- `@ToolParam(required = false)` op een **nullable** `String?` is hier veiliger dan het bestaande
  repo-patroon (`String = ""`), omdat Spring AI Kotlin-defaults niet toepast; goede keuze.

### Bevindingen (niet blokkerend)

- [suggestie] `order` is alleen dicht (0..n-1) ná `PUT /documents/order`. Na een `DELETE` blijft er
  een gat staan (bv. 0, 2), terwijl acceptatiecriterium 2 "altijd dichte posities 0..n-1" zegt. De
  scope-tekst eist densificatie alleen bij het order-endpoint en de lijst blijft correct gesorteerd,
  dus functioneel onschadelijk voor de app-dropdown. Kleinste fix: na `deleteDocument` één keer
  `reorder(emptyList())` aanroepen.
- [suggestie] Firestore-leesverbruik per autosave. `ensureDocuments()` doet een volledige
  collectie-query bij élke toegang, en `NotesController.updateDocument` roept eerst
  `notesService.updateText(id, …)` (query + doc-read) en daarna nóg eens `notesService.document(id)`
  (query + doc-read) aan alleen voor de titel. Eén autosave-PUT kost zo ±4 leesacties i.p.v. 1.
  Twee goedkope fixes: (a) een `@Volatile`-vlag die onthoudt dat de migratie al gedaan is, (b)
  `updateText` het bijgewerkte `NoteDocument` laten teruggeven zodat de tweede read wegvalt.
- [suggestie] `NotesControllerAuthTest` dekt de negen nieuwe `/documents`-endpoints niet. Alle negen
  roepen `authService.requireAuthorization(...)` aan (per endpoint nagelezen), dus de gate zit er,
  maar er is geen test die regressie daarop vangt.
- [info] `NotesTools` gebruikt Nederlandse private identifiers (`antwoord`, `zoekDocument`,
  `titels`, `ToolFout`), terwijl de rest van de repo Nederlandse commentaar/teksten combineert met
  Engelse code-identifiers (`describe`, `parseNotify` in `WatchTools`). Puur stijl.
- [info] `FirestoreNotesRepository` heeft (net als vóór deze story) geen emulator-/mocktest; de
  indeling `notes/<docId>` + subcollectie is alleen via code-review geverifieerd. De echte
  migratiebevestiging (bestaande tekst + versies aan 'todo') komt pas op de PR-preview/prod — punt
  voor de story-brede test SF-1894.
