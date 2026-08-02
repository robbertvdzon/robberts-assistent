# notities

Flutter-app (APK-only) met één auto-opslaande notitie, gekoppeld aan
`robberts-assistent-backend`. Google-login vereist.

## Gedrag

- De app is donker (sinds SF-1801): `notitiesDarkTheme` in `lib/main.dart` met
  `Brightness.dark`, `useMaterial3: true`, `scaffoldBackgroundColor: Colors.black`
  en `ColorScheme.dark(surface: Colors.black)`. AppBar donker met witte titel en
  iconen, witte cursor/selectie, grijze hint; ook het login-scherm is leesbaar op
  zwart (donkergrijze kaart, wit `Icons.edit_note`, `Colors.white70`-uitleg).
- De notitie is een **WYSIWYG-editor** (`flutter_quill`, `lib/notes_editor_screen.dart`)
  met precies vijf opmaakknoppen direct onder de AppBar — tooltips
  `Vet`, `Cursief`, `Onderstreept`, `Opsomming` en `Opmaak wissen` (die laatste haalt
  vet/cursief/onderstreept én de bullet-opmaak van de selectie af). Actieve knoppen
  zijn te herkennen aan een accentkleur met gevulde achtergrond. Placeholder:
  'Typ hier je notities…'.
- A− en A+ in dezelfde horizontaal scrollbare balk wijzigen alleen de lettergrootte van de
  bewerkbare notitie, direct in stappen van 2 pt. Beschikbaar is 12 t/m 28 pt; standaard 16 pt.
  Op de grenzen is de betreffende knop uitgeschakeld. De voorkeur wordt lokaal onder
  `notes_editor_font_size` in `SharedPreferences` bewaard, vóór de notitie geladen en blijft na
  uitloggen of herstart behouden. Gewone en opgemaakte tekst, lijsttekst en bulletmarkeringen
  schalen samen. AppBar, balk, statusmeldingen en de alleen-lezen versieweergave behouden hun
  bestaande grootte. Dit wijzigt het Quill-document niet en veroorzaakt dus geen dirty-state,
  autosave of API-aanroep; handmatig opslaan levert dezelfde markdown.
- Onder water blijft de notitie **één platte markdown-string**; er wordt nooit
  Delta-JSON naar `/api/v1/notes` geschreven. De conversie zit in
  `lib/markdown_delta.dart` (`markdownToDelta()`/`deltaToMarkdown()`, zonder
  widget-afhankelijkheden, dus als unittest te draaien) en kent uitsluitend
  `**vet**`, `*cursief*`, `<u>onderstreept</u>` en `- ` voor bullets. Alle overige
  markup (`#`-kopjes, genummerde lijsten, tabellen, links, code, inspringing, lege
  regels) is platte tekst en gaat letterlijk heen en terug — tekst die de assistent
  of de briefing toevoegt blijft dus ongeschonden. `notities/lib/api_client.dart` en
  het contract van `GET`/`PUT /api/v1/notes` zijn ongewijzigd.
- Links in diezelfde opmaakbalk staan sinds SF-1808 twee knoppen `Ongedaan maken`
  (`Icons.undo`) en `Opnieuw` (`Icons.redo`). Ze gebruiken de undo-historie die
  `QuillController` zelf bijhoudt en zijn uitgegrijsd als er niets te doen valt —
  direct na het laden van de notitie dus allebei, want het initiële laden staat
  niet in de historie (één keer undo maakt de notitie nooit leeg). Een undo/redo
  is een gewone wijziging en gaat via de normale debounce-autosave. Er is bewust
  geen Ctrl+Z-sneltoets; de knoppen zijn de enige weg.
- **Versies** (`Icons.history`) in de AppBar opent een eigen scherm
  (`lib/note_versions_screen.dart`) met de eerdere versies van de notitie uit
  `GET /api/v1/notes/versions` (nieuwste eerst, max 200). Per regel datum + tijd in
  lokale tijd en Nederlandse notatie — `vandaag 11:30`, `gisteren 11:30`,
  `ma 28 jul 09:05` — via de eigen helper `formatVersionMoment()`, dus zonder
  `intl` of een extra dependency. Tikken opent een alleen-lezen weergave van die
  oude tekst (selecteerbare platte markdown) met de knop `Terugzetten` en een
  bevestigingsdialoog. Terugzetten vervangt de inhoud van de editor via een
  bewerking op het bestaande document, dus het is met de undo-knop ongedaan te
  maken en wordt daarna gewoon door de autosave als nieuwe versie opgeslagen.
  De backend bewaart bij elke save een versie (tenzij de tekst identiek is aan de
  vorige) en ruimt 's nachts op: laatste 7 dagen alles, daarvóór één versie per dag.
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
