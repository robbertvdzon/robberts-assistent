# SF-1076 - Worklog

Story-context bij eerste pickup:
Save-knop toevoegen aan notities-editor

Voeg in notities/lib/notes_editor_screen.dart een 'Opslaan'-IconButton toe aan de AppBar.actions, naast de bestaande statustekst en uitlog-knop. Bij indrukken: annuleer de lopende debounce-Timer en roep direct de bestaande _save()-flow aan (hergebruik dezelfde statusweergave 'Opgeslagen' / 'Opslaan mislukt: ...'). Voeg een 'bezig met opslaan'-vlag toe zodat de knop tijdens het opslaan kort uitgeschakeld is en dubbelklikken geen dubbele requests veroorzaakt. Laat de bestaande auto-save-mechanismen (10s debounce, save bij AppLifecycleState.paused/inactive, save bij dispose()) ongewijzigd werken. Breid notities/test/widget_test.dart uit (of voeg een nieuw testbestand toe) met een test die het indrukken van de save-knop dekt (saveNotes wordt aangeroepen, status 'Opgeslagen' verschijnt), met een fake/mock ApiClient. Zorg dat bestaande tests blijven slagen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- SF-1078: "Opslaan"-IconButton toegevoegd aan de AppBar van `NotesEditorScreen`
  (`notities/lib/notes_editor_screen.dart`), naast de bestaande statustekst en
  uitlog-knop.
- `_save()` kreeg een `force`-parameter: bij handmatig opslaan wordt de lopende
  debounce-`Timer` geannuleerd en direct opgeslagen, ook als er sinds de
  laatste (auto-)save niets gewijzigd is (`_dirty == false`). De bestaande
  statusweergave ("Opgeslagen" / "Opslaan mislukt: ...") wordt hergebruikt.
- Nieuwe `_saving`-vlag: de knop is uitgeschakeld en toont een kleine
  laadindicator tijdens het opslaan, zodat dubbelklikken geen dubbele
  requests veroorzaakt.
- Bestaande auto-save-mechanismen (10s debounce, save bij
  `AppLifecycleState.paused/inactive`, best-effort save bij `dispose()`)
  ongewijzigd gelaten.
- Nieuw testbestand `notities/test/notes_editor_screen_test.dart` met een
  fake `ApiClient`-subclass: dekt (1) dat de save-knop meteen opslaat zonder
  op de debounce te wachten en de status "Opgeslagen" toont, en (2) dat een
  mislukte save de status "Opslaan mislukt: ..." toont.
- `flutter pub get`, `flutter test` en `flutter analyze` lokaal gedraaid
  vanuit `notities/` (flutter-SDK bleek in deze sandbox wél beschikbaar,
  ondanks de aantekening in `development.md` over de tester/reviewer-
  sandbox): alle 3 tests slagen, `flutter analyze` geeft geen issues.
  `pubspec.lock`-wijzigingen van `pub get` (onafhankelijke transitieve
  dependency-upgrades, niet aangevraagd door deze story) teruggedraaid.
