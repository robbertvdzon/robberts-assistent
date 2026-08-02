# SF-1823 - Notities: tekstkleur herstellen (wit), oude versie juist rood, Terugzetten-knop zichtbaar

## Story

Notities: tekstkleur herstellen (wit), oude versie juist rood, Terugzetten-knop zichtbaar

<!-- refined-by-factory -->

## Samenvatting

In de notities-app zijn drie dingen mis sinds de laatste wijzigingen. De letters in de
editor zijn rood en in een schrijfmachine-lettertype geworden; die moeten weer gewoon
wit en leesbaar zijn, zoals de rest van de app.

Bij het bekijken van een oude versie van een notitie is juist het omgekeerde gewenst:
die tekst wordt rood, zodat direct duidelijk is dat je naar een oude versie kijkt en
niet naar je huidige notitie. Er komt ook een korte rode kop boven met datum en tijd.

Tot slot valt de knop "Terugzetten" onderaan dat scherm op sommige telefoons deels
achter de systeembalk. Die knop wordt voortaan altijd volledig zichtbaar en aantikbaar.

Er verandert niets aan hoe notities worden opgeslagen en er is geen serverwijziging.

## Scope

Alleen frontend, alleen de map `notities/`. Geen backendwijziging, geen wijziging aan
`notities/lib/api_client.dart`, `notities/lib/markdown_delta.dart`, het opslagformaat
(platte markdown via `PUT /api/v1/notes`) of `pubspec.yaml`/`pubspec.lock`
(geen nieuwe dependency).

**1. Editortekst weer wit/leesbaar (`notities/lib/notes_editor_screen.dart`)**

- `_editorStyles(...)` bouwt de `DefaultStyles` voor `QuillEditorConfig.customStyles`
  niet langer op een basis die de Flutter-fallbacktekststijl kan bevatten. De basisstijl
  wordt expliciet uit het thema afgeleid (bijvoorbeeld
  `Theme.of(context).textTheme.bodyMedium` en `colorScheme.onSurface`).
- In de overschreven stijlen (`paragraph`, `lists`, `leading`) worden naast `fontSize`
  ook `color` en `fontFamily` expliciet gezet, zodat de error-fallback (rood
  `0xD0FF0000`, monospace) nooit meer kan doorlekken.
- De gekozen oplossing moet robuust zijn ongeacht vanaf welke `BuildContext` de stijlen
  worden opgebouwd (bijvoorbeeld door de context onder de `Scaffold`/`Material` te
  gebruiken via een `Builder`, of door de themawaarden direct uit te lezen).
- Bestaand gedrag blijft ongewijzigd: de A−/A+-knoppen schalen 12–28 pt in stappen van
  2 pt met standaard 16 pt, de voorkeur blijft onder `notes_editor_font_size` in
  `shared_preferences`, en tekst, lijsttekst én bulletmarkering (`leading`) schalen
  samen mee. Vet/cursief/onderstreept, undo/redo, opmaakbalk, autosave, statusregel en
  de versie-actie blijven functioneel gelijk.

**2. Oude versie juist in rood (`notities/lib/note_versions_screen.dart`)**

- In `NoteVersionDetailScreen` krijgt de getoonde versietekst (`SelectableText`) een
  goed leesbare, lichte roodtint op de zwarte achtergrond (geen donkerrood).
- Boven de tekst komt een kort rood label `Oude versie van <datum tijd>`, met dezelfde
  momentnotatie als de lijst (`formatVersionMoment`).
- De roodtint wordt als één benoemde constante in dit bestand vastgelegd en zowel voor
  het label als de tekst gebruikt, zodat er één plek is om te wijzigen en de kleur
  testbaar is.
- De versielijst (`NoteVersionsScreen`), de laad-/fout-/lege-toestanden en de
  bevestigingsdialoog blijven ongewijzigd.

**3. "Terugzetten"-knop altijd zichtbaar (`notities/lib/note_versions_screen.dart`)**

- Het onderste blok (`Divider` + `Padding` + `FilledButton.icon('Terugzetten')`) wordt
  in een `SafeArea` gewikkeld, zodat de knop bij edge-to-edge/gesture-navigatie
  (Android 15) volledig zichtbaar en aantikbaar blijft.
- De knopkleur heeft voldoende contrast tegen de zwarte achtergrond; het gedrag
  (bevestigingsdialoog → `Navigator.pop(_text)` → terugzetten in de editor via
  `replaceText` met behoud van undo-historie) blijft ongewijzigd.

**Tests (`notities/test/`)**

- Nieuwe widgettests die 1, 2 en 3 aantonen (zie acceptatiecriteria).
- Bestaande tests blijven ongewijzigd groen.

## Acceptance criteria

1. In `notities/`: `flutter test` volledig groen en `flutter analyze` zonder issues.
   `notes_editor_screen_test.dart`, `note_versions_screen_test.dart`,
   `markdown_delta_test.dart` en `widget_test.dart` blijven groen.
2. Een widgettest die de editor rendert binnen `notitiesDarkTheme` (zoals de app dat
   doet) toont aan dat de `customStyles` van de `QuillEditor` voor `paragraph` een
   `color` hebben die gelijk is aan de themakleur (`onSurface`/`bodyMedium`) en
   nadrukkelijk **niet** `0xD0FF0000` is, en dat de `fontFamily` niet de monospace
   error-fallback is.
3. Dezelfde test (of een tweede) toont aan dat dit ook geldt na een druk op A+ /A−:
   de kleur blijft de themakleur, alleen `fontSize` verandert, en `paragraph`, `lists`
   en `leading` schalen samen mee — dus de bestaande lettergrootte-tests blijven
   inhoudelijk gelden.
4. Een widgettest op `NoteVersionDetailScreen` toont aan dat de versietekst met de
   gedefinieerde rode kleur wordt gerenderd en dat het label `Oude versie van …`
   aanwezig is en eveneens rood is.
5. Een widgettest toont aan dat het onderste knopblok met de knop `Terugzetten` zich
   binnen een `SafeArea` bevindt.
6. Het terugzetten werkt functioneel onveranderd: bevestigingsdialoog met `Annuleren` /
   `Ja, terugzetten`, en na bevestigen komt de tekst in de editor terecht via
   `replaceText` op het bestaande document (undo-historie en changes-abonnement
   intact, autosave slaat het als nieuwe versie op).
7. Er gaat geen byte anders naar `PUT /api/v1/notes`: het opslagformaat blijft platte
   markdown en er is geen backend- of API-clientwijziging.
8. Geen nieuwe dependency in `notities/pubspec.yaml`.

## Aannames

- "Wit" wordt niet hardcoded maar uit het thema gehaald (`notitiesDarkTheme` levert op
  zwart feitelijk witte tekst); daarmee blijft de app consistent als het thema ooit
  wijzigt, en dekt criterium 2 hetzelfde probleem.
- De bestaande editortests pumpen een `MaterialApp` zonder expliciet thema (dus het
  lichte Flutter-standaardthema). De nieuwe kleurentest moet daarom expliciet
  `theme: notitiesDarkTheme` meegeven, of asserten tegen de themakleur van de
  gebruikte `MaterialApp` in plaats van tegen letterlijk wit. Bestaande tests hoeven
  hiervoor niet aangepast te worden.
- `note_versions_screen_test.dart` bevat nu alleen unittests op `formatVersionMoment`;
  voor de nieuwe widgettests komt er een fake `ApiClient` bij (`getNoteVersion`),
  in de stijl van `_FakeApiClient` in `notes_editor_screen_test.dart`.
- Concrete roodtint: een lichte rood-tint met goed contrast op zwart (richtwaarde
  `Colors.red.shade300` / `Colors.redAccent.shade100`); de developer mag een andere
  lichte tint kiezen zolang het één benoemde constante is en duidelijk leesbaar op zwart.
- Het rode label boven de versietekst is onderdeel van de scope (niet optioneel), zodat
  het signaal "dit is een oude versie" ook los van de tekstkleur duidelijk is.
- Alleen het onderste knopblok krijgt `SafeArea`; de rest van het scherm (AppBar,
  scrollende tekst) verandert niet van layout.
- Verificatie gebeurt via `flutter test` + `flutter analyze` in `notities/`; een APK
  bouwen is in de sandbox niet mogelijk (geen Android SDK), dus de
  `notities-apk.yml`-workflow op `main` en een handmatige check op toestel zijn de
  laatste bevestiging — dit blokkeert de story niet.

## Eindsamenvatting

## Eindsamenvatting SF-1823 — Notities: tekstkleur herstellen, oude versie rood, Terugzetten-knop zichtbaar

**Wat is gebouwd (alleen `notities/`, geen backend- of API-wijziging)**

1. **Editortekst weer wit en leesbaar.** De oorzaak van de rode schrijfmachinetekst is gevonden en bewezen: de editorstijlen werden opgebouwd op `DefaultStyles.getInstance(context)`, en die erft `DefaultTextStyle` van een context bóven de `Material` — daar staat Flutters error-fallbackstijl (rood `0xD0FF0000`, monospace). Die lekte door naar de hele notitietekst. `_editorStyles()` gebruikt die helper niet meer; een nieuwe `_baseTextStyle(context)` bouwt de basisstijl expliciet uit het thema (`textTheme.bodyMedium` + `colorScheme.onSurface`), met dezelfde spacing/hoogte als Quill zelf hanteert. Gemeten op de gerenderde tekst: vóór de fix rood/monospace, ná de fix wit/Roboto.
2. **Oude versie duidelijk rood.** In het versiedetailscherm staat boven de tekst het label `Oude versie van <datum tijd>` (zelfde momentnotatie als de lijst) en zowel label als versietekst gebruiken één benoemde constante `noteVersionColor = Color(0xFFE57373)` (lichte rood, goed leesbaar op zwart).
3. **"Terugzetten" altijd zichtbaar.** Het onderste knopblok zit nu in een `SafeArea(top: false)`, zodat de knop bij edge-to-edge/gesture-navigatie (Android 15) niet meer deels achter de systeembalk valt.

**Gemaakte keuzes**
- "Wit" is niet hardcoded maar uit het thema afgeleid, zodat de app consistent blijft als het thema wijzigt.
- Geen `Builder` nodig: omdat er geen enkele overgeërfde tekststijl meer meegaat, maakt het niet uit vanaf welke `BuildContext` de stijlen worden opgebouwd — robuuster dan de context verplaatsen.
- De Terugzetten-knop houdt de standaard themakleuren (voldoende contrast op zwart), geen eigen kleuroverride.
- Het versiemoment staat nu tweemaal op het scherm (AppBar-titel + rood label); dat is bewust, zo gevraagd in de story.

**Wat is getest**
- `flutter analyze` in `notities/` → geen issues; `flutter test` → **57/57 groen** (was 50), inclusief zeven nieuwe tests: themakleur van `paragraph`/`lists`/`leading`, een render-tree-test die op de oude code aantoonbaar faalde, meeschalen bij A+/A− (alleen `fontSize` verandert), rode versietekst + label, lege versie, de `SafeArea` rond het knopblok en de bevestigingsdialoog met teruggave van de tekst.
- Visuele controle via gerenderde screenshots: witte editortekst op zwart; rood label + rode versietekst met volledig zichtbare Terugzetten-knop.
- Backend `mvn test` ter controle gedraaid (geen backendwijziging in deze story).
- Alle acceptatiecriteria 1 t/m 8 zijn door reviewer en tester afgevinkt; geen bevindingen.

**Bewust niet gedaan**
- Geen wijziging aan `api_client.dart`, `markdown_delta.dart`, het opslagformaat (platte markdown via `PUT /api/v1/notes`) of `pubspec.yaml`/`pubspec.lock` — geen nieuwe dependency.
- Twee cosmetische reviewsuggesties niet doorgevoerd (horizontale insets van de `SafeArea` uitzetten; één `Column`-niveau minder) — geen functioneel effect.
- Geen APK-build in de sandbox (geen Android SDK op arm64). De `notities-apk.yml`-workflow op `main` plus één handmatige check op toestel zijn de laatste bevestiging, vooral voor punt 3: dat de knop achter de systeembalk vandaan blijft is alleen op een echt toestel met gesture-navigatie visueel te zien. Dit blokkeert de story niet.

*(Opmerking: de rolinstructies in `.task.md` noemen `{"phase":"summary-finished"}`, het factory-contract in mijn opdracht `{"phase":"summarized"}`; ik volg het laatste.)*
