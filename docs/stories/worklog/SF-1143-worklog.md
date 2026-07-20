# SF-1143 — Automatisch bewerkbaar geheugen

## Stappenplan

- [x] `MemoryItem` + `MemoryRepository` (in-memory + Firestore, stub/fallback-patroon)
- [x] `memoryChatClient`-bean in `AiConfig` (licht, geen tools, analoog `titleChatClient`)
- [x] `AssistantService`: geheugen-CRUD, geheugen-update-stap na elke chat-beurt (stil falend,
      overgeslagen onder `RA_MOCK_AI`), geheugen-items als context bij elke chat-beurt
- [x] `AssistantController` + DTO's: `GET/POST/PUT/DELETE /api/v1/assistant/memory(/{id})`
- [x] Backend-unit-/integratietests
- [x] Frontend: `memory_screen.dart`, `ApiClient`-methodes, entry in `more_screen.dart`
- [x] Backend `mvn test` + frontend `flutter analyze`/`flutter test` groen

## Uitgevoerd

Backend: nieuw `MemoryItem(id, text, createdAt, updatedAt)` + `MemoryRepository`
(`InMemoryMemoryRepository`/`FirestoreMemoryRepository`, collectie `assistant-memory`),
gewired via `AssistantStoreConfig` (bean `assistantMemoryRepository`, zelfde
stub/fallback-patroon als `assistantConversationRepository`). Nieuwe `memoryChatClient`-bean
(geen tools, eigen systeemprompt) in `AiConfig`.

`AssistantService.chat()`: geeft de actuele geheugen-items als tekstprefix mee aan de
gebruikersvraag naar `assistantChatClient` (zodat ze aantoonbaar in de prompt terechtkomen —
getest via het mock-model dat de laatste user-message echoot). Na de beurt, alleen als
`!secrets.effectiveMockAi`, een losse `memoryChatClient`-aanroep met de laatste
vraag/antwoord-uitwisseling + huidige geheugen-items; het antwoord (verwachte vorm: complete
bijgewerkte lijst, één item per regel, of `GEEN`) wordt gereconcilieerd tegen de bestaande
items (ongewijzigde tekst blijft staan met zijn id, nieuwe teksten worden aangemaakt,
verdwenen teksten verwijderd). Faalt de aanroep of is het antwoord leeg/onbruikbaar, dan blijft
het geheugen ongewijzigd (`runCatching`, geen exception naar de gebruiker). Onder `RA_MOCK_AI`
wordt de stap volledig overgeslagen — deterministisch, geen niet-gestuurd gedrag in tests.

Nieuwe endpoints op `AssistantController`, auth-gated via `authService.requireAuthorization`:
`GET/POST/PUT/DELETE /api/v1/assistant/memory(/{id})`.

Frontend: nieuw `memory_screen.dart` (lijst, toevoegen via dialoog, bewerken via tik-op-item
dialoog, verwijderen via icoon-knop met bevestiging), `ApiClient`-methodes
(`listMemory`/`createMemoryItem`/`updateMemoryItem`/`deleteMemoryItem`), en een nieuw item
"Geheugen" in `more_screen.dart`.

## Niet gedaan / aangepast

Geen afwijkingen van de scope. `RA_MOCK_AI` slaat de geheugen-update-AI-aanroep over (conform
AC7); de context-injectie in de hoofd-chat-aanroep gebeurt wel altijd (ook onder `RA_MOCK_AI`),
zodat het testbaar en zichtbaar is via het mock-model.

## Review (reviewer, SF-1143)

- Volledige `mvn test` lokaal gedraaid tegen de branch: groen (exit 0, geen failures), inclusief
  `ModulithArchitectureTest`, `MemoryRepositoryTest`, uitgebreide `AssistantServiceTest` (context-
  injectie, RA_MOCK_AI-skip, reconciliatie nieuw/behouden/verwijderd, falende AI-aanroep) en
  `AssistantIntegrationTest` (memory-CRUD-endpoints + 404's).
- `flutter analyze` en `flutter test` (robberts_assistent) lokaal gedraaid: beide groen (geen
  issues, alle tests slagen) — `/opt/flutter/bin/flutter` was beschikbaar in deze sandbox, dus dit
  is echt testbewijs i.p.v. blanco review.
- Code doorgenomen: `MemoryItem`/`MemoryRepository` (in-memory + Firestore) volgt het bestaande
  stub/fallback-patroon 1-op-1 (analoog `ConversationRepository`); `memoryChatClient` analoog
  `titleChatClient`; geheugen-update faalt stil via `runCatching` en wordt overgeslagen onder
  `RA_MOCK_AI`; geheugen-context wordt wél altijd (ook onder mock-AI) meegegeven aan de hoofd-
  prompt, zodat AC8 aantoonbaar getest kan worden. Endpoints zijn auth-gated en consistent met de
  rest van `AssistantController`. Frontend-scherm en `more_screen.dart`-entry kloppen met de
  acceptatiecriteria.
- Geen blockers gevonden. Alle 13 acceptatiecriteria uit de story-scope voor deze subtaak (SF-1143,
  onderdeel 2 "Automatisch bewerkbaar geheugen") zijn gedekt.
