# SF-1978 - Notities-app: brede documenttab in AppBar met dirty-indicator en overflow-menu

## Story

Notities-app: brede documenttab in AppBar met dirty-indicator en overflow-menu

<!-- refined-by-factory -->

## Samenvatting

De bovenste balk van de notities-app zit propvol knoppen, waardoor je nauwelijks ziet welk
document je open hebt. Die balk wordt opgeruimd: de documentnaam krijgt bijna de hele breedte,
met er direct naast een klein tekentje dat laat zien of je werk al is opgeslagen. Alle knoppen
(opslaan, documenten beheren, versies, uitloggen) schuiven naar één menu met drie puntjes rechts.
De statustekst "Opgeslagen" verdwijnt, maar foutmeldingen blijven wel gewoon zichtbaar. De
opmaakbalk en de editor zelf veranderen niet.

## Scope

Alleen `notities/` (Flutter-app), en daarbinnen alleen de AppBar van
`lib/notes_editor_screen.dart` plus de bijbehorende tests. Geen backend-, API-, opslagformaat-,
thema- of dependencywijziging; `lib/api_client.dart`, `lib/markdown_delta.dart`,
`lib/note_documents_screen.dart`, `lib/note_versions_screen.dart` en `lib/main.dart` blijven
ongewijzigd.

**1. Brede documenttitel.** `_documentDropdown()` (`ValueKey('documentkeuze')`) krijgt vrijwel de
volledige AppBar-breedte; de titel kapt af met ellipsis (`TextOverflow.ellipsis`, één regel) in
plaats van te overlopen of de rest weg te drukken. De bestaande blokkering tijdens laden/opslaan
(`onChanged: (_loading || _saving) ? null : …`) en de terugval op de tekst `Notities` zolang de
lijst nog niet geladen is, blijven zoals ze zijn.

**2. Opslag-indicator naast de titel.** Direct naast de documenttitel komt een compacte indicator
op basis van de bestaande velden:
- `_saving == true` → kleine voortgangsindicator (`CircularProgressIndicator`, ±16 px, `strokeWidth: 2`);
- `_saving == false && _dirty == true` → duidelijk symbool voor niet-opgeslagen wijzigingen
  (bolletje/sterretje, bv. `Icons.circle`/`Icons.fiber_manual_record`, klein);
- alles opgeslagen → geen symbool (of een subtiel, gedempt vinkje).

`_dirty` wordt nu op twee plekken buiten `setState` gezet (`_onChanged()` en `_save()`, ±regel 242
en 260); dat moet zo aangepast worden dat de indicator gegarandeerd herbouwt bij elke overgang —
bijvoorbeeld door die toekenningen in `setState` te trekken (met `mounted`-guard, want `dispose()`
leest `_dirty` ook) of door `_dirty` in een `ValueNotifier` te zetten. De indicator krijgt een
`ValueKey` (bv. `opslagindicator`) en een leesbaar `Tooltip`/semantisch label, zodat 'ie stabiel
testbaar en toegankelijk is.

**3. Eén overflow-menu.** De losse AppBar-knoppen `Documenten beheren` (`Icons.folder_open`),
`Opslaan` (`Icons.save`), `Versies` (`Icons.history`) en `Uitloggen` (`Icons.logout`) verdwijnen
en komen terug als items in één `PopupMenuButton` (drie puntjes) rechts in de AppBar, in deze
volgorde:
- **Opslaan** → `_save(force: true)`, item uitgeschakeld zolang `_saving == true`;
- **Documenten beheren** → `_openDocuments()`;
- **Versies** → `_openVersions()`;
- **Uitloggen** → `widget.onLoggedOut()`.

De aangeroepen methodes zelf blijven ongewijzigd; alleen de plek van de knop verandert. De
menu-items houden dezelfde Nederlandse labels als de huidige tooltips, zodat ze op tekst vindbaar
zijn.

**4. Statustekst weg, fouten blijven zichtbaar.** De losse statustekst in de AppBar (`_status`,
o.a. `Opgeslagen`) vervalt; het veld `_status` mag verdwijnen. De bestaande foutafhandeling in
`_save()` (`Opslaan mislukt: …`) en `_load()`/`_openDocument()`/`_openDocuments()`
(`Laden mislukt: …`) blijft functioneel intact en wordt aan de gebruiker getoond als **SnackBar**
met exact dezelfde meldingstekst, via `ScaffoldMessenger.maybeOf(context)` met
`hideCurrentSnackBar()` ervoor (patroon zoals in `robberts_assistent/lib/assistant_screen.dart`),
zodat meldingen niet stapelen en er niets crasht als de scaffold al weg is. De harde
laadfout-weergave in de body (`_error`, hele scherm) blijft zoals 'ie is.

**5. Onaangeroerd.** `_toolbar()` (`ValueKey('opmaakbalk')`: A−/A+, undo/redo, vet/cursief/
onderstreept/opsomming/opmaak wissen), de `QuillEditor` met `ColoredBox`
(`ValueKey('editorachtergrond')`, `notitiesEditorBackground`), `_editorStyles`/`_baseTextStyle`,
de debounce-autosave (10 s), de save bij `paused`/`inactive`, de best-effort save in `dispose()`,
het opslagformaat (platte markdown) en de documentkeuze-voorkeur
(`notes_editor_document_id`/`notes_editor_font_size`) blijven ongewijzigd.

## Acceptance criteria

1. De AppBar bevat nog maar drie elementen: de documentkeuze (vrijwel volledige breedte), de
   opslag-indicator ernaast, en rechts één overflow-knop (drie puntjes).
2. Een lange documenttitel kapt af met ellipsis op één regel en duwt de indicator of de
   overflow-knop niet uit beeld.
3. Na het typen van een wijziging verschijnt de dirty-indicator; tijdens het opslaan toont de
   indicator een voortgangsindicator; na een geslaagde save is de dirty-indicator weg. Dit werkt
   ook wanneer `_dirty` via `_onChanged()`/`_save()` wijzigt (geen "vergeten" rebuild).
4. Het overflow-menu bevat de vier acties Opslaan, Documenten beheren, Versies en Uitloggen; het
   aantikken ervan doet exact hetzelfde als de oude knoppen (`_save(force: true)`,
   `_openDocuments()`, `_openVersions()`, `widget.onLoggedOut`). "Opslaan" is uitgeschakeld
   terwijl `_saving == true`.
5. De tekst `Opgeslagen` staat niet meer in de AppBar; een mislukte save toont
   `Opslaan mislukt: …` en een mislukt laden `Laden mislukt: …` als SnackBar met ongewijzigde
   meldingstekst.
6. De opmaakbalk telt onveranderd negen `IconButton`s en de editor (achtergrond, stijlen,
   lettergrootte-knoppen, undo/redo) is functioneel ongewijzigd.
7. Nieuwe widgettests in `notities/test/notes_editor_screen_test.dart`:
   (a) de dirty-indicator verschijnt na een wijziging en is weg na een succesvolle save;
   (b) het overflow-menu bevat de vier acties en elk item doet hetzelfde als de oude knop
   (opslaan slaat op via de fake API, Versies/Documenten beheren openen de bijbehorende route,
   Uitloggen roept de callback aan).
8. Bestaande tests in `notities/test/` (`notes_editor_screen_test.dart`,
   `note_versions_screen_test.dart`, `note_documents_screen_test.dart`, `widget_test.dart`,
   `markdown_delta_test.dart`) blijven groen; waar ze nu rechtstreeks op
   `find.byTooltip('Opslaan'/'Versies'/'Documenten beheren')` tikken wordt alleen het
   interactiepad aangepast (eerst het overflow-menu openen, dan het item aantikken) en blijft de
   assertie inhoudelijk gelijk. De enige assertie die vervalt is
   `expect(find.text('Opgeslagen'), findsOneWidget)`; die wordt vervangen door een assertie op de
   verdwenen dirty-indicator.
9. In `notities/`: `flutter analyze` → geen issues, `flutter test` → alles groen.
10. De workflow `.github/workflows/notities-apk.yml` (flutter pub get + flutter test +
    flutter build apk) slaagt op `main`.

## Aannames

- De opslag-indicator is puur presentatie: hij verandert niets aan wanneer of wat er opgeslagen
  wordt, en het aanpassen van de `_dirty`-toekenningen mag de autosave-/dispose-flow niet van
  gedrag veranderen.
- "Alles opgeslagen" wordt weergegeven als *geen* symbool (rustigst, en het onderscheid met
  "wel wijzigingen" blijft duidelijk); een subtiel vinkje is een toegestane alternatieve invulling.
- Foutmeldingen gaan als SnackBar (niet als rood icoon in de balk): dat houdt de bestaande
  meldingsteksten en de bestaande testassertie `find.textContaining('Opslaan mislukt')` intact.
- Er komt geen sneltoets (bv. Ctrl+S) en geen bevestigingsdialoog bij uitloggen — het gedrag van
  de acties blijft één-op-een gelijk aan nu.
- De overflow-knop gebruikt Flutters standaard `PopupMenuButton` met het standaard
  drie-puntjes-icoon; er wordt geen extra dependency toegevoegd.
- Een APK is in de factory-sandbox niet te bouwen (geen Android SDK), dus de APK-workflow op
  `main` en een visuele check op een fysiek toestel zijn de laatste bevestiging; in de sandbox
  volstaan `flutter test`/`flutter analyze` (en eventueel `flutter build bundle --release`).

## Eindsamenvatting

Alles gelezen (.task.md, worklog SF-1978 met developer-, reviewer- en testerbijdragen, en de diff). Hieronder de eindsamenvatting.

## SF-1978 — Notities-app: brede documenttab in AppBar met dirty-indicator en overflow-menu

### Wat is gebouwd
Alleen de Flutter-app `notities/`, en daarbinnen uitsluitend de AppBar van `lib/notes_editor_screen.dart` plus tests. De AppBar bestaat nu uit precies drie elementen:

1. **Brede documenttitel** — de documentkeuze (`ValueKey('documentkeuze')`) zit in een `Expanded` en beslaat vrijwel de volle breedte; een lange titel kapt af op één regel (`maxLines: 1` + `softWrap: false` + ellipsis) in plaats van de rest weg te duwen. Terugval op "Notities" tijdens laden en de blokkering bij `_loading`/`_saving` zijn ongewijzigd.
2. **Opslag-indicator** ernaast (`ValueKey('opslagindicator')`, met tooltip als semantisch label): spinner (16 px) tijdens opslaan, klein bolletje (`Icons.fiber_manual_record`) bij niet-opgeslagen wijzigingen, en géén symbool als alles opgeslagen is.
3. **Eén overflow-menu** (standaard `PopupMenuButton`, geen nieuwe dependency) met in volgorde Opslaan (uitgeschakeld tijdens opslaan), Documenten beheren, Versies en Uitloggen — dezelfde Nederlandse labels als de oude tooltips, en exact dezelfde onderliggende methodes.

De statustekst "Opgeslagen" (`_status`) is verdwenen; foutmeldingen blijven letterlijk gelijk (`Opslaan mislukt: …`, `Laden mislukt: …`) maar verschijnen nu als SnackBar via `ScaffoldMessenger.maybeOf` + `hideCurrentSnackBar()`. De harde laadfout-weergave in de body blijft zoals ie was.

### Belangrijkste keuzes
- **`_setDirty()`-helper**: beide `_dirty`-toekenningen lopen nu via `setState` (met `mounted`-guard en directe toekenning zonder mount), zodat de indicator gegarandeerd herbouwt. De save-semantiek is bewust niet veranderd: `_dirty = false` staat nog steeds vóór de `await` (een wijziging tijdens het opslaan markeert dus meteen weer dirty) en `dispose()` leest het veld ongewijzigd voor de best-effort save.
- "Alles opgeslagen" is weergegeven als *geen* symbool (rustigst), conform de aanname in de story.
- Fouten als SnackBar in plaats van in de balk, zodat de bestaande meldingsteksten en testasserties intact blijven.

### Getest
- `flutter analyze` → geen issues; `flutter test` → **80 groen** (was 73), door developer, reviewer én tester onafhankelijk uitgevoerd.
- Nieuwe/aangepaste widgettests dekken: AppBar-samenstelling, afkappende lange titel op 360×640 zonder dat indicator/overflow uit beeld vallen, dirty-indicator verschijnt na wijziging en verdwijnt na een geslaagde save, spinner + uitgeschakeld "Opslaan" tijdens een (kunstmatig geblokkeerde) save, de vier menu-acties in volgorde met hun oude gedrag. Bestaande tooltip-tikken zijn omgezet naar "menu openen → item aantikken" met gelijke asserties.
- `flutter build bundle --release` geslaagd; backend `mvn -o test` → 433 groen als regressie-vangnet (niet geraakt door deze story).
- Visueel bewijs via een scratch-widget-screenshot (`SF-1980-appbar-dirty.png`), omdat `notities/` APK-only is.

### Bewust niet gedaan
- Geen backend-, API-, opslagformaat-, thema- of dependencywijziging; `api_client.dart`, `markdown_delta.dart`, `note_documents_screen.dart`, `note_versions_screen.dart` en `main.dart` zijn niet aangeraakt. Opmaakbalk (9 knoppen), editor, autosave, lifecycle-/dispose-save en voorkeuren ongewijzigd.
- Geen Ctrl+S-sneltoets en geen bevestigingsdialoog bij uitloggen.
- **`flutter build apk` is niet uitgevoerd** — geen Android SDK in de sandbox (linux/arm64). Acceptatiecriterium 10 (APK-workflow op `main`) en een visuele check op een fysiek toestel blijven daarmee de laatste bevestiging.

### Openstaand puntje (cosmetisch, niet blokkerend)
De KDoc van `_switchDocument()` spreekt nog van "de bestaande foutmelding blijft staan", terwijl de melding nu een SnackBar is — kandidaat voor de documentatie-subtaak.

<!-- deploy-summary:start -->
De bovenste balk in de notitie-app is opgeruimd: de naam van je document krijgt nu bijna de hele breedte, zodat je in één oogopslag ziet waar je in werkt. Ernaast zie je een klein tekentje wanneer je wijzigingen nog niet zijn opgeslagen, en tijdens het opslaan een draaiend rondje. Opslaan, documenten beheren, versies en uitloggen staan voortaan samen onder het menu met drie puntjes rechtsboven.
<!-- deploy-summary:end -->
