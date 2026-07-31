# SF-1571 — Developer-worklog

## Stappenplan

- [x] Huidige Flutter-architectuur, tests, assets en deeplink-/refreshgedrag inventariseren.
- [x] Licht thema, gedeelde stijlen, statuspillen en logo-assets implementeren.
- [x] Headeropzet, vier-tabsnavigatie, Meer-routes en push-deeplinks migreren.
- [x] Bestaande tests migreren en gerichte widgettests voor status, navigatie en 390px toevoegen.
- [ ] Formatter, gerichte tests en het volledige vangnet uit `docs/factory/development.md` groen draaien.
- [x] Eigen review uitvoeren en dit worklog afronden.

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

## Verificatie

- `dart format .`: groen.
- `flutter analyze`: groen, 0 issues.
- `flutter test`: groen, 54 tests, 0 failures en 0 errors.
- Widgettest op 390x844: vier navigatielabels, geen layout-exception en éénregelige labels.
- `flutter build web --release`: groen (`build/web`).
- `dart run flutter_launcher_icons`: groen; Android- en webiconen gegenereerd.
- `git diff --check`, conflictmarkercontrole, scopecontrole en controles op
  `Colors.deepPurple`/`colorSchemeSeed`/oude deeplinkvelden: groen.
- `flutter build apk --release`: afgebroken vóór compilatie door de Flutter-toolchain: er is geen Android SDK of
  `ANDROID_HOME` in deze container. `flutter doctor -v` bevestigt dit; de host is linux/arm64 en er is
  lokaal geen Android SDK gevonden. Daardoor kan het verplichte APK-deel van het volledige vangnet in
  deze run niet met exitcode 0 worden bewezen.
