# SF-1978 - Worklog

Story-context bij eerste pickup:
AppBar notities-editor: brede documenttab, opslag-indicator en overflow-menu

Pas notities/lib/notes_editor_screen.dart aan: (1) _documentDropdown() krijgt vrijwel de volle AppBar-breedte met eenregelige ellipsis-titel; terugval op 'Notities' en de blokkering onChanged bij _loading/_saving blijven. (2) Voeg naast de titel een compacte opslag-indicator toe met stabiele ValueKey en tooltip/semantisch label: _saving -> kleine CircularProgressIndicator (~16px, strokeWidth 2), anders _dirty -> klein duidelijk symbool, anders geen symbool. (3) Trek de _dirty-toekenningen in _onChanged() (~242) en _save() (~260/270) zo in setState (met mounted-guard, en bij !mounted alsnog direct toekennen) dat de indicator gegarandeerd herbouwt, ZONDER de bestaande semantiek te wijzigen: _dirty = false blijft vóór de await staan en dispose() moet nog steeds de juiste waarde lezen voor de best-effort save. (4) Vervang de vier losse AppBar-IconButtons door één PopupMenuButton (standaard drie puntjes, geen nieuwe dependency) met in volgorde Opslaan (_save(force: true), disabled zolang _saving), Documenten beheren (_openDocuments), Versies (_openVersions), Uitloggen (widget.onLoggedOut) - dezelfde Nederlandse labels als de huidige tooltips, aangeroepen methodes ongewijzigd. (5) Verwijder de _status-tekst uit de AppBar (veld mag vervallen) en toon 'Opslaan mislukt: ...' en 'Laden mislukt: ...' met ongewijzigde tekst als SnackBar via ScaffoldMessenger.maybeOf(context) met hideCurrentSnackBar() ervoor; de harde _error-body blijft. (6) Laat _toolbar() (9 IconButtons, ValueKey('opmaakbalk')), de QuillEditor met ColoredBox, _editorStyles/_baseTextStyle, de debounce-autosave, de save bij paused/inactive, de dispose-save, het markdown-formaat en de voorkeuren ongemoeid. Tests horen bij dit werk: pas bestaande tests in notities/test/ die via find.byTooltip('Opslaan'/'Versies'/'Documenten beheren') tikken aan naar 'menu openen -> item op tekst aantikken' met inhoudelijk gelijke asserties, vervang expect(find.text('Opgeslagen')) door een assertie op de verdwenen dirty-indicator, en voeg nieuwe widgettests toe voor (a) dirty-indicator verschijnt na een wijziging en verdwijnt na een geslaagde save, en (b) het overflow-menu bevat de vier acties en elk item doet hetzelfde als de oude knop. Sluit af met flutter analyze (geen issues) en flutter test (groen) in notities/, en voer zelf een reviewstap uit op de diff.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

## Done / rationale

### 1. Brede documenttitel (`notities/lib/notes_editor_screen.dart`)
De AppBar-`title` is nu een `Row` met `Expanded(child: _documentDropdown())` en daarnaast de
opslag-indicator; de vier losse `IconButton`s in `actions` zijn vervangen door één
`PopupMenuButton`. De dropdown zelf is inhoudelijk ongewijzigd (`ValueKey('documentkeuze')`,
`isExpanded: true`, terugval op `Text('Notities')`, `onChanged: (_loading || _saving) ? null : …`);
alleen de item-`Text` kreeg expliciet `maxLines: 1` + `softWrap: false` naast de al aanwezige
`TextOverflow.ellipsis`, zodat een lange titel gegarandeerd op één regel afkapt in plaats van de
indicator/overflow-knop weg te duwen.

### 2. Opslag-indicator
Nieuwe `_saveIndicator()`: bij `_saving` een `SizedBox(16×16)` met
`CircularProgressIndicator(strokeWidth: 2)` en tooltip "Bezig met opslaan", anders bij `_dirty`
een kleine `Icons.fiber_manual_record` (12 px) met tooltip "Niet-opgeslagen wijzigingen", en bij
"alles opgeslagen" géén symbool (alleen een `SizedBox(width: 8)` als ruimte). Beide zichtbare
varianten dragen `ValueKey('opslagindicator')`, dus stabiel testbaar; de `Tooltip` levert meteen
het semantische label.

### 3. `_dirty` altijd via `setState`
Nieuwe helper `_setDirty(bool)`: zonder mount wordt `_dirty` gewoon direct toegekend (want
`dispose()` leest het veld voor de best-effort save), met mount gaat de toekenning door
`setState`. Gebruikt in `_onChanged()` (true) en in `_save()` (`false` — nog steeds *vóór* de
`await`, zodat wijzigingen tijdens het opslaan opnieuw dirty maken — en `true` in de catch). De
save-/autosave-/dispose-semantiek verandert daarmee niet; alleen de rebuild is gegarandeerd.

### 4. Eén overflow-menu
`_overflowMenu()` is een `PopupMenuButton<_EditorAction>` (`ValueKey('overflowmenu')`, standaard
drie-puntjes-icoon, geen nieuwe dependency) met in volgorde **Opslaan** (`_save(force: true)`,
`enabled: !_saving`), **Documenten beheren** (`_openDocuments()`), **Versies** (`_openVersions()`)
en **Uitloggen** (`widget.onLoggedOut()`). De labels zijn exact de oude tooltips; de aangeroepen
methodes zijn niet aangeraakt.

### 5. Statustekst weg, fouten als SnackBar
Het veld `_status` is verwijderd. Nieuwe `_showMessage(String)` gebruikt
`ScaffoldMessenger.maybeOf(context)` met `hideCurrentSnackBar()` ervoor (patroon uit
`robberts_assistent/lib/assistant_screen.dart`) en een `mounted`-guard. De meldingsteksten zijn
letterlijk gelijk gebleven: `Opslaan mislukt: $e` in `_save()` en `Laden mislukt: $e` in
`_openDocument()`/`_openDocuments()`. De harde laadfout in de body (`_error`) is ongewijzigd.

### 6. Onaangeroerd
`_toolbar()` (negen `IconButton`s), de `QuillEditor` met `ColoredBox`, `_editorStyles`/
`_baseTextStyle`, de debounce-autosave, de save bij `paused`/`inactive`, de dispose-save, het
markdown-opslagformaat en beide `SharedPreferences`-voorkeuren. Geen backend-, API-, thema- of
dependencywijziging; `api_client.dart`, `markdown_delta.dart`, `note_documents_screen.dart`,
`note_versions_screen.dart` en `main.dart` zijn niet aangeraakt.

### Tests
- Bestaande tests die via `find.byTooltip('Opslaan'/'Versies'/'Documenten beheren')` tikten gaan
  nu via de nieuwe helper `_tapMenu(tester, label)` (menu openen → item op tekst aantikken); de
  asserties zelf zijn inhoudelijk gelijk gebleven.
- `expect(find.text('Opgeslagen'), findsOneWidget)` is vervangen door
  `expect(find.byKey(const ValueKey('opslagindicator')), findsNothing)` (plus `find.text('Opgeslagen')`
  is nu `findsNothing`).
- Nieuw: AppBar bevat alleen documentkeuze + indicator + één overflow-knop; lange titel kapt af met
  ellipsis en indicator/overflow blijven in beeld; dirty-indicator verschijnt na een wijziging en is
  na een geslaagde save weg; tijdens het opslaan toont de indicator een `CircularProgressIndicator`
  en is het menu-item Opslaan disabled; het menu bevat de vier acties in volgorde; Uitloggen roept
  de callback aan; Versies/Documenten beheren openen hun eigen scherm.
- `test/fake_api_client.dart` kreeg `blockSave`/`completeSave()` (een `Completer`) zodat de
  "bezig met opslaan"-toestand deterministisch te testen is.

Twee testdetails die opvielen en in helpers zijn vastgelegd:
- `find.byType(PopupMenuItem<...>)` matcht op exacte `runtimeType` en het waardetype `_EditorAction`
  is privé — daarom `find.byWidgetPredicate((w) => w is PopupMenuItem)` in `_menuLabels`/
  `_menuItemEnabled`.
- `pumpAndSettle()` loopt vast zolang de voortgangsindicator draait; in die test wordt met losse
  `pump()`-frames gewerkt. Na een SnackBar-test wordt de SnackBar uitgepompt (`_dismissSnackBar`)
  zodat er geen timer blijft hangen bij teardown.

## Verificatie

- `notities/`: `flutter analyze` → **No issues found!**
- `notities/`: `flutter test` → **80 tests groen** (was 73 vóór deze story).
- `notities/`: `flutter build bundle --release` → geslaagd.
- Backend als regressie-vangnet: in `robberts-assistent-backend/` `rm -rf target && mvn -o test` →
  **433 tests, 0 failures, 0 errors, BUILD SUCCESS** (ongewijzigd; deze story raakt de backend niet).
- `flutter build apk` kan niet in de sandbox (geen Android SDK op linux/arm64), dus de workflow
  `.github/workflows/notities-apk.yml` op `main` en een visuele check op een fysiek toestel blijven
  de laatste bevestiging.
