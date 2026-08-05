# SF-1967 - Worklog

Story-context bij eerste pickup:
Hotfix uitvoeren

De achtergrondkleur van de tekst in de notities-app is `0xFF262626` en moet
`#404040` worden. Geen refine/plan/review/test/documentatie-stap: dit is een hotfix.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `notities/lib/main.dart`: de top-level constante `notitiesEditorBackground` van
  `Color(0xFF262626)` naar `Color(0xFF404040)` gezet. Dat is de enige plek waar de
  kleur staat — `notes_editor_screen.dart` importeert 'm (`ColoredBox` met
  `ValueKey('editorachtergrond')` rond de `QuillEditor`), dus één regel volstaat en
  het thema (AppBar, opmaakbalk, documentenlijst, versiegeschiedenis, inlogscherm)
  blijft ongewijzigd zwart.
- `notities/test/notes_editor_screen_test.dart`: aan de bestaande achtergrondtest een
  expliciete assertie `expect(notitiesEditorBackground, const Color(0xFF404040))`
  toegevoegd, zodat de gevraagde waarde vastligt en een toekomstige onbedoelde
  wijziging rood wordt. De bestaande asserties (niet zwart, vlak loopt van onder de
  opmaakbalk tot de onderkant van het `Scaffold`) blijven staan.
- Docs bijgewerkt waar de kleur als huidige waarde beschreven stond:
  `notities/README.md`, `docs/factory/technical-spec.md` (incl. de contrastregel die
  naar de kleur verwijst) en `CLAUDE.md` §6. De historische SF-1899-entry in
  `CLAUDE.md` §9 is bewust ongemoeid gelaten — die beschrijft wat die story destijds
  deed.
- Leesbaarheid: `#404040` is nóg lichter dan `#262626`, dus witte tekst
  (`colorScheme.onSurface`), witte cursor en de selectiekleur `0x66FFFFFF` blijven
  ruim voldoende contrasteren; niets bijgesteld.

Verificatie:
- `notities/`: `flutter test` → **73 groen** (0 failures/errors), `flutter analyze` →
  "No issues found!".
- Backend als vangnet: `rm -rf target && mvn -o test` in
  `robberts-assistent-backend/` → **433 groen**, 0 failures/errors, BUILD SUCCESS
  (exitcode 0).
- Een APK bouwen kan niet in de sandbox (geen Android SDK); de visuele bevestiging op
  toestel via `notities-apk.yml` op `main` blijft de laatste stap.
