# SF-1767 - Assistent-chat: afbeelding uit klembord plakken

## Story

Assistent-chat: afbeelding uit klembord plakken

<!-- refined-by-factory -->

## Samenvatting

In de Assistent-chat kun je nu een afbeelding die op je klembord staat — meestal een screenshot — rechtstreeks in het typveld plakken. De afbeelding komt dan als bijlage bij je bericht te staan, precies zoals een foto die je uit de galerij kiest, en gaat mee zodra je verstuurt.

Je hoeft dus niet meer eerst de omweg via de galerij te maken. Gewoon tekst plakken blijft ongewijzigd werken. Staat er geen bruikbare afbeelding op het klembord, dan gebeurt er niets vervelends: je ziet hooguit een korte melding onderin.

Dit werkt via het toetsenbord op de telefoon (de plak-knop van Gboard). In de webversie blijft plakken beperkt tot tekst.

## Scope

In scope (uitsluitend frontend, `robberts_assistent/`):

- `lib/assistant_screen.dart`, `_chatControls()`: het bestaande chat-`TextField` krijgt een `contentInsertionConfiguration` (`ContentInsertionConfiguration`) met `allowedMimeTypes: ['image/png', 'image/jpeg']` en een `onContentInserted`-callback die de ingevoegde `KeyboardInsertedContent` als bijlage toevoegt.
- De callback hergebruikt de bestaande `_attach(List<XFile>)`-flow — geen tweede, parallelle bijlagenroute. De ontvangen bytes worden omgezet naar een `XFile` via `XFile.fromData(bytes, name: ..., mimeType: content.mimeType)` (`cross_file` is al transitief aanwezig via `image_picker`), zodat `_pending`, de pending-strook (`_pendingPreview()`) en het verzenden in `_send(...)` ongewijzigd blijven.
- Bestandsnaam voor een geplakte afbeelding wordt client-side gegenereerd (bijv. `geplakt-<epoch-ms>.png` / `.jpg`, afgeleid van de mimetype); `contentType` komt uit `KeyboardInsertedContent.mimeType`.
- Ontbrekende of lege `data`, of een mimetype buiten PNG/JPEG: geen bijlage, geen crash, hooguit één korte `SnackBar` ("Geen afbeelding op het klembord").
- Alle bestaande gedrag van het veld uit SF-1732 blijft intact: `minLines: 1`, `maxLines: 5`, `keyboardType: TextInputType.multiline`, `textInputAction: TextInputAction.newline`, `onSubmitted == null`, `enabled: !_busy`, en de `CrossAxisAlignment.end` van de omliggende `Row`.
- Nieuwe widget-test in `robberts_assistent/test/assistant_screen_test.dart`, in de stijl van de bestaande `_FakeApiClient`-tests.

Buiten scope:

- Elke backend-wijziging. `POST /api/v1/assistant/chat` (multipart met `photos`) ondersteunt dit al; er verandert niets aan API-contract, `assistant`-module of andere apps.
- Een extra klembord-dependency (`super_clipboard`, `pasteboard`, …) en daarmee een expliciete "Plakken uit klembord"-regel in `_showAttachSheet()`. `_showAttachSheet()` blijft ongewijzigd bij 'Foto maken' + 'Uit galerij kiezen'.
- Afbeelding plakken met Ctrl+V in de webversie en op desktop (zie Aannames).
- De praatmodus (`_Mode.voice`), de spraaklus, `_sendTyped()`/`_send(...)` zelf en het `groentetuin`-chatscherm.

## Acceptance criteria

1. Het chat-`TextField` in `_chatControls()` heeft een `contentInsertionConfiguration` met minimaal `image/png` en `image/jpeg` als toegestane mimetypes.
2. Wordt via die callback een `KeyboardInsertedContent` met mimetype `image/png` of `image/jpeg` en niet-lege `data` aangeboden, dan staat er daarna precies één extra bijlage in `_pending`, met die bytes, de aangeboden mimetype als `contentType` en een niet-lege bestandsnaam; de pending-strook toont die bijlage.
3. Na een tik op de send-knop komt die geplakte afbeelding als element in `photos` bij `ApiClient.assistantChat(...)` aan, met dezelfde bytes en contentType; ook zonder ingetypte tekst is versturen mogelijk (bestaand gedrag: leeg bericht mag mits `_pending` niet leeg is).
4. Bij een aanbieding zonder bruikbare afbeeldingsdata verandert `_pending` niet, gooit het scherm geen exception en verschijnt er hooguit één `SnackBar` met een korte Nederlandse melding.
5. Tekst plakken en tekst typen zijn ongewijzigd: Enter maakt een nieuwe regel, versturen gaat alleen via de send-knop, en het veld groeit nog steeds van 1 tot 5 regels (de SF-1732-testasserties op `minLines`/`maxLines`/`keyboardType`/`textInputAction`/`onSubmitted` blijven groen).
6. Nieuwe widget-test in `robberts_assistent/test/` dekt criteria 2, 3 en 4: hij haalt het `TextField` op met `tester.widget<TextField>(...)`, roept `contentInsertionConfiguration!.onContentInserted(...)` rechtstreeks aan met dummy-bytes, en toont via het bestaande `_FakeApiClient`-patroon (`lastPhotos`) aan dat de bijlage bij het verzenden meegaat.
7. `flutter analyze` zonder nieuwe meldingen en `flutter test` volledig groen in `robberts_assistent/`; de APK-build (`.github/workflows/robberts-assistent-apk.yml`) slaagt.
8. `pubspec.yaml` bevat geen nieuwe dependency (`cross_file` mag als expliciete directe dependency worden toegevoegd als de analyzer daarop wijst — verder niets).

## Aannames

- **Alleen de Android-toetsenbordroute.** `ContentInsertionConfiguration` is de door Flutter ondersteunde weg voor rich content vanuit het toetsenbord en werkt in de praktijk op Android (Gboard 'plakken' van een gekopieerde screenshot). Afbeelding plakken met Ctrl+V in Flutter web/desktop vereist een extra klembord-package; dat weegt niet op tegen de dependency-kosten voor een app die primair als APK gebruikt wordt. In de webversie blijft plakken dus tekst-only — dat is bestaand, niet-storend gedrag, geen foutmelding.
- **Geen zichtbare "Plakken uit klembord"-actie** in `_showAttachSheet()`, omdat er zonder klembord-package geen betrouwbare programmatische afbeeldingsuitlezing is; een knop die vrijwel altijd "Geen afbeelding op het klembord" zou tonen is slechter dan geen knop.
- **Geen compressie/verkleining** van geplakte afbeeldingen. Camera en galerij gebruiken `imageQuality: 70` van `image_picker`; bytes uit het klembord worden ongewijzigd doorgegeven. Screenshots zijn klein genoeg voor de bestaande multipart-upload.
- **De widget-test gebruikt geldige minimale PNG-bytes** (bijv. een ingebedde 1×1-PNG), niet willekeurige bytes: de pending-strook rendert de bijlage met `Image.memory`, dat op ongeldige data een decodeerfout in de test veroorzaakt.
- **Dit is bewust niet volledig automatisch te testen.** Het écht plakken via Gboard vereist een fysiek toestel; dat wordt niet nagebouwd of gesimuleerd. De harde acceptatie is: compileert, bestaande tests groen, nieuwe widget-test groen, APK-build slaagt. Eindverificatie gebeurt handmatig op Robberts telefoon (screenshot maken → kopiëren → in de chat plakken → versturen).

## Eindsamenvatting

## Eindsamenvatting SF-1767 — Assistent-chat: afbeelding uit klembord plakken

**Wat is gebouwd**

In de Assistent-chat van `robberts_assistent` kun je nu een afbeelding die op je klembord staat (meestal een screenshot) rechtstreeks in het typveld plakken via de plak-knop van het toetsenbord (Gboard). De afbeelding verschijnt als bijlage in de bestaande pending-strook, precies als een foto uit camera/galerij, en gaat mee zodra je verstuurt — ook zonder ingetypte tekst.

Uitsluitend frontend, drie bestanden geraakt (`+277` regels, geen verwijderingen):
- `robberts_assistent/lib/assistant_screen.dart` — het chat-`TextField` in `_chatControls()` kreeg een `contentInsertionConfiguration` (`allowedMimeTypes: image/png, image/jpeg`) plus de nieuwe callback `_onContentInserted(...)`.
- `robberts_assistent/test/assistant_screen_test.dart` — drie nieuwe widget-tests.
- `docs/stories/worklog/SF-1767-worklog.md` — worklog.

**Gemaakte keuzes**

- **Geen tweede bijlagenroute**: de geplakte bytes worden omgezet naar een `XFile.fromData(...)` en door de bestaande `_attach(List<XFile>)`-flow gevoerd, zodat `_pending`, de pending-strook en `_send(...)` ongewijzigd blijven.
- **`path` én `name` zetten** op de `XFile`: op mobiel negeert `cross_file` de `name`-parameter en leidt de naam uit `path` af — met alleen `name` zou de bestandsnaam op Android leeg zijn. Geverifieerd in de package-broncode door zowel reviewer als tester.
- **Bestandsnaam client-side gegenereerd** (`geplakt-<epoch-ms>.png`/`.jpg`), `contentType` komt uit de aangeboden mimetype.
- **Nette degradatie**: ontbrekende/lege data of een niet-ondersteunde mimetype geeft geen bijlage, geen exception, hooguit één korte SnackBar "Geen afbeelding op het klembord" (vorige melding wordt eerst weggehaald).
- **Geen nieuwe dependency**: `cross_file` was al transitief via `image_picker`; `pubspec.yaml` is niet gewijzigd.
- Alle SF-1732-eigenschappen van het veld (1–5 regels, Enter = nieuwe regel, versturen alleen via de send-knop) zijn ongemoeid gelaten.

**Wat is getest**

- `flutter analyze` → "No issues found!"; `flutter test` → **78/78 groen** (developer, reviewer en tester onafhankelijk gedraaid), inclusief de drie nieuwe plak-tests en de bestaande SF-1732-asserties.
- Backend als regressiecheck: `mvn test` → 388 tests, 0 failures (niet geraakt door deze story).
- Preview-verificatie op PR #47 (Chromium, 390×844): de build bevat de nieuwe code, het invoerveld gedraagt zich ongewijzigd (Enter maakt een regel en stuurt géén chat-request; versturen gaat via de send-knop, meerregelige tekst komt ongewijzigd aan). Geen JS-fouten. Testgesprek daarna opgeruimd.
- Oordeel tester: **geslaagd**, geen blockers.

**Bewust niet gedaan**

- Geen backend-wijziging — `POST /api/v1/assistant/chat` ondersteunde multipart-foto's al.
- Geen afbeelding-plakken met Ctrl+V op web/desktop: dat vereist een extra klembord-package; op web blijft plakken tekst-only (bestaand, niet-storend gedrag).
- Geen zichtbare "Plakken uit klembord"-regel in het bijlagen-sheet, geen compressie/verkleining van geplakte afbeeldingen (screenshots zijn klein genoeg).
- Praatmodus, `groentetuin`-chat en andere apps ongemoeid.

**Nog te doen (handmatig)**

Het écht plakken via Gboard is alleen op een fysiek toestel te testen — Flutter-web stuurt geen rich content. **Eindverificatie op Robberts telefoon**: screenshot maken → kopiëren → in de chat plakken → versturen.

**Niet-blokkerende observatie**: `contentType` gaat ongewijzigd mee terwijl de filter lowercased vergelijkt, dus een IME die `IMAGE/PNG` stuurt levert die hoofdlettervariant als multipart-`Content-Type`. De backend leest de bytes, niet de contentType — praktisch geen effect.
