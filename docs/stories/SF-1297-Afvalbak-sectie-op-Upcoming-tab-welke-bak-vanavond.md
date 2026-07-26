# SF-1297 - Afvalbak-sectie op Upcoming-tab (welke bak vanavond buiten)

## Story

Afvalbak-sectie op Upcoming-tab (welke bak vanavond buiten)

<!-- refined-by-factory -->

## Scope

Nieuwe briefingsectie **afval** op de "Upcoming"-tab: welke afvalbak wanneer buiten moet, plus een korte "vanavond buiten"-melding in de dagelijkse 18:00-push.

Nieuw bestand `robberts-assistent-backend/src/main/kotlin/nl/vdzon/robbertsassistent/briefing/WasteSectionProvider.kt`: een `@Component` die `BriefingSectionProvider` implementeert en `waste.WasteClient.upcomingPickups()` gebruikt (bestaande poort, keyless HVC-koppeling, altijd echt of `StubWasteClient` in tests — geen nieuwe koppeling nodig).

**`section()` (Upcoming-tab):**
- Filtert `WasteSchedule.pickups` (al oplopend gesorteerd op datum) tot de komende 7 dagen vanaf vandaag (inclusief vandaag).
- Toont per ophaalmoment een regel met datum + bak-type (`WastePickup.type`), oplopend op datum — via `BriefingSection.items` (`BriefingItem` per regel) of een nette meerregelige `text`, analoog aan andere secties.
- Geen ophaalmomenten in de komende 7 dagen → neutrale tekst ("geen ophaal deze week" of vergelijkbaar), geen crash, geen lege/kapotte sectie.
- `WasteSchedule.error` gezet (netwerk-/parsfout) → stil degraderen naar een duidelijke, niet-crashende tekst, zelfde beschermende patroon als `WeekTasksSectionProvider`/`SystemStatusSectionProvider` (`runCatching` rond de opbouw).
- `order` zodanig gekozen dat de kaart logisch tussen de bestaande secties staat (bestaande volgorde: weerkaart=-10, kite=0, strandfietsen=5, agenda=10, weektaken=20, moestuin=30, systeemstatus=40).

**`shortSummary()` (18:00-push, `BriefingScheduler`):**
- Geeft alleen de ophaal van **morgen** terug, in de vorm "Zet vanavond de \<bak\> buiten" — meerdere types op dezelfde dag netjes samengevoegd in één zin.
- Geen ophaal morgen (of een fout) → `null`, zodat de sectie in de push wordt overgeslagen (bestaand `mapNotNull`-patroon in `BriefingScheduler`, geen wijziging nodig).

Geen wijziging aan `BriefingService`, `BriefingController`, `BriefingScheduler`, `BriefingSectionProvider`-interface of frontend: het SPI-patroon (Spring injecteert automatisch `List<BriefingSectionProvider>`) en de generieke sectie-rendering in `summary_screen.dart` volstaan. Health check-tab blijft ongewijzigd.

## Acceptance criteria

- Nieuwe `WasteSectionProvider` (`@Component`) in de `briefing`-module, gebruikt `WasteClient.upcomingPickups()`.
- `section()` toont alle ophaalmomenten in de komende 7 dagen (vandaag t/m +6 dagen), oplopend op datum, elk met bak-type; buiten dat venster wordt niets getoond.
- Lege 7-dagen-lijst → neutrale, niet-crashende tekst (geen exception, geen kapotte sectie in de API-response).
- `WasteSchedule.error` gezet → sectie faalt stil naar een duidelijke foutmelding-tekst, geen crash van de hele briefing.
- `shortSummary()` retourneert een "Zet vanavond de \<bak(ken)\> buiten"-achtige tekst zodra er morgen minstens één ophaalmoment is (meerdere types samengevoegd in één regel); `null` zodra er morgen geen ophaal is (of bij een fout), zodat `BriefingScheduler` de sectie overslaat in de push.
- Gekozen `order`-waarde plaatst de sectie logisch tussen de bestaande secties (geen botsing met bestaande waarden: -10, 0, 5, 10, 20, 30, 40).
- Geen wijziging nodig/toegestaan aan `BriefingService`, `BriefingController`, `BriefingSectionProvider`, `BriefingScheduler`, of enige frontend-bestand (`summary_screen.dart`, `health_check_screen.dart`) — nieuwe unit-test(s) voor `WasteSectionProvider` dekken `section()` (met/zonder pickups binnen 7 dagen, met error) en `shortSummary()` (met/zonder ophaal morgen, met error), naar het patroon van `WeekTasksSectionProviderTest`/`BeachCycleSectionProviderTest`.

## Aannames

- `WasteClient.upcomingPickups()` levert alle toekomstige ophaalmomenten (niet vooraf beperkt tot 7 dagen); de 7-dagen-filtering gebeurt dus in `WasteSectionProvider` zelf.
- Geen AI-call nodig voor deze sectie (in tegenstelling tot `WeekTasksSectionProvider`/`SystemStatusSectionProvider`) — de tekst is deterministisch op te bouwen uit `WastePickup`-data, wat testen zonder `RA_MOCK_AI`-afhankelijkheid vereenvoudigt.
- Exacte sectie-`key`/titel en precieze tekstopmaak (bv. "12-08: GFT" vs. een andere datumnotatie) worden door de developer ingevuld in de stijl van bestaande secties; dit is geen blokkerende keuze voor de refinement.
- Een concrete `order`-waarde (bv. 15, tussen Agenda en Weektaken) wordt door de developer gekozen mits die logisch tussen bestaande secties past — geen vaste waarde afgedwongen door deze story.

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summary-finished"}
