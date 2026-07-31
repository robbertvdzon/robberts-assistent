# SF-1595 - Worklog

Story-context bij eerste pickup:
WatchTools voor de assistent-chat + registratie in AiConfig + unit-test

Maak assistant/ai/WatchTools.kt (@Component met WatchService als constructor-dependency), exact in de stijl van ReminderTools.kt: Nederlandse @Tool-descriptions, @ToolParam per argument, korte Nederlandse zin als returnwaarde, geen exceptions naar buiten.

1. listWatches(): som WatchService.list() op met per regel titel, url, zoekinstructie, frequentie, status + statusDescription, actief ja/nee, lastCheckedAt (formatter 'EEEE d MMMM HH:mm' in Europe/Amsterdam zoals ReminderTools; null -> 'nog niet gecontroleerd') en de eerste 8 tekens van het id. Lege lijst -> vriendelijke Nederlandse melding.
2. createWatch(title, url, instruction, frequency, notifyOnFound): roept WatchService.create aan. frequency is vrije tekst -> KANTOORUREN bij 'kantooruren', DAGELIJKS bij 'dagelijks' (case-insensitive), leeg/onbekend -> DAGELIJKS. notifyOnFound optioneel, default true. WatchValidationException opvangen en als leesbare Nederlandse foutmelding teruggeven.
3. updateWatch(id, ...): zoek via WatchService.list().firstOrNull { it.id.startsWith(id) } (zoals deleteReminder), eerste treffer wint. Geen match -> 'Geen zoekopdracht gevonden met id ...'. Alleen meegegeven velden wijzigen; overige velden overnemen van de bestaande watch omdat WatchService.update alle velden verwacht. Let op: modelleer 'niet meegegeven' expliciet (optionele @ToolParam(required = false) met neutrale/lege default, en voor notifyOnFound een driewaardige vorm) zodat een niet-meegegeven veld niet stil overschreven wordt. Vermeld in de bevestigingstekst dat de zoekopdracht weer actief is en opnieuw gecontroleerd gaat worden (update reset status naar NOG_NIET_GECONTROLEERD en active naar true). Validatiefouten net als bij createWatch afvangen.

Registreer WatchTools als constructor-parameter van AiConfig.assistantChatClient(...) EN in defaultTools(...) - zonder dat werkt de chat-kant niet.

Schrijf src/test/kotlin/nl/vdzon/robbertsassistent/assistant/ai/WatchToolsTest.kt in dezelfde stijl als ReminderToolsTest.kt (echte WatchService op InMemoryWatchRepository, geen mocking-framework), met dekking voor: lege lijst, gevulde lijst, aanmaken, frequentie-parsing (kantooruren/dagelijks/onbekend), onbekend id bij update, gedeeltelijke update die overige velden behoudt, en een validatiefout (ongeldige URL).

Niet in scope: verwijderen via de chat, wijzigingen aan de watches-module/REST-API/datamodel en frontend. Draai zelf mvn test vanuit robberts-assistent-backend/ en doe een eigen review van je wijziging.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuw `assistant/ai/WatchTools.kt` (`@Component`, constructor-dependency `WatchService`), in de
  stijl van `ReminderTools.kt`: Nederlandse `@Tool`/`@ToolParam`-teksten, korte Nederlandse zin als
  returnwaarde, geen exceptions naar buiten.
  - `listWatches()`: `WatchService.list()` (actieve eerst, dan op titel) met per regel titel, url,
    zoekinstructie, frequentie, status + statusDescription, actief ja/nee, `lastCheckedAt` via
    dezelfde formatter als `ReminderTools` (`EEEE d MMMM HH:mm`, `Europe/Amsterdam`; `null` ->
    "nog niet gecontroleerd") en de eerste 8 tekens van het id. Lege lijst -> "Er lopen op dit
    moment geen zoekopdrachten."
  - `createWatch(...)`: frequentie als vrije tekst, case-insensitive "kantooruren" -> `KANTOORUREN`,
    al het andere (inclusief leeg/onbekend) -> `DAGELIJKS`; `notifyOnFound` default `true`;
    `WatchValidationException` wordt opgevangen en als Nederlandse foutmelding teruggegeven.
  - `updateWatch(...)`: match via `list().firstOrNull { it.id.startsWith(id) }` (zoals
    `deleteReminder`, eerste treffer wint), geen match -> "Geen zoekopdracht gevonden met id ...".
    Alleen meegegeven velden wijzigen; de rest wordt overgenomen van de bestaande watch omdat
    `WatchService.update` alle velden verwacht. `notifyOnFound` is bewust een driewaardige `String`
    ("ja"/"nee"/leeg) i.p.v. een `Boolean`, zodat "niet meegegeven" niet stil `false` wordt. De
    bevestiging vermeldt expliciet dat de zoekopdracht weer actief is en opnieuw gecontroleerd wordt.
- `AiConfig.assistantChatClient(...)`: `WatchTools` als parameter toegevoegd en in `defaultTools(...)`
  meegegeven. In `SYSTEM_PROMPT` één zin toegevoegd over de zoekopdrachten (de prompt somt alle
  capabilities op; zonder die regel zou de opsomming niet meer kloppen). Verder geen wijziging aan
  de `watches`-module, REST-API, datamodel of frontend.
- Nieuwe `WatchToolsTest.kt` (echte `WatchService` op `InMemoryWatchRepository`, geen mocking-
  framework), 9 tests: lege lijst, gevulde lijst (alle velden + id-prefix), aanmaken,
  frequentie-parsing (kantooruren/dagelijks/onbekend/leeg), validatiefout bij aanmaken (ongeldige
  URL), onbekend id bij update, gedeeltelijke update van een gevonden/inactieve watch die de
  overige velden behoudt en de watch weer actief zet, driewaardige pushvoorkeur, en een
  validatiefout bij update.

Testresultaat:
- `rm -rf target && mvn -o test` vanuit `robberts-assistent-backend/`: BUILD SUCCESS,
  350 tests, 0 failures, 0 errors — inclusief `WatchToolsTest` (9) en `ModulithArchitectureTest`.
  (`mvn clean` kan niet offline, vandaar `rm -rf target`.)

Review (SF-1622, reviewer):
- Volledige story-diff t.o.v. `main` beoordeeld (`AiConfig.kt`, `WatchTools.kt`, `WatchToolsTest.kt`,
  worklog). Alle acceptatiecriteria gedekt; geen wijziging aan `watches`-module, REST-API of
  frontend, dus scope klopt.
- Eigen gerichte verificatie: `mvn -o test -Dtest='WatchToolsTest,ModulithArchitectureTest,
  WatchesControllerTest'` groen (WatchToolsTest 9/9, ModulithArchitectureTest 1/1) en
  `AssistantControllerTest,BriefingControllerTest` groen — bevestigt dat de nieuwe `WatchTools`-bean
  correct in de Spring-context wiret.
- [suggestie] `createWatch`'s `notifyOnFound: Boolean = true` is een Kotlin-default op een primitieve
  parameter. Spring AI's `MethodToolCallback` roept `Method.invoke` aan met `null` voor een
  weggelaten `required = false`-argument; Kotlin-defaults worden daarbij niet toegepast, dus een
  call zonder `notifyOnFound` zou op een argument-type-mismatch stuklopen i.p.v. `true` te gebruiken.
  Zelfde patroon als het bestaande `ReminderTools.everyInterval`, en de story schrijft deze vorm
  expliciet voor — daarom geen blocker, maar het is de reden dat `updateWatch`'s driewaardige
  `String` robuuster is.
- [suggestie] Een lege `id` bij `updateWatch` matcht via `startsWith("")` de eerste zoekopdracht in
  de lijst (die dan ook op actief/`NOG_NIET_GECONTROLEERD` gereset wordt). Identiek aan het bestaande
  `deleteReminder`-gedrag; een expliciete blank-check zou dat afvangen.
- [info] `WatchService.update` kan `WatchNotFoundException` gooien als de watch tussen `list()` en
  `update()` verdwijnt; alleen `WatchValidationException` wordt afgevangen. Zeer smalle race,
  geen actie nodig.
- Conclusie: akkoord.
