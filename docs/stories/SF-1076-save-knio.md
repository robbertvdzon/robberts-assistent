# SF-1076 - save knio

## Story

save knio

<!-- refined-by-factory -->

## Scope
In de notities-app (`notities/`) een expliciete "Opslaan"-knop toevoegen aan de editor, zodat de gebruiker de huidige notitie handmatig direct kan opslaan, zonder te wachten op de bestaande auto-save (10s debounce / bij app-pauze / bij sluiten).

De knop komt in de `AppBar` van `NotesEditorScreen` (`notities/lib/notes_editor_screen.dart`), naast de bestaande status-tekst en uitlog-knop. Bij indrukken wordt de bestaande `_save()`-flow direct aangeroepen (ongeacht de debounce-timer), met dezelfde statusweergave ("Opgeslagen" / "Opslaan mislukt: ...") als nu al bij auto-save gebeurt.

## Acceptance criteria
- In de notitie-editor is een zichtbare "Opslaan"-knop (icoon en/of tekst) aanwezig in de AppBar.
- Bij indrukken van de knop wordt de huidige inhoud van het tekstvak meteen opgeslagen via `ApiClient.saveNotes`, ook als de auto-save debounce-timer nog loopt (de lopende timer wordt geannuleerd/overgenomen zodat er niet dubbel wordt opgeslagen).
- Na een geslaagde handmatige save toont de bestaande statusindicator "Opgeslagen"; bij een fout toont deze "Opslaan mislukt: ...", net als bij de huidige auto-save.
- De knop is tijdens het opslaan (kort) uitgeschakeld of toont een laadstatus, zodat dubbelklikken geen dubbele requests veroorzaakt.
- De bestaande auto-save-mechanismen (debounce na 10s, opslaan bij pauzeren/sluiten van de app) blijven ongewijzigd werken.
- Bestaande tests (`notities/test/widget_test.dart`) blijven slagen; indien relevant wordt een test toegevoegd die het indrukken van de save-knop dekt.

## Aannames
- "Alles opslaan" betekent: de huidige inhoud van de ene notitie-tekstvak (er is maar één notitie-document per gebruiker in deze app), niet meerdere afzonderlijke notities.
- De knop is puur een expliciete trigger bovenop de bestaande opslaglogica; er wordt geen nieuw opslagmechanisme of API-endpoint toegevoegd (de bestaande `PUT /api/v1/notes` volstaat).
- Geen aparte "onopgeslagen wijzigingen"-indicator wordt gevraagd; de bestaande statustekst in de AppBar is voldoende feedback.
- Geen keyboard-shortcut (bv. Ctrl+S) wordt gevraagd, alleen een klikbare knop; dit kan later als aparte wens worden toegevoegd indien gewenst.

## Eindsamenvatting

Ik heb genoeg context. De eindsamenvatting:

## Eindsamenvatting SF-1076 — Save-knop in de notities-editor

**Gebouwd:**
In de notities-app is een expliciete "Opslaan"-knop toegevoegd aan de `AppBar` van `NotesEditorScreen` (`notities/lib/notes_editor_screen.dart`), naast de bestaande statustekst en uitlog-knop. Bij indrukken wordt de bestaande opslaglogica direct aangeroepen — ongeacht de lopende 10s-debounce-timer en ongeacht of er iets gewijzigd is sinds de laatste save.

**Keuzes:**
- `_save()` kreeg een `force`-parameter: bij handmatig opslaan wordt de debounce-timer geannuleerd en wordt altijd opgeslagen, ook als `_dirty == false` (dus ook als er niets gewijzigd is).
- Een nieuwe `_saving`-vlag schakelt de knop tijdens het opslaan uit en toont een kleine laadindicator, zodat dubbelklikken geen dubbele requests veroorzaakt.
- Dezelfde bestaande statusweergave ("Opgeslagen" / "Opslaan mislukt: ...") wordt hergebruikt — geen nieuwe UI-elementen of API-endpoints.
- De bestaande auto-save-mechanismen (10s debounce, save bij app-pauze/inactief, best-effort save bij sluiten) zijn ongewijzigd gelaten.

**Getest:**
- Nieuw testbestand `notities/test/notes_editor_screen_test.dart` met een fake `ApiClient`: dekt zowel het succespad (knop slaat meteen op, status "Opgeslagen" verschijnt) als het foutpad (status "Opslaan mislukt: ...").
- Tester heeft dit keer een werkende Flutter-sandbox gehad en een volledige testrun gedraaid: `flutter test` (3/3 tests groen), `flutter analyze` (geen issues). Reviewer kon dit niet zelf draaien en heeft op basis van grondige handmatige code-review goedgekeurd, conform de vaste afspraak in dit project.
- Onafhankelijke `pubspec.lock`-wijzigingen van `flutter pub get` (transitieve dependency-upgrades, niet aangevraagd) zijn na iedere testrun teruggedraaid.

**Bewust niet gedaan:**
- Geen keyboard-shortcut (Ctrl+S) — alleen een klikbare knop, zoals in de scope afgesproken.
- Geen aparte "onopgeslagen wijzigingen"-indicator — de bestaande statustekst volstaat.
- Geen preview/screenshot, aangezien `notities` een APK-only app is zonder web-preview.

Geen openstaande vragen of blockers.
