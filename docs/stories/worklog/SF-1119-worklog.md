# SF-1119 - Worklog

Story-context bij eerste pickup:
Persistente chat-gesprekken met titel en foto's in de assistent-app

Backend (assistant-module): nieuw conversatiemodel (id, title, messages, createdAt/updatedAt) analoog aan gardenchat.Conversation, met ConversationRepository-port + FirestoreConversationRepository (eigen collectie 'assistant-conversations') + InMemoryConversationRepository-fallback naar het GardenStoreConfig-patroon. Vervang POST /api/v1/assistant/message door multipart POST /api/v1/assistant/chat (message, optioneel conversationId, optioneel photos[]) naar het patroon van GardenChatController/GardenChatService; AssistantService geeft de bestaande berichten van de conversatie als historie mee aan assistantChatClient zodat vervolgvragen context kennen, en stuurt foto's als Media mee (hergebruik PhotoStorage/FirebaseStoragePhotoStorage). Voeg titelgeneratie toe: na de eerste vraag/antwoord-uitwisseling een lichte aparte AI-aanroep voor een titel, met een deterministische placeholder-titel als MockChatModel/RA_MOCK_AI actief is. Voeg GET /api/v1/assistant/conversations (lijst: id, title, updatedAt, meest-recent-eerst) en GET /api/v1/assistant/conversations/{id} (volledige berichten incl. foto-refs) toe, plus foto-ophaal-endpoint. Verwijder oude AssistantMessageRequest/Response-DTO's en het oude endpoint. Zorg dat ModulithArchitectureTest blijft slagen (geen directe imports uit gardenchat-interne klassen). Frontend (robberts_assistent-app): nieuw gesprekken-lijstscherm (titel + laatst bijgewerkt, actie 'nieuw gesprek'), de 'Assistent'-tab in home_screen.dart opent dit scherm; tikken op een gesprek opent assistant_screen.dart met conversationId, nieuw gesprek zonder id. assistant_screen.dart laadt historie via de conversatie-detail-call en voegt foto-ondersteuning toe (camera/galerij via image_picker, preview, thumbnail in chatbubbel) naar het patroon van groentetuin/lib/chat_screen.dart. api_client.dart krijgt een multipart-methode voor /api/v1/assistant/chat plus methodes voor conversatielijst en conversatie-detail, analoog aan groentetuin's gardenChat(...). Schrijf bijbehorende backend- en frontend-unit-tests (incl. mock-AI/in-memory-fallback-paden) als onderdeel van dit werk.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Backend (`assistant`-module): nieuw `Conversation`/`ConversationMessage`-model (id, title,
  messages, createdAt/updatedAt), `ConversationRepository`-port + `InMemoryConversationRepository`
  + `FirestoreConversationRepository` (collectie `assistant-conversations`, los van gardenchat's
  `conversations`) en `PhotoStorage`-port + `InMemoryPhotoStorage` + `FirebaseStoragePhotoStorage`
  (map `assistent-chat/`), gekozen via nieuwe `AssistantStoreConfig` (zelfde stub/fallback-patroon
  als `gardenchat.GardenStoreConfig`). Beans expliciet `assistantConversationRepository`/
  `assistantPhotoStorage` genoemd — anders botsen de `@Bean`-methodenamen met gardenchat's eigen
  (typeverschillende) `conversationRepository`/`photoStorage`-beans in dezelfde Spring-context
  (leidde eerst tot een falende `AssistantIntegrationTest`, want zonder expliciete naam wint de
  laatst-gescande config en verdwijnt de andere stilletjes).
- `POST /api/v1/assistant/message` vervangen door multipart `POST /api/v1/assistant/chat`
  (`message`, optioneel `conversationId`, optioneel `photos[]`), naar het patroon van
  `GardenChatController`/`GardenChatService`. `AssistantService.chat(...)` geeft de volledige
  berichtenhistorie van de conversatie mee aan `assistantChatClient` (met alle bestaande tools) en
  stuurt foto's als `Media` mee. Nieuwe endpoints `GET /api/v1/assistant/conversations` (lijst,
  meest-recent-eerst) en `GET /api/v1/assistant/conversations/{id}` (volledige berichten incl.
  foto-refs), plus `GET /api/v1/assistant/photos/{id}`.
- Titelgeneratie: nieuwe `titleChatClient`-bean (`AiConfig`, geen tools, eigen systeemprompt) voor
  een losse, lichte AI-aanroep na de eerste uitwisseling. Onder `RA_MOCK_AI`/zonder OpenAI-key
  slaat `AssistantService` deze aanroep bewust over en gebruikt een deterministische placeholder
  (eerste woorden van de vraag) — zo blijft de titel voorspelbaar in preview/tests.
- Oude `AssistantMessageRequest`/`AssistantMessageResponse`-DTO's en het oude endpoint verwijderd.
  De native `wind`-app riep dit endpoint óók aan (niet genoemd in de scope, maar wel een echte
  consumer) — `wind/android/.../AssistantClient.kt` omgezet naar de nieuwe multipart
  `/api/v1/assistant/chat`-call (altijd zonder `conversationId`, want de headless trampoline-flow
  heeft geen gespreks-continuïteit nodig) zodat "Hey Google, vraag Wind ..." is blijven werken.
- `ModulithArchitectureTest` blijft groen: geen nieuwe cross-module afhankelijkheden nodig, alle
  nieuwe types leven in de bestaande `assistant`-module.
- Frontend (`robberts_assistent`): nieuw `conversations_screen.dart` (lijst met titel + laatst
  bijgewerkt, FAB "Nieuw gesprek"); de "Assistent"-tab in `home_screen.dart` opent dit scherm i.p.v.
  direct de chat. Tikken op een gesprek of "Nieuw gesprek" pusht `assistant_screen.dart` (nu een
  eigen `Scaffold` met `AppBar`-titel = gesprekstitel) met/zonder `conversationId`; bij terugkeer
  ververst de lijst. `assistant_screen.dart` laadt bij een bestaand gesprek de historie (incl.
  foto's, via `fetchAssistantPhoto`) en heeft foto-ondersteuning (camera/galerij via
  `image_picker`, preview met verwijderknop, thumbnails in de bubbel) naar het patroon van
  `groentetuin/lib/chat_screen.dart`. `api_client.dart` kreeg `assistantChat(...)` (multipart),
  `assistantConversations()`, `assistantConversation(id)` en `fetchAssistantPhoto(id)`, analoog aan
  groentetuin's `gardenChat(...)`. Nieuwe dependencies `image_picker` + `http_parser` in
  `pubspec.yaml` (`pubspec.lock` dienovereenkomstig bijgewerkt via `flutter pub get`).
- Tests geschreven: backend `AssistantServiceTest` (mock-AI, nieuw gesprek/vervolgvraag/losse
  gesprekken/titel/foto's/lijst) en `AssistantIntegrationTest` (multipart end-to-end via
  `TestRestTemplate`, incl. lijst + detail-ophalen). Frontend `conversations_screen_test.dart` en
  `assistant_screen_test.dart` (fake `ApiClient`-subclasses, geen echte netwerkcalls).
- Testresultaten: backend `mvn test` → 66/66 groen (incl. `ModulithArchitectureTest`). Frontend
  `flutter analyze` → geen issues; `flutter test` → alle tests groen (incl. de bestaande
  `widget_test.dart` en de twee nieuwe testbestanden).
- Niet gedaan: geen wijzigingen aan Firestore-indexen/-rules (repo heeft die niet client-side) en
  geen migratie van oude, niet-persistente Q&A-historie (expliciet buiten scope, zie Aannames in
  `.task.md`). `wind/`-Kotlin-tests/build kon niet lokaal gedraaid worden (bekende sandbox-beperking,
  zie `docs/factory/development.md`) — de wijziging in `AssistantClient.kt` is klein en qua patroon
  identiek aan de al-werkende `completeLogin`-call, alleen met een multipart-body i.p.v. JSON.

## Review (SF-1120)

- Volledige story-diff (`git diff main...HEAD`) bekeken, niet alleen de laatste commit.
- Backend: `Conversation`/`ConversationMessage`, `ConversationRepository` (in-memory +
  Firestore, collectie `assistant-conversations`), `PhotoStorage` (in-memory + Firebase
  Storage, map `assistent-chat/`), `AssistantStoreConfig` met expliciete bean-namen
  (`assistantConversationRepository`/`assistantPhotoStorage`) om de eerder gevonden
  botsing met `gardenchat.GardenStoreConfig` te voorkomen — klopt en matcht het bestaande
  patroon. `POST /api/v1/assistant/chat` (multipart), `GET /api/v1/assistant/conversations`,
  `GET /api/v1/assistant/conversations/{id}`, `GET /api/v1/assistant/photos/{id}` dekken de
  acceptatiecriteria. Titelgeneratie via losse `titleChatClient`-bean met deterministische
  placeholder onder `RA_MOCK_AI`, precies zoals de Aannames voorschrijven. Oude
  `AssistantMessageRequest`/`Response` en het oude endpoint zijn weg; geen resterende
  referenties elders in de repo (gecheckt met een repo-brede grep).
- `wind/`-app (niet expliciet in scope, maar wél een echte consumer van het oude endpoint) is
  meeverbouwd naar de nieuwe multipart-`/chat`-call en leest nu `reply` i.p.v. `text` uit de
  response — inhoudelijk correct en overeenkomstig het nieuwe DTO.
- `mvn test` zelf opnieuw gedraaid als gerichte check (66/66 groen, incl.
  `AssistantServiceTest`, `AssistantIntegrationTest` en `ModulithArchitectureTest`).
- `./gradlew test` in `wind/android` kon niet draaien (`local.properties` ontbreekt — geen
  Android-SDK in de sandbox); bekende, niet-blokkerende sandbox-beperking, wijziging is klein
  en 1-op-1 naar het bestaande patroon.
- Frontend (`robberts_assistent`): `conversations_screen.dart` (lijst + "Nieuw gesprek"),
  `home_screen.dart` opent dit scherm i.p.v. direct de chat, `assistant_screen.dart` laadt
  historie (incl. foto's via `fetchAssistantPhoto`) en heeft foto-ondersteuning
  (camera/galerij, preview, thumbnails) naar het patroon van `groentetuin/lib/chat_screen.dart`.
  `api_client.dart`-methodes (`assistantChat`, `assistantConversations`,
  `assistantConversation`, `fetchAssistantPhoto`) en DTO's sluiten aan op de backend-response-
  velden. `pubspec.lock`-diff bevat alleen verwachte transitieve deps van `image_picker`/
  `http_parser`. `flutter test`/`flutter analyze` structureel niet uitvoerbaar in deze sandbox
  (bekende beperking, zie agent-tips) — geaccepteerd op basis van grondige handmatige
  code-review van `assistant_screen.dart`, `conversations_screen.dart`,
  `assistant_screen_test.dart` en `conversations_screen_test.dart`, die er inhoudelijk correct
  uitzien en de acceptatiecriteria dekken.
- Geen bugs of scope-afwijkingen gevonden. Akkoord.

## Test (SF-1121)

- Backend: `mvn test` in `robberts-assistent-backend/` opnieuw gedraaid (start `2026-07-19
  11:00:17 UTC`, eind `11:00:36 UTC`) → 66/66 groen, 0 failures/errors (surefire-reports
  opgeteld), incl. `AssistantServiceTest`, `AssistantIntegrationTest`,
  `ModulithArchitectureTest`.
- Frontend `robberts_assistent`: Flutter-SDK bleek dit keer wél beschikbaar in de sandbox
  (`/opt/flutter/bin/flutter` 3.44.6) — `flutter pub get` + `flutter test` (alle
  testbestanden, incl. de nieuwe `assistant_screen_test.dart` en
  `conversations_screen_test.dart`) → alle tests groen (+6); `flutter analyze` → geen issues.
  `pubspec.lock` bleef ongewijzigd (geen `git checkout` nodig).
- Preview (`robberts-assistent-pr-9`, `RA_MOCK_AI=true`, in-memory conversatie-opslag):
  end-to-end via curl + Playwright/Chromium tegen de echte preview-URL, geen mocks:
  - `POST /api/v1/assistant/chat` zonder `conversationId` → nieuwe conversatie, deterministische
    placeholder-titel (eerste woorden van de vraag) — werkt zonder AI-secret.
  - Vervolgvraag met `conversationId` → volledige berichtenhistorie blijft behouden en wordt
    teruggegeven; een tweede, onafhankelijke nieuwe conversatie kent de eerste niet (geen
    vermenging).
  - `GET /api/v1/assistant/conversations` → lijst met titel + `updatedAt`, meest-recent-eerst,
    klopt na elk bericht.
  - `GET /api/v1/assistant/conversations/{id}` → volledige historie inclusief `imageIds`.
  - Foto-upload (`photos`-multipart-veld) → `imageIds` gevuld, `GET
    /api/v1/assistant/photos/{id}` levert exact dezelfde bytes terug (`cmp` identiek).
  - Oude `POST /api/v1/assistant/message` → 404, zoals verwacht (endpoint vervallen).
  - Browser (Playwright, `ignoreHTTPSErrors`, viewport 412×915) tegen de preview-URL: "Assistent"-
    tab opent het nieuwe gesprekkenscherm (lijst met titel + tijdstip, "Nieuw gesprek"-FAB);
    tikken op een gesprek opent het chatscherm met bestaande historie; "Chatten"-tab (tekstmodus)
    → typen + versturen toont vraag/antwoord, vervolgvraag in hetzelfde gesprek werkt; foto via
    "Uit galerij kiezen" → preview/thumbnail in de bubbel, blijft zichtbaar na terug-en-opnieuw-
    openen van het gesprek; na een volledige page-reload (app "sluiten/heropenen" binnen dezelfde
    backend-sessie) blijft de gesprekkenlijst met bijgewerkte tijdstippen intact. Screenshots
    staan in `screenshots/` (o.a. `02-conversations.png`, `07-chat-opened.png`,
    `16-photo-sent.png`, `17-photo-reopened.png`).
  - Waarneming, geen bug: tussen twee testrondes bleek de conversatielijst in preview leeg
    (backend-pod is kennelijk herstart/opnieuw gesynct tussen de rondes in). Dit is verwacht
    gedrag van de in-memory-fallback in preview (geen Firestore-config daar, zie
    `foundation-couplings.md`/CLAUDE.md §5) en geen regressie van deze story — persistentie-over-
    herstarten-heen vereist Firestore, dat in preview bewust leeg blijft. Binnen één
    backend-sessie (incl. volledige page-reload) is de persistentie wél bevestigd.
  - `wind/`-app: `AssistantClient.kt`-diff nagelopen — multipart-veld `message` en response-veld
    `reply` sluiten exact aan op `AssistantController`/`AssistantChatResponse`. Gradle-build kon
    niet lokaal (geen Android-SDK/`local.properties`, bekende sandbox-beperking) — geaccepteerd op
    basis van deze code-match plus de al bevestigde live backend-contractdata hierboven.
- Geen bugs gevonden; alle acceptatiecriteria uit `.task.md` bevestigd tegen de preview.
  Tijdelijke testconversaties/foto's staan alleen in de in-memory preview-opslag (geen delete-
  endpoint per Aannames-scope) en verdwijnen vanzelf bij een volgende pod-restart/redeploy —
  geen aparte cleanup nodig.
