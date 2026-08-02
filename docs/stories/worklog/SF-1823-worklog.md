# SF-1823 - Worklog

Story-context bij eerste pickup:
Notities: editortekst themakleur, versiedetail rood, Terugzetten in SafeArea

Alleen notities/ (frontend), geen backend/API/opslagformaat-wijziging en geen nieuwe dependency.

1) notities/lib/notes_editor_screen.dart - _editorStyles(context) bouwt de DefaultStyles voor
QuillEditorConfig.customStyles niet langer op een basis die Flutters error-fallback (rood
0xD0FF0000, monospace) kan bevatten.

2) notities/lib/note_versions_screen.dart - NoteVersionDetailScreen toont de versietekst en een
label 'Oude versie van <datum tijd>' in een lichte roodtint (benoemde constante).

3) notities/lib/note_versions_screen.dart - het onderste knopblok (Divider + Padding +
FilledButton 'Terugzetten') in een SafeArea.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

## Wat is er gedaan en waarom

### 1. Editortekst weer wit/leesbaar (`notities/lib/notes_editor_screen.dart`)

**Oorzaak, bevestigd met een render-tree-test:** `_editorStyles()` bouwde de overschrijvingen op
`DefaultStyles.getInstance(context)`. Die leest `DefaultTextStyle.of(context)`, en de `context`
van de `State` zit *boven* de `Material` van het `Scaffold`. Daar staat in debug-builds Flutters
error-fallbackstijl (`Color(0xD0FF0000)`, `monospace`). Die stijl werd zo als basis
gekopieerd naar `paragraph`/`lists`/`leading` en daarmee de documentbrede tekststijl. Gemeten op
de gerenderde `RenderParagraph`s vóór de fix: `color 0xD0FF0000`, `fontFamily monospace`; na de
fix: `Colors.white`, `Roboto`.

**Oplossing:** `_editorStyles()` gebruikt `DefaultStyles.getInstance` helemaal niet meer. Nieuwe
helper `_baseTextStyle(context)` bouwt de basisstijl expliciet uit het thema
(`textTheme.bodyMedium` voor fontFamily/-fallback/gewicht/letterSpacing, `colorScheme.onSurface`
voor de kleur) en zet `fontSize`, `height: 1.15` en `decoration: TextDecoration.none` — dezelfde
waarden die Quill zelf voor zijn basisstijl gebruikt. `paragraph`, `lists` en `leading` worden
daarmee opgebouwd met dezelfde spacing-constanten als Quill (`HorizontalSpacing(0,0)`;
lists: `VerticalSpacing(6,0)` / `VerticalSpacing(0,6)`). Omdat er geen enkele inherited
tekststijl meer in meegaat, maakt het niet meer uit vanaf welke `BuildContext` de stijlen worden
opgebouwd — een `Builder` was daardoor niet nodig. De overige Quill-stijlen (h1..h6, quote, code,
placeholder) komen ongewijzigd uit Quill zelf; die worden intern, ónder de `Material`, met deze
drie overschrijvingen samengevoegd en waren dus al correct.

Ongewijzigd: A−/A+ (12–28 pt, stappen van 2, standaard 16), de voorkeur onder
`notes_editor_font_size` in `shared_preferences`, het samen meeschalen van tekst, lijsttekst en
bulletmarkering, en verder opmaakknoppen, undo/redo, autosave, statusregel en de Versies-actie.

### 2. Oude versie in rood (`notities/lib/note_versions_screen.dart`)

Nieuwe top-level constante `noteVersionColor = Color(0xFFE57373)` (`Colors.red.shade300`; als
letterlijke `Color` zodat 'ie `const` kan zijn) — één plek om te wijzigen en direct testbaar.
`NoteVersionDetailScreen` toont boven de tekst `Oude versie van <formatVersionMoment(...)>` in die
kleur (semi-bold) en geeft de `SelectableText` met de versietekst dezelfde kleur. Versielijst,
laad-/fout-/lege-toestanden en de bevestigingsdialoog zijn ongewijzigd.

### 3. "Terugzetten" altijd zichtbaar

Het onderste blok (`Divider` + `Padding` + `FilledButton.icon`) zit nu in een
`SafeArea(top: false)`, zodat de knop bij edge-to-edge/gesture-navigatie (Android 15) niet meer
deels achter de systeembalk valt. Alleen dit blok; AppBar en scrollende tekst behouden hun layout.
De knop houdt de standaard `FilledButton`-kleuren van het thema (`colorScheme.primary`, een licht
paars, op `onPrimary` donker) — ruim voldoende contrast op zwart, dus geen eigen kleuroverride.
Het terugzetgedrag (dialoog → `Navigator.pop(_text)` → `replaceText` op het bestaande document)
is niet aangeraakt.

### Tests

`notities/test/notes_editor_screen_test.dart`: `_app`/`_pumpLoaded` kregen een optionele
`theme`-parameter (bestaande tests ongewijzigd). Drie nieuwe tests:
- de `customStyles` van `paragraph`/`lists`/`leading` hebben binnen `notitiesDarkTheme` de
  themakleur (`onSurface`, hier wit), niet `0xD0FF0000` en niet `monospace`; ook de getekende
  bulletmarkering volgt die kleur;
- een end-to-end variant die de `RenderParagraph`s ín de `QuillEditor` afloopt en controleert dat
  de daadwerkelijk gerenderde tekststijl wit/niet-monospace/16 pt is (deze test faalde op de code
  van vóór de fix — daarmee is de diagnose hierboven bewezen);
- na A+ en tweemaal A− verandert alleen `fontSize` (18 resp. 14) en blijft de kleur de themakleur,
  voor alle drie de onderdelen samen.

`notities/test/note_versions_screen_test.dart`: fake `ApiClient` met `getNoteVersion` plus een
helper die het detailscherm als eigen route pusht (zodat `Navigator.pop` een echte route heeft).
Vier nieuwe widgettests: tekst + label in `noteVersionColor`, lege versie ('(lege notitie)') ook
rood, het knopblok met 'Terugzetten' zit in een `SafeArea` met `top == false`, en de
bevestigingsdialoog geeft na 'Ja, terugzetten' de tekst terug via `Navigator.pop`.

### Verificatie

- `notities/`: `flutter analyze` → "No issues found!"; `flutter test` → **57/57 groen**
  (was 50 vóór deze story). `markdown_delta_test.dart` en `widget_test.dart` ongemoeid en groen.
- Backend `mvn -o test` gedraaid ter controle dat het vangnet buiten `notities/` groen blijft
  (geen backendwijziging in deze story).
- Geen wijziging aan `api_client.dart`, `markdown_delta.dart`, het opslagformaat (platte markdown
  via `PUT /api/v1/notes`), `pubspec.yaml` of `pubspec.lock`.
- Een APK bouwen kan niet in deze sandbox (geen Android SDK, arm64), dus de `notities-apk.yml`-
  workflow op `main` en een handmatige check op toestel zijn de laatste bevestiging — met name
  voor punt 3, dat alleen op een toestel met gesture-navigatie echt zichtbaar is.

## Review (SF-1824, reviewer)

Volledige story-diff t.o.v. `main` beoordeeld (`notities/lib/notes_editor_screen.dart`,
`notities/lib/note_versions_screen.dart` + beide testbestanden).

Zelf geverifieerd in deze sandbox (flutter 3.44 is hier wél aanwezig):
`flutter analyze` → "No issues found!", `flutter test` → **57/57 groen**.

Diagnose tegengelezen in de packagebronnen:
- `_errorTextStyle` (`Color(0xD0FF0000)`, `monospace`) staat in
  `flutter/lib/src/material/app.dart` en is de `DefaultTextStyle` van `MaterialApp` — dus
  **niet debug-only**; dat verklaart het symptoom ook in de release-APK.
- `DefaultStyles.getInstance(context)` (flutter_quill 11.5.1) bouwt zijn `baseStyle` inderdaad
  uit `DefaultTextStyle.of(context)`.
- De nieuwe `paragraph`/`lists`/`leading` gebruiken exact dezelfde spacing als Quills eigen
  defaults (`HorizontalSpacing(0,0)`, lists `VerticalSpacing(6,0)`/`VerticalSpacing(0,6)`, rest
  `VerticalSpacing.zero`) en dezelfde `height: 1.15` / `decoration: none` — geen layoutregressie.
- `QuillRawEditorState.didChangeDependencies` doet `DefaultStyles.getInstance(context).merge(
  customStyles)` ónder de `Material`, dus h1..h6/quote/code/placeholder blijven correct.

Alle acceptatiecriteria 1 t/m 8 afgevinkt; geen scope-overschrijding (geen wijziging aan
`api_client.dart`, `markdown_delta.dart`, opslagformaat, `pubspec.*` of de backend).

Niet-blokkerende opmerkingen:
- [info] Het detailscherm toont het versiemoment nu twee keer (AppBar-titel + het nieuwe rode
  label). Dat is expliciet zo gevraagd in de story.
- [suggestie] `SafeArea(top: false)` laat `left`/`right` op `true`; in landschap met een notch
  krijgt het knopblok daardoor ook horizontale inset. Onschadelijk, eventueel `left: false,
  right: false` als het exact het oude gedrag moet houden.
- [suggestie] De `Column(mainAxisSize: .min)` binnen de `SafeArea` kan een `Column`-niveau
  minder als `Divider` en `Padding` direct in de buitenste `Column` blijven en alleen de
  `Padding` in de `SafeArea` zit. Puur cosmetisch.
- [info] Punt 3 (systeembalk bij gesture-navigatie) is alleen op een echt toestel visueel te
  bevestigen; de widgettest dekt de aanwezigheid van de `SafeArea`.

## Testronde (SF-1825)

Uitgevoerd in `notities/` met de Flutter-SDK in de sandbox (Flutter 3.44.7 / Dart 3.12.2):

- `flutter analyze` → **No issues found!** (AC1)
- `flutter test` → **57/57 groen**, incl. de bestaande `markdown_delta_test.dart`,
  `widget_test.dart`, `notes_editor_screen_test.dart`, `note_versions_screen_test.dart` en
  de vier nieuwe kleur-/SafeArea-tests (AC1–AC6).
- Visueel bewijs via een tijdelijke render-test buiten de repo (`/tmp`, PNG's in
  `/work/screenshots`): `SF-1823-editor-tekst.png` toont witte editortekst (bullets +
  gewone regel) op zwart, `SF-1823-versiedetail-rood.png` toont het rode label
  `Oude versie van …` + de rode versietekst en de volledig zichtbare `Terugzetten`-knop
  onderaan. (Testfonts renderen als Ahem-blokjes; kleur/layout zijn wél representatief.)
- Codecontrole tegen de flutter_quill 11.5.1-bron: de gekozen spacings
  (`HorizontalSpacing(0,0)`, lists `VerticalSpacing(6,0)`/`(0,6)`, rest zero) en
  `height: 1.15` komen exact overeen met Quills eigen `DefaultStyles`, dus geen
  layoutregressie; `QuillRawEditorState.didChangeDependencies` merget de custom styles
  ónder de `Material`, dus overige bloktypes (placeholder/quote/code) blijven ongemoeid.
- Scope (AC7/AC8): `git diff main...HEAD` raakt alleen `notities/lib/notes_editor_screen.dart`,
  `notities/lib/note_versions_screen.dart`, twee testbestanden en dit worklog — geen backend,
  geen `api_client.dart`/`markdown_delta.dart`, geen `pubspec.yaml`/`pubspec.lock`.

Geen bevindingen. Niet automatisch verifieerbaar (zoals in de story voorzien): het echte
gedrag achter de systeembalk bij gesture-navigatie op een fysiek Android-toestel.
