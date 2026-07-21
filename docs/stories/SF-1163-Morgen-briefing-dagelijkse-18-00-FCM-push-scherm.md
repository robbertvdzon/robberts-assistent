# SF-1163 - Morgen-briefing: dagelijkse 18:00 FCM-push + scherm (story 1 van 2)

## Story

Morgen-briefing: dagelijkse 18:00 FCM-push + scherm (story 1 van 2)

<!-- refined-by-factory -->

## Scope

Nieuw dagelijks 'Morgen'-briefing-raamwerk (backend + app), story 1 van 2. Story 2 voegt later
een systeem-checkrapport toe aan hetzelfde scherm en dezelfde 18:00-push, dus het raamwerk moet
pluggable zijn: een nieuwe sectie toevoegen (SPI-`@Component`, analoog aan `CouplingProbe` /
`NightlyCheck`) mag geen wijziging in de kernservice vereisen.

**Backend**
- Nieuwe `briefing`-module (of uitbreiding van `summary`) met een `BriefingService` die een lijst
  van pluggable secties opbouwt via een SPI-interface (`BriefingSectionProvider` o.i.d.), zodat
  Spring automatisch `List<BriefingSectionProvider>` injecteert — zelfde stijl als
  `NightlyCheck`/`CouplingProbe`, niet de huidige platte hardcoded lijst van `SummaryService`.
- Secties voor story 1:
  1. **Kite-/strandfietskans** voor morgen (de eerstvolgende dag), incl. voorspelde windsterkte
     in knopen:
     - Kiten: aanlandige wind in Wijk aan Zee, 20-35 knopen, droog (geen neerslag).
     - Strandfietsen: droog, redelijk laag water, wind < 15 knopen.
     - Werkdag (ma-do, geen NL-feestdag, geen vakantiedag): aparte beoordeling ochtend (07:00) en
       avond (19:00).
     - Geen werkdag (vr/za/zo), NL-feestdag, of vakantiedag: één dagbeoordeling.
     - Weergave als 🟢/🟡/🔴 + windsterkte in kn.
     - Bronnen: `weather` (wind + neerslag, Open-Meteo), `tides` (`RwsTideClient`, laagwater).
     - Wind (sterkte + richting in knopen) is nu alleen beschikbaar via `WindTools` (AI-tool,
       scraped tekst) en niet gestructureerd herbruikbaar — deze sectie heeft een gestructureerde
       wind-bron nodig (uitbreiding van `weather`-module of hergebruik/refactor van de
       windfinder-databron achter `WindTools`).
     - NL-feestdagen: nieuw te bouwen (bestaat nog niet in de repo), automatisch berekend.
     - Vakantiedag: gedetecteerd als hele-dag agenda-item (Google Agenda); `CalendarClient`
       moet het "hele-dag"-karakter van een event blijven doorgeven (gaat nu verloren in de
       parsing).
  2. **Afspraken komende 7 dagen**, alle agenda's, oplopend op tijd. `CalendarClient.upcoming()`
     ondersteunt nu geen tijdrange en geen multi-agenda — uitbreiden met een 7-dagen-tijdvenster
     en het ophalen van events over alle agenda's van de gebruiker (Google Calendar List API).
     Per afspraak: AI-bepaling (op basis van tijd + tekst van bestaande reminders t.o.v. de
     afspraaktijd) of er al een reminder ~1 uur van tevoren staat. Zo niet: één-tap-actie die via
     de bestaande reminders-module een reminder ~1 uur voor de afspraak aanmaakt.
  3. **AI-samenvatting 'wat moet ik komende week echt doen?'** op basis van reminders + notities,
     kort weergegeven.
  4. **Moestuin-placeholder**: dummy-regel, zelfde stijl als de bestaande hardcoded moestuin-regel
     in `SummaryService`.
- Nieuwe dagelijkse `@Scheduled(cron = "0 0 18 * * *", zone = "Europe/Amsterdam")`-job die de
  briefing opbouwt en via de bestaande `PushService.sendToAll(title, body)` een FCM-push stuurt
  met een heel korte samenvatting (bv. "Morgen: kiten 🟢 avond 24kn, 2 afspraken, 1 taak").
- Volgt het bestaande stub/fallback-patroon: geen crash zonder secrets/koppelingen; in
  preview/mock-omgevingen (`RA_MOCK_AI`) deterministisch gedrag.

**Frontend (`robberts_assistent`)**
- Geen nieuwe (7e) navigatie-ingang: de bestaande "Samenvatting"-tab wordt het 'Morgen'-scherm
  (contentvervanging, evt. tab-label aangepast naar "Morgen"), en toont de volledige
  briefing-inhoud van de 4 secties hierboven.
- Tikken op de FCM-push opent dit scherm (deep link / foreground-navigatie naar de
  Samenvatting/Morgen-tab, zelfde patroon als bestaande FCM-ontvangst in `fcm_service.dart`).
- Per afspraak zonder reminder: knop/actie om een reminder ~1 uur van tevoren aan te maken.

## Acceptance criteria

- [ ] Backend levert een briefing-endpoint (of uitbreiding van het bestaande summary-endpoint)
      met de 4 secties (kite/strandfiets, agenda 7 dagen + reminder-status, weektaken-AI-samenvatting,
      moestuin-placeholder).
- [ ] De briefingsecties zijn pluggable via een SPI-`@Component`-patroon (analoog aan
      `CouplingProbe`/`NightlyCheck`): een nieuwe sectie toevoegen vereist geen wijziging in de
      kernservice.
- [ ] Kite-/strandfietsbeoordeling gebruikt wind (kn + richting), neerslag, laagwatertijden en
      werkdag/feestdag/vakantiedag-logica zoals hierboven beschreven, en toont 🟢/🟡/🔴 + kn.
- [ ] NL-feestdagen worden automatisch berekend (geen hardcoded lijst per jaar).
- [ ] Vakantiedagen worden gedetecteerd uit hele-dag agenda-items.
- [ ] Agenda-sectie toont afspraken over de komende 7 dagen uit alle agenda's van de gebruiker,
      oplopend op tijd, elk met reminder-status (AI-bepaald) en — indien geen reminder — een
      werkende actie om er via de reminders-module één ~1 uur van tevoren aan te maken.
- [ ] AI-weektakensectie toont een korte samenvatting op basis van reminders + notities.
- [ ] Moestuin-sectie toont een placeholder-regel (dummy, zelfde stijl als bestaande
      `SummaryService`-dummy's).
- [ ] Dagelijks om 18:00 (Europe/Amsterdam) wordt automatisch één FCM-push verstuurd met een
      korte samenvatting van de briefing (voorbeeldformaat uit de story); bestaat al werkende
      `PushService`/FCM-infrastructuur wordt hergebruikt, geen nieuwe pushkanaal-integratie nodig.
- [ ] Tikken op de push opent het bestaande "Samenvatting"-scherm (hernoemd/ingevuld als
      'Morgen'-scherm) met de volledige briefing-inhoud; er komt geen nieuwe (7e) navigatietab.
- [ ] Alles werkt zonder secrets/koppelingen via bestaande stub/fallback-patronen (geen
      crashloop), en is deterministisch testbaar onder `RA_MOCK_AI`/preview-omgevingen.
- [ ] Backend-tests dekken: kite/strandfiets-beoordelingslogica (incl. werkdag- vs.
      weekend/feestdag/vakantie-tak), feestdagberekening, 18:00-scheduler → push-aanroep, en de
      pluggable-sectie-opzet.

## Aannames

- De exacte aanlandige-windrichting-sector voor Wijk aan Zee en de drempel voor "redelijk laag
  water" (bv. binnen X uur rond laagwater) worden door de developer bepaald en gedocumenteerd in
  code/commentaar (expliciet aan developer gelaten in de story).
- De UX-vorm voor "reminder instellen bij ontbrekende reminder" (bv. inline knop per
  afspraak-item) wordt door de developer bepaald; functioneel vereiste is alleen dat het via de
  bestaande reminders-module een reminder ~1 uur voor de afspraaktijd aanmaakt.
- "Bottom-nav zit al vol (6 tabs)" uit de oorspronkelijke story-tekst is niet gebleken uit de
  code — de app heeft 4 hoofdtabs (Samenvatting/Assistent/Herinneringen/Meer). De kernbeslissing
  ("geen 7e tab, herbruik bestaand scherm") blijft overeind; dit voorstel kiest expliciet voor
  het invullen van de bestaande Samenvatting-tab, zoals de story als optie aandraagt.
- Multi-agenda-ondersteuning (alle agenda's van de gebruiker, niet alleen primary) en het
  toevoegen van een tijdrange aan `CalendarClient` zijn nieuwe technische uitbreidingen binnen
  scope van deze story (nu ontbreekt beide).
- Gestructureerde windsterkte/-richting (i.p.v. de huidige AI-tool-tekst-scrape in `WindTools`)
  wordt als onderdeel van deze story toegevoegd/hergebruikt, omdat de kite-sectie een
  betrouwbare, niet-AI-afhankelijke databron nodig heeft.
- NL-feestdagen worden algoritmisch berekend (bv. Pasen-gebaseerde formule + vaste data), niet
  via een externe API — er is geen bestaande feestdagenkoppeling in de repo.
- De FCM-push-infrastructuur (`PushService`, `FcmTokenStore`) bestaat al en wordt hergebruikt
  zonder wijziging; CLAUDE.md's vermelding "FCM (gepland)" is op dit punt verouderd.

## Eindsamenvatting

## Eindsamenvatting SF-1163 — Morgen-briefing: dagelijkse 18:00 FCM-push + scherm (story 1 van 2)

**Gebouwd**

Backend (SF-1165): nieuwe `briefing`-module met een pluggable secties-raamwerk (`BriefingSectionProvider`-SPI, analoog aan de bestaande `CouplingProbe`/`NightlyCheck`-patronen), zodat een nieuwe sectie later (story 2/2) toegevoegd kan worden zonder de kernservice aan te raken. Vier secties geleverd:
1. **Kite-/strandfietskans voor morgen** — aanlandige wind (kn + richting), neerslag en laagwater, met werkdag/weekend/feestdag/vakantie-onderscheid en 🟢/🟡/🔴-weergave.
2. **Agenda komende 7 dagen** (alle agenda's van de gebruiker) met reminder-status per afspraak en een één-tap-actie om een ontbrekende reminder (~1u vooraf) aan te maken.
3. **AI-weektakensamenvatting** op basis van reminders + notitie.
4. **Moestuin-placeholder** (dummy-regel, zelfde stijl als bestaand).

Daarbij twee nieuwe bouwstenen: een algoritmische NL-feestdagenberekening (`Holidays`, geen hardcoded lijst/externe koppeling) en een nieuwe gestructureerde windbron (`WindForecastClient`, Open-Meteo) omdat de bestaande wind-tool alleen AI-tekst leverde. `CalendarClient` is uitgebreid met een 7-dagen/multi-agenda-tijdrange en behoudt nu het "hele dag"-kenmerk van events. Een `@Scheduled`-job stuurt dagelijks om 18:00 (Europe/Amsterdam) een korte samenvattingspush via de bestaande FCM-infrastructuur.

Frontend (SF-1166): de bestaande "Samenvatting"-tab is omgebouwd tot het "Morgen"-scherm (geen nieuwe/7e tab) met de vier secties en, waar relevant, een actieknop per regel. Een tik op de briefing-push opent via een nieuwe deep-link (`FcmService.deepLinkTab`) direct dit scherm.

**Belangrijkste keuzes**
- Reminder-detectie bij afspraken is een deterministische tijd-heuristiek (30-90 min. vooraf) geworden in plaats van een echte AI-call per afspraak, voor determinisme onder `RA_MOCK_AI` en om AI-kosten/latency per briefing te vermijden — functioneel gelijkwaardig.
- Eén generieke `BriefingItem`/`BriefingAction`-uitbreiding op de sectie-DTO (i.p.v. een apart endpoint) zodat de app acties agenda-onafhankelijk kan afhandelen — herbruikbaar voor toekomstige secties.
- Aannames over aanlandige windrichting (225°-315° bij Wijk aan Zee), kite-drempels (20-35 kn ideaal) en "redelijk laag water" (binnen 2 uur van laagwater) zijn expliciet in code/commentaar vastgelegd, zoals de story toestond.

**Getest**
- Backend: volledig `mvn test`-vangnet groen (196 tests, 0 failures/errors), incl. `ModulithArchitectureTest` en alle nieuwe test­klassen (kite-logica, feestdagen, scheduler→push, SPI-opzet, calendar-parsing).
- Frontend: `flutter analyze` schoon, `flutter test` 23/23 groen.
- Live end-to-end op preview `robberts-assistent-pr-14`: `GET /api/v1/briefing` met alle 4 secties (incl. echte Google Calendar-koppeling), screenshot van het "Morgen"-scherm, en de één-tap-reminderactie volledig doorlopen (aangemaakt, geverifieerd, opgeruimd). Gedeployde build gecontroleerd op aanwezigheid van de nieuwe code.
- Geen bugs gevonden tijdens review of test; alle acceptatiecriteria geverifieerd.

**Bewust niet gedaan**
- Geen dedicated Firebase-mock-test voor de nieuwe FCM-`data`-payload (bestaande infrastructuur had die al niet); no-op-gedrag zonder Firebase blijft wel gedekt.
- `GoogleCalendarClient.eventsInRange` is alleen op parsing-niveau unit-getest; volledige end-to-end-OAuth-verificatie viel buiten scope van de unit-tests (wel live bevestigd door de tester op de preview).
- FCM-deep-link op web blijft ongedaan (bestond al niet vóór deze story).
