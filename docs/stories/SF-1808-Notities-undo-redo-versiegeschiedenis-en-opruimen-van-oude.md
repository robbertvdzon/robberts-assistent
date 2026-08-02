# SF-1808 - Notities: undo/redo, versiegeschiedenis en opruimen van oude versies

## Story

Notities: undo/redo, versiegeschiedenis en opruimen van oude versies

<!-- refined-by-factory -->

## Samenvatting

In de notities-app komen knoppen om een wijziging ongedaan te maken of opnieuw
te doen. Daarnaast onthoudt de backend voortaan elke opgeslagen versie van de
notitie, zodat je via een nieuwe knop "Versies" kunt terugkijken wat er eerder
in stond en een oude versie kunt terugzetten in de editor.

Omdat de notitie elke tien seconden automatisch wordt opgeslagen, zou dat op
den duur heel veel versies opleveren. Daarom ruimt de backend 's nachts op:
van de afgelopen week blijft alles bewaard, van alles wat ouder is blijft nog
één versie per dag over.

## Scope

### 1. Undo/redo in de notities-editor (`notities/lib/notes_editor_screen.dart`)

- Twee extra `IconButton`s in de bestaande zelfgebouwde opmaakbalk (`ValueKey('opmaakbalk')`),
  **links van** Vet/Cursief/Onderstreept/Opsomming/Opmaak wissen: tooltips `Ongedaan maken`
  (`Icons.undo`) en `Opnieuw` (`Icons.redo`).
- Ze gebruiken de undo-historie die `QuillController` zelf al bijhoudt:
  `controller.undo()`/`controller.redo()`, enabled-state uit `controller.hasUndo`/`hasRedo`
  (`onPressed: null` als er niets te doen valt → uitgegrijsd). De bestaande
  `ListenableBuilder` op de controller zorgt voor het herteken.
- Het initiële laden mag niet undo-baar zijn: `_load()` zet het document en abonneert daarna pas
  op `document.changes` (bestaand gedrag); direct na het laden zijn beide knoppen dus disabled.
- Een undo/redo is een gewone documentwijziging en triggert dus de bestaande
  debounce-autosave — geen aparte save-route.

### 2. Versiegeschiedenis bewaren (backend `notes`-module)

- Nieuw datatype `NoteVersion(id: String, text: String, savedAt: Instant)`.
- `NotesRepository` krijgt er drie methodes bij: versies opslaan/ophalen (nieuwste eerst, met
  limiet), één versie op id ophalen, alle versies ophalen t.b.v. opruimen, en verwijderen op id.
  Zowel `FirestoreNotesRepository` als `InMemoryNotesRepository` implementeren dit volledig, zodat
  tests zonder Firebase werken.
- Firestore-vorm: subcollectie `notes/note/versions`, per document de velden `text` (String) en
  `savedAt` (tijdstip). Het bestaande document `notes/note` (veld `text`) blijft ongewijzigd de
  huidige tekst; er verandert niets aan het opslagformaat van de notitie zelf.
- `NotesService.update(text)` schrijft eerst de huidige tekst weg (bestaand gedrag) en bewaart
  daarna een versie-record — **behalve** als de tekst identiek is aan de meest recente bestaande
  versie (voorkomt dubbels door de autosave). Is er nog geen enkele versie, dan wordt er altijd
  één weggeschreven.
- Omdat dit in `NotesService.update` zit, levert ook een wijziging via de chat
  (`assistant/ai/NotesTools.updateNotes`) een versie op. `NotesTools` en
  `briefing/WeekTasksSectionProvider` zelf wijzigen niet.
- Twee nieuwe endpoints in `NotesController`, met hetzelfde
  `authService.requireAuthorization(authorization)`-patroon als de bestaande:
  - `GET /api/v1/notes/versions` → `{"versions":[{"id":"…","savedAt":"<ISO-8601 UTC>"}, …]}`,
    nieuwste eerst, maximaal de laatste 200. Geen tekst in deze respons.
  - `GET /api/v1/notes/versions/{id}` → `{"id":"…","savedAt":"…","text":"<markdown>"}`;
    onbekend id → HTTP 404.

### 3. Versies bekijken en terugzetten in de app

- `notities/lib/api_client.dart` krijgt `listNoteVersions()` (lijst van `id` + `savedAt`) en
  `getNoteVersion(String id)` (tekst), via het bestaande `authHeaders()`/`_throwOnError`-patroon.
- Nieuwe AppBar-actie `Versies` (`Icons.history`) in `NotesEditorScreen`; opent een nieuw scherm
  met de versielijst (laadspinner, foutmelding, en een nette lege-lijstmelding).
- Per regel datum + tijd in Nederlandse notatie en lokale (Amsterdamse) tijd:
  vandaag → `vandaag 11:30`, gisteren → `gisteren 11:30`, ouder → `ma 28 jul 09:05`.
- Tikken op een regel opent een alleen-lezen weergave van die oude tekst (platte markdown als
  selecteerbare tekst, geen bewerkbare editor) met een knop `Terugzetten`.
- `Terugzetten` vraagt eerst om bevestiging (dialoog met Annuleren/Terugzetten). Bij bevestiging
  wordt de inhoud van de editor vervangen door de oude tekst; het scherm keert terug naar de
  editor. De vervanging gebeurt als bewerking op het bestaande document, zodat de undo-historie
  intact blijft (je kunt het terugzetten met de undo-knop ongedaan maken) en de normale
  debounce-autosave 'm daarna als nieuwe versie opslaat.

### 4. Nachtelijk opruimen van oude versies (backend)

- Nieuwe `@Component` in de `notes`-module met
  `@Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam")`, in de stijl van
  `briefing/BriefingCacheScheduler` (hele job in `runCatching`, `logger.warn` bij falen — een fout
  laat de applicatie niet crashen).
- Regel: versies met `savedAt` binnen de laatste 7 dagen blijven allemaal staan. Van alles ouder
  dan 7 dagen blijft per kalenderdag (Europe/Amsterdam) alleen de laatste versie van die dag over;
  de rest wordt verwijderd.
- De selectie zit in een **pure** functie (invoer: lijst versies + "nu"; uitvoer: te verwijderen
  ids), zodat 'ie zonder Firestore of wachttijd te testen is; de scheduler doet alleen ophalen →
  functie → verwijderen.
- Eén INFO-logregel per run met het aantal verwijderde versies, terug te vinden via
  `oc logs deploy/robberts-assistent-backend -n robberts-assistent`.

### Buiten scope

- Diff-weergave tussen versies, versies benoemen/pinnen, versies verwijderen vanuit de app.
- Versiegeschiedenis in de `robberts_assistent`-app of in de chat-assistent.
- Wijzigingen aan het opslagformaat van de notitie, aan `NotesTools`, aan
  `WeekTasksSectionProvider` of aan `GET`/`PUT /api/v1/notes`.
- Een Ctrl+Z-sneltoets (de knoppen zijn de enige weg; Quill's eigen toetsbindingen blijven zoals ze zijn).

## Acceptance criteria

1. In de notities-editor staan links in de opmaakbalk een `Ongedaan maken`- en een
   `Opnieuw`-knop. Na het typen van tekst maakt `Ongedaan maken` die wijziging ongedaan en zet
   `Opnieuw` 'm terug.
2. Direct na het laden van de notitie zijn beide knoppen uitgegrijsd; undo draait het initiële
   laden nooit terug (de notitie wordt nooit leeg door één keer undo te drukken na openen).
3. Na een `PUT /api/v1/notes` met nieuwe tekst is er een versie-record met die tekst en het
   opslagmoment; een tweede `PUT` met exact dezelfde tekst voegt geen tweede versie toe.
4. `GET /api/v1/notes/versions` geeft de versies nieuwste eerst met `id` en `savedAt`, maximaal
   200; `GET /api/v1/notes/versions/{id}` geeft de tekst van die versie en 404 bij een onbekend
   id. Beide endpoints geven zonder geldige `Authorization`-header dezelfde 401 als de bestaande
   notes-endpoints.
5. De opruimlogica is als pure functie getest op een vaste lijst versies met vaste tijdstippen:
   alles binnen 7 dagen blijft behouden; van oudere dagen blijft precies de laatste versie per
   kalenderdag over en worden de overige ids als "te verwijderen" teruggegeven. De scheduler logt
   het aantal verwijderde versies op INFO.
6. De AppBar heeft een `Versies`-actie die een lijst van eerdere versies toont met Nederlandse
   datum/tijd in lokale tijd (`vandaag 11:30` / `gisteren 11:30` / `ma 28 jul 09:05`); tikken opent
   een alleen-lezen weergave van die tekst.
7. `Terugzetten` vraagt om bevestiging en vervangt daarna de inhoud van de editor door de oude
   tekst; daarna werkt undo nog steeds (het terugzetten is ongedaan te maken) en wordt de tekst
   via de normale save opgeslagen.
8. Widget-test in `notities/test/` dekt de undo/redo-knoppen (aanwezigheid, disabled-state bij
   niets te doen, werking na een wijziging) en de terugzet-flow inclusief bevestiging.
9. Bestaande tests blijven groen: `flutter test` in `notities/` en `mvn test` in
   `robberts-assistent-backend/` (incl. `NotesServiceTest`, `NotesToolsTest`,
   `ModulithArchitectureTest`); `flutter analyze` in `notities/` meldt geen issues.
10. Het opgeslagen notitie-formaat blijft platte markdown: er gaat nooit Delta-JSON naar
    `/api/v1/notes`, en `NotesTools`/`WeekTasksSectionProvider` blijven ongewijzigd werken.

## Aannames

- **Geen nieuwe dependencies.** De Nederlandse datum/tijd-weergave wordt met een kleine eigen
  helper (vaste dag-/maandafkortingen + `vandaag`/`gisteren`) gemaakt in plaats van met `intl`
  of een timezone-package. `intl` zit alleen transitief via `flutter_quill` in de lockfile en is
  geen gedeclareerde dependency van `notities/`.
- **Tijdzone:** de app toont `savedAt.toLocal()`; de telefoon staat op Europe/Amsterdam, dus dat
  is in de praktijk Amsterdamse tijd. De backend slaat `savedAt` als UTC-instant op en serialiseert
  'm als ISO-8601. De dag-grens in de opruimtaak wordt wél expliciet in `Europe/Amsterdam`
  gerekend.
- **Version-id:** Firestore genereert het document-id (auto-id); de in-memory repository gebruikt
  een UUID. Ids zijn ondoorzichtige strings voor de app.
- **Dubbel-detectie** vergelijkt met de meest recente bestaande versie, niet met de huidige
  notitietekst — zo levert een wijziging-en-terug (A → B → A) wél drie versies op, wat gewenst is
  voor terugkijken.
- **Opruimen leest alle versies** (dus zonder de 200-limiet van het endpoint) en verwijdert per id;
  bij tienduizenden versies is dat acceptabel omdat de taak dagelijks draait en het aantal daardoor
  laag blijft. Er is bewust geen batch/paginatie-optimalisatie.
- **Terugzetten** vervangt de documentinhoud via een bewerking op het bestaande document
  (bijv. `replaceText` over de volledige lengte) in plaats van `_controller.document = …`, omdat een
  nieuw `Document` de undo-historie wist en een nieuw changes-abonnement zou vereisen.
- De versielijst is een **eigen route** (`Navigator.push`), niet een dialoog, zodat de alleen-lezen
  weergave er als tweede route bovenop past.
- Een versie wordt pas weggeschreven ná een geslaagde update van de huidige tekst; faalt het
  wegschrijven van de versie zelf, dan mag dat de `PUT` niet laten falen (best-effort, `runCatching`
  + `logger.warn`) — de notitie zelf is dan immers al opgeslagen.
- Er is geen migratie nodig: bestaande installaties hebben simpelweg nog geen versies en de lijst
  is dan leeg tot de eerste save.

## Eindsamenvatting

## Eindsamenvatting SF-1808 — Notities: undo/redo, versiegeschiedenis en opruimen van oude versies

**Wat is gebouwd**

*Notities-app (`notities/`)*
- Twee extra knoppen links in de opmaakbalk: **Ongedaan maken** en **Opnieuw**. Ze zijn uitgegrijsd als er niets te doen valt en werken op de historie die de editor zelf al bijhoudt. Het openen van een notitie staat bewust niet in die historie — één keer undo na openen maakt de notitie dus nooit leeg.
- Nieuwe AppBar-actie **Versies** die een lijst met eerdere versies opent (nieuwste eerst), met Nederlandse datum/tijd in lokale tijd: `vandaag 11:30`, `gisteren 11:30`, `ma 28 jul 09:05`. Laadspinner, foutmelding en een nette "nog geen versies"-melding zijn er.
- Tikken op een versie toont die oude tekst alleen-lezen (selecteerbaar) met een knop **Terugzetten**, met bevestigingsdialoog. Terugzetten vervangt de inhoud van de editor; daarna is het gewoon met undo ongedaan te maken en slaat de bestaande autosave het als nieuwe versie op.

*Backend (`notes`-module)*
- Elke opgeslagen notitie levert nu een versie-record (tekst + opslagmoment) op, opgeslagen in Firestore (subcollectie onder de bestaande notitie) met een in-memory variant zodat tests zonder Firebase draaien. Slaat de app twee keer exact dezelfde tekst op, dan komt er geen dubbele versie bij.
- Twee nieuwe, afgeschermde endpoints: een versieoverzicht (max. 200, nieuwste eerst, zonder tekst) en het opvragen van één versie inclusief tekst (404 bij een onbekend id).
- Nachtelijke opruimtaak (03:30 Amsterdamse tijd): alles van de afgelopen 7 dagen blijft staan, van oudere dagen blijft per kalenderdag de laatste versie over. Eén logregel per run met het aantal verwijderde versies; een fout laat de applicatie niet crashen.

**Belangrijkste keuzes**
- Geen nieuwe dependencies: de Nederlandse datum/tijd-weergave is een klein eigen hulpje.
- Het opslagformaat van de notitie zelf is niet gewijzigd (platte markdown), dus de chat-assistent (`NotesTools`) en de weektaken-briefing blijven ongewijzigd werken.
- Versie-opslag is "best effort": mislukt het wegschrijven van een versie, dan mislukt het opslaan van de notitie zelf niet.
- Dubbel-detectie vergelijkt met de laatste versie, niet met de huidige tekst — A → B → A levert dus bewust drie versies op, wat prettig terugkijken geeft.
- De opruimregel zit in een losse, pure functie zodat 'ie zonder database en zonder wachttijd exact te testen is.

**Wat is getest**
- Backend `mvn test`: **405 tests groen** (inclusief modulegrenzen-test en de bestaande notes-tests).
- App `flutter test`: **44 tests groen**; `flutter analyze`: geen issues.
- Live end-to-end op de preview-omgeving: lege versielijst, versie na opslaan, géén dubbel bij identieke tekst, volgorde nieuwste-eerst, opvragen van één versie, 404 bij onbekend id, en A→B→A geeft drie versies.
- Widget-tests dekken undo/redo (aanwezigheid, uitgegrijsd na laden, werking na een wijziging) en de volledige terugzet-flow inclusief annuleren, bevestigen, undo-baarheid en behoud van opmaak.

**Bewust niet gedaan**
- Geen diff-weergave tussen versies, geen versies benoemen/pinnen, geen versies verwijderen vanuit de app.
- Geen versiegeschiedenis in de assistent-app of in de chat.
- Geen Ctrl+Z-sneltoets: de knoppen zijn de weg.
- De APK is in de bouwomgeving niet te compileren (geen Android SDK); de APK-workflow op `main` is de eerste echte bevestiging.

**Kleine, niet-blokkerende aandachtspunten** (opgemerkt, niet opgelost)
- De Versies-knop is ook actief terwijl de notitie nog laadt of het laden is mislukt; terugzetten in dat foutgeval wordt dan niet automatisch opgeslagen.
- Op de dag ná de overgang naar zomertijd kan een versie van gisteren één keer per jaar als "vandaag" gelabeld worden (alleen het label; tijd en volgorde kloppen).
- Elke autosave doet één extra leesquery op Firestore voor de dubbel-detectie — functioneel correct, alleen relevant voor leesverbruik.
