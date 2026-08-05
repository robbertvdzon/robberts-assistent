# notities

Flutter-app (APK-only) met meerdere auto-opslaande notitiedocumenten, gekoppeld aan
`robberts-assistent-backend`. Google-login vereist.

## Gedrag

- **Meerdere documenten** (sinds SF-1891): in de AppBar van de editor staat een
  `DropdownButton` (`ValueKey('documentkeuze')`) met alle documenten in de volgorde die de
  backend teruggeeft, en ernaast een knop "Documenten beheren". Wisselen slaat eerst het
  openstaande werk van het huidige document op (de debounce wordt direct afgedwongen) en
  wisselt **alleen bij succes** — mislukt de save, dan blijft de tekst plus de bestaande
  foutmelding staan, dus geen tekstverlies. De laatst gekozen document-id staat onder
  `notes_editor_document_id` in `SharedPreferences`; bestaat dat document niet meer, dan opent
  de app het eerste document in de volgorde. Robberts bestaande notitie is backend-side
  automatisch het document 'todo' geworden, inclusief de bewaarde versies.
- **Beheerscherm** (`lib/note_documents_screen.dart`): toevoegen via een titeldialoog,
  hernoemen, verwijderen met bevestigingsdialoog en slepen om de volgorde te wijzigen
  (`ReorderableListView.builder`). Bij precies één document is de verwijderknop uitgeschakeld —
  de backend geeft daar 409 op. Fouten (lege titel, dubbele titel, laatste document) komen als
  `SnackBar` met de Nederlandse melding van de backend. Bij terugkomst herlaadt de editor de
  lijst en schakelt hij naar het eerste document als het huidige verwijderd is.
- Autosave, undo/redo, de opmaakbalk, A−/A+ en het versiescherm werken ongewijzigd, maar per
  gekozen document. De lettergrootte-voorkeur blijft app-breed (niet per document).
- De app is donker (sinds SF-1801): `notitiesDarkTheme` in `lib/main.dart` met
  `Brightness.dark`, `useMaterial3: true`, `scaffoldBackgroundColor: Colors.black`
  en `ColorScheme.dark(surface: Colors.black)`. AppBar donker met witte titel en
  iconen, witte cursor/selectie, grijze hint; ook het login-scherm is leesbaar op
  zwart (donkergrijze kaart, wit `Icons.edit_note`, `Colors.white70`-uitleg).
- Alleen het **bewerkbare tekstvlak** van de editor is sinds SF-1899 donkergrijs in plaats van
  zwart: de top-level constante `notitiesEditorBackground = Color(0xFF404040)` in `lib/main.dart`
  (bewust buiten `notitiesDarkTheme`) wordt in `notes_editor_screen.dart` gebruikt als
  `ColoredBox` (`ValueKey('editorachtergrond')`) binnen het `Expanded` rond de `QuillEditor`.
  Omdat de editor `expands: true` gebruikt, vult die kleur het hele vlak onder de opmaakbalk en
  de `Divider` tot de onderkant van het scherm — ook bij een leeg of kort document. Zo is
  zichtbaar waar de menu's ophouden en de notitie begint. AppBar, opmaakbalk, laad-, fout- en
  inlogscherm, documentenlijst en versiegeschiedenis blijven zwart; tekst, cursor en selectie
  blijven ongewijzigd wit/lichtwit en dus goed leesbaar op de nieuwe achtergrond.
- De notitie is een **WYSIWYG-editor** (`flutter_quill`, `lib/notes_editor_screen.dart`)
  met precies vijf opmaakknoppen direct onder de AppBar — tooltips
  `Vet`, `Cursief`, `Onderstreept`, `Opsomming` en `Opmaak wissen` (die laatste haalt
  vet/cursief/onderstreept én de bullet-opmaak van de selectie af). Actieve knoppen
  zijn te herkennen aan een accentkleur met gevulde achtergrond. Placeholder:
  'Typ hier je notities…'.
- De tekststijl van de editor komt sinds SF-1823 expliciet uit het thema
  (`textTheme.bodyMedium` + `colorScheme.onSurface`, dus wit op zwart) in plaats van
  uit `DefaultStyles.getInstance(context)`. Die las `DefaultTextStyle.of(context)`, en
  boven de `Material` van het `Scaffold` is dat `MaterialApp`s error-fallback (rood
  `0xD0FF0000`, monospace) — daardoor stond de notitie rood in schrijfmachineletter.
  `paragraph`, `lists` en `leading` gebruiken verder exact Quills eigen spacing en
  `height: 1.15`, dus alleen de kleur/het lettertype veranderde; alle overige bloktypes
  (kopjes, quote, code, placeholder) komen ongewijzigd uit Quill.
- A− en A+ in dezelfde horizontaal scrollbare balk wijzigen alleen de lettergrootte van de
  bewerkbare notitie, direct in stappen van 2 pt. Beschikbaar is 12 t/m 28 pt; standaard 16 pt.
  Op de grenzen is de betreffende knop uitgeschakeld. De voorkeur wordt lokaal onder
  `notes_editor_font_size` in `SharedPreferences` bewaard, vóór de notitie geladen en blijft na
  uitloggen of herstart behouden. Gewone en opgemaakte tekst, lijsttekst en bulletmarkeringen
  schalen samen. AppBar, balk, statusmeldingen en de alleen-lezen versieweergave behouden hun
  bestaande grootte. Dit wijzigt het Quill-document niet en veroorzaakt dus geen dirty-state,
  autosave of API-aanroep; handmatig opslaan levert dezelfde markdown.
- Onder water blijft elk document **één platte markdown-string**; er wordt nooit
  Delta-JSON naar de notes-API geschreven. De conversie zit in
  `lib/markdown_delta.dart` (`markdownToDelta()`/`deltaToMarkdown()`, zonder
  widget-afhankelijkheden, dus als unittest te draaien) en kent uitsluitend
  `**vet**`, `*cursief*`, `<u>onderstreept</u>` en `- ` voor bullets. Alle overige
  markup (`#`-kopjes, genummerde lijsten, tabellen, links, code, inspringing, lege
  regels) is platte tekst en gaat letterlijk heen en terug — tekst die de assistent
  of de briefing toevoegt blijft dus ongeschonden. Sinds SF-1891 praat `lib/api_client.dart`
  met de per-document-endpoints (`GET`/`PUT /api/v1/notes/documents/{id}` en de
  `documents`-CRUD); de oude `GET`/`PUT /api/v1/notes` blijven bestaan voor de briefing, de
  AI-tools en oudere APK's, maar worden door deze app niet meer gebruikt. Een fout van de
  backend komt als `ApiException(statusCode, message)` met de Nederlandse `{"error": …}`-melding
  terug, zodat het beheerscherm die kan tonen.
- Links in diezelfde opmaakbalk staan sinds SF-1808 twee knoppen `Ongedaan maken`
  (`Icons.undo`) en `Opnieuw` (`Icons.redo`). Ze gebruiken de undo-historie die
  `QuillController` zelf bijhoudt en zijn uitgegrijsd als er niets te doen valt —
  direct na het laden van de notitie dus allebei, want het initiële laden staat
  niet in de historie (één keer undo maakt de notitie nooit leeg). Een undo/redo
  is een gewone wijziging en gaat via de normale debounce-autosave. Er is bewust
  geen Ctrl+Z-sneltoets; de knoppen zijn de enige weg.
- **Versies** (`Icons.history`) in de AppBar opent een eigen scherm
  (`lib/note_versions_screen.dart`) met de eerdere versies van het gekozen document uit
  `GET /api/v1/notes/documents/{id}/versions` (nieuwste eerst, max 200; versies van het ene
  document verschijnen nooit bij een ander). Per regel datum + tijd in
  lokale tijd en Nederlandse notatie — `vandaag 11:30`, `gisteren 11:30`,
  `ma 28 jul 09:05` — via de eigen helper `formatVersionMoment()`, dus zonder
  `intl` of een extra dependency. Tikken opent een alleen-lezen weergave van die
  oude tekst (selecteerbare platte markdown) met de knop `Terugzetten` en een
  bevestigingsdialoog. Die weergave staat sinds SF-1823 bewust in een lichte roodtint
  (`noteVersionColor = Color(0xFFE57373)`, één benoemde constante in
  `lib/note_versions_screen.dart`) met daarboven het rode label
  `Oude versie van <datum tijd>` — zelfde momentnotatie als de lijst — zodat direct
  duidelijk is dat dit niet de huidige notitie is. Het onderste knopblok zit in een
  `SafeArea(top: false)`, zodat `Terugzetten` bij edge-to-edge/gesture-navigatie
  (Android 15) niet deels achter de systeembalk valt. Terugzetten vervangt de inhoud van de editor via een
  bewerking op het bestaande document, dus het is met de undo-knop ongedaan te
  maken en wordt daarna gewoon door de autosave als nieuwe versie opgeslagen.
  De backend bewaart bij elke save een versie van dát document (tenzij de tekst identiek is aan
  de vorige versie van hetzelfde document) en ruimt 's nachts in alle documenten op: laatste
  7 dagen alles, daarvóór één versie per dag.
- Slaat automatisch op: 10 seconden na de laatste wijziging (debounce), en
  meteen bij het naar de achtergrond gaan of afsluiten van de app.
- Heeft daarnaast een "Opslaan"-knop in de `AppBar` van de editor
  (naast de statustekst en de uitlog-knop) om direct handmatig op te slaan,
  zonder op de debounce te wachten. De knop annuleert een eventueel lopende
  debounce-timer, is tijdens het opslaan kort uitgeschakeld (laadindicator)
  om dubbele requests te voorkomen, en toont dezelfde statusindicator
  ("Opgeslagen" / "Opslaan mislukt: ...") als de auto-save.
- Checkt bij opstarten (async, niet-blokkerend) of er een nieuwere versie op
  GitHub staat en vraagt een dialoogje om bij te werken zo ja
  (`lib/self_update_prompt.dart`/`lib/update_checker.dart`).

## Build & test

```bash
flutter pub get
flutter test
flutter build apk --release \
  --build-number=<N> \
  --dart-define=API_BASE_URL=https://robberts-assistent.vdzonsoftware.nl \
  --dart-define=GOOGLE_CLIENT_ID=<web-oauth-client-id>
```

CI (`.github/workflows/notities-apk.yml`) bouwt en publiceert de release-APK
naar de vaste GitHub-Release-tag `notities-latest` bij elke push naar `main`.
