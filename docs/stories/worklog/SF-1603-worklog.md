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

Developer-herstel (2026-07-31):
- [x] De semantische tapactie van een Vandaag-tegel herstellen en met een widgettest afdekken.
- [x] Zelfreview en het volledige developer-vangnet opnieuw groen afronden.

Gedaan / rationale developer-herstel:
- De buitenste `Semantics` roept nu dezelfde tegel-toggle aan als de visuele `InkWell`, zodat een screenreader de uitgesproken knop ook daadwerkelijk kan activeren.
- De bestaande één-tegeltest verifieert expliciet `SemanticsAction.tap`, voert die actie via de semantics-tree uit en controleert dat het volledige kite-detail opent.
- Vangnet opnieuw groen: `mvn -o test` (340 tests, 0 failures/errors), `mvn -o -DskipTests package`, `flutter test` (61 tests), `flutter analyze` en `flutter build web`, allemaal exitcode 0.

Herreview (2026-07-31):
- Volledige story-diff `main...HEAD` opnieuw beoordeeld. Gerichte verificatie groen: 30 tests in `BriefingSectionContractTest`, `KiteSectionProviderTest`, `BeachCycleSectionProviderTest` en `WasteSectionProviderTest`; daarnaast alle 17 tests in `test/summary_screen_test.dart`.
- [bug] `WasteSectionProvider.kt:54`: de nieuwe vandaag/morgen-status gebruikt `LocalDate.now()` in de JVM-defaulttijdzone. De productie-image stelt geen `TZ`/`user.timezone` in en draait standaard op UTC, terwijl briefingdatumlogica elders expliciet `Europe/Amsterdam` gebruikt. Tussen 00:00 en 02:00 Amsterdam-zomertijd ziet de backend nog de vorige UTC-datum; een ophaalmoment van de lokale volgende dag valt dan in `+2` en krijgt onterecht `GOED` in plaats van `LET_OP`. Gebruik voor afval consequent de Amsterdam-datum (bij voorkeur via een injecteerbare `Clock`) en dek de UTC/Amsterdam-datumgrens af met een test.
