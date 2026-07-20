# SF-1126 - Worklog

Story-context bij eerste pickup:
Bottom-nav, MoreScreen, header-logo en nieuwe app-iconen

lib/home_screen.dart: NavigationBar terugbrengen naar 4 tabs (Samenvatting, Assistent, Herinneringen, Meer); IndexedStack aanpassen zodat het 4e item het nieuwe lib/more_screen.dart is. Nieuw MoreScreen met ListTile-items (icoon+label) die via Navigator.push doornavigeren naar de bestaande, ongewijzigde CouplingsScreen, NightlyChecksScreen en UpdatesScreen (in die volgorde). AppBar-title uitbreiden met een klein logo (Image.asset uit assets/icon/icon.png) links van de titeltekst 'Robbert's assistent'. Nieuw, verzorgd assistent-icoon genereren en assets/icon/icon.png vervangen; flutter_launcher_icons opnieuw draaien (bestaande config in pubspec.yaml) zodat Android-launcher-icons en web/icons/Icon-192/512(-maskable).png + web/favicon.png ververst worden; web/manifest.json controleren/bijwerken waar nodig. Widget-test toevoegen/bijwerken die verifieert dat de bottom-nav 4 tabs telt en dat 'Meer' de 3 lijst-items toont. Bestaande test/widget_test.dart moet groen blijven. Nederlandse UI-teksten en code-commentaar.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `lib/home_screen.dart`: `NavigationBar` teruggebracht naar 4 tabs (Samenvatting, Assistent,
  Herinneringen, Meer); `IndexedStack` gebruikt nu `MoreScreen` als 4e item, zodat de state van
  de 3 hoofdschermen bewaard blijft bij tab-wissel. `AppBar`-title uitgebreid met een `Row` met
  daarin het app-icoon (`Image.asset('assets/icon/icon.png')`, 28x28) links van de titeltekst.
- Nieuw `lib/more_screen.dart`: `ListView` met 3 `ListTile`s (Koppelingen, Nachtchecks, Updates,
  in die volgorde) die via `Navigator.push` naar de bestaande, ongewijzigde schermen navigeren.
- `pubspec.yaml`: `assets/icon/icon.png` toegevoegd aan de `flutter.assets`-lijst (nodig omdat
  `Image.asset` in de `AppBar` er nu naar verwijst; stond er nog niet in).
- Nieuw, verzorgd assistent-icoon gegenereerd (Pillow-script, lokaal): deepPurple-gradient
  achtergrond, afgeronde hoeken, witte chatbubbel met 3 paarse stippen (typing-indicator) en een
  geel sprankel-accent — consistent met de bestaande deepPurple-huisstijl en herkenbaar als
  "assistent"-icoon. `assets/icon/icon.png` vervangen; `dart run flutter_launcher_icons` gedraaid
  (bestaande config, incl. `web:`-sectie) → Android-`mipmap-*/ic_launcher.png` en
  `web/icons/Icon-192/512(-maskable).png` + `web/favicon.png` ververst.
- `web/manifest.json`: `description` was nog de Flutter-scaffold-placeholder ("A new Flutter
  project.") — vervangen door een korte Nederlandse omschrijving. Naam/iconenlijst was al
  consistent, geen wijziging nodig.
- Nieuwe test `test/home_screen_test.dart`: verifieert dat de bottom-nav precies 4
  `NavigationDestination`s telt (Samenvatting/Assistent/Herinneringen/Meer), dat tikken op 'Meer'
  `MoreScreen` opent met de 3 verwachte items, en dat elk item in `MoreScreen` naar het juiste
  scherm (`CouplingsScreen`/`NightlyChecksScreen`/`UpdatesScreen`) navigeert. Gebruikt een
  stub-`ApiClient` (zoals in de bestaande screen-tests) zodat er geen echte netwerkcalls
  plaatsvinden; de `UpdatesScreen`-native-MethodChannel is gemockt zodat navigeren daarnaartoe
  niet crasht in de test-omgeving.
- Volledig vangnet gedraaid: `flutter pub get` (geen wijziging in `pubspec.lock`), `flutter
  analyze` (geen issues), `flutter test` (alle bestaande + nieuwe tests groen, incl.
  `test/widget_test.dart`).
- Niet gewijzigd: backend, andere apps (`groentetuin`, `notities`, `wind`) — puur
  front-end/`robberts_assistent`-scoped zoals in de aannames van de story.

## Review-notities (SF-1127)

- `home_screen.dart`/`more_screen.dart`/`pubspec.yaml`/`web/manifest.json` tegen de story
  gecontroleerd: 4 tabs (Samenvatting/Assistent/Herinneringen/Meer), `IndexedStack` bewaart
  state van de 3 hoofdschermen, `MoreScreen` met 3 `ListTile`s in de juiste volgorde
  (Koppelingen/Nachtchecks/Updates) die naar de ongewijzigde bestaande schermen pushen,
  AppBar-logo (28x28, `assets/icon/icon.png`) links van de titel, asset correct geregistreerd
  in `pubspec.yaml`. Alles conform de acceptatiecriteria.
- In tegenstelling tot de bekende agent-tip ("Flutter-tests zijn structureel niet uitvoerbaar
  in deze sandbox") bleek `flutter` in déze reviewer-sandbox wél beschikbaar (aarch64, Flutter
  3.44.6). Zelf gedraaid als gerichte check: `flutter test` → alle 11 tests groen (incl. de
  nieuwe `test/home_screen_test.dart`), `flutter analyze` → geen issues. Bevestigt de
  testclaims uit het developer-worklog met echt bewijs.
- Oordeel: akkoord, geen blockers.
