# SF-1185 - Morgen-tab: leesbaarheid Agenda- en Kiten-kaart fixen

## Story

Morgen-tab: leesbaarheid Agenda- en Kiten-kaart fixen

<!-- refined-by-factory -->

## Scope
In `robberts_assistent/lib/summary_screen.dart` (de "Morgen"-briefing-tab) wordt de layout van twee kaarten leesbaarder gemaakt, zonder wijzigingen aan de backend, aan `api_client.dart` (`BriefingSection`/`BriefingItem`) of aan andere secties (moestuin, weektaken):

1. **Agenda-kaart** (`_buildItemRow`): de afspraaktekst en de actieknop (bv. "Reminder 1 uur van tevoren aanmaken") staan nu naast elkaar in een `Row`, waardoor de knop bijna de hele breedte opeist en de afspraaktekst letter-voor-letter afbreekt. Wordt een `Column`: de afspraaktekst krijgt de volle breedte op de eerste regel, de actieknop (of de laad-spinner tijdens een lopende actie) staat er compact onder, rechts uitgelijnd.
2. **Kiten/strandfietsen-kaart** (`_buildSectionCard`, sectie `kite`): de tekst komt als één string (meerdere regels gescheiden door `\n`) van de backend, met per regel inline 🟢/🟡/🔴-emoji's. Deze regels worden nu per stuk als eigen, duidelijk gescheiden regel/paragraaf gerenderd (in plaats van als één doorlopend tekstblok dat lelijk afbreekt), met nette regelafstand. De tekstinhoud zelf (inclusief positie van de emoji's binnen een regel) verandert niet.

## Acceptance criteria
- Agenda-item: afspraaktekst staat op de volle kaartbreedte boven de actieknop; de knop (of spinner) staat eronder, rechts uitgelijnd; knoplabel-tekst en tap-gedrag ongewijzigd.
- Lange agendateksten breken niet meer letter-voor-letter af, maar wrappen normaal op woordgrenzen.
- De kite-sectietekst wordt per `\n`-regel apart en leesbaar weergegeven (geen orphan-regel met alleen het bolletje), zonder de tekstinhoud of emoji-volgorde per regel te wijzigen.
- Overige secties (moestuin, weektaken) blijven qua layout ongewijzigd.
- Bestaande widget-tests in `test/summary_screen_test.dart` blijven ongewijzigd slagen (inclusief de exacte-tekst-checks en het tap-gedrag van de actieknop).
- `flutter test` en `flutter analyze` (in `robberts_assistent/`) blijven groen.
- Geen wijzigingen aan de backend, aan `BriefingSection`/`BriefingItem` in `api_client.dart`, of aan andere section-providers.

## Aannames
- "Netter opmaken, bijv. status-indicator vooraan de regel" voor de kite-kaart wordt uitgevoerd als een layout-only fix (regels splitsen op `\n`, regelafstand/uitlijning verbeteren), niet als het parsen en herschikken van de emoji-positie binnen de tekst zelf — omdat de tekst als kant-en-klare string (incl. emoji-positie) van de backend komt en herschikken de bestaande exacte-tekst-widgettest (`find.text(...)`) zou breken. Een eventuele herschikking van de emoji-positie an sich valt buiten deze story (zou een backend-wijziging vereisen).
- Kleurgebruik/iconen worden niet nieuw geïntroduceerd; de emoji's in de tekst blijven de enige statusindicatie, alleen de omliggende layout verandert.

## Eindsamenvatting

Ik heb voldoende context: het worklog en de developer/reviewer/tester-commits geven een volledig beeld.

## Eindsamenvatting SF-1185: Morgen-tab: leesbaarheid Agenda- en Kiten-kaart fixen

**Gebouwd:** In `robberts_assistent/lib/summary_screen.dart` zijn twee kaarten in de "Morgen"-briefingtab leesbaarder gemaakt:

1. **Agenda-kaart** (`_buildItemRow`): omgezet van een `Row` (tekst + knop naast elkaar) naar een `Column`. De afspraaktekst krijgt nu de volle kaartbreedte op de eerste regel; de actieknop ("Reminder 1 uur van tevoren aanmaken") of de laad-spinner staat er compact onder, rechts uitgelijnd. Voorheen brak lange afspraakteksten letter-voor-letter af doordat de knop bijna de hele breedte opeiste.
2. **Kiten/strandfietsen-kaart** (`_buildSectionCard`): de tekst die als één string met `\n`-gescheiden regels van de backend komt, wordt nu per regel als eigen `Text`-widget in een `Column` gerenderd, met nette regelafstand — geen doorlopend, lelijk afbrekend tekstblok meer.

**Keuzes:**
- Puur layout-only: tekstinhoud en emoji-volgorde per regel zijn niet aangepast (zou de bestaande exacte-tekst-widgettests breken en een backend-wijziging vereisen — expliciet buiten scope).
- Geen wijzigingen aan backend, `api_client.dart` (`BriefingSection`/`BriefingItem`) of andere secties.
- Bewuste, kleine afwijking (door reviewer gesignaleerd, geen blocker): de `\n`-split in `_buildSectionCard` geldt voor de gedeelde tak `section.items.isEmpty`, dus in theorie ook voor weektaken/moestuin als hun tekst een `\n` bevat — effect is puur cosmetisch (~2-4px extra regelafstand), geen tekst-/gedragswijziging.

**Getest:**
- `flutter test`: 24/24 groen, inclusief een nieuwe widget-test voor de meerregelige kite-tekst.
- `flutter analyze`: geen issues.
- Live preview (`robberts-assistent-pr-16`) visueel geverifieerd met een Playwright-screenshot van de "Morgen"-tab: beide acceptatiecriteria bevestigd, overige secties (weektaken, moestuin, systeemstatus) ogen ongewijzigd.
- Geen bugs gevonden tijdens review of test.

**Bewust niet gedaan:** geen herschikking van emoji-positie binnen de tekst zelf, geen strikte per-sectie-isolatie van de `\n`-splitlogica (zie afwijking hierboven) — beide expliciet als toekomstige, kleinere verbetering genoemd, geen blocker voor deze story.
