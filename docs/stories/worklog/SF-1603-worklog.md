# SF-1603 - Worklog

Story-context bij eerste pickup:
Statusgegevens en interactieve Vandaag-tegels

Stappenplan:
- [x] Taak, factoryrichtlijnen en bestaande agenttips lezen.
- [x] Bestaande briefingmodellen, providers, Vandaag-UI en tests analyseren.
- [x] Backendcontract en providerlogica met unit-tests implementeren.
- [x] Fluttermodel en interactieve statustegels met widget-tests implementeren.
- [x] Functionele en technische factorydocumentatie bijwerken.
- [x] Zelfreview uitvoeren en het volledige voorgeschreven vangnet groen afronden.

Gedaan / rationale:
- Worklog bij start van de developer-run aangemaakt, zodat aanpak en validatie onderdeel van de PR blijven.
- `BriefingSection` uitgebreid met nullable `status`/`tileLabel`; oude cache-JSON blijft leesbaar en enumwaarden zijn contractueel getest.
- Kite en strandfietsen kiezen uit dezelfde beoordeling als hun detailtekst het gunstigste, bij gelijkstand vroegste dagdeel; foutdata levert geen tegel.
- Afvaltekst en -tegel worden uit één planning opgebouwd, inclusief vandaag/morgen/later/leeg, korte baktypen en veilige foutdegradatie.
- Vandaag toont maximaal drie even brede, afgekorte en semantisch gelabelde tegels met exacte statuskleuren; één tikbaar detail staat tegelijk open en getegelde kaarten worden niet dubbel getoond.
- Tests toegevoegd voor backendcontract/providers en Flutter-JSON/layout/kleuren/semantiek/interactie/ontdubbeling.
- `docs/factory/functional-spec.md` en `docs/factory/technical-spec.md` bijgewerkt met contract en UI-gedrag.
- Vangnet: `mvn -o test` (340 tests, 0 failures/errors), `mvn -o -DskipTests package`, `flutter analyze`, `flutter test` (61 tests) en `flutter build web` allemaal exitcode 0.
- APK-build niet gestart: `flutter doctor -v` bevestigt de bekende factorybeperking dat geen Android SDK aanwezig is; de relevante webbuild is wel groen.

Review (2026-07-31):
- Volledige story-diff `main...HEAD` beoordeeld; aanvullend zijn de vier gewijzigde backend-testklassen en `test/summary_screen_test.dart` gericht groen gedraaid.
- [bug] `summary_screen.dart:178-187`: de buitenste `Semantics` zet alleen `button` en `label`, terwijl `ExcludeSemantics` de semantische tapactie van de onderliggende `InkWell` verwijdert. De tegel wordt dus wel uitgesproken, maar heeft geen `SemanticsAction.tap` en kan met een screenreader niet worden geactiveerd. Registreer dezelfde toggle ook als semantische `onTap` (of behoud de `InkWell`-actie in de semantics tree) en voeg een widgettest toe die de tapactie op de semantieknode controleert en uitvoert.
