# SF-1119 - chat gesprek

## Story

chat gesprek

<!-- refined-by-factory -->

## Scope

De chat-assistent in de `robberts_assistent`-app wordt omgebouwd van één doorlopende, niet-persistente vraag/antwoord-lijst naar losse, persistente **gesprekken** (conversaties) — analoog aan het bestaande multi-turn-patroon van de moestuin-chat (`gardenchat`-module), met een door de agent zelf verzonnen titel en ondersteuning voor foto's.

**Backend (`assistant`-module):**
- Vervang het stateless `POST /api/v1/assistant/message` door een conversatie-model, opgeslagen in Firestore (collectie `assistant-conversations`, zelfde patroon als `gardenchat.Conversation`/`FirestoreConversationRepository`): een conversatie heeft `id`, `title`, een lijst berichten (`role`, `text`, optionele foto-referenties, `createdAt`) en `createdAt`/`updatedAt`. In-memory fallback zoals bij gardenchat wanneer Firestore niet beschikbaar is.
- Nieuw endpoint `POST /api/v1/assistant/chat` (multipart, zoals `/api/v1/garden/chat`): velden `message`, optioneel `conversationId`, optioneel `photos[]`. Zonder `conversationId` wordt een nieuwe conversatie aangemaakt. De volledige berichtenhistorie van de conversatie wordt als context meegegeven aan de `assistantChatClient`, zodat vervolgvragen het eerdere deel van het gesprek kennen. Bestaande tools (notities, wind, reminders, alarms, agenda, docs, push) blijven binnen een conversatie werken.
- Titelgeneratie: na de eerste vraag/antwoord-uitwisseling van een conversatie genereert de assistent zelf een korte titel (bv. via een aparte, lichte LLM-aanroep) en slaat die op als `title` van de conversatie. Bestaat er nog geen titel, dan valt de respons terug op een deterministische placeholder (bv. eerste woorden van de vraag) zodat dit ook werkt onder `RA_MOCK_AI`.
- Nieuw endpoint `GET /api/v1/assistant/conversations` — lijst van conversaties van de ingelogde gebruiker (`id`, `title`, `updatedAt`), gesorteerd op meest recent.
- Nieuw endpoint `GET /api/v1/assistant/conversations/{id}` — volledige conversatie inclusief berichten en foto-referenties.
- Fotostorage/-ophalen hergebruikt het bestaande patroon uit `gardenchat` (`PhotoStorage`, foto-ophaal-endpoint), zodat foto's die in een gesprek gestuurd zijn later getoond kunnen worden.
- Het oude endpoint `POST /api/v1/assistant/message` mag vervallen (geen bestaande consumers buiten deze app te behouden).

**Frontend (`robberts_assistent`-app):**
- Nieuw "gesprekken"-scherm: toont de lijst van conversaties (titel + laatst bijgewerkt), met een actie om een nieuw gesprek te starten. De "Assistent"-tab in de bottom navigation opent dit scherm in plaats van direct het chatscherm.
- Tikken op een gesprek in de lijst opent het bestaande chatscherm met de gekozen `conversationId`; nieuw gesprek opent hetzelfde scherm zonder `conversationId` (wordt aangemaakt bij eerste bericht).
- Chatscherm krijgt foto-ondersteuning naar het patroon van `groentetuin/lib/chat_screen.dart`: foto maken (camera) of kiezen (galerij) via `image_picker`, preview van te versturen foto's, en weergave van verzonden foto's als thumbnail in de chatbubbel.
- `ApiClient` in `robberts_assistent` krijgt methodes analoog aan `groentetuin`'s `gardenChat(...)`: een multipart-aanroep naar `/api/v1/assistant/chat`, plus methodes voor het ophalen van de conversatielijst en een specifieke conversatie.

## Acceptance criteria

- Vanuit de "Assistent"-tab ziet de gebruiker een lijst van eerdere gesprekken (titel + laatst bijgewerkt) en kan een nieuw gesprek starten.
- Een nieuw gesprek krijgt, zodra de assistent voor het eerst antwoordt, automatisch een door de assistent verzonnen titel; deze titel is zichtbaar in de gesprekkenlijst.
- Binnen een geopend gesprek kent de assistent bij een vervolgvraag de eerdere vraag/antwoorden uit datzelfde gesprek (context blijft behouden binnen de conversatie).
- Berichten van verschillende gesprekken worden niet met elkaar vermengd: een nieuw gesprek start zonder kennis van andere gesprekken.
- Na het sluiten en heropenen van de app zijn eerdere gesprekken (inclusief titel en berichten) nog steeds beschikbaar (persistent in Firestore, niet alleen lokaal in de app).
- De gebruiker kan in een gesprek een foto (camera of galerij) toevoegen aan een bericht; de assistent kan op basis van die foto antwoorden (vision), en de foto blijft zichtbaar in de gespreksgeschiedenis bij het opnieuw openen van dat gesprek.
- Bestaande assistent-functionaliteit (notities lezen/bijwerken, wind/kite-check, reminders, alarms, agenda, Google Docs, push-notificatie sturen) blijft werken binnen een gesprek.
- Zonder AI-secret (`RA_MOCK_AI`/mock-fallback) blijft de gesprekken-flow functioneel en deterministisch (inclusief een titel), zodat preview-omgevingen en tests groen blijven zonder secrets.
- Zonder Firestore-secret valt de conversatie-opslag terug op een in-memory implementatie (zelfde patroon als overige koppelingen), zodat de app en tests ook dan blijven werken.

## Aannames

- Verwijderen, hernoemen of expliciet delen/exporteren van gesprekken zit niet in scope van deze story (issue vraagt alleen starten/selecteren/doorpraten); dit kan een vervolgstory zijn.
- Titelgeneratie gebeurt via een losse, lichte AI-aanroep na de eerste uitwisseling (niet via een structured-output-veld in het hoofdantwoord), met een deterministische placeholdertitel als fallback zodat mock-/preview-omgevingen consistent blijven werken.
- Foto-ondersteuning hergebruikt zoveel mogelijk de bestaande `gardenchat`-infrastructuur (Firebase Storage, `PhotoStorage`, hetzelfde vision-capable model `gpt-4o-mini`) in plaats van een nieuwe losstaande opslag te bouwen.
- Er is geen migratie nodig van de oude, niet-persistente Q&A-historie: die bestond alleen client-side in het geheugen en gaat niet mee naar het nieuwe conversatiemodel.
- De focus ligt op de Android/APK-flow van `robberts_assistent` (zoals ook bij `groentetuin`); eventuele web-specifieke aanpassingen aan de fotoflow zijn geen blokkerend onderdeel van deze story.
- Er komt geen limiet op het aantal gesprekken of berichten per gesprek in deze story; performance/paginering van de gesprekkenlijst is geen onderdeel van de acceptatiecriteria.

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summarized"}
