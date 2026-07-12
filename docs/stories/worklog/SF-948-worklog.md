# SF-948 - Worklog

Story-context bij eerste pickup:
Titel wijzigen naar "Robbert's Assistent" in Flutter-frontend

Vervang alle zichtbare voorkomens van "Robberts Assistent" door "Robbert's Assistent" (rechte apostrof) in: robberts_assistent/lib/main.dart (MaterialApp.title + Text op startscherm), robberts_assistent/lib/home_screen.dart (AppBar-titel), robberts_assistent/android/app/src/main/AndroidManifest.xml (android:label). Werk daarnaast robberts_assistent/test/widget_test.dart bij zodat de find.text(...)-matcher de nieuwe titel verwacht (dit is onderdeel van het ontwikkelwerk, geen aparte teststap). Niet-UI teksten (pom.xml, CI-workflownaam, secrets.example.env) blijven ongewijzigd. Zorg dat flutter test lokaal slaagt na de wijziging.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- SF-949: "Robberts Assistent" → "Robbert's Assistent" gewijzigd in
  `robberts_assistent/lib/main.dart` (MaterialApp.title + Text op loginscherm),
  `robberts_assistent/lib/home_screen.dart` (AppBar-titel),
  `robberts_assistent/android/app/src/main/AndroidManifest.xml`
  (`android:label`, apostrof als `&apos;` i.v.m. XML-attribuutwaarde), en
  `robberts_assistent/test/widget_test.dart` (verwachte tekst in
  `find.text(...)`).
- Niet-UI teksten (backend, CI-workflownaam, `pom.xml`,
  `secrets.example.env`) bewust ongewijzigd gelaten, buiten scope.
- `grep -rn "Robberts Assistent" robberts_assistent/` geeft nu geen treffers
  meer; AndroidManifest.xml geverifieerd als well-formed XML
  (`python3 -c "import xml.dom.minidom as m; m.parse(...)"`).
- `flutter test` kon niet lokaal gedraaid worden: de factory build-omgeving
  heeft geen flutter/dart SDK (bekende beperking, zie agent-tips
  `environment/flutter-android-sdk-absent`). CI moet `flutter test` en
  `./gradlew test` draaien om de widget-test en Android-build te bevestigen.
