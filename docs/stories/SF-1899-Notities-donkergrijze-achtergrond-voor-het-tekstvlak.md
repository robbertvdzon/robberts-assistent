# SF-1899 - Notities: donkergrijze achtergrond voor het tekstvlak

## Story

Notities: donkergrijze achtergrond voor het tekstvlak

<!-- refined-by-factory -->

## Samenvatting

In de notities-app is nu alles zwart: de balk bovenin, de knoppenbalk en het
tekstvlak lopen visueel in elkaar over. Het tekstvlak krijgt daarom een eigen,
iets lichtere donkergrijze achtergrond, zodat duidelijk te zien is waar de
menu's ophouden en de notitie begint. Het hele vlak onder de knoppenbalk krijgt
die kleur, niet alleen de regels waar tekst staat. De tekst blijft wit en goed
leesbaar, en verder verandert er niets: de balk bovenin, de knoppenbalk en de
andere schermen (documentenlijst, versiegeschiedenis, inloggen) blijven zwart.

## Scope

Alleen de Flutter-app `notities/`. Geen backendwijziging, geen wijziging aan
`notities/lib/api_client.dart`, `lib/markdown_delta.dart`, het opslagformaat
(platte markdown via `PUT /api/v1/notes/documents/{id}`), de save-/autosave-flow,
de versiegeschiedenis of de dependencies.

- `notities/lib/main.dart`: naast het bestaande `notitiesDarkTheme` een nieuwe
  top-level constante voor de editor-achtergrond, bijvoorbeeld
  `const notitiesEditorBackground = Color(0xFF262626);`. Waarde binnen
  `0xFF242424`–`0xFF2B2B2B` mag, mits duidelijk lichter dan zwart en nog echt
  donker. `notitiesDarkTheme` zelf (`scaffoldBackgroundColor`,
  `ColorScheme.dark(surface: Colors.black)`, `appBarTheme`,
  `textSelectionTheme`, `inputDecorationTheme`) blijft ongewijzigd, zodat de
  overige schermen zwart blijven.
- `notities/lib/notes_editor_screen.dart`, `build()`: het `Expanded` met de
  `QuillEditor` (`expands: true`) wordt gewikkeld in een `ColoredBox`/`Container`
  met `notitiesEditorBackground`, zó dat het volledige resterende vlak onder de
  `Divider` die kleur krijgt — ook als het document leeg of korter dan het scherm
  is. De `QuillEditorConfig` (placeholder `'Typ hier je notities…'`,
  `padding: EdgeInsets.all(16)`, `expands: true`, `customStyles`) blijft verder
  ongewijzigd.
- Tekst-, placeholder-, cursor- en selectieleesbaarheid op de nieuwe achtergrond
  wordt gecontroleerd; is een van die drie onvoldoende leesbaar, dan mag de
  bijbehorende kleur gericht worden bijgesteld (bijvoorbeeld de
  placeholder-stijl in `_editorStyles(context)`). De bestaande, bewuste
  constructie in `_baseTextStyle(context)`/`_editorStyles(context)` blijft
  gehandhaafd: de basisstijl wordt expliciet uit het thema afgeleid
  (`textTheme.bodyMedium` + `colorScheme.onSurface`) en er wordt níét
  teruggevallen op `DefaultStyles.getInstance(context)` (Flutters rode
  monospace error-fallback, zie SF-1823).

Buiten scope: de AppBar, de opmaakbalk (`_toolbar()`), de laad-, fout- en
inlogschermen, `lib/note_documents_screen.dart`, `lib/note_versions_screen.dart`
(inclusief de rode "oude versie"-weergave), de A−/A+-lettergrootte-logica,
undo/redo en elke andere kleur- of themawijziging.

## Acceptance criteria

1. Het tekstvlak van de editor heeft een donkergrijze achtergrond
   (`notitiesEditorBackground`, standaard `Color(0xFF262626)`), zichtbaar
   afwijkend van het zwart van de AppBar en de opmaakbalk.
2. De achtergrondkleur vult het hele gebied onder de opmaakbalk/`Divider` tot de
   onderkant van het scherm, ook bij een leeg of kort document — niet alleen
   achter de tekstregels.
3. De kleur staat als één genoemde constante in `notities/lib/main.dart` en wordt
   vanuit `notes_editor_screen.dart` gebruikt; er staat geen losse kleurliteral in
   het editorscherm.
4. AppBar, opmaakbalk, documentenlijst, versiegeschiedenis en inlogscherm zijn
   ongewijzigd zwart.
5. De notitietekst is wit/goed leesbaar (geen rode monospace fallback), en
   placeholder, cursor en selectie zijn leesbaar op de nieuwe achtergrond.
6. `notities/test/notes_editor_screen_test.dart` bevat een test die aantoont dat
   het editorvlak een niet-zwarte achtergrondkleur heeft — bijvoorbeeld dat de
   `ColoredBox`/`Container` met `notitiesEditorBackground` boven de `QuillEditor`
   in de widgetboom aanwezig is.
7. In `notities/`: `flutter analyze` zonder issues en `flutter test` volledig
   groen (de bestaande tests blijven slagen).
8. De APK-build (`.github/workflows/notities-apk.yml`) slaagt op `main`.

## Aannames

- Er is geen backend- of API-wijziging nodig; dit is puur een presentatiewijziging.
- Standaardkeuze is `Color(0xFF262626)`; afwijken binnen `0xFF242424`–`0xFF2B2B2B`
  mag zonder nieuwe afstemming.
- De naam van de constante is `notitiesEditorBackground` (vrij te wijzigen mits
  eenduidig en op één plek gedefinieerd).
- Quills eigen placeholder-, cursor- en selectiekleuren blijven ongewijzigd zolang
  ze op `#262626` voldoende leesbaar zijn; alleen bij aantoonbare onleesbaarheid
  wordt er gericht bijgesteld.
- De laad-, fout- en lege toestanden van het scherm (spinner, foutmelding) houden
  de zwarte scaffold-achtergrond; alleen de editor-body krijgt de nieuwe kleur.
- Een APK bouwen is in de factory-sandbox niet mogelijk (geen Android SDK), dus
  lokale verificatie gebeurt met `flutter analyze`, `flutter test` en zo nodig
  `flutter build bundle --release`; criterium 8 wordt bevestigd door de
  bestaande APK-workflow op `main`.

## Eindsamenvatting

## Eindsamenvatting SF-1899 — Notities: donkergrijze achtergrond voor het tekstvlak

**Wat is gebouwd**
In de notities-app liepen AppBar, opmaakbalk en tekstvlak visueel in elkaar over omdat alles zwart was. Het editorvlak heeft nu een eigen, iets lichtere donkergrijze achtergrond (`Color(0xFF262626)`), zodat zichtbaar is waar de menu's ophouden en de notitie begint. Alleen de Flutter-app `notities/` is gewijzigd: `lib/main.dart` (nieuwe top-level constante `notitiesEditorBackground`), `lib/notes_editor_screen.dart` (het `Expanded` met de `QuillEditor` in een `ColoredBox`) en de bijbehorende widgettest.

**Gemaakte keuzes**
- De kleur staat bewust *buiten* `notitiesDarkTheme` als één genoemde constante, zodat documentenlijst, versiegeschiedenis en inlogscherm gegarandeerd zwart blijven; in het editorscherm staat geen kleurliteral.
- De `ColoredBox` zit binnen het `Expanded` en de editor gebruikt `expands: true`, dus het hele vlak onder de opmaakbalk is gekleurd tot onderaan het scherm — ook bij een leeg of kort document.
- Leesbaarheid gecontroleerd, niets bijgesteld: tekst wit, cursor wit, selectie half-transparant wit. Quills grijze placeholder is bewust gedempt gelaten (nog goed zichtbaar).
- De constructie uit SF-1823 (`_baseTextStyle`/`_editorStyles` expliciet uit het thema, géén `DefaultStyles.getInstance`) is ongemoeid, zodat de rode monospace-fallback niet terugkomt.

**Wat is getest**
- `flutter analyze` in `notities/` → geen issues; `flutter test` → 73/73 groen (was 72; één nieuwe test die aantoont dat het editorvlak een niet-zwarte achtergrond heeft die tot onderaan het scherm loopt, bij een leeg document).
- `flutter build bundle --release` geslaagd; backend als vangnet `mvn -o test` → 433 groen (ongewijzigd).
- Visuele bevestiging via twee scratch-screenshots (leeg document en document met tekst): zwarte AppBar/opmaakbalk boven een duidelijk lichter grijs vlak, witte tekst.
- Review en teststap: geen bevindingen.

**Bewust niet gedaan**
- Geen backend-, API-, opslagformaat- of dependencywijziging; AppBar, opmaakbalk, laad-/fout-/inlogschermen, documentenlijst en versiegeschiedenis zijn niet aangeraakt.
- Geen APK-build in de sandbox (geen Android SDK) — acceptatiecriterium 8 wordt bevestigd door de bestaande `notities-apk.yml`-workflow op `main`; de visuele check op een fysiek toestel blijft de laatste handmatige stap.
- Niet-blokkerende suggestie uit de review, bewust niet opgevolgd: `notes_editor_screen.dart` importeert `main.dart` en omgekeerd; een los `lib/theme.dart` zou die cyclus vermijden (Dart staat het toe, analyze is schoon).

<!-- deploy-summary:start -->
Het schrijfvlak in de notities-app heeft nu een iets lichtere donkergrijze achtergrond. Daardoor zie je in één oogopslag waar de knoppen bovenaan ophouden en je notitie begint. De tekst blijft wit en verder verandert er niets aan de app.
<!-- deploy-summary:end -->
