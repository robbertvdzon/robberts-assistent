# SF-1141 - Gesprekkenlijst met archiveren + automatisch bewerkbaar geheugen

## Story

Gesprekkenlijst met archiveren + automatisch bewerkbaar geheugen

<!-- refined-by-factory -->

## Scope

Twee samenhangende, maar onafhankelijk te bouwen features in de `robberts_assistent`-app en de
backend `assistant`-module.

### 1) Gesprekkenlijst: archiveren, verwijderen, sortering, paginatie

**Backend (`assistant`-module):**
- `Conversation` krijgt een `archived: Boolean` (default `false`).
- `ConversationRepository` (in-memory + Firestore): `listAll()` wordt uitgebreid met
  `includeArchived`-filter en paginatie (limit/offset op `updatedAt` descending).
- Nieuwe endpoints op `AssistantController`:
  - `GET /api/v1/assistant/conversations?includeArchived=false&limit=10&offset=0` — gesorteerd op
    `updatedAt` descending, standaard alleen niet-gearchiveerde gesprekken.
  - `PATCH /api/v1/assistant/conversations/{id}/archive` — zet `archived=true`.
  - `PATCH /api/v1/assistant/conversations/{id}/unarchive` — zet `archived=false`.
  - `DELETE /api/v1/assistant/conversations/{id}` — verwijdert het gesprek (best-effort ook de
    bijbehorende foto's via `PhotoStorage`, zonder dat een foto-opruimfout de delete blokkeert).

**Frontend (`conversations_screen.dart`):**
- Bij opstarten: de eerste 10 (niet-gearchiveerde) gesprekken direct getoond, nieuwste bovenaan.
- Gesprekken 11+ onder een uitklapbare sectie "Ouder" (opgehaald via een vervolg-`GET` met
  `offset=10` zodra de sectie wordt uitgeklapt).
- Swipe-naar-links op een gesprek toont twee acties: **Archiveren** en **Verwijderen** (nieuwe
  dependency `flutter_slidable`, consistent met de rest van de app qua stijl/kleuren).
- Verwijderen vraagt een bevestiging (`AlertDialog`) voordat de backend wordt aangeroepen.
- Een toggle/actie in de AppBar ("Toon gearchiveerd") schakelt `includeArchived` om en herlaadt de
  lijst; gearchiveerde gesprekken tonen een visuele marker (bv. icoon) in de lijst.

### 2) Automatisch bewerkbaar geheugen

**Backend (`assistant`-module):**
- Nieuw concept `MemoryItem(id, text, createdAt, updatedAt)` + `MemoryRepository`
  (in-memory + Firestore, zelfde stub/fallback-patroon als `ConversationRepository`, Firestore-
  collectie `assistant-memory`).
- Na elke `AssistantService.chat(...)`-beurt: een losse, lichte AI-aanroep (zelfde patroon als
  `titleChatClient` voor titels) krijgt de laatste vraag/antwoord-uitwisseling + de huidige
  geheugen-items, en levert een bijgewerkte lijst geheugen-items terug (nieuwe feiten/voorkeuren
  toegevoegd, overbodige/verouderde bijgewerkt of verwijderd). Deze aanroep faalt stil (bestaand
  geheugen blijft ongewijzigd) als de AI geen bruikbaar antwoord geeft; onder `RA_MOCK_AI` wordt
  geheugen-update overgeslagen (geen niet-deterministisch gedrag in tests).
- De actuele geheugen-items worden als extra context (systeembericht/prefix) meegegeven aan
  `assistantChatClient` bij elke chat-beurt, zodat het geheugen gebruikt wordt in latere
  gesprekken.
- Nieuwe endpoints:
  - `GET /api/v1/assistant/memory`
  - `POST /api/v1/assistant/memory` (body: `{ "text": "..." }`)
  - `PUT /api/v1/assistant/memory/{id}` (body: `{ "text": "..." }`)
  - `DELETE /api/v1/assistant/memory/{id}`

**Frontend:**
- Nieuw scherm `memory_screen.dart`: lijst van geheugen-items, met toevoegen (dialoog/tekstveld),
  bewerken (tik op item) en verwijderen (swipe of icoon-knop).
- Toegevoegd aan `more_screen.dart` als nieuw item "Geheugen" (analoog aan "Koppelingen" /
  "Nachtchecks").

## Acceptance criteria

1. `Conversation` heeft een `archived`-veld; bestaande gesprekken (zonder het veld in Firestore)
   worden gelezen als `archived=false`.
2. `GET /api/v1/assistant/conversations` ondersteunt `includeArchived`, `limit`, `offset` en
   sorteert op `updatedAt` descending; zonder parameters is het gedrag gelijk aan vandaag (alle
   niet-gearchiveerde gesprekken).
3. `PATCH .../archive`, `PATCH .../unarchive` en `DELETE /api/v1/assistant/conversations/{id}`
   zijn auth-gated (zelfde `authService.requireAuthorization`-patroon) en werken zowel op de
   in-memory als de Firestore-implementatie van `ConversationRepository`.
4. In `conversations_screen.dart`: de eerste 10 gesprekken staan direct in de lijst, gesprek 11+
   staat onder een uitklapbare "Ouder"-sectie; swipe-links toont Archiveren/Verwijderen; een
   toggle laat gearchiveerde gesprekken alsnog zien.
5. Verwijderen van een gesprek in de app vraagt eerst een bevestiging.
6. Na elk assistent-antwoord wordt het geheugen bijgewerkt via een losse AI-aanroep (niet de
   hoofd-`assistantChatClient`-aanroep zelf), met een in-memory + Firestore-opslag
   (`assistant-memory`) en stub/fallback-patroon consistent met de rest van de codebase.
7. Onder `RA_MOCK_AI` verloopt de geheugen-update deterministisch (geen wijziging, of een
   vaste/voorspelbare placeholder-uitkomst) zodat backend-tests groen en stabiel blijven.
8. Geheugen-items worden meegegeven als context bij een volgende chat-beurt (aantoonbaar via een
   test die controleert dat de geheugen-tekst in de prompt/aanroep naar de `ChatClient` terechtkomt).
9. `GET/POST/PUT/DELETE /api/v1/assistant/memory(/{id})` zijn auth-gated en werken met zowel de
   in-memory als de Firestore-repository.
10. Nieuw scherm `memory_screen.dart` toont, voegt toe, bewerkt en verwijdert geheugen-items, en
    is bereikbaar via `more_screen.dart` ("Geheugen").
11. `ModulithArchitectureTest` blijft groen: alle nieuwe klassen (memory, archivering) blijven
    binnen de bestaande `assistant`-module.
12. Backend: `mvn test` groen inclusief nieuwe unit-tests voor archiveren/verwijderen/paginatie en
    voor de geheugen-update-flow (met een stub/mock-`ChatClient` voor het geheugen-aanroep-gedrag).
13. Frontend: `flutter analyze` en `flutter test` groen na toevoegen van `flutter_slidable` en de
    nieuwe/aangepaste schermen.

## Aannames

- "De eerstvolgende 10 gesprekken" wordt geïnterpreteerd als: de 10 meest-recent-bijgewerkte
  niet-gearchiveerde gesprekken direct zichtbaar, oudere gesprekken (11+) onder de "Ouder"-sectie
  — niet als een aparte "nieuwste 1 + volgende 10"-telling.
- Paginatie is `limit`/`offset` (geen cursor-gebaseerde paginatie); dit is voldoende voor het
  verwachte aantal gesprekken van één gebruiker.
- Verwijderen van een gesprek is een harde delete (geen "soft delete"/prullenbak); dit is anders
  dan archiveren, dat wél reversibel is.
- Foto's van een verwijderd gesprek worden best-effort opgeruimd uit `PhotoStorage`; een fout
  daarbij blokkeert de delete van het gesprek niet.
- Geheugen-items zijn simpele vrije tekst-items (geen categorieën/sleutel-waarde-structuur); dat
  past bij "feiten/voorkeuren/context" zoals in de omschrijving en is het eenvoudigst te bewerken
  in de app.
- De geheugen-update-AI-aanroep gebruikt een eigen, lichte `ChatClient` (zonder tools, zonder
  gesprekshistorie) — zelfde patroon als `titleChatClient` — en niet de hoofd-`assistantChatClient`,
  om de hoofd-chat-aanroep niet trager/duurder te maken.
- Geheugen is gebruiker-breed (niet per-conversatie): er is één gedeelde lijst geheugen-items die
  bij elk gesprek als context gebruikt wordt, consistent met "single-user"-opzet van de rest van
  de app (één Robbert, geen multi-tenant-scheiding nodig).
- Er komt geen limiet op het aantal geheugen-items in deze story (geen automatische opruiming of
  samenvatting bij groei) — dat is een mogelijke toekomstige verbetering.

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summary-finished"}
