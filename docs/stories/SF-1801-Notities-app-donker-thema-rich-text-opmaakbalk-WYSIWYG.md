# SF-1801 - Notities-app: donker thema + rich text opmaakbalk (WYSIWYG)

## Story

Notities-app: donker thema + rich text opmaakbalk (WYSIWYG)

<!-- refined-by-factory -->

## Samenvatting

De notities-app krijgt een donker uiterlijk: zwarte achtergrond met witte, goed leesbare
letters, ook op het inlogscherm.

Daarnaast kun je je notitie voortaan echt opmaken terwijl je typt. Bovenin verschijnt een
smalle balk met vijf knoppen: vet, cursief, onderstrepen, opsommingstekens en een knop om
opmaak weer weg te halen. Wat je ziet is wat je krijgt.

De notitie wordt onder water nog steeds als gewone tekst opgeslagen, zodat de assistent en
de dagelijkse briefing er net als nu bij kunnen. Tekst die de assistent zelf toevoegt blijft
altijd ongeschonden staan.

Automatisch opslaan, de opslaan-knop en uitloggen blijven werken zoals je gewend bent.

## Scope

Alleen de Flutter-app in `notities/`. Geen backend-wijziging, geen wijziging aan
`GET`/`PUT /api/v1/notes`, `assistant/ai/NotesTools.kt` of
`briefing/WeekTasksSectionProvider.kt`. `notities/lib/api_client.dart` blijft ongewijzigd.

### 1. Donker thema (`notities/lib/main.dart`)
- `MaterialApp.theme` wordt `ThemeData(brightness: Brightness.dark, useMaterial3: true,
  scaffoldBackgroundColor: Colors.black)` met een `ColorScheme.dark`; de huidige
  `colorSchemeSeed: Colors.amber` + `scaffoldBackgroundColor: Colors.yellow` vervallen.
- AppBar donker met witte titel en witte iconen; tekst, cursor, selectie en placeholder
  leesbaar op zwart (placeholder mag grijs).
- `_loginView()`: de `Card`, het `Icons.edit_note`-icoon en de tekst
  'Log in met Google om verder te gaan.' (nu `Colors.black54`) worden leesbaar op donker.
  De teksten zelf en de knoppen/flow van het inloggen veranderen niet.

### 2. Rich-text-editor (`notities/lib/notes_editor_screen.dart`)
- De enkele `TextField` (`expands: true`, `InputBorder.none`) wordt vervangen door een
  WYSIWYG-editor op basis van `flutter_quill` (nieuwe dependency in `notities/pubspec.yaml`).
- Direct onder de AppBar een compacte opmaakbalk met **precies vijf** knoppen: vet, cursief,
  onderstrepen, opsommingslijst en 'opmaak wissen' (verwijdert bold/italic/underline én de
  bullet-opmaak van de selectie). Geen andere opmaakknoppen. Zelfgebouwde knoppenrij óf
  `QuillSimpleToolbar` met alle overige knoppen uitgezet; beide zijn goed.
- Elke knop heeft een `tooltip` (`Vet`, `Cursief`, `Onderstreept`, `Opsomming`,
  `Opmaak wissen`) zodat widget-tests ze via `find.byTooltip(...)` kunnen aanspreken.
- Toolbar leesbaar op zwart: witte iconen, zichtbaar verschil tussen actieve en inactieve
  staat.
- De placeholder 'Typ hier je notities…' blijft bestaan als Quill-placeholder.

### 3. Opslagformaat blijft platte markdown-tekst
- De notitie blijft server-side één platte tekst-string. Quill-Delta-JSON wegschrijven is
  niet toegestaan.
- Nieuwe, losstaande conversiefile `notities/lib/markdown_delta.dart` met
  `markdownToDelta()` (markdown-string → Quill-`Delta`) en `deltaToMarkdown()`
  (Quill-`Delta` → markdown-string), zonder Flutter-widget-afhankelijkheden zodat de
  conversie puur als unit-test te draaien is.
- Mapping (en niets anders): bold = `**tekst**`, italic = `*tekst*`,
  underline = `<u>tekst</u>`, bullet = regel die begint met `- `.
- Alles buiten die mapping (kopjes met `#`, tabellen, links, code, inspringingen, losse
  regels, lege regels) is platte tekst: het wordt letterlijk getoond en letterlijk
  teruggeschreven.
- Roundtrip-garantie: `deltaToMarkdown(markdownToDelta(s)) == s` voor elke notitie die
  alleen uit platte tekst, lege regels en onbekende markup bestaat — byte-identiek, dus
  geen extra spaties, geen escapes, geen toegevoegde of weggegooide (lege) regels, geen
  extra afsluitende newline.

### 4. Bestaand gedrag behouden
- Autosave: 10s debounce na de laatste wijziging (nu gevoed door de Quill-document-listener
  i.p.v. `TextField.onChanged`), plus direct opslaan bij `paused`/`inactive` in
  `didChangeAppLifecycleState` en best-effort bij `dispose`.
- AppBar blijft: statusregel ('Opgeslagen' / 'Opslaan mislukt: …'), handmatige Opslaan-knop
  (force save, ook zonder wijzigingen) en Uitloggen.
- Laad-spinner en de foutmelding bij mislukt laden blijven ongewijzigd.
- Wat er wordt opgeslagen is altijd `deltaToMarkdown(...)` van het huidige document, via de
  bestaande `api.saveNotes(...)`.

## Acceptance criteria

1. Het thema is donker: `MaterialApp.theme` heeft `Brightness.dark` en
   `scaffoldBackgroundColor: Colors.black`; er staat nergens meer een gele/amberkleurige
   achtergrond of seed.
2. Het loginscherm is op donker leesbaar: het icoon en beide teksten hebben geen
   `Colors.black54`/donker-op-donker meer; de bestaande teksten 'Notities' en 'Log in met
   Google om verder te gaan.' zijn onveranderd aanwezig.
3. Het notitiescherm toont een Quill-editor met een opmaakbalk met precies de vijf
   genoemde knoppen (vet, cursief, onderstrepen, opsomming, opmaak wissen), elk met de
   afgesproken tooltip; er zijn geen andere opmaakknoppen zichtbaar.
4. Opmaken werkt WYSIWYG: vet/cursief/onderstreept/bullet toepassen op een selectie is
   direct zichtbaar in de editor.
5. Unit-tests op `markdown_delta.dart` dekken minimaal:
   - bold, italic, underline en bullet elk heen (`markdownToDelta`) en terug
     (`deltaToMarkdown`);
   - gecombineerde/geneste opmaak op dezelfde tekst (bijv. vet + cursief + onderstreept),
     heen en terug;
   - een roundtrip op een notitie met platte tekst, meerdere lege regels en onbekende
     markup (o.a. een `#`-kop) die exact dezelfde string oplevert.
6. Widget-tests dekken minimaal:
   - de vijf toolbar-knoppen bestaan;
   - tekst selecteren + 'vet' + opslaan levert `**tekst**` in de string die naar
     `api.saveNotes(...)` gaat (via het bestaande `_FakeApiClient`-patroon);
   - daarna 'opmaak wissen' op dezelfde selectie + opslaan levert weer de kale tekst.
7. De bestaande tests `notities/test/notes_editor_screen_test.dart` en
   `notities/test/widget_test.dart` zijn aangepast waar ze nog van een kale `TextField`
   uitgaan, en zijn groen. Ze blijven aantonen dat de Opslaan-knop meteen opslaat met
   statusregel 'Opgeslagen', en dat een mislukte save 'Opslaan mislukt: …' toont zonder de
   inhoud te verliezen.
8. Autosave-gedrag is aantoonbaar behouden: na een wijziging wordt binnen de 10s-debounce
   niet opgeslagen en daarna wél, en bij `paused`/`inactive` meteen.
9. `flutter analyze` in `notities/` geeft geen nieuwe warnings; `flutter test` in
   `notities/` is groen.
10. `.github/workflows/notities-apk.yml` blijft ongewijzigd slagen (`flutter pub get`,
    `flutter test`, `flutter build apk --release`) — de nieuwe dependency mag geen extra
    CI-stap of platform-configuratie vereisen.
11. Er is geen wijziging aan de backend, aan `api_client.dart`, of aan het
    request/response-contract van `/api/v1/notes`.

## Aannames

- **Quill-versie**: er wordt de meest recente stabiele `flutter_quill`-major gebruikt die
  met `environment: sdk: ^3.9.0` en de CI-Flutter (`channel: stable`) werkt, met een
  caret-constraint in `pubspec.yaml`. `flutter_quill_extensions` is niet nodig (geen
  afbeeldingen/embeds). Blijkt de gekozen versie extra platform-setup te eisen, dan wordt
  een oudere compatibele major gepakt in plaats van de workflow aan te passen.
- **Localisatie**: `flutter_quill` vereist zijn eigen localizations-delegate; die wordt in
  `MaterialApp` (en in de widget-tests) toegevoegd. Dat is een implementatiedetail, geen
  functionele wijziging.
- **Markdown-grammatica**:
  - inline-opmaak wordt per regel geparseerd; markers lopen niet over regelgrenzen heen;
  - een niet-afgesloten marker (bijv. `**tekst` zonder sluiting) blijft letterlijke tekst;
  - gecombineerde opmaak wordt in een vaste, deterministische nestvolgorde geschreven —
    underline buitenom, dan bold, dan italic: `<u>***tekst***</u>`; de parser leest die
    vorm ook weer in;
  - bullets zijn uitsluitend regels die met exact `- ` beginnen; ingesprongen bullets,
    `* `/`+ ` als bullet-marker en genummerde lijsten worden platte tekst;
  - lege regels blijven lege regels;
  - er wordt niets ge-escaped: een letterlijke `**` in de brontekst die toevallig een paar
    vormt wordt als vet geïnterpreteerd en ook weer als `**` teruggeschreven — de string
    blijft dus identiek, alleen de weergave verandert. Dat is een bewust geaccepteerde
    beperking van de simpele mapping.
- **Trailing newline**: Quill houdt intern altijd een afsluitende newline aan;
  `deltaToMarkdown()` verwijdert die zodat de roundtrip byte-identiek blijft.
- **Alleen Android/APK**: de notities-app wordt niet als web-app gebouwd, dus er is geen
  webspecifieke verificatie nodig.
- De vijf toolbar-knoppen krijgen Nederlandse tooltips (zie Scope); die tooltips zijn de
  afgesproken testhaak.

## Eindsamenvatting

## Eindsamenvatting SF-1801 — Notities-app: donker thema + rich text opmaakbalk (WYSIWYG)

### Wat is gebouwd
Alleen de Flutter-app in `notities/` (9 bestanden gewijzigd; geen backend, geen API-wijziging).

**1. Donker thema** (`lib/main.dart`) — nieuw `notitiesDarkTheme`: `Brightness.dark`, `useMaterial3`, zwarte achtergrond, `ColorScheme.dark`, donkere AppBar met witte titel/iconen, witte cursor/selectie en grijze hint. De amber-seed en gele achtergrond zijn weg. Het inlogscherm is leesbaar gemaakt (donkere kaart, wit icoon, witte titel, `white70`-uitleg); de teksten en de inlogflow zijn ongewijzigd.

**2. WYSIWYG-editor** (`lib/notes_editor_screen.dart`) — de kale `TextField` is vervangen door een `QuillEditor` + `QuillController` (`flutter_quill ^11.5.1`). Direct onder de AppBar een zelfgebouwde opmaakbalk met precies vijf knoppen (tooltips: Vet, Cursief, Onderstreept, Opsomming, Opmaak wissen); actieve staat licht op via accentkleur + gevulde achtergrond. Placeholder 'Typ hier je notities…' blijft.

**3. Opslag blijft platte markdown** (`lib/markdown_delta.dart`, nieuw) — `markdownToDelta()`/`deltaToMarkdown()` zonder widget-afhankelijkheden. Mapping: `**vet**`, `*cursief*`, `<u>onderstreept</u>`, `- ` voor bullets; al het andere (kopjes, tabellen, links, code, inspringing, lege regels) is en blijft letterlijke platte tekst. Er gaat nooit Delta-JSON naar `/api/v1/notes`, dus `NotesTools` en de weektaken-briefing blijven werken.

**4. Bestaand gedrag behouden** — 10s-autosave-debounce (nu op de Quill-document-listener; het initiële laden triggert geen save), directe save bij `paused`/`inactive`, best-effort save in `dispose()`, statusregel, force-save-knop, uitloggen, laadspinner en foutmelding ongewijzigd.

### Belangrijkste keuzes
- **Zelfgebouwde knoppenrij** i.p.v. `QuillSimpleToolbar` — eenvoudiger om exact vijf knoppen met vaste testhaken (tooltips + `ValueKey('opmaakbalk')`) te garanderen.
- **Vaste nestvolgorde bij schrijven** (underline buiten → bold → italic, dus `<u>***x***</u>`), die de parser ook weer inleest; `deltaToMarkdown` schrijft genest per opmaak-run i.p.v. per segment, zodat er geen opgeblazen markers ontstaan.
- **`*`-reeksen worden atomair behandeld** (naar aanleiding van de eerste review): een opener van lengte 1/2/3 sluit alleen op een reeks van exact dezelfde lengte; 4+ is nooit een marker. Lege opmaak-spans (`<u></u>`, `******`) blijven letterlijke tekst.
- **`flutter_quill_extensions` bewust weggelaten** (geen embeds/afbeeldingen nodig), zodat de APK-workflow ongewijzigd blijft.

### Verloop
Twee reviewrondes. De eerste review keurde af op de roundtrip-garantie: een fuzzrun toonde dat losse `*`-tekens naast een `**`-paar de markdown mangelden (`Bereken 2 * 3 en **let op** dit * dat` raakte de `**` kwijt) en dat lege opmaak-spans stil verdwenen. Dat is opgelost in ronde 2 met regressietests erop; de tweede review is akkoord.

### Wat is getest
- `flutter analyze` in `notities/`: **No issues found**.
- `flutter test`: **34/34 groen** — unit-tests op de conversie (bold/italic/underline/bullet heen en terug, gecombineerde opmaak, niet-afgesloten markers, byte-identieke roundtrips, stabiliteit over 4 cycli) en widget-tests (vijf knoppen, selectie+Vet → `**tekst**`, Opmaak wissen → kale tekst, Opsomming → `- melk`, Opslaan met status 'Opgeslagen', mislukte save zonder inhoudsverlies, autosave 9s/11s, save bij `paused`).
- Tester deed een onafhankelijke roundtrip-check met een eigen script (25 gevallen, allemaal byte-identiek) en renderde de UI naar screenshots: zwart loginscherm en zwarte editor met witte tekst, vijf knoppen met zichtbare actieve staat, echte opmaak, `# Kop` blijft plat.
- Reviewer draaide eigen fuzzruns: 20.000 strings met platte tekst + onbekende markup → **0 mismatches**.

### Bewust niet gedaan / aandachtspunten
- **`flutter build apk --release` is niet gedraaid** — geen Android SDK in de sandbox. Afgeleid uit de plugin-eisen dat het goed gaat: de drie nieuwe transitieve Android-plugins eisen `minSdk 24` (= wat de app al gebruikt) en `jvmTarget 17` (= JDK 17 in de workflow). De APK-workflow op `main` is de eerste echte bevestiging.
- **Nestvolgorde-normalisatie is geaccepteerd**: handmatig aangeleverde markdown met bold/italic *buiten* underline (`**<u>x</u>**`) wordt hernest naar `<u>**x**</u>` — semantisch identiek en stabiel vanaf cyclus 2. In zeldzame gevallen (3 op 30.000 fuzz-strings) komt daarbij een letterlijk sterretje naast een marker te staan. Zonder escapen — wat de story verbiedt — niet op te lossen; de app schrijft zelf altijd de canonieke vorm weg.
- **`pubspec.lock` vraagt nu `dart >=3.12.0` / `flutter >=3.44.0`** terwijl `pubspec.yaml` `sdk: ^3.9.0` declareert. Met `channel: stable` in CI prima; een oudere Flutter zou op de lockfile stuklopen.
- Een geplakte afbeelding/embed verdwijnt stil bij het opslaan (correct voor platte-tekst-opslag, maar zonder melding aan de gebruiker) — geen actie binnen deze story.
- Geen wijziging aan backend, `api_client.dart`, `/api/v1/notes` of `.github/workflows/notities-apk.yml`.
