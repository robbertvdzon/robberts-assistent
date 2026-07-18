# SF-1106 - notes in geel

## Story

notes in geel

<!-- refined-by-factory -->

## Scope
De achtergrond van de notities-app (`notities/`, Flutter-app) wordt geel. Dit betreft zowel het login-scherm als het notitie-editor-scherm (de hoofdschermen van de app in `notities/lib/main.dart` en `notities/lib/notes_editor_screen.dart`). De huidige `ThemeData(colorSchemeSeed: Colors.amber)` resulteert in een lichte crème/beige achtergrond in plaats van een duidelijk gele; dit moet aangepast worden zodat de achtergrond zichtbaar geel is.

## Acceptance criteria
- Op zowel het login-scherm als het notitie-editor-scherm van de notities-app is de achtergrondkleur van de `Scaffold` (of het gelijkwaardige theme-niveau `scaffoldBackgroundColor`) een duidelijk gele kleur.
- De rest van de UI (tekst, knoppen, AppBar, kaarten) blijft leesbaar en bruikbaar met voldoende contrast tegen de gele achtergrond.
- De wijziging raakt alleen de notities-app (`notities/`), niet `robberts_assistent/` of de backend.
- Bestaande tests (o.a. `notities/test/notes_editor_screen_test.dart`) blijven slagen; als een test expliciet op een kleur/thema controleert die door deze wijziging verandert, wordt de test bijgewerkt.

## Aannames
- "Notities app" verwijst naar de Flutter-app in de map `notities/` (niet `robberts_assistent/`).
- "Geel" wordt geïnterpreteerd als een standaard Material-gele kleur (bijv. `Colors.yellow` of een vergelijkbare gele tint), tenzij een specifieke kleurcode later wordt aangeleverd; de developer kiest een passende, toegankelijke gele tint met voldoende contrast voor tekst.
- Alleen de achtergrond wordt aangepast; overige kleuraccenten (bijv. iconen, knoppen) blijven ongewijzigd tenzij dat nodig is voor leesbaarheid tegen de nieuwe gele achtergrond.

## Eindsamenvatting

I heb voldoende context. Nu de eindsamenvatting.

## Eindsamenvatting SF-1106: Notities-app achtergrond geel

**Gebouwd:**
`notities/lib/main.dart` — `scaffoldBackgroundColor: Colors.yellow` toegevoegd aan de bestaande `ThemeData(colorSchemeSeed: Colors.amber, useMaterial3: true)`. Omdat zowel het login-scherm (`RootScreen._loginView`) als het notitie-editor-scherm (`NotesEditorScreen`) een `Scaffold` zonder eigen `backgroundColor` gebruiken, wordt de gele achtergrond automatisch op beide schermen zichtbaar.

**Gemaakte keuzes:**
- Alleen `scaffoldBackgroundColor` aangepast; AppBar, Card en knoppen behouden hun eigen M3-surface-kleur, waardoor bestaand contrast van tekst/knoppen ongewijzigd en leesbaar blijft.
- Geen wijziging aan `notes_editor_screen.dart` nodig — de `TextField` staat direct op de gele achtergrond en gebruikt standaard `onSurface`/`hintColor`-styling, wat voldoende contrast geeft.
- `notities/test/notes_editor_screen_test.dart` bevat geen assertions op achtergrondkleur, dus niet aangepast.

**Getest:**
- `flutter pub get`, `flutter test` en `flutter analyze` gedraaid door zowel developer als tester (tester met echte Flutter SDK in sandbox): 3/3 tests groen, geen analyze-issues.
- Reviewer heeft AC's handmatig geverifieerd (code-review, kon zelf geen Flutter-run doen door sandbox-beperking) en akkoord gegeven.
- Bevestigd via `git diff --stat` dat alleen `notities/` geraakt is, niet `robberts_assistent/` of de backend.

**Bewust niet gedaan:**
- Geen screenshot/preview, want `notities/` is APK-only en heeft geen preview-URL.
- Geen aanpassing van kleuraccenten buiten de achtergrond (iconen, knoppen), conform de scope-aanname dat alleen de achtergrond moest veranderen.

Alle acceptatiecriteria zijn voldaan; story is klaar voor documentatie en merge.
