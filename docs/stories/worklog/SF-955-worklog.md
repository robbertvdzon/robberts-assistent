# SF-955 - Worklog

Story-context bij eerste pickup:
Titel naar 'Robbert's assistent' en custom web-icon toepassen

Wijzig de zichtbare titel 'Robbert's Assistent' naar 'Robbert's assistent' (kleine letter) in: lib/main.dart (MaterialApp.title en de Text-widget op het startscherm), lib/home_screen.dart (AppBar-titel), android/app/src/main/AndroidManifest.xml (android:label), web/index.html (<title> en apple-mobile-web-app-title) en web/manifest.json (name en short_name). Werk test/widget_test.dart bij naar de nieuwe titeltekst. Breid daarnaast de flutter_launcher_icons-config in pubspec.yaml uit met `web: true` en genereer/vervang de web-iconbestanden (web/icons/Icon-*.png, web/favicon.png) zodat ze het bestaande custom icon (assets/icon/icon.png) tonen in plaats van het standaard Flutter-logo. Android-icons niet wijzigen; iOS blijft buiten scope.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Titel "Robbert's Assistent" → "Robbert's assistent" aangepast in `lib/main.dart`
  (`MaterialApp.title` + login-`Text`), `lib/home_screen.dart` (AppBar-titel),
  `android/app/src/main/AndroidManifest.xml` (`android:label`), `web/index.html`
  (`<title>` + `apple-mobile-web-app-title`, voorheen nog default
  "robberts_assistent") en `web/manifest.json` (`name`/`short_name`, idem).
  `test/widget_test.dart` bijgewerkt naar de nieuwe verwachte tekst.
- `pubspec.yaml`: `flutter_launcher_icons`-config uitgebreid met een `web:`-blok
  (`generate: true`, `image_path: assets/icon/icon.png`,
  `background_color`/`theme_color` gelijk aan bestaande manifest-waarden
  `#0175C2`) zodat een toekomstige `dart run flutter_launcher_icons` de
  web-iconen uit hetzelfde custom icon blijft genereren. Android-config
  ongewijzigd.
- Web-icoonbestanden (`web/favicon.png`, `web/icons/Icon-192.png`,
  `Icon-512.png`, `Icon-maskable-192.png`, `Icon-maskable-512.png`) vervangen
  door varianten van het bestaande custom icon (`assets/icon/icon.png`,
  1024x1024 vierkant, geen padding nodig). De sandbox heeft geen
  flutter/dart-SDK (bekende beperking, zie `development.md` en agent-tips), dus
  `flutter_launcher_icons` kon niet gedraaid worden; in plaats daarvan is de
  package-broncode (`web_icon_generator.dart`/`utils.dart`/`constants.dart` van
  `fluttercommunity/flutter_launcher_icons`) opgezocht om exact hetzelfde
  resultaat te reproduceren: alle vijf bestanden zijn simpele resizes zonder
  padding/mask-specifieke bewerking (favicon 16x16, Icon-*-192 192x192,
  Icon-*-512 512x512), gegenereerd met Pillow (venv met `--without-pip` +
  `get-pip.py` omdat het systeem-python geen pip/ensurepip heeft en
  "externally managed" is). `manifest.json`-iconlijst was al in het juiste
  formaat en hoefde niet aangepast.
- Tests: `flutter test`/`flutter build` kunnen niet lokaal draaien (geen
  flutter-SDK in de sandbox, bekende/gedocumenteerde beperking). Wel lokaal
  gevalideerd: `AndroidManifest.xml` is well-formed XML, `web/manifest.json` is
  valide JSON, `pubspec.yaml` (incl. nieuw `web:`-blok) is valide YAML, en alle
  gegenereerde PNG's zijn geopend/geverifieerd op de juiste afmetingen met
  Pillow. Geen andere restanten van de oude titeltekst gevonden
  (`grep -rn "Robbert's Assistent"` op de gewijzigde bestanden is leeg).

## Review (SF-956)

- Volledige diff `main...HEAD` gecontroleerd (13 gewijzigde bestanden).
- Titelwijziging "Robbert's Assistent" → "Robbert's assistent" geverifieerd in
  alle scope-bestanden: `lib/main.dart` (title + Text-widget),
  `lib/home_screen.dart` (AppBar), `AndroidManifest.xml` (`android:label`),
  `web/index.html` (`<title>` + `apple-mobile-web-app-title`, was nog default
  "robberts_assistent"), `web/manifest.json` (`name`/`short_name`, idem).
  `test/widget_test.dart` bijgewerkt naar de nieuwe tekst. Grep op
  "Robbert's Assistent" binnen `robberts_assistent/` levert niets meer op.
- `pubspec.yaml`: `flutter_launcher_icons`-config correct uitgebreid met
  `web:` blok (`generate: true`, zelfde `image_path`, kleuren consistent met
  manifest). Android-blok ongewijzigd.
- Web-iconbestanden geopend en gecontroleerd op afmetingen (favicon 16x16,
  Icon-192/512 192x192/512x512, maskable idem) en visueel geverifieerd
  (`Icon-512.png`, `favicon.png`): tonen het custom paarse chatbubbel-met-ster
  icon, niet meer het standaard Flutter-logo. `manifest.json`-iconlijst was al
  correct en hoefde niet aangepast.
- [info] Maskable-iconvarianten zijn simpele resizes zonder ingebouwde
  safe-zone-padding; dat is conform hoe `flutter_launcher_icons` dit ook zelf
  genereert (developer heeft de package-broncode nagetrokken) en zat niet in
  scope van deze story, dus geen blocker.
- Geen backend-/Kotlin-wijzigingen in deze diff, dus `./gradlew test` niet van
  toepassing. Ontbrekend `flutter test`-bewijs is voor deze zuivere
  Flutter-UI/config-wijziging geen blocker (structurele ARM64-sandboxbeperking +
  geen branch-CI, zie agent-tips) — geaccepteerd op basis van grondige
  handmatige code-review tegen de acceptatiecriteria.
- Oordeel: voldoet aan scope en acceptatiecriteria. Akkoord.

## Test (SF-957)

- Diff `main...HEAD` (13 bestanden) gecontroleerd tegen acceptatiecriteria: titel
  "Robbert's Assistent" → "Robbert's assistent" correct doorgevoerd in
  `lib/main.dart` (`MaterialApp.title` + login-`Text`), `lib/home_screen.dart`
  (AppBar), `AndroidManifest.xml` (`android:label`), `web/index.html`
  (`<title>` + `apple-mobile-web-app-title`) en `web/manifest.json`
  (`name`/`short_name`). `test/widget_test.dart` bijgewerkt naar de nieuwe
  tekst. Grep op "Robbert's Assistent" / "robberts_assistent" (default-tekst)
  binnen `robberts_assistent/` levert niets meer op.
- `pubspec.yaml`: `flutter_launcher_icons`-config bevat nu een correct
  `web:`-blok (`generate: true`, `image_path: assets/icon/icon.png`,
  kleuren consistent met manifest). Android-blok ongewijzigd. Gegenereerde
  web-iconbestanden lokaal geopend en op afmeting geverifieerd (PNG-header):
  favicon 16x16, Icon-192/maskable-192 192x192, Icon-512/maskable-512 512x512.
- Preview-omgeving (`https://robberts-assistent-frontend-robberts-assistent-pr-6.apps.sno.lab.vdzon.com`)
  live getest zonder Google-login (SKIP_GOOGLE_AUTH): `<title>` in geserveerde
  `index.html`, `manifest.json` (`name`/`short_name`) en het gecompileerde
  `main.dart.js` tonen allemaal "Robbert's assistent" (0 treffers voor de oude
  hoofdletter-variant in `main.dart.js`). `favicon.png`/`icons/Icon-192.png`
  van de preview opgehaald en gecontroleerd op grootte + visueel: tonen het
  custom paarse chatbubbel-met-ster-icon (`assets/icon/icon.png`), niet meer
  het standaard Flutter-logo. Browser-screenshot gemaakt
  (`SF-957-preview-startscherm.png`) — AppBar toont "Robbert's assistent".
- `flutter test` kon zoals gedocumenteerd niet lokaal draaien (geen linux-arm64
  Flutter-SDK in de sandbox, geen PR-branch-CI). Gecompenseerd door
  bron-diff-review + live verificatie van de daadwerkelijk gedeployde build
  (index.html, manifest.json, main.dart.js, icon-assets) op de preview, zoals
  vermeld in agent-tips. Geen andere (backend-/Kotlin-)tests geraakt door deze
  diff.
- Geen wijzigingen buiten scope aangetroffen (geen wijzigingen in
  wind/notities/backend).
- Oordeel: voldoet aan alle acceptatiecriteria van SF-955/SF-956. Geen bugs
  gevonden.

## Developer-hercontrole (SF-956, nieuwe run)

- Deze developer-run kreeg opnieuw de opdracht om SF-956 te implementeren
  (mogelijk een re-run na de `[FACTORY EVIDENCE REJECTED]`-melding op de
  backend-mvn-test-timing van de vorige testronde). De working tree was echter
  al schoon en alle scope-wijzigingen bleken al aanwezig en gecommit
  (commit `9a867b7`, "SF-955: developer changes").
- Herverificatie uitgevoerd: alle vijf titel-bestanden plus
  `test/widget_test.dart` bevatten de correcte tekst "Robbert's assistent";
  `grep -rn "Robbert's Assistent"` binnen `robberts_assistent/` levert niets
  meer op. `pubspec.yaml` bevat het `flutter_launcher_icons`-`web:`-blok;
  `web/icons/Icon-*.png` en `web/favicon.png` zijn de custom-icon-varianten
  (niet meer het Flutter-standaardlogo). `AndroidManifest.xml` is valide XML,
  `web/manifest.json` valide JSON.
- Geen code-wijzigingen nodig in deze run; niets om te committen (de factory
  handelt commit/push/PR af zoals gebruikelijk).

## Reviewer-controle (SF-956, nieuwe run)

- Volledige story-diff (`main...HEAD`) herbeoordeeld: titelwijzigingen in
  `main.dart`, `home_screen.dart`, `AndroidManifest.xml`, `web/index.html`,
  `web/manifest.json` en `test/widget_test.dart` zijn correct en consistent
  ("Robbert's assistent"). `grep` op de oude schrijfwijze levert niets meer op.
- `pubspec.yaml` bevat het `flutter_launcher_icons`-`web:`-blok; de
  gegenereerde web-iconbestanden (favicon, Icon-192/512, Icon-maskable-192/512)
  zijn visueel geïnspecteerd en tonen het custom paarse
  chatbubbel-met-ster-icon uit `assets/icon/icon.png`, niet het
  Flutter-standaardlogo. Afmetingen kloppen (16x16, 192x192, 512x512).
  Android-icon/manifest ongewijzigd.
- Geen scope-overschrijding (geen iOS-, pom.xml-, CI-workflow- of
  secrets-wijzigingen).
- `flutter test` niet uitvoerbaar in sandbox (bekende arm64-beperking, geen
  branch-CI) — geaccepteerd conform agent-tips-beleid, gecompenseerd door
  eerdere tester-run met live-preview-verificatie + screenshot en deze
  code-review. Oordeel: akkoord, geen bugs of blockers gevonden.

## Test (SF-957, nieuwe run)

- Reden nieuwe run: vorige testronde kreeg `[FACTORY EVIDENCE REJECTED]` op de
  timing van `backend-mvn-test` (start/eind kwamen niet overeen met de
  gerapporteerde duur). Deze run heeft `mvn test` daadwerkelijk uitgevoerd en
  de start-/eindtijd expliciet gelogd i.p.v. alleen het resultaat te
  citeren: gestart `2026-07-12T10:45:17Z`, klaar `2026-07-12T10:45:49Z`
  (Maven zelf rapporteerde ook `Total time: 31.684 s`, consistent met de
  gemeten wall-clock duur). Resultaat: `Tests run: 34, Failures: 0, Errors: 0,
  Skipped: 0`, `BUILD SUCCESS`. Er zijn geen backend-wijzigingen in deze
  story-diff; dit was dus een regressiecheck, geen gerichte test van nieuwe
  functionaliteit.
- Diff `main...HEAD` (13 bestanden, uitsluitend `robberts_assistent/`)
  opnieuw doorgenomen tegen de acceptatiecriteria: titel "Robbert's Assistent"
  → "Robbert's assistent" correct in `lib/main.dart` (`MaterialApp.title` +
  login-`Text`), `lib/home_screen.dart` (AppBar), `AndroidManifest.xml`
  (`android:label`), `web/index.html` (`<title>` +
  `apple-mobile-web-app-title`) en `web/manifest.json` (`name`/`short_name`).
  `test/widget_test.dart` verwacht de nieuwe tekst. Grep op
  "Robbert's Assistent" en op de default-tekst "robberts_assistent" in
  `web/index.html`/`web/manifest.json` levert niets meer op.
- `pubspec.yaml` bevat het `flutter_launcher_icons`-`web:`-blok
  (`generate: true`, `image_path: assets/icon/icon.png`, kleuren consistent
  met manifest). Web-iconbestanden lokaal met Python/`struct` op PNG-afmeting
  gecontroleerd: `favicon.png` 16x16, `Icon-192`/`Icon-maskable-192` 192x192,
  `Icon-512`/`Icon-maskable-512` 512x512 — allemaal kloppend.
- Live preview
  (`https://robberts-assistent-frontend-robberts-assistent-pr-6.apps.sno.lab.vdzon.com`)
  opnieuw geverifieerd zonder Google-login: `index.html` (`<title>` +
  apple-meta-tag), `manifest.json` (`name`/`short_name`) tonen
  "Robbert's assistent"; het gecompileerde `main.dart.js` bevat 0 keer de oude
  schrijfwijze en 3 keer de nieuwe. `favicon.png` (16x16) en `Icon-512.png`
  (512x512) van de preview opgehaald; `Icon-512.png` visueel geïnspecteerd —
  toont het custom paarse chatbubbel-met-ster-icon, geen Flutter-standaardlogo.
  Nieuw Playwright-screenshot gemaakt (Chromium via lokale npm-globale install,
  `ignoreHTTPSErrors` nodig voor het zelfondertekende lab-certificaat):
  `SF-957-preview-startscherm.png`, AppBar-titel bevestigd als
  "Robbert's assistent".
- `flutter test` blijft structureel niet uitvoerbaar in deze sandbox (geen
  linux-arm64 Flutter-SDK, geen PR-branch-CI) — zoals eerder gedocumenteerd
  gecompenseerd door bron-diff-review + live-build-verificatie. Dit keer is
  het wél mogelijk gebleken `mvn test` daadwerkelijk (opnieuw) te draaien voor
  de backend, met correcte/consistente start-/eindtijden, om de vorige
  evidence-rejection recht te zetten.
- `git status` schoon, geen scope-overschrijding (geen wijzigingen buiten
  `robberts_assistent/`, geen iOS/pom.xml/CI-workflow/secrets-wijzigingen).
- Oordeel: alle acceptatiecriteria van SF-955/SF-956 blijven geverifieerd,
  geen bugs gevonden.

## Developer-hercontrole (SF-956, nogmaals nieuwe run)

- Opnieuw opdracht voor SF-956 ontvangen op een schone working tree (`git
  status`: clean, `HEAD` nog steeds `9a867b7`). Alle scope-wijzigingen bleken
  wederom al aanwezig; geen code-wijzigingen nodig.
- Herverificatie: `grep -rn "Robbert's Assistent"` en de default-tekst
  "robberts_assistent" in `web/index.html`/`web/manifest.json` binnen
  `robberts_assistent/` leveren niets op. Titel "Robbert's assistent"
  bevestigd in `lib/main.dart` (title + login-Text), `lib/home_screen.dart`
  (AppBar), `AndroidManifest.xml` (`android:label`), `web/index.html`
  (`<title>` + apple-meta-tag), `web/manifest.json` (`name`/`short_name`) en
  `test/widget_test.dart`.
- `pubspec.yaml` bevat het `flutter_launcher_icons`-`web:`-blok
  (`generate: true`, `image_path: assets/icon/icon.png`, kleuren consistent
  met manifest). Web-iconbestanden opnieuw op afmeting gecontroleerd via
  PNG-header (`struct`): favicon 16x16, Icon-192/maskable-192 192x192,
  Icon-512/maskable-512 512x512 — allemaal kloppend.
- `AndroidManifest.xml` en `web/manifest.json` opnieuw geparsed (valide XML
  resp. JSON). `flutter`/`dart` blijven afwezig in deze sandbox (bekende
  beperking, zie agent-tips); `flutter test`/`flutter build` dus wederom niet
  lokaal uitvoerbaar.
- Working tree blijft schoon na deze run; niets om te committen — de factory
  handelt commit/push/PR-acties af.
