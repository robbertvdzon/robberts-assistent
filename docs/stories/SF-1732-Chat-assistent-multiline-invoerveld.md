# SF-1732 - Chat-assistent: multiline invoerveld

## Story

Chat-assistent: multiline invoerveld

<!-- refined-by-factory -->

## Samenvatting

In de Assistent-chat is het invoerveld nu maar één regel hoog. Typ je een langere
vraag, dan zie je slechts een klein stukje van je eigen tekst en kun je niet
overzien wat je hebt geschreven.

Na deze wijziging groeit het invoerveld mee met je tekst, tot ongeveer vijf regels.
Enter maakt een nieuwe regel in plaats van het bericht meteen te versturen;
versturen doe je met de bestaande verzendknop rechts. De knoppen blijven netjes
onderaan staan terwijl het veld groeit.

## Scope

Uitsluitend het chat-invoerveld in `robberts_assistent/lib/assistant_screen.dart`
(`_chatControls()`, de `TextField` met `hintText: 'Typ een vraag…'`) plus een
widget-test in `robberts_assistent/test/`.

In scope:
- `TextField` multiline maken: `minLines: 1`, `maxLines: 5`,
  `keyboardType: TextInputType.multiline`, `textInputAction: TextInputAction.newline`.
  Voorbij 5 regels scrollt het veld intern (standaardgedrag van `TextField`).
- `onSubmitted: (_) => _sendTyped()` verwijderen van dit veld, zodat Enter een
  nieuwe regel invoegt in plaats van te versturen.
- De omliggende `Row` uitlijnen met `crossAxisAlignment: CrossAxisAlignment.end`,
  zodat de foto-knop (links) en de send-knop (rechts) onderaan blijven staan als
  het veld groeit.
- Een widget-test die (a) aantoont dat het veld multiline is (`minLines`/`maxLines`
  op de `TextField`) en (b) dat een tekst met newlines via een tik op de send-knop
  ongewijzigd (afgezien van `trim()`) bij `ApiClient.assistantChat(message: ...)`
  aankomt, via het bestaande `_FakeApiClient`-patroon in
  `test/assistant_screen_test.dart`.

Buiten scope:
- De spraakmodus (`_Mode.voice`), de spraaklus, `_startVoiceLoop`/`_stopListening`,
  TTS en alles wat `voice: true` meestuurt.
- De logica van `_sendTyped()`/`_send(...)` zelf, de `_busy`-afhandeling, foto-
  bijlagen en de `_pending`-flow.
- Backend, API-contract, andere schermen en andere apps.
- Overige styling/thema van het invoerveld.

## Acceptance criteria

1. Het chat-invoerveld start op één regel hoog en groeit mee met de ingetypte
   tekst tot maximaal 5 regels; daarna scrollt de inhoud binnen het veld en groeit
   het veld niet verder.
2. Een Enter/return in het invoerveld voegt een nieuwe regel toe en verstuurt het
   bericht niet.
3. De send-knop rechts verstuurt het bericht zoals voorheen, inclusief de
   `_busy`-state (uitgeschakeld terwijl er een antwoord onderweg is) en het
   bestaande gedrag van `_sendTyped()` (leegmaken van het veld, verstuurbaar met
   alleen bijlagen).
4. Meerregelige tekst wordt met behoud van de newlines verstuurd; alleen leading/
   trailing whitespace wordt weggetrimd zoals nu.
5. Terwijl het veld groeit blijven de foto-knop en de send-knop onderaan uitgelijnd
   en schuiven ze niet verticaal mee naar het midden.
6. De spraakmodus is functioneel ongewijzigd.
7. `flutter analyze` en `flutter test` in `robberts_assistent/` zijn groen,
   inclusief alle bestaande tests.
8. Er is een nieuwe widget-test in `robberts_assistent/test/` die het multiline-
   karakter van het veld aantoont (`minLines`/`maxLines`) én aantoont dat tekst met
   newlines correct via de send-knop wordt verstuurd.

## Aannames

- `onSubmitted` wordt van dit veld verwijderd (niet behouden): met
  `TextInputAction.newline` zou het toch niet meer voor Enter afgaan, en het laten
  staan zou alleen verwarring geven.
- Er komt geen sneltoets-alternatief (zoals Ctrl+Enter of Shift+Enter) om te
  versturen; de send-knop is de enige verstuurweg. Dit is niet gevraagd in de story.
- Er wordt gekozen voor `minLines: 1, maxLines: 5` in plaats van
  `maxLines: null` + `ConstrainedBox`; dat is de eenvoudigste variant en geeft
  hetzelfde gedrag inclusief intern scrollen.
- De widget-test leest `minLines`/`maxLines` af via `tester.widget<TextField>(...)`
  in plaats van via een pixel-/hoogtemeting, omdat de exacte gerenderde hoogte
  afhangt van het thema en fragiel is.
- Er is geen extra injectie-seam nodig: de test gebruikt de chatmodus (de
  standaardmodus) en het bestaande `_FakeApiClient`-patroon; spraak/TTS blijven
  buiten de test.
- De verzendkant van de backend blijft ongewijzigd — meerregelige berichten worden
  al ondersteund door het bestaande multipart-`message`-veld.

## Eindsamenvatting

## Eindsamenvatting SF-1732 — Chat-assistent: multiline invoerveld

### Wat is gebouwd
Het invoerveld van de Assistent-chat in `robberts_assistent` is niet langer één regel hoog. Het start op één regel, groeit mee met de getypte tekst tot maximaal vijf regels en scrollt daarna intern verder. Enter voegt nu een nieuwe regel in plaats van het bericht meteen te versturen; versturen gaat via de bestaande verzendknop rechts. De foto-knop en de send-knop blijven onderaan uitgelijnd terwijl het veld groeit.

De wijziging beslaat drie bestanden en negen regels productiecode:
- `robberts_assistent/lib/assistant_screen.dart` (`_chatControls()`): `minLines: 1`, `maxLines: 5`, `keyboardType: TextInputType.multiline`, `textInputAction: TextInputAction.newline`; `onSubmitted` verwijderd; `crossAxisAlignment: CrossAxisAlignment.end` op de omliggende `Row`.
- `robberts_assistent/test/assistant_screen_test.dart`: nieuwe widget-test.
- `docs/stories/worklog/SF-1732-worklog.md`.

### Gemaakte keuzes
- **`minLines: 1` + `maxLines: 5`** in plaats van `maxLines: null` met een `ConstrainedBox` — eenvoudigste variant, met hetzelfde intern-scrollen-gedrag.
- **Geen sneltoets-alternatief** (Ctrl/Shift+Enter) om te versturen; de send-knop is bewust de enige verstuurweg, zoals in de story afgesproken.
- **`onSubmitted` volledig weggehaald** in plaats van laten staan: met `TextInputAction.newline` zou het toch niet meer afgaan en het zou alleen verwarring geven.
- **Geen backend- of API-wijziging nodig**: `_sendTyped()` doet alleen `trim()`, dus interne newlines gaan vanzelf ongewijzigd mee in het bestaande multipart-`message`-veld.
- De **widget-test leest `minLines`/`maxLines` via `tester.widget<TextField>(...)`** in plaats van een pixel-/hoogtemeting, omdat de gerenderde hoogte themagevoelig en fragiel is.

### Wat is getest
- Lokaal in `robberts_assistent/`: `flutter analyze` → geen issues; `flutter test` → **75 tests groen**, inclusief alle bestaande spraakmodus-tests. Zowel developer, reviewer als tester hebben dit zelf gedraaid.
- Nieuwe widget-test dekt zowel het multiline-karakter van het veld (`minLines`/`maxLines`/`keyboardType`/`textInputAction`/`onSubmitted == null`) als het versturen van meerregelige tekst via de send-knop.
- Live geverifieerd op preview-omgeving `robberts-assistent-pr-46` (PR-head = branch-HEAD): veld groeit tot 5 regels en scrollt daarna (screenshots); vier keer Enter leverde **geen enkele** request naar `/assistant/chat`; de send-knop verstuurt en leegt het veld; de onderschepte multipart-body bevatte `regel een\nregel twee\n\nregel vier` — leading/trailing spaties weg, interne newlines én lege regel intact; knoppen bleven onderaan uitgelijnd. Testdata (het aangemaakte previewgesprek) is opgeruimd.
- Review van de volledige story-diff: geen scope-overschrijding, geen bevindingen.

### Bewust niet gedaan
- Geen sneltoets om met het toetsenbord te versturen (Ctrl/Shift+Enter) — niet gevraagd in de story.
- De **spraakmodus** (`_Mode.voice`, spraaklus, TTS, `voice: true`) is niet aangeraakt en functioneel ongewijzigd.
- `_sendTyped()`/`_send(...)`, de `_busy`-afhandeling en de bijlagen-/`_pending`-flow zijn niet gewijzigd.
- Geen wijziging aan backend, API-contract, andere schermen, andere apps of overige styling van het invoerveld.
- Geen aanvullende styling/thema-aanpassing van het gegroeide veld.

### Restrisico
Klein: de wijziging is negen regels UI-code, volledig gedekt door unit- en live preview-tests. De enige gedragsverandering voor de gebruiker is dat Enter niet meer verstuurt — dat is expliciet gewenst gedrag uit deze story, maar het is wel even wennen voor wie gewend was met Enter te versturen.
