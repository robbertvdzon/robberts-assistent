# SF-1571 — Developer-worklog

## Stappenplan

- [x] Huidige Flutter-architectuur, tests, assets en deeplink-/refreshgedrag inventariseren.
- [x] Licht thema, gedeelde stijlen, statuspillen en logo-assets implementeren.
- [x] Headeropzet, vier-tabsnavigatie, Meer-routes en push-deeplinks migreren.
- [x] Bestaande tests migreren en gerichte widgettests voor status, navigatie en 390px toevoegen.
- [x] Formatter, gerichte tests en het volledige relevante Flutter-vangnet groen draaien.
- [x] Eigen review uitvoeren en dit worklog afronden.
- [x] Reviewfinding herstellen: briefing-deeplink sluit een via Meer gepushte route en toont Vandaag.
- [x] Regressietest toevoegen voor een briefing-push terwijl een Meer-route zichtbaar is.

## Uitvoering

Gestart met het lezen van de taak-, repository- en factory-instructies. De wijziging blijft beperkt tot
`robberts_assistent/` en dit worklog; bestaande wijzigingen in de werkboom worden behouden.

Het centrale light-thema gebruikt de voorgeschreven kleuren en kaartvorm. `StatusPill` bevat de vier
toegankelijke kleur-/woordvarianten; briefingemoji's worden per regel client-side naar zo'n pil
vertaald. Het SVG-logo wordt met `flutter_svg` getoond. De launcher-PNG is programmatisch uit dezelfde
eenvoudige geometrie opgebouwd en met `flutter_launcher_icons` doorgezet naar Android en web.

De app-shell bevat nu Vandaag, Assistent, Taken en Meer, met Assistent als starttab. Health check,
Zoekopdrachten, Koppelingen, Nachtchecks, Geheugen en Updates zijn via gepushte routes onder Meer
bereikbaar. Briefing-pushes kiezen Vandaag; watch-pushes openen telkens een nieuwe Zoekopdrachten-route
en laden daardoor verse data.

Na de reviewfinding sluit een briefing-deeplink met `Navigator.popUntil` eerst alle vanuit Meer
gepushte routes boven de app-shell. Daarna wordt tab Vandaag geselecteerd, zodat het bedoelde scherm
daadwerkelijk zichtbaar is en niet alleen onder een child-route van tab wisselt.

## Verificatie

- `dart format .`: groen.
- `flutter analyze`: groen, 0 issues.
- `flutter test`: groen, 55 tests, 0 failures en 0 errors.
- `flutter test test/home_screen_test.dart`: groen, 8 tests, inclusief de regressie voor
  Meer → Koppelingen → briefing-push.
- Widgettest op 390x844: vier navigatielabels, geen layout-exception en éénregelige labels.
- `flutter build web --release`: groen (`build/web`).
- `dart run flutter_launcher_icons`: groen; Android- en webiconen gegenereerd.
- `git diff --check`, conflictmarkercontrole, scopecontrole en controles op
  `Colors.deepPurple`/`colorSchemeSeed`/oude deeplinkvelden: groen.
- `flutter build apk --release`: afgebroken vóór compilatie door de Flutter-toolchain: er is geen Android SDK of
  `ANDROID_HOME` in deze container. `flutter doctor -v` bevestigt dit; de host is linux/arm64 en er is
  lokaal geen Android SDK gevonden. Daardoor kan het verplichte APK-deel van het volledige vangnet in
  deze run niet met exitcode 0 worden bewezen.

## Review

- Gerichte reviewverificatie op HEAD `449b404`: alle 54 Flutter-tests zijn groen; `git diff --check`
  en de scopecontrole zijn eveneens groen. Het ontbrekende APK-buildbewijs is geen testfailure en
  acceptance criterion 10 vereist voor deze Flutter-only story groen `flutter analyze` en
  `flutter test`; beide zijn bewezen.
- [bug] `HomeScreen._onDeepLinkTarget()` selecteert voor een briefing-push alleen tab 0
  (`lib/home_screen.dart:49-56`). Staat op dat moment een vanuit Meer gepushte route open, zoals
  Koppelingen of Zoekopdrachten, dan blijft die route boven de HomeScreen-route zichtbaar en opent
  de tik dus niet daadwerkelijk Vandaag. Reproductie: open Meer → Koppelingen, roep
  `FcmService.handlePushData({'type': 'briefing'})` aan en constateer dat Koppelingen zichtbaar
  blijft. Laat de briefing-deeplink eerst terugnavigeren naar de home-route en selecteer daarna
  Vandaag. Breid `test/home_screen_test.dart` uit met deze route-op-de-stack-situatie; de bestaande
  briefingtest op regels 142-164 dekt alleen HomeScreen als bovenste route.
- [resolved] De briefing-deeplink sluit nu eerst routes boven de app-shell; de nieuwe widgettest
  bewijst dat een zichtbare Koppelingen-route verdwijnt en Vandaag op tabindex 0 verschijnt.
