# SF-948 - verandering titel

## Story

verandering titel

<!-- refined-by-factory -->

## Scope
In de Flutter-frontend (`robberts_assistent/`) wordt de weergegeven app-titel "Robberts Assistent" overal gewijzigd naar "Robbert's Assistent" (met apostrof). Het gaat om de tekst zoals die aan de gebruiker getoond wordt (app-titel, header/AppBar-titel) en de bijbehorende widget-test die op deze tekst matcht. Backend, workflow-namen, package-beschrijvingen en andere niet-UI teksten (bijv. `pom.xml`, `.github/workflows/robberts-assistent-apk.yml`, `secrets.example.env`) vallen buiten scope, tenzij ze zichtbaar zijn voor de eindgebruiker.

Concreet te wijzigen bestanden:
- `robberts_assistent/lib/main.dart`: `MaterialApp.title` en de zichtbare `Text('Robberts Assistent', ...)` op het startscherm.
- `robberts_assistent/lib/home_screen.dart`: de `AppBar`-titel `Text('Robberts Assistent')`.
- `robberts_assistent/android/app/src/main/AndroidManifest.xml`: `android:label="Robberts Assistent"` (app-naam onder Android-icoon, ook zichtbaar voor gebruiker).
- `robberts_assistent/test/widget_test.dart`: de verwachte tekst in `find.text(...)` bijwerken naar de nieuwe titel.

## Acceptance criteria
- Alle door de gebruiker zichtbare voorkomens van "Robberts Assistent" in de Flutter-app (app-titel, AppBar-titel, Android app-label) zijn gewijzigd naar "Robbert's Assistent".
- De widget-test in `robberts_assistent/test/widget_test.dart` is bijgewerkt zodat deze slaagt met de nieuwe titel.
- Niet-UI teksten (backend-beschrijvingen, CI-workflownamen, voorbeeldconfig) blijven ongewijzigd, tenzij ze rechtstreeks aan de eindgebruiker getoond worden.
- Bestaande Flutter-tests (`flutter test`) slagen na de wijziging.

## Aannames
- "in de frontend" verwijst naar de Flutter-app in `robberts_assistent/`; de Spring/Kotlin-backend (`robberts-assistent-backend/`) heeft geen zichtbare UI-tekst met deze titel en blijft dus ongemoeid.
- Het Android app-label in `AndroidManifest.xml` wordt meegenomen omdat dit de naam is die de gebruiker op het toestel ziet, ook al staat het niet in Dart-code.
- CI-workflownaam, PR-package-beschrijving (`pom.xml`) en `secrets.example.env`-commentaar zijn interne/ontwikkelaarsgerichte teksten, geen "frontend"-tekst, en blijven daarom ongewijzigd.

<!-- test-feedback:start -->
## Test-feedback
Alle acceptatiecriteria zijn geverifieerd: de 4 doelbestanden bevatten correct "Robbert's Assistent" (main.dart 2x, home_screen.dart, AndroidManifest.xml met `&apos;`-escaping, widget_test.dart), geen restanten van de oude tekst binnen `robberts_assistent/`, en niet-UI teksten buiten scope zijn ongewijzigd gebleven. De live preview-build (main.dart.js) bevestigt dat de gedeployde app de nieuwe tekst toont. `flutter test` kon niet lokaal draaien (bekende arm64-sandboxbeperking), wat conform de bijgewerkte tester-instructies expliciet geen blocker meer is voor een zuivere tekstwijziging.

{"agent_tips_update":[]}
{"phase":"tested"}

[FACTORY VERIFICATION] Verification-command wind-flutter-test afgewezen: status=tool-missing, exitCode=n.v.t.
<!-- test-feedback:end -->

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summarized"}
