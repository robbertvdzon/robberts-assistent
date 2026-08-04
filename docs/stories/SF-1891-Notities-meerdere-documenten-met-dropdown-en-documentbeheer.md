# SF-1891 - Notities: meerdere documenten met dropdown en documentbeheer

## Story

Notities: meerdere documenten met dropdown en documentbeheer

<!-- refined-by-factory -->

## Samenvatting

Nu is "de notitie" in de notities-app één lange tekst. Robbert wil meerdere losse
documenten kunnen bijhouden, bijvoorbeeld 'todo' en 'recepten', zelf de volgorde
bepalen en bovenin de app kiezen welk document hij bewerkt. Zijn huidige notitie
gaat automatisch mee als document 'todo', inclusief de bewaarde versies — er gaat
niets verloren. Via een beheerscherm kan hij documenten toevoegen, hernoemen,
verwijderen (met bevestiging) en slepen om de volgorde te wijzigen. Alles wat de
editor nu al kan blijft werken, maar dan per document. Ook de AI-assistent kan de
documenten opsommen, lezen en bijwerken.

## Scope

### Backend — `notes`-module

Datamodel: een notitiedocument heeft `id`, `title`, `order` (positie) en `text`.
De inhoud blijft **platte markdown**; er wordt nooit Quill-Delta-JSON opgeslagen.
Versies blijven `NoteVersion(id, text, savedAt)`, maar per document.

Firestore-indeling: per document `notes/<docId>` met velden `title`, `order`,
`text`; versies in de subcollectie `notes/<docId>/versions` (ongewijzigde vorm,
velden `text` + `savedAt`). Het bestaande document `notes/note` wordt hergebruikt
als het 'todo'-document, zodat de huidige tekst én de bestaande subcollectie
`versions` blijven staan. De lijst-query pakt alleen documenten met een gezet
`title`-veld mee, zodat een document zonder notitievelden nooit als notitie in de
lijst belandt.

Migratie naar 'todo' (idempotent, gebeurt lazily bij elke documenten-toegang —
dus ook bij de oude endpoints en de AI-tools, niet alleen bij `GET /documents`):
- Bestaan er al notitiedocumenten (documenten met `title`), dan gebeurt er niets.
- Anders wordt `notes/note` het 'todo'-document: `title = "todo"`, `order = 0`,
  `text` = de bestaande tekst (ontbreekt `notes/note`, dan lege tekst). De
  subcollectie `versions` wordt niet aangeraakt en hangt dus automatisch aan
  'todo'.
- Twee keer draaien levert nooit twee 'todo'-documenten op.

`NotesRepository` (+ `InMemoryNotesRepository` + `FirestoreNotesRepository`) wordt
uitgebreid met documentoperaties: lijst, aanmaken, hernoemen, verwijderen (incl.
versies), volgorde opslaan, tekst lezen/schrijven per document, en versies per
document (`addVersion`/`latestVersions`/`version`/`allVersions`/`deleteVersion`
krijgen een document-id). `NotesService` krijgt de bijbehorende methodes; de
dubbel-detectie bij opslaan blijft ongewijzigd van gedrag, maar per document (geen
nieuwe versie als de tekst identiek is aan de nieuwste versie van dát document).

Nieuwe endpoints, alle achter `authService.requireAuthorization(header)` net als nu:

| Methode + pad | Doel |
|---|---|
| `GET /api/v1/notes/documents` | lijst `{id, title, order}`, op volgorde (triggert de migratie) |
| `POST /api/v1/notes/documents` | nieuw document `{title}`, start leeg, komt onderaan |
| `PUT /api/v1/notes/documents/order` | nieuwe volgorde, `{ids: [...]}` |
| `PUT /api/v1/notes/documents/{id}/title` | hernoemen `{title}` |
| `DELETE /api/v1/notes/documents/{id}` | verwijderen incl. alle versies |
| `GET /api/v1/notes/documents/{id}` | `{id, title, text}` |
| `PUT /api/v1/notes/documents/{id}` | tekst opslaan `{text}` (+ versie-record) |
| `GET /api/v1/notes/documents/{id}/versions` | versie-overzicht (`id` + `savedAt`, nieuwste eerst, max 200) |
| `GET /api/v1/notes/documents/{id}/versions/{versionId}` | `{id, savedAt, text}` |

Gemotiveerde afwijking van de aangeleverde richting: hernoemen krijgt een eigen pad
`.../{id}/title` in plaats van dezelfde `PUT .../{id}` als het opslaan van tekst —
één endpoint met twee betekenissen (`{title}` óf `{text}`) is niet eenduidig en
maakt "titel wissen" en "tekst wissen" onmogelijk te onderscheiden.

Foutgedrag: onbekend document-id of versie-id → **404** met Nederlandse melding.
Lege of alleen-witruimte titel → **400**. Een titel die (hoofdletter-ongevoelig, na
`trim`) al bestaat → **409**, zodat de AI-tools titels eenduidig kunnen matchen.
Verwijderen van het laatste overgebleven document → **409** met een duidelijke
melding; er kunnen dus nooit nul documenten zijn. `PUT .../order` verwacht een
lijst met bestaande ids (onbekend id → 404) en herschrijft `order` dicht naar
0..n-1; niet-genoemde bestaande documenten behouden hun onderlinge volgorde en
komen erachter.

Backwards compatibility: `GET`/`PUT /api/v1/notes` en
`GET /api/v1/notes/versions(/{id})` blijven ongewijzigd bestaan en werken op het
**standaarddocument** (het document met id `note`, oftewel het gemigreerde
'todo'-document). `briefing/WeekTasksSectionProvider.kt` blijft daardoor
ongewijzigd en leest de 'todo'-inhoud.

`NoteVersionCleanup.idsToDelete(versions, now)` blijft ongewijzigd puur; de
`NoteVersionCleanupScheduler` (03:30 Europe/Amsterdam) loopt nu over **alle**
documenten, past de regel per document toe (binnen 7 dagen alles bewaren, daarvóór
per kalenderdag alleen de laatste) en logt één INFO-regel met het totaal aantal
verwijderde versies. De hele run blijft in één `runCatching`.

### Backend — AI-chat

`assistant/ai/NotesTools.kt` wordt uitgebreid in de bestaande stijl (Nederlandse
`@Tool`-beschrijvingen, `@ToolParam` per argument, returnwaarde is een korte
Nederlandse zin, nooit een exception naar buiten):
- documenten opsommen (titels);
- inhoud van een document op naam opvragen; zonder naam → het standaarddocument;
- inhoud van een document op naam overschrijven; zonder naam → het
  standaarddocument;
- nieuw document aanmaken met een titel.

Naam-matching: hoofdletter-ongevoelig, eerst exacte titel, anders titels die met de
opgegeven tekst beginnen; precies één match is nodig. Geen of meerdere matches →
duidelijke Nederlandse foutzin met de beschikbare titels. De bestaande
`getNotes`/`updateNotes` blijven werken op het standaarddocument.
`SYSTEM_PROMPT` in `assistant/ai/AiConfig.kt` wordt bijgewerkt waar de
notities-tool wordt genoemd, zodat het model weet dat er meerdere
notitiedocumenten zijn.

### App — `notities/`

- `lib/api_client.dart`: methodes voor alle nieuwe endpoints, plus een
  `NoteDocument`-model (`id`, `title`, `order`); zelfde `authHeaders()` +
  `_throwOnError`-patroon.
- `lib/notes_editor_screen.dart`: in de AppBar een dropdown met alle documenten in
  de ingestelde volgorde. Wisselen slaat eerst het openstaande werk van het huidige
  document op (pending debounce direct afdwingen) en laadt daarna de andere tekst;
  bij een mislukte save wordt er niet gewisseld en verschijnt de bestaande
  foutmelding. De laatst gekozen document-id staat in `shared_preferences` onder
  `notes_editor_document_id` en wordt bij het starten gebruikt; bestaat dat
  document niet meer, dan valt de app terug op het eerste document in de volgorde.
- Nieuw beheerscherm (route vanuit een AppBar-knop naast de dropdown):
  toevoegen met titel, hernoemen, verwijderen met bevestigingsdialoog en volgorde
  slepen via `ReorderableListView`. Bij precies één document is de verwijderactie
  niet beschikbaar. Bij terugkomst herlaadt de editor de documentenlijst; is het
  huidige document verwijderd, dan schakelt hij naar het eerste document.
- Versiegeschiedenis (`lib/note_versions_screen.dart`) werkt op het gekozen
  document en gebruikt de per-document-endpoints; terugzetten blijft via
  `controller.replaceText(...)` op het bestaande document zodat de undo-historie
  intact blijft.
- Autosave (10s debounce, direct bij `paused`/`inactive`, best-effort in
  `dispose`), undo/redo, opmaakbalk en A−/A+ blijven ongewijzigd werken, nu per
  document. De lettergrootte-voorkeur blijft app-breed (niet per document).
- Bekende valkuilen respecteren: de basisstijl voor `QuillEditorConfig.customStyles`
  komt uit `Theme.of(context)` (`textTheme.bodyMedium` + `colorScheme.onSurface`),
  níet uit `DefaultTextStyle.of(context)`; knoppen onderin zitten in een
  `SafeArea(top: false)`. Thema blijft `notitiesDarkTheme`.

### Buiten scope

- Documenten in `robberts_assistent` of het geheugen-/chatscherm van die app.
- Diff-weergave tussen versies, versies benoemen/pinnen/verwijderen vanuit de app.
- Mappen/nesting, tags, zoeken over documenten, delen of per-document-rechten.
- Wijzigingen aan het opslagformaat (blijft platte markdown) of aan
  `markdown_delta.dart`.
- Wijzigingen aan `briefing/WeekTasksSectionProvider.kt`.

## Acceptance criteria

1. Bij een lege installatie én bij een bestaande installatie levert
   `GET /api/v1/notes/documents` minstens één document op met titel `todo`; bij een
   bestaande installatie bevat dat document exact de tekst die eerder in
   `notes/note` stond en zijn de bestaande versies eraan gekoppeld. Twee
   opeenvolgende aanroepen leveren precies één 'todo'-document op.
2. Documenten aanmaken (leeg, onderaan de volgorde), hernoemen, verwijderen (incl.
   de versies van dat document) en herordenen werkt via de endpoints; de lijst komt
   altijd terug in `order`-volgorde met dichte posities 0..n-1.
3. Tekst opslaan per document maakt een versie-record aan, behalve als de tekst
   identiek is aan de nieuwste versie van dát document. Versies van document A
   verschijnen nooit bij document B.
4. Een onbekend document-id of versie-id geeft 404; een lege titel 400; een
   dubbele titel 409; verwijderen van het laatste document 409 en het document
   blijft bestaan.
5. `GET`/`PUT /api/v1/notes` en `GET /api/v1/notes/versions(/{id})` gedragen zich
   ongewijzigd en werken op het 'todo'-document; de weektaken-sectie in de briefing
   leest nog steeds die inhoud.
6. De nachtelijke opruimjob verwijdert oude versies in álle documenten volgens de
   ongewijzigde regel en logt één INFO-regel met het totaal.
7. De AI-tools kunnen documenten opsommen, een document op naam lezen en
   overschrijven en een nieuw document aanmaken; zonder naam werken ze op 'todo'.
   Een onbekende of ambigue naam levert een Nederlandse foutzin met de beschikbare
   titels op, geen exception. `SYSTEM_PROMPT` noemt de meerdere documenten.
8. In de app staat bovenin een dropdown met de documenten in de ingestelde
   volgorde; kiezen laadt de tekst van dat document, en openstaand werk van het
   vorige document is daarvóór opgeslagen (geen tekstverlies).
9. Na herstart opent de app op het laatst gekozen document; bestaat dat niet meer,
   dan op het eerste document in de volgorde.
10. Het beheerscherm ondersteunt toevoegen, hernoemen, verwijderen met
    bevestiging en slepen om te herordenen; bij één document is verwijderen niet
    beschikbaar.
11. Autosave, undo/redo, lettergrootte (A−/A+), opmaakbalk en het
    versiegeschiedenis-scherm werken per gekozen document; de editortekst is wit op
    zwart (geen rode monospace).
12. `mvn test` in `robberts-assistent-backend/` is groen, inclusief
    `ModulithArchitectureTest` en nieuwe tests voor: migratie naar 'todo'
    (idempotent, tekst + versies behouden), aanmaken/hernoemen/verwijderen/
    herordenen, tekst + versies per document, de 404/400/409-gevallen, de oude
    `/api/v1/notes`-endpoints die 'todo' teruggeven, cleanup over meerdere
    documenten, en de uitgebreide `NotesTools` (`NotesToolsTest`).
13. In `notities/` is `flutter analyze` schoon en `flutter test` groen, met nieuwe
    widget-tests voor de dropdown (documenten op volgorde; wisselen laadt andere
    tekst en slaat eerst op) en voor het beheerscherm (toevoegen, hernoemen,
    verwijderen-met-bevestiging, herordenen).
14. De workflow `.github/workflows/notities-apk.yml` slaagt.

## Aannames

- Het **standaarddocument** is het document met Firestore-id `note` (het
  gemigreerde 'todo'-document). De oude endpoints, `WeekTasksSectionProvider` en de
  naamloze AI-tools blijven dat document gebruiken, óók als Robbert de titel later
  verandert — de identiteit hangt aan het id, niet aan de titel.
- Document-ids zijn ondoorzichtig: `note` voor het gemigreerde document,
  Firestore-auto-ids voor nieuwe documenten. De app toont ze niet.
- Titels zijn uniek (hoofdletter-ongevoelig, na `trim`) en maximaal 60 tekens; dat
  houdt naam-matching in de chat eenduidig.
- `PUT /api/v1/notes/documents/order` staat vóór `PUT .../{id}` als letterlijk pad;
  omdat ids `note` of Firestore-auto-ids zijn, kan er geen document met id `order`
  ontstaan.
- Er is geen Firestore-migratiescript nodig: de migratie is lazily en idempotent en
  het bestaande document/de bestaande subcollectie worden hergebruikt.
- De volgorde is app-breed (één gedeelde lijst), niet per gebruiker — de app kent
  maar één gebruiker.
- De lettergrootte-voorkeur blijft één app-brede waarde onder
  `notes_editor_font_size`.
- Er is geen API-versionering nodig: de nieuwe endpoints komen erbij, de bestaande
  blijven ongewijzigd, dus een oudere APK blijft werken op het 'todo'-document.
- Een APK bouwen kan niet in de sandbox (geen Android SDK); de APK-workflow op
  `main` is de eerste echte bevestiging, en het slepen/dropdown-gedrag op een
  fysiek toestel is de laatste handmatige verificatie.

## Eindsamenvatting

Ik heb `.task.md` en het volledige worklog (SF-1892 t/m SF-1894, inclusief beide reviewrondes en de testronde) gelezen. Hieronder de eindsamenvatting.

---

# SF-1891 — Notities: meerdere documenten met dropdown en documentbeheer

## Wat is gebouwd

**Backend (`notes`-module)** — van één notitie-string naar meerdere documenten:
- Datamodel `NoteDocument(id, title, order, text)`; inhoud blijft platte markdown, versies (`NoteVersion`) nu per document. Firestore: `notes/<docId>` met `title`/`order`/`text`, versies in de subcollectie `notes/<docId>/versions`.
- Negen nieuwe endpoints onder `/api/v1/notes/documents` (lijst, aanmaken, herordenen, hernoemen, verwijderen, tekst lezen/opslaan, versie-overzicht en versie-detail), alle achter dezelfde autorisatie-gate.
- **Lazy, idempotente migratie**: het bestaande `notes/note` wordt hergebruikt als document `todo` — bestaande tekst én bestaande versies blijven eraan hangen. Twee keer draaien geeft nooit twee 'todo'-documenten.
- Foutgedrag: onbekend id → 404, lege titel → 400, dubbele titel → 409, verwijderen van het láátste document → 409 (er kunnen dus nooit nul documenten zijn).
- De nachtelijke opruimjob (03:30) loopt nu over álle documenten met dezelfde ongewijzigde bewaarregel en logt één INFO-regel met het totaal.

**AI-chat** — `NotesTools` kreeg vier tools erbij: documenten opsommen, op naam lezen, op naam overschrijven en een nieuw document aanmaken. Naam-matching is hoofdletter-ongevoelig (eerst exact, dan beginstuk); onbekend of ambigu geeft een Nederlandse foutzin met de beschikbare titels, nooit een exception. De system-prompt vermeldt de meerdere documenten.

**App `notities/`** — dropdown in de AppBar met de documenten in de ingestelde volgorde, plus een nieuw beheerscherm (toevoegen, hernoemen, verwijderen met bevestiging, slepen om te herordenen; verwijderen uitgeschakeld bij één document). Het laatst gekozen document wordt onthouden en na herstart geopend. Autosave, undo/redo, opmaakbalk, A−/A+ en de versiegeschiedenis werken ongewijzigd, maar nu per document.

## Belangrijkste keuzes

- **Standaarddocument hangt aan het id (`note`), niet aan de titel** — hernoemt Robbert 'todo' later, dan blijven de oude endpoints, de briefing-weektakensectie en de naamloze AI-tools gewoon werken.
- **Volledige backwards compatibility**: `GET`/`PUT /api/v1/notes` en de oude versie-endpoints blijven bestaan en werken op 'todo'. Geen API-versionering, geen migratiescript nodig; een oudere APK blijft werken.
- **Hernoemen kreeg een eigen pad** (`PUT .../{id}/title`) in plaats van hetzelfde pad als tekst opslaan — één endpoint met twee betekenissen is niet eenduidig.
- Wisselen van document slaat eerst het openstaande werk op en wisselt **alleen bij succes**, zodat er nooit tekst verloren gaat.

## Wat is getest

- Backend `mvn test`: **433 tests groen**, inclusief de architectuurtest en nieuwe tests voor migratie, CRUD, herordenen, versies per document, alle 404/400/409-gevallen, de oude endpoints, cleanup over meerdere documenten en de uitgebreide chat-tools.
- App: `flutter analyze` schoon, `flutter test` **72 groen** (was 57), met nieuwe tests voor de dropdown en het volledige beheerscherm.
- **Live end-to-end op de PR-preview**: acceptatiecriteria 1 t/m 7 daadwerkelijk tegen een draaiende backend afgevinkt (migratie tweemaal, aanmaken/hernoemen/herordenen/verwijderen, versies per document, alle foutcodes, de oude endpoints en de briefing). Twee screenshots van editor en beheerscherm. Alle testdata is opgeruimd.

## Bewust niet gedaan

- Geen documenten in de `robberts_assistent`-app, geen mappen/tags/zoeken, geen diff-weergave of versies benoemen/pinnen; opslagformaat blijft platte markdown.
- Na een `DELETE` blijft er een gat in de volgnummers (bv. 0, 2) in plaats van strikt 0..n-1. Zichtbaar effect is er niet — de app werkt op lijstvolgorde en de sortering klopt — maar het wijkt letterlijk af van de acceptatietekst. Bekend en geaccepteerd; herordenen hernummert wél dicht.
- Extra Firestore-leesverbruik per autosave (de migratiecheck en een dubbele document-lees) is als suggestie genoteerd, niet opgelost.
- De APK-workflow en het slepen/de dropdown op een fysiek toestel zijn niet in de sandbox te bevestigen (geen Android SDK) — dat is de laatste handmatige stap na merge.

<!-- deploy-summary:start -->
Je notities-app kan nu meerdere losse documenten bijhouden in plaats van één lange tekst. Bovenin kies je met een keuzelijst welk document je bewerkt, en via een beheerscherm voeg je documenten toe, hernoem of verwijder je ze en sleep je ze in de volgorde die jij wilt. Je huidige notitie staat er gewoon nog, als document 'todo', inclusief alle eerder bewaarde versies.
<!-- deploy-summary:end -->
