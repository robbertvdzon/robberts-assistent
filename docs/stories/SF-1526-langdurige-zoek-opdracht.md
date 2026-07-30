# SF-1526 - langdurige zoek opdracht

## Story

langdurige zoek opdracht

<!-- refined-by-factory -->

## Samenvatting
Robbert wil zoekopdrachten kunnen aanmaken die de assistent periodiek uitvoert, bijvoorbeeld
"waarschuw me zodra dit product weer op voorraad is op deze site". Hiervoor komt een nieuwe
tab "Zoekopdrachten" in de app. Daar ziet hij een lijst van zijn zoekopdrachten met titel en
status ("nog steeds uitverkocht" / "nu beschikbaar"), en kan hij nieuwe opdrachten aanmaken
met een titel, een link en een instructie, en aangeven hoe vaak er gecheckt moet worden en of
hij een pushmelding wil als het gevonden is.

## Scope
**Backend — nieuwe module `watches`:**
- Model `Watch`: `id`, `title` (bv. "aaltjes tegen slakken"), `url`, `instruction` (vrije tekst,
  bv. "geef een seintje als ze weer beschikbaar zijn"), `frequency` (`KANTOORUREN` of
  `DAGELIJKS`), `notifyOnFound` (boolean), `status` (`ONBEKEND` / `NIET_GEVONDEN` / `GEVONDEN`),
  `statusText` (vrije tekst-toelichting, bv. "nog steeds uitverkocht"), `active` (boolean),
  `lastCheckedAt` (nullable timestamp).
- `WatchRepository`-poort met Firestore-implementatie (collectie `watches`) en in-memory
  fallback, zelfde patroon als `reminders.ReminderRepository`.
- REST: `GET/POST /api/v1/watches`, `PUT/DELETE /api/v1/watches/{id}` (CRUD, auth verplicht,
  zelfde stijl als `RemindersController`).
- Eén `@Scheduled` poller (`ra.watches.poll-interval-ms`, default 300000) die per actieve watch
  een pure "is deze aan de beurt?"-functie toepast op basis van `frequency`:
  - `KANTOORUREN`: ma–vr, 09:00–17:00, elk uur.
  - `DAGELIJKS`: één keer per dag.
- Bij een beurt: pagina ophalen via `java.net.http.HttpClient` + een eigen `htmlToPlainText()`-
  kopie in de `watches`-module (de bestaande variant in `assistant.ai.WindTools` is `internal`
  en niet herbruikbaar over modulegrenzen heen, bewaakt door `ModulithArchitectureTest`).
- Beoordeling via een losse, tool-loze `watchChatClient` (patroon `briefing.BriefingAiConfig`):
  input = instructie + paginatekst, vast antwoordformaat regel 1 = `GEVONDEN`/`NIET GEVONDEN`,
  regel 2 = een korte statuszin; defensief geparsed (bij een onverwacht/leeg antwoord, ook onder
  `RA_MOCK_AI`/`MockChatModel`, valt de status terug op `ONBEKEND` zonder te crashen).
- Bij een transitie naar `GEVONDEN` (dus niet bij elke poll die al `GEVONDEN` was): als
  `notifyOnFound = true`, precies één `PushService.sendToAll(...)` met `data["type"] = "watch"`,
  en daarna wordt de watch op `active = false` gezet (stopt met pollen totdat de gebruiker 'm
  weer activeert/aanpast).

**Frontend — nieuwe tab in `robberts_assistent`:**
- Nieuwe zesde tab "Zoekopdrachten" op index 4 (vóór "Meer", dat opschuift naar index 5);
  `home_screen.dart`s beide parallelle lijsten (screens in de `IndexedStack` +
  `NavigationBar.destinations`) worden beide uitgebreid. Bestaande tabs/indices (0=Upcoming,
  1=Health check, 2=Assistent, 3=Herinneringen) en de briefing-deep-link blijven ongewijzigd.
- Lijstscherm (patroon: CRUD-lijst + dialoog, zoals `schedules_screen.dart`/
  `conversations_screen.dart`/`health_check_screen.dart`): per zoekopdracht titel + status-tekst;
  aanmaken/bewerken via een dialoog met titel, url, instructie, frequentie
  (kantooruren/dagelijks) en een schakelaar voor pushmelding; verwijderen met bevestiging.
- `ApiClient` krijgt de CRUD-methoden tegen `/api/v1/watches`.

## Acceptance criteria
1. Op de nieuwe tab "Zoekopdrachten" kan een gebruiker een zoekopdracht aanmaken met titel, url
   en instructie, en daarbij een check-frequentie (kantooruren of dagelijks) en een
   pushmelding-voorkeur instellen.
2. De lijst toont per zoekopdracht de titel en de actuele status (bv. "nog steeds uitverkocht"
   of "nu beschikbaar"), en een zoekopdracht kan bewerkt of verwijderd worden.
3. De backend controleert elke actieve zoekopdracht periodiek volgens de ingestelde frequentie,
   haalt de pagina op en laat de AI beoordelen of aan de instructie is voldaan; de status in de
   lijst wordt hierop bijgewerkt.
4. Zodra een zoekopdracht overgaat van "niet gevonden" naar "gevonden" én pushmeldingen aanstaan,
   ontvangt de gebruiker precies één pushbericht; de zoekopdracht stopt daarna met pollen totdat
   de gebruiker 'm opnieuw activeert of aanpast.
5. Falen van de paginaophaal- of AI-beoordelingsstap voor één zoekopdracht crasht de poller niet
   en beïnvloedt andere zoekopdrachten niet.
6. Bestaande tabs, indices en de briefing-pushdeep-link blijven functioneel ongewijzigd.

## Aannames
- Precies twee frequenties volstaan voor nu: "kantooruren" (ma–vr 09:00–17:00, elk uur) en
  "1 keer per dag"; geen vrij instelbaar interval of cron-expressie.
- Titel, url en instructie zijn losse velden die de gebruiker apart invult (geen automatische
  extractie van een url uit vrije tekst).
- Er is geen aparte pushmelding bij falende checks (alleen bij de transitie naar "gevonden").
- De paginainhoud wordt als platte tekst (HTML gestript) aan de AI gegeven, geen screenshot/
  vision-aanpak.
- Eén globale poll-interval-configuratie (`ra.watches.poll-interval-ms`) is voldoende; de
  "aan de beurt?"-bepaling per watch gebeurt in code, niet via losse per-watch cron-triggers.

## Eindsamenvatting

Ik heb voldoende context uit het worklog. De samenvatting is duidelijk genoeg om direct de eindsamenvatting te schrijven.

## Eindsamenvatting SF-1526 — Langdurige zoekopdracht

**Gebouwd:**

Een nieuwe functie waarmee Robbert periodieke zoekopdrachten kan aanmaken die de assistent automatisch controleert (bv. "waarschuw me zodra dit product weer op voorraad is").

- **Backend** — nieuwe module `watches`, gebouwd naar het bestaande patroon van `reminders`/`briefing`: CRUD-endpoints (`/api/v1/watches`), opslag in Firestore met in-memory fallback, en een scheduler die actieve zoekopdrachten volgens de ingestelde frequentie (kantooruren of dagelijks) controleert: pagina ophalen, tekst laten beoordelen door een AI-model, en bij een overgang naar "gevonden" precies één pushmelding sturen waarna de zoekopdracht automatisch stopt met pollen.
- **Frontend** (`robberts_assistent`) — nieuwe tab "Zoekopdrachten" (6e tab, vóór "Meer"): lijst met titel + status, aanmaken/bewerken via een dialoog (titel, url, instructie, frequentie, pushmelding-schakelaar), verwijderen met bevestiging.

**Belangrijke keuzes:**
- Een gestopte (inactieve) zoekopdracht heeft geen apart "activeer"-knopje — bewerken reactiveert 'm automatisch en wist de vorige beoordeling.
- HTML-naar-tekst-conversie is als eigen kopie in de `watches`-module gebouwd omdat de bestaande variant in een andere module niet herbruikbaar was (modulegrenzen).
- Fouten bij het ophalen van een pagina of de AI-beoordeling worden per zoekopdracht geïsoleerd afgevangen — één falende check beïnvloedt andere zoekopdrachten niet en wordt bij de volgende ronde opnieuw geprobeerd.

**Getest:**
- Backend: volledige testsuite (329 tests) groen, inclusief gerichte tests voor de tijdlogica, de defensieve AI-antwoordparsing, en de scheduler (transitie-naar-gevonden, push, isolatie van fouten).
- Frontend: volledige testsuite (41 tests) + `flutter analyze` groen.
- End-to-end geverifieerd op een preview-omgeving: CRUD-flow via de API, validatie op ongeldige invoer, en met screenshots bevestigd dat de nieuwe tab, lege staat, aanmaak- en bewerk-dialoog er correct uitzien.
- De daadwerkelijke periodieke poll en de push-bij-transitie zijn op de preview niet binnen de testtijd te forceren (mock-AI geeft altijd een negatieve uitkomst) — dit gedrag is wel volledig gedekt door backend-unittests.

**Bewust niet gedaan:**
- Geen aparte controller-test (MockMvc) — consistent met het ontbreken daarvan bij vergelijkbare modules zoals `reminders`.
- Geen nieuwe koppeling/`CouplingProbe` — er is geen nieuw secret nodig.
- Geen los "activeren"-knopje in de UI, conform de scope-aanname.

Geen bugs gevonden tijdens test. Geen blockers voor verdere voortgang.
