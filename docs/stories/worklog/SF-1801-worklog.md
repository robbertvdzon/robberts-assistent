# SF-1801 - Worklog

Story-context bij eerste pickup:
Donker thema + Quill rich-text editor met markdown-opslag in notities-app

Implementeer SF-1801 volledig in notities/ (geen backend-wijziging, api_client.dart ongewijzigd).

1) Donker thema in lib/main.dart: MaterialApp.theme wordt ThemeData(brightness: Brightness.dark, useMaterial3: true, scaffoldBackgroundColor: Colors.black) met ColorScheme.dark; de amber-seed en gele achtergrond vervallen. AppBar donker met witte titel/iconen; tekst, cursor, selectie en hint leesbaar op zwart (hint grijs). In _loginView(): Card, het Icons.edit_note-icoon (nu Colors.amber) en de tekst 'Log in met Google om verder te gaan.' (nu Colors.black54) leesbaar maken op donker; de teksten 'Notities' en 'Log in met Google om verder te gaan.' blijven letterlijk staan (widget_test.dart matcht erop).

2) Nieuwe lib/markdown_delta.dart met markdownToDelta() en deltaToMarkdown(), zonder widget-afhankelijkheden. Mapping en niets anders: bold = **tekst**, italic = *tekst*, underline = <u>tekst</u>, bullet = regel die begint met exact '- '. Per regel parsen, markers lopen niet over regelgrenzen; niet-afgesloten markers blijven letterlijke tekst. Vaste deterministische nestvolgorde bij schrijven (underline buiten, dan bold, dan italic) die de parser ook weer inleest. Alle overige markup (#-kopjes, genummerde lijsten, '* '-bullets, inspringing, tabellen, links, code) is platte tekst en gaat letterlijk heen en terug; lege regels blijven behouden; niets escapen. Quill's interne afsluitende newline wordt bij het schrijven afgeknipt zodat deltaToMarkdown(markdownToDelta(s)) == s byte-identiek geldt voor opmaakloze notities.

3) lib/notes_editor_screen.dart: vervang de TextField door een QuillEditor + QuillController (flutter_quill, caret-constraint in pubspec.yaml, geen flutter_quill_extensions; localizations-delegate toevoegen in MaterialApp en in de widget-tests). Direct onder de AppBar een compacte rij met precies vijf knoppen met tooltips 'Vet', 'Cursief', 'Onderstreept', 'Opsomming', 'Opmaak wissen' (die laatste haalt bold/italic/underline en de bullet-opmaak van de selectie af); geen andere opmaakknoppen. Witte iconen, zichtbaar verschil tussen actieve en inactieve staat. Placeholder 'Typ hier je notities…' blijft. Voorkeur voor een zelfgebouwde knoppenrij boven QuillSimpleToolbar.

4) Laden: api.getNotes() -> markdownToDelta -> document. Opslaan: deltaToMarkdown(document.toDelta()) -> bestaande api.saveNotes(). Behoud 10s-debounce (nu gevoed door een document-listener; het initiële laden mag geen save triggeren), directe save bij paused/inactive in didChangeAppLifecycleState en best-effort save in dispose() (controller pas na die save disposen). Statusregel ('Opgeslagen' / 'Opslaan mislukt: ...'), force-save-knop, Uitloggen, laad-spinner en foutmelding blijven ongewijzigd.

5) Tests horen bij deze subtaak: unit-tests op markdown_delta.dart (bold/italic/underline/bullet heen en terug, gecombineerde/geneste opmaak op dezelfde tekst, en een byte-identieke roundtrip op een notitie met platte tekst, meerdere lege regels en onbekende markup incl. een #-kop); nieuwe widget-tests (de vijf knoppen bestaan; selectie + 'Vet' + opslaan levert **tekst** in _FakeApiClient.lastSavedText; daarna 'Opmaak wissen' op dezelfde selectie levert weer de kale tekst); en het aanpassen van test/notes_editor_screen_test.dart en test/widget_test.dart waar ze nog van een kale TextField uitgaan.

Voer zelf een reviewstap uit. Acceptatie: flutter analyze zonder nieuwe warnings, flutter test groen in notities/, en de dependency mag geen extra CI-stap of platform-configuratie vereisen zodat .github/workflows/notities-apk.yml (pub get + test + build apk --release) ongewijzigd blijft slagen - pak bij twijfel een oudere compatibele flutter_quill-major i.p.v. de workflow aan te passen.

Belangrijkste risico: er mag NOOIT Delta-JSON of gemangelde markdown in het gedeelde notitie-tekstveld belanden; dat breekt assistant/ai/NotesTools.kt en briefing/WeekTasksSectionProvider.kt.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1802 (development)

### Donker thema (`notities/lib/main.dart`)
- Nieuw top-level `notitiesDarkTheme`: `Brightness.dark`, `useMaterial3: true`,
  `scaffoldBackgroundColor: Colors.black`, `ColorScheme.dark(surface: Colors.black)`,
  donkere `AppBarTheme` met witte titel/iconen, witte cursor/selectie
  (`textSelectionTheme`) en een grijze hint (`inputDecorationTheme`). De amber-seed en
  `Colors.yellow` zijn weg.
- `_loginView()`: de `Card` krijgt een donkergrijze kleur, het `Icons.edit_note`-icoon is
  wit, 'Notities' expliciet wit en 'Log in met Google om verder te gaan.' `Colors.white70`
  i.p.v. `Colors.black54`. De teksten zelf en de inlogflow zijn ongewijzigd.
- `MaterialApp` krijgt `FlutterQuillLocalizations.localizationsDelegates`/`supportedLocales`
  (vereist door flutter_quill).

### Conversie (`notities/lib/markdown_delta.dart`, nieuw)
- `markdownToDelta()`/`deltaToMarkdown()` zonder Flutter-widget-afhankelijkheden (alleen
  `package:flutter_quill/quill_delta.dart`, dat puur Dart is), dus als unit-test te draaien.
- Mapping en niets anders: bold `**tekst**`, italic `*tekst*`, underline `<u>tekst</u>`,
  bullet = regel met exact `- ` (in Quill een `list: bullet`-attribuut op het newline-teken).
  Alles daarbuiten is platte tekst.
- Per regel parsen; niet-afgesloten markers blijven letterlijk. Schrijven gebeurt in een
  vaste nestvolgorde (underline buiten, dan bold, dan italic → `<u>***tekst***</u>`); de
  parser leest een `***`-run daarom expliciet als vet+cursief terug.
- Quill's afsluitende newline wordt afgeknipt, aangrenzende stukken met dezelfde opmaak
  worden samengevoegd (`**ab**` i.p.v. `**a****b**`), zodat
  `deltaToMarkdown(markdownToDelta(s)) == s` byte-identiek geldt voor opmaakloze notities.

### Editor (`notities/lib/notes_editor_screen.dart`)
- `TextField` → `QuillEditor` + `QuillController`; zelfgebouwde opmaakbalk (geen
  `QuillSimpleToolbar`) met precies vijf `IconButton`s met tooltips 'Vet', 'Cursief',
  'Onderstreept', 'Opsomming', 'Opmaak wissen'. De balk zit in een `ListenableBuilder` op de
  controller zodat de actieve staat (accentkleur + gevulde achtergrond) meeloopt met de
  selectie; de `Row` heeft `ValueKey('opmaakbalk')` als testhaak voor "precies vijf knoppen".
- Laden: `getNotes()` → `markdownToDelta` → `Document.fromDelta`. Pas ná het zetten van het
  document wordt op `document.changes` geabonneerd, zodat het initiële laden geen save
  triggert. Opslaan is altijd `deltaToMarkdown(document.toDelta())` via de bestaande
  `api.saveNotes(...)` — er gaat dus nooit Delta-JSON naar `/api/v1/notes`.
- 10s-debounce, directe save bij `paused`/`inactive`, best-effort save in `dispose()` (tekst
  wordt opgehaald vóór `_controller.dispose()`), statusregel, force-save-knop, Uitloggen,
  laadspinner en foutmelding ongewijzigd.

### Tests
- Nieuw `test/markdown_delta_test.dart`: bold/italic/underline/bullet heen en terug,
  gecombineerde opmaak, niet-afgesloten markers, markers over regelgrenzen, en byte-identieke
  roundtrips (o.a. een notitie met `#`-kop, lege regels, tabel/link/code, lege notitie en een
  afsluitende lege regel).
- `test/notes_editor_screen_test.dart` omgebouwd van `TextField` naar Quill: bestaande
  save-/foutgevallen behouden, plus de vijf knoppen, 'selectie + Vet' → `**notitie**`,
  daarna 'Opmaak wissen' → `notitie`, 'Opsomming' → `- melk`, en autosave (niets na 9 s, wél
  na 11 s) en directe save bij `paused`.
- `test/widget_test.dart` uitgebreid met een thema-assert (dark + zwarte achtergrond) en de
  leesbaarheid van icoon/tekst op het loginscherm.

### Verificatie
- `flutter test` in `notities/`: 30 tests groen. `flutter analyze`: "No issues found!".
- Backend `mvn -o test` gedraaid als regressiecheck (geen backendwijziging in deze story).
- `flutter build apk` is in deze sandbox niet te draaien (geen Android SDK). Wel gecheckt dat
  de nieuwe dependency geen platform-configuratie vraagt: `flutter_quill 11.5.1` trekt
  `quill_native_bridge_android` en `url_launcher_android` mee, die `minSdk = 24` eisen — dat
  is precies de `flutter.minSdkVersion`-default die `notities/android/app/build.gradle.kts`
  al gebruikt. `.github/workflows/notities-apk.yml` blijft dus ongewijzigd.
- `pubspec.lock` bevat naast flutter_quill enkele transitieve bumps (matcher, meta, test_api,
  material_color_utilities) die de lokale Flutter-SDK bij `pub get` afdwingt.

## Review SF-1802 (reviewer, 2026-08-02)

Zelf geverifieerd in `notities/`: `flutter test` → 30 tests groen, `flutter analyze` →
"No issues found!". Scope klopt: alleen `notities/**` + dit worklog; geen backend,
`api_client.dart` of workflow-wijziging. Donker thema, de vijf toolbarknoppen met de
afgesproken tooltips, de placeholder, laden/opslaan via `markdownToDelta`/`deltaToMarkdown`
en het autosave-/dispose-gedrag zijn conform de story.

**Afgekeurd op de roundtrip-garantie van `markdown_delta.dart`.** Met een gerichte
fuzz-/steekproefrun (20.000 willekeurige strings uit markdown-atomen + handmatige gevallen)
blijkt `deltaToMarkdown(markdownToDelta(s)) == s` niet te gelden zodra er losse
marker-tekens in de tekst staan — dat is precies het "gemangelde markdown in het gedeelde
notitieveld"-risico uit de description:

- `Bereken 2 * 3 en **let op** dit * dat` → `Bereken 2 * 3 en let op dit *dat`
  (de `**`-markers verdwijnen: een losse `*` opent cursief en slikt het `**`-paar op).
- `**Lijst: melk * brood * kaas**` → `**Lijst: melk ***** brood ***** kaas**`.
- `******` → `` en `<u></u>` → `` (lege opmaak-span wordt stil weggegooid).
- `a **b <u>c</u> d** e` → `a **b **<u>**c**</u>** d** e` (normalisatie naar underline-buiten;
  wel stabiel bij een tweede cyclus).

Alle gevallen zijn idempotent vanaf de tweede cyclus, dus er is geen onbegrensde corruptie,
maar de eerste open+opslaan-cyclus wijzigt de opgeslagen tekst wél.

Voorgestelde richting: in `_parseInline` een sluit-marker alleen accepteren als 'ie niet
deel is van een langere `*`-run (dus geen enkele `*` matchen op een positie binnen `**`/`***`),
en bij een lege inner-inhoud terugvallen op letterlijke tekst i.p.v. het segment weg te gooien.
Plus regressietests op bovenstaande vier strings.
