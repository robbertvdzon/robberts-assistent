# SF-1297 - Worklog

Story-context bij eerste pickup:
WasteSectionProvider implementeren

Nieuw bestand robberts-assistent-backend/src/main/kotlin/nl/vdzon/robbertsassistent/briefing/WasteSectionProvider.kt: @Component die BriefingSectionProvider implementeert en waste.WasteClient.upcomingPickups() gebruikt. order = een vrije waarde tussen bestaande secties (bv. 15, tussen agenda=10 en weektaken=20). section(): filtert WasteSchedule.pickups (al oplopend gesorteerd) tot vandaag t/m +6 dagen, toont per ophaalmoment datum + WastePickup.type (via BriefingItem-regels of nette meerregelige text); lege lijst binnen het venster of WasteSchedule.error → neutrale, niet-crashende tekst (zelfde beschermende runCatching-patroon als WeekTasksSectionProvider). shortSummary(): filtert op exact 'morgen', meerdere types samengevoegd tot 'Zet vanavond de <bak(ken)> buiten'; geen ophaal morgen of een fout → null. Geen wijziging aan BriefingSectionProvider, BriefingService, BriefingController, BriefingScheduler of frontend. Inclusief unit-tests (WasteSectionProviderTest) naar het patroon van WeekTasksSectionProviderTest/BeachCycleSectionProviderTest: section() met/zonder pickups binnen 7 dagen en met error; shortSummary() met/zonder ophaal morgen en met error.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1304 - WasteSectionProvider implementeren

- Nieuw `briefing/WasteSectionProvider.kt` (`@Component`, `order = 15` — tussen
  agenda=10 en weektaken=20), gebruikt de bestaande `waste.WasteClient.upcomingPickups()`
  (geen nieuwe koppeling).
- `section()`: filtert `WasteSchedule.pickups` op vandaag t/m +6 dagen, toont per
  ophaalmoment `dd-MM: <type>` op een eigen regel, oplopend op datum (de lijst komt al
  gesorteerd binnen). Lege lijst binnen het venster → "Geen ophaalmomenten deze week.";
  `WasteSchedule.error` gezet → "Kon de afvalkalender niet ophalen: <error>"; onverwachte
  exception → `runCatching`-fallback naar een neutrale foutmelding (zelfde patroon als
  `WeekTasksSectionProvider`).
- `shortSummary()`: kijkt naar `LocalDate.now().plusDays(1)` (morgen), voegt meerdere
  types op dezelfde dag samen tot "Zet vanavond de \<type1\> & \<type2\> buiten"; `null`
  bij geen ophaal morgen of bij een fout (schedule-`error` of exception), zodat
  `BriefingScheduler`'s bestaande `mapNotNull` de sectie in de 18:00-push overslaat.
- Geen wijziging aan `BriefingSectionProvider`, `BriefingService`, `BriefingController`,
  `BriefingScheduler` of frontend nodig — puur een nieuwe SPI-implementatie.
- Nieuwe `WasteSectionProviderTest` (7 tests): `section()` met ophaalmomenten binnen 7
  dagen (met sortering/filtering buiten het venster), zonder ophaalmomenten binnen 7
  dagen, en met een `error`; `shortSummary()` met één ophaal morgen, met meerdere types
  morgen, zonder ophaal morgen, en met een `error`.
- Volledig vangnet: `mvn test` → 304 tests, 0 failures, 0 errors, BUILD SUCCESS.

## SF-1305 - Story-brede test (tester)

- Code-review `WasteSectionProvider.kt`/`WasteSectionProviderTest.kt`: implementatie en
  tests dekken alle acceptatiecriteria (7-dagen-filter, sortering, lege lijst, error-
  fallback, `shortSummary()` met/zonder ophaal morgen/error, meerdere types samengevoegd);
  `order = 15` botst niet met bestaande waarden en zit logisch tussen agenda(10) en
  weektaken(20).
- Volledig vangnet opnieuw gedraaid (`mvn test` in `robberts-assistent-backend/`,
  start 2026-07-26T15:01:19Z, eind 2026-07-26T15:01:52Z, Total time 32.313s):
  304 tests, 0 failures, 0 errors, BUILD SUCCESS — incl. de 7 nieuwe
  `WasteSectionProviderTest`-tests.
- Live geverifieerd op preview `robberts-assistent-pr-32`
  (`https://robberts-assistent-frontend-robberts-assistent-pr-32.apps.sno.lab.vdzon.com`):
  `GET /api/v1/briefing` bevat een nieuwe sectie `"key":"waste","title":"Afval"` met tekst
  `"28-07: gft & etensresten"`, correct gepositioneerd tussen `agenda` en `week-tasks`;
  `POST /api/v1/briefing/refresh` → HTTP 200, sectie blijft aanwezig na refresh.
- Screenshot van de Upcoming-tab (Flutter-web-preview) bevestigt dat de nieuwe
  "Afval"-kaart zichtbaar rendert tussen Agenda en "Deze week", zonder enige
  frontend-codewijziging nodig (generieke sectie-rendering) —
  `screenshots/sf1305-upcoming-waste-section-scrolled.png`.
- Geen bugs gevonden. Geen wijziging aan `BriefingService`/`BriefingController`/
  `BriefingScheduler`/frontend aangetroffen, zoals vereist.
