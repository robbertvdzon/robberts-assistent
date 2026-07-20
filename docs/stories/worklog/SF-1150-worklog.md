# SF-1150 — Geheugen omzetten naar één string (backend + app)

## Review

- Volledige story-diff (`git diff main...HEAD`) bekeken, niet alleen de laatste commit.
- Backend: `MemoryRepository`/`InMemoryMemoryRepository`/`FirestoreMemoryRepository` correct
  omgezet naar `current()/update(text)` op één document `assistant-memory/memory` (veld `text`).
  `AssistantController` heeft nu alleen `GET`/`PUT /api/v1/assistant/memory`; oude
  POST/PUT-by-id/DELETE-endpoints zijn weg. `AssistantService.buildPromptText` geeft de kale
  string door; `updateMemoryFromExchange` slaat het volledige AI-antwoord direct op;
  `parseMemoryLines`/`reconcileMemory` zijn vervallen. Stil-falen bij fout/leeg antwoord en
  overslaan onder `RA_MOCK_AI` blijven ongewijzigd, conform de AC's.
- App: `memory_screen.dart` herschreven naar het `notes_editor_screen.dart`-patroon (multiline
  `TextField`, debounce-auto-save + save bij lifecycle-pause/dispose, AppBar-opslaanknop);
  `api_client.dart` heeft `getMemoryText()`/`saveMemoryText(text)` i.p.v. `MemoryItem` + CRUD.
  Geen restanten van de oude API gevonden (`grep` op `MemoryItem`/`listMemory`/CRUD-methoden/
  `parseMemoryLines`/`reconcileMemory` in de hele repo: leeg).
- Testbewijs zelf geverifieerd (gericht, niet het hele netvangst opnieuw als verplichting):
  - `mvn test` (backend): 155 tests, 0 failures/errors, BUILD SUCCESS.
  - `flutter test` (app): alle 17 tests groen; `flutter analyze`: geen issues. Flutter-SDK was in
    deze sandbox-run beschikbaar (`/opt/flutter`), dus dit is echt testbewijs, geen blanco-review.
- Geen bugs, regressies of scope-afwijkingen gevonden. Implementatie dekt alle acceptatiecriteria
  uit de story.

## Oordeel

Akkoord, geen blockers.
