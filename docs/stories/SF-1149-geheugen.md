# SF-1149 - geheugen

## Story

geheugen

<!-- refined-by-factory -->

## Scope

Het assistent-geheugen verandert van een lijst losse `MemoryItem`s naar **één grote vrije-tekst-string** per gebruiker. De AI-agent werkt die ene string na elke chat-beurt bij (in plaats van een lijst te reconciliëren), en de app krijgt één groot bewerkbaar tekstveld in plaats van een lijst met toevoeg/bewerk/verwijder-dialogen.

**Backend (`assistant`-module):**
- `MemoryRepository`/`MemoryItem`/`FirestoreMemoryRepository`/`InMemoryMemoryRepository` vervangen door een opslag voor één tekst-document (bv. `MemoryText`/`MemoryStore` met `get(): String` en `save(text: String)`), Firestore-fallback-patroon blijft (collectie/document `assistant-memory`, in-memory fallback zoals nu).
- REST-endpoints `GET/POST/PUT/DELETE /api/v1/assistant/memory(/{id})` vervangen door simpele `GET /api/v1/assistant/memory` (huidige tekst) en `PUT /api/v1/assistant/memory` (volledige tekst opslaan, door de gebruiker bewerkt in de app).
- `AssistantService.buildPromptText`: geheugen-context in de prompt is nu direct de opgeslagen string (geen `- item`-lijst meer opbouwen).
- `AssistantService.updateMemoryFromExchange`/`AiConfig.memoryChatClient`: de AI-aanroep krijgt de huidige geheugen-string + de laatste vraag/antwoord-uitwisseling mee, en retourneert de **volledige nieuwe geheugen-tekst** (geen lijst met regels meer om te reconciliëren tegen bestaande items — dus `parseMemoryLines`/`reconcileMemory` vervallen, het antwoord wordt direct opgeslagen als nieuwe tekst). Blijft stil falen (geheugen ongewijzigd) bij een fout of leeg antwoord; blijft overgeslagen onder `RA_MOCK_AI`.
- Bestaande tests (`MemoryRepositoryTest.kt` en gerelateerde `AssistantService`/`AssistantController`-tests) aanpassen aan het nieuwe model.

**App (`robberts_assistent`):**
- `memory_screen.dart`: lijst-UI (items, toevoeg-FAB, per-item bewerk/verwijder-dialogen) vervangen door één groot multiline `TextField`/`TextFormField` met de volledige geheugen-tekst, plus een opslaan-actie (bv. knop in de AppBar of auto-save-patroon zoals `notities/lib/notes_editor_screen.dart`).
- `api_client.dart`: `MemoryItem`-klasse en de vier CRUD-methoden vervangen door `getMemoryText()`/`saveMemoryText(String text)` tegen de nieuwe endpoints.
- Bijbehorende widgettest (`memory_screen_test.dart`) aanpassen aan de nieuwe UI.

Migratie van bestaande losse geheugen-items naar één string is geen onderdeel van deze story (geen productiedata om te bewaren volgens de huidige status); bestaande Firestore-documenten in `assistant-memory` mogen vervallen/opnieuw beginnen.

## Acceptance criteria

- Het geheugen wordt backend-breed opgeslagen en uitgewisseld als één string (geen lijst van items meer), zowel in Firestore- als in-memory-opslag.
- `GET /api/v1/assistant/memory` geeft de huidige geheugen-tekst terug; `PUT /api/v1/assistant/memory` slaat een door de gebruiker aangeleverde tekst volledig op. De oude `POST`/`PUT .../{id}`/`DELETE .../{id}`-endpoints bestaan niet meer.
- Na elke chat-beurt (buiten `RA_MOCK_AI`) roept de assistent een AI-aanroep aan die, op basis van de huidige geheugen-tekst en de laatste vraag/antwoord-uitwisseling, een bijgewerkte volledige geheugen-tekst teruggeeft; die tekst wordt opgeslagen. Bij een fout of leeg AI-antwoord blijft het geheugen ongewijzigd (stil falen, zoals nu).
- De geheugen-tekst gaat als contextprefix mee in de prompt van elke volgende chat-beurt (zelfde plek/patroon als nu, maar als kale tekst i.p.v. `- item`-opsomming).
- Het "Geheugen"-scherm in de `robberts_assistent`-app toont één groot, bewerkbaar tekstveld met de volledige geheugen-tekst; de gebruiker kan deze aanpassen en opslaan, en de opgeslagen tekst is direct de tekst die bij de volgende chat wordt meegegeven.
- Backend-tests (`mvn test`) en app-tests (`flutter test`) zijn aangepast aan het nieuwe model en slagen.

## Aannames

- Geen datamigratie nodig: bestaande losse geheugen-items in Firestore (`assistant-memory`) hoeven niet automatisch samengevoegd te worden tot één string; de collectie mag leeg beginnen met de nieuwe structuur.
- De AI-aanroep na een chat-beurt geeft de **volledige** nieuwe geheugen-tekst terug (geen diff/patch-formaat); die tekst vervangt de oude 1-op-1.
- Er is geen limiet op de lengte van de geheugen-string vereist door deze story (geen expliciete truncatie/waarschuwing in UI of backend).
- De opslag-vorm (Firestore-document met één tekstveld, in-memory fallback met één string) volgt hetzelfde stub/fallback-patroon als de rest van de repo (§5 CLAUDE.md), zonder nieuwe `AppSecrets`-key (hergebruikt bestaande Firebase-configuratie).

## Eindsamenvatting

Ik heb genoeg context om de eindsamenvatting te schrijven.

## Eindsamenvatting SF-1149: Geheugen omzetten naar één string

**Gebouwd:**
Het assistent-geheugen (`assistant`-module) is omgezet van een lijst losse `MemoryItem`s naar één vrije-tekst-string per gebruiker, zowel backend als in de app.

- **Backend:** `MemoryRepository` is een simpele tekst-opslag geworden (`current()`/`update(text)`), met dezelfde Firestore/in-memory-fallback als voorheen (document `assistant-memory/memory`, veld `text`). De oude CRUD-endpoints (`GET/POST/PUT/DELETE .../memory(/{id})`) zijn vervangen door alleen `GET`/`PUT /api/v1/assistant/memory`. De prompt-context geeft nu de kale geheugen-string door (geen `- item`-opsomming meer); de AI-aanroep na elke chat-beurt slaat het volledige antwoord direct op als nieuwe geheugen-tekst (geen reconciliatie van losse regels meer — `parseMemoryLines`/`reconcileMemory` zijn vervallen).
- **App (`robberts_assistent`):** `memory_screen.dart` is herschreven naar het patroon van `notities/lib/notes_editor_screen.dart`: één multiline tekstveld met debounced auto-save (10s) + expliciete opslaanknop in de AppBar. `api_client.dart` heeft `getMemoryText()`/`saveMemoryText(text)` i.p.v. de vier CRUD-methoden.

**Keuzes:**
- Geen datamigratie: bestaande losse geheugen-items in Firestore vervallen, collectie start leeg (expliciet afgesproken aanname, geen productiedata te bewaren).
- Stil-falen-gedrag bij fouten/leeg AI-antwoord en het overslaan onder `RA_MOCK_AI` zijn ongewijzigd overgenomen, nu toegepast op de string i.p.v. de lijst.
- Geen lengtelimiet/truncatie op de geheugen-tekst toegevoegd (buiten scope).

**Getest:**
- Backend `mvn test`: 155 tests, 0 failures/errors.
- App `flutter test`: 17 tests groen, `flutter analyze` zonder issues.
- Preview-E2E (PR-omgeving): `GET`/`PUT`-roundtrip geverifieerd, oude endpoints correct verdwenen (405/404), en volledige browser-E2E (Meer → Geheugen: typen, opslaan, "Opgeslagen"-status, netwerkcall bevestigd) via Playwright.
- Reviewer en tester hebben de volledige story-diff doorgenomen; geen bugs, regressies of scope-afwijkingen gevonden. Alle acceptatiecriteria zijn geverifieerd.

**Bewust niet gedaan:** migratie van bestaande geheugen-items (buiten scope per aanname); geen limiet/waarschuwing op de lengte van de tekst.
