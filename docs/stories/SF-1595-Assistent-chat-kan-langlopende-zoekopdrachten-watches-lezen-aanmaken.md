# SF-1595 - Assistent-chat kan langlopende zoekopdrachten (watches) lezen, aanmaken en aanpassen

## Story

Assistent-chat kan langlopende zoekopdrachten (watches) lezen, aanmaken en aanpassen

<!-- refined-by-factory -->

## Samenvatting

Je kunt straks in de Assistent-chat gewoon in gewone taal vragen welke
zoekopdrachten er lopen, een nieuwe zoekopdracht laten aanmaken en een
bestaande laten aanpassen. Nu kan dat alleen nog via het aparte
Zoekopdrachten-scherm in de app.

Bijvoorbeeld: "welke zoekopdrachten lopen er?", "houd deze pagina in de gaten
en zeg het als er X op staat, elke dag", of "zet zoekopdracht Vakantiehuis op
kantooruren". Wat je via de chat aanmaakt of wijzigt, zie je meteen terug in
het Zoekopdrachten-scherm. Verwijderen via de chat kan bewust nog niet.

## Scope

Alleen backend, module `assistant` (+ registratie in `AiConfig`). Geen
wijziging aan de `watches`-module, de REST-API of de Flutter-apps.

**Nieuw: `assistant/ai/WatchTools.kt`** — `@Component` met `WatchService` als
constructor-dependency, exact in de stijl van `ReminderTools.kt` (Nederlandse
`@Tool`-description, `@ToolParam` per argument, korte Nederlandse zin als
returnwaarde, geen exceptions naar buiten).

1. `listWatches()` — somt alle zoekopdrachten op (`WatchService.list()`, dus
   actieve eerst, daarna op titel). Per regel: titel, url, zoekinstructie,
   frequentie, status + `statusDescription`, of 'ie actief is, het laatste
   controlemoment (`lastCheckedAt`, geformatteerd in `Europe/Amsterdam` met
   dezelfde formatter als `ReminderTools`; leeg = "nog niet gecontroleerd") en
   de eerste 8 tekens van het id. Lege lijst → één vriendelijke Nederlandse
   melding.
2. `createWatch(title, url, instruction, frequency, notifyOnFound)` — roept
   `WatchService.create(...)` aan. `frequency` is vrije tekst: `kantooruren` →
   `KANTOORUREN`, `dagelijks` → `DAGELIJKS`, case-insensitive; leeg of
   onbekend → `DAGELIJKS`. `notifyOnFound` default `true`. Een
   `WatchValidationException` (ongeldige URL, lege titel, lege instructie)
   wordt opgevangen en als Nederlandse foutmelding teruggegeven.
3. `updateWatch(id, ...)` — zoekt de zoekopdracht op via het (begin van het)
   id met `startsWith` over `WatchService.list()`, net als
   `deleteReminder`. Alleen meegegeven velden wijzigen; niet-meegegeven velden
   worden overgenomen van de bestaande watch, omdat `WatchService.update` alle
   velden verwacht. Geen match → "Geen zoekopdracht gevonden met id ...".
   Validatiefouten net als bij `createWatch`. De bevestigingstekst vermeldt
   expliciet dat de zoekopdracht weer op actief staat en opnieuw gecontroleerd
   wordt (`update` reset `status` naar `NOG_NIET_GECONTROLEERD` en `active`
   naar `true`).

**Registratie**: `WatchTools` wordt als parameter aan
`AiConfig.assistantChatClient(...)` toegevoegd en meegegeven in
`defaultTools(...)`, naast de bestaande tools — zonder dat werkt de chat-kant
niet.

### Niet in scope

- Zoekopdrachten verwijderen via de chat.
- Frontend-wijzigingen (`watches_screen.dart`, `api_client.dart`).
- Wijzigingen aan `watches`-module, `WatchesController`, `WatchRunner` of het
  datamodel.

## Acceptance criteria

- "Welke zoekopdrachten lopen er?" in de assistent-chat geeft de lijst met
  zoekopdrachten inclusief status en statusomschrijving; zonder
  zoekopdrachten een nette melding in plaats van een lege lijst.
- "Houd https://... in de gaten en laat het weten als daar X op staat, elke
  dag" maakt via de chat een zoekopdracht aan die daarna ongewijzigd zichtbaar
  is in het Zoekopdrachten-scherm van de app.
- "Zet zoekopdracht \<titel\> op kantooruren" past de frequentie aan terwijl
  titel, url, instructie en pushvoorkeur behouden blijven; het antwoord meldt
  dat de zoekopdracht weer actief is en opnieuw gecontroleerd gaat worden.
- Een ongeldige URL of lege instructie levert een leesbare Nederlandse
  foutmelding op in de chat, geen stacktrace of tool-fout.
- Een onbekend (begin van een) id levert "Geen zoekopdracht gevonden met id
  ...".
- Nieuwe unit-test
  `src/test/kotlin/nl/vdzon/robbertsassistent/assistant/ai/WatchToolsTest.kt`
  in dezelfde stijl als `ReminderToolsTest.kt` (echte `WatchService` op
  `InMemoryWatchRepository`, geen mocking-framework), met dekking voor: lege
  lijst, gevulde lijst, aanmaken, frequentie-parsing (kantooruren/dagelijks/
  onbekend), onbekend id bij update, gedeeltelijke update die overige velden
  behoudt, en een validatiefout.
- `mvn test` vanuit `robberts-assistent-backend/` is groen, inclusief
  `ModulithArchitectureTest`.

## Aannames

- De `watches`-module is een gewone Spring-Modulith-module zonder
  named-interface-restricties, dus `assistant` mag `WatchService` injecteren
  net zoals het nu al `RemindersService` injecteert; `ModulithArchitectureTest`
  blijft daarmee groen.
- Bij het aanpassen op titel ("zet zoekopdracht \<titel\> op kantooruren")
  roept het model eerst `listWatches()` aan om het id te vinden en daarna
  `updateWatch` met dat id — er komt geen aparte zoek-op-titel-parameter.
- Matcht het opgegeven id-prefix meerdere zoekopdrachten, dan wordt de eerste
  treffer gebruikt (zelfde gedrag als `deleteReminder`).
- Niet-meegegeven update-velden worden gemodelleerd als optionele
  `@ToolParam(required = false)`-parameters met lege/neutrale defaults; een
  lege waarde betekent "niet wijzigen".
- De datum/tijd-opmaak volgt `ReminderTools.FORMATTER`
  (`EEEE d MMMM HH:mm`, `Europe/Amsterdam`).
- Geen aparte handmatige verificatie op prod nodig; de bestaande
  chat-als-testharness plus de unit-test volstaan.

## Eindsamenvatting

## Eindsamenvatting SF-1595 — Zoekopdrachten (watches) via de Assistent-chat

**Wat is gebouwd**

De assistent-chat kan nu zelf met langlopende zoekopdrachten werken, in gewone taal:

- **Opvragen** — "welke zoekopdrachten lopen er?" geeft per opdracht titel, webadres, zoekinstructie, frequentie, status + statusomschrijving, actief ja/nee, het laatste controlemoment (Nederlandse datum/tijd, Europe/Amsterdam; leeg = "nog niet gecontroleerd") en een kort id. Zijn er geen opdrachten, dan komt er één nette melding in plaats van een lege lijst.
- **Aanmaken** — "houd deze pagina in de gaten en zeg het als er X op staat, elke dag" maakt een zoekopdracht aan. Frequentie mag vrije tekst zijn ("kantooruren" / "dagelijks"); onbekend of leeg wordt dagelijks. Pushmelding staat standaard aan.
- **Aanpassen** — "zet zoekopdracht \<titel\> op kantooruren" wijzigt alleen wat je noemt; de overige velden blijven ongewijzigd. Het antwoord meldt expliciet dat de opdracht weer actief is en opnieuw gecontroleerd gaat worden.

Wat via de chat wordt aangemaakt of gewijzigd, is meteen zichtbaar in het bestaande Zoekopdrachten-scherm in de app — het is dezelfde onderliggende dienst.

**Gemaakte keuzes**

- Alleen backend, in de bestaande chat-module (`assistant/ai/WatchTools.kt` + registratie in `AiConfig`). De zoekopdrachten-module zelf, de REST-API, het datamodel en de Flutter-apps zijn niet aangeraakt.
- Opgezet in exact dezelfde stijl als de bestaande herinneringen-tools, zodat het patroon herkenbaar blijft.
- Fouten (ongeldig webadres, lege titel of instructie, onbekend id) komen als leesbare Nederlandse zin terug in de chat — nooit als technische fout of stacktrace.
- Bij het aanpassen is "niet meegegeven" bewust apart gemodelleerd, zodat een veld dat je niet noemt niet stilzwijgend wordt overschreven (bv. de pushvoorkeur).
- Eén zin toegevoegd aan de systeemprompt van de assistent, zodat de opsomming van wat 'ie kan blijft kloppen.

**Wat is getest**

- Nieuwe unit-tests (9) zonder mocking-framework, tegen de echte dienst: lege lijst, gevulde lijst, aanmaken, frequentie-varianten, onbekend id, gedeeltelijke wijziging die de rest behoudt, en foutmeldingen.
- Volledige backend-testsuite groen: 350 tests, 0 fouten, inclusief de architectuurtest die de modulegrenzen bewaakt.
- Op de preview-omgeving is geverifieerd dat de app opstart met de nieuwe tools en dat de chat antwoordt; daarnaast is end-to-end bevestigd dat een aangemaakte zoekopdracht ongewijzigd in het Zoekopdrachten-scherm verschijnt (testdata daarna opgeruimd).
- Een echte tool-aanroep via de chat is op preview niet af te dwingen (daar draait een mock-AI die per ontwerp geen tools aanroept); dat pad is via de unit-tests gedekt.

**Bewust niet gedaan**

- Zoekopdrachten **verwijderen** via de chat — buiten scope.
- Geen frontend-wijzigingen en geen wijzigingen aan de zoekopdrachten-module of REST-API.
- Twee kleine, bekende randgevallen zijn geaccepteerd omdat ze identiek zijn aan het bestaande, in productie draaiende herinneringen-patroon: een leeg id bij aanpassen pakt de eerste opdracht uit de lijst, en laat het model bij een aanmaak-verzoek de pushvoorkeur helemaal weg, dan komt er een foutmelding terug (waarna het model het opnieuw kan proberen) in plaats van de standaardwaarde.
