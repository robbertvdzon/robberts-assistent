# SF-1491 - langdurige zoek opdrachten (4.5)

## Story

langdurige zoek opdrachten (4.5)

<!-- refined-by-factory -->

## Samenvatting

Deze story voegt de mogelijkheid toe om langdurige zoekopdrachten ("watches") aan te maken in de persoonlijke assistent. Een watch is een opdracht aan de backend om periodiek een webpagina te controleren op een bepaalde conditie, bijvoorbeeld "laat me weten als aaltjes tegen slakken weer beschikbaar zijn". De gebruiker ziet een nieuwe tab met een overzicht van alle watches, hun status, en kan nieuwe watches aanmaken met een titel, URL, instructie, en check-frequentie.

## Scope

**Backend (nieuwe `watches`-module):**
- `Watch`-model: `id`, `title`, `url`, `instruction`, `frequency` (KANTOORUREN of DAGELIJKS), `status` (ONBEKEND/GEVONDEN/NIET_GEVONDEN), `statusText`, `lastChecked`, `active`
- `WatchRepository`-poort met Firestore- en in-memory-implementatie (zelfde patroon als `reminders`)
- `WatchesService` voor CRUD-operaties
- `WatchesController` met REST-endpoints: `GET /api/v1/watches`, `POST /api/v1/watches`, `DELETE /api/v1/watches/{id}`
- `WatchScheduler` met één `@Scheduled fixedDelay`-poller (configureerbaar via `ra.watches.poll-interval-ms`, default 300000ms/5 min) die per watch checkt of 'ie aan de beurt is:
  - KANTOORUREN: ma-vr 09:00-17:00, maximaal één check per uur
  - DAGELIJKS: maximaal één check per 24 uur
- AI-beoordeling via een losse, tool-loze `watchChatClient` (zelfde patroon als `briefing.BriefingAiConfig`): haalt de webpagina op, stuurt tekst + instructie naar de AI, parseert regel 1 als `GEVONDEN`/`NIET GEVONDEN`, regel 2 als statustekst
- Bij transitie van status naar `GEVONDEN`: precies één `PushService.sendToAll(...)` met `data["type"] = "watch"`, daarna `active = false`
- Eigen `htmlToPlainText()`-kopie in de watches-module (de WindTools-variant is `internal` en valt buiten de module-grens)

**Frontend (Flutter, `robberts_assistent`):**
- Nieuwe "Watches"-tab op index 4 in de bottom-nav (de huidige "Meer"-tab schuift naar index 5)
- `watches_screen.dart`: lijst van watches met per item: titel, status-icoon, laatst gecheckt
- CRUD via dialogen: titel, URL, instructie (vrije tekst), frequentie (dropdown: Kantooruren/Dagelijks)
- Swipe-to-delete (met bevestiging) of delete-knop

**Buiten scope:**
- Push-deep-link naar de Watches-tab (kan later als aparte verbetering)
- Bewerken van bestaande watches (alleen aanmaken en verwijderen)
- Handmatig triggeren van een check

## Acceptance criteria

1. Er is een nieuwe "Watches"-tab in de app (zesde tab, vóór "Meer") met een lijst van alle watches
2. De gebruiker kan een nieuwe watch aanmaken met titel, URL, instructie en frequentie
3. De gebruiker kan een watch verwijderen (met bevestiging)
4. Elke watch toont de huidige status (ONBEKEND/GEVONDEN/NIET_GEVONDEN), een korte statustekst, en wanneer 'ie voor het laatst is gecheckt
5. De backend pollt actieve watches periodiek volgens hun frequentie en werkt de status bij
6. Bij een transitie naar GEVONDEN ontvangt de gebruiker een push-notificatie en wordt de watch automatisch gedeactiveerd
7. Zonder OpenAI-secret (`RA_MOCK_AI`) geeft de AI-check status ONBEKEND (deterministische fallback)
8. Zonder Firebase-config valt de opslag terug op in-memory (app en tests groen zonder secrets)
9. `ModulithArchitectureTest` slaagt (module-grenzen intact)

## Aannames

- Eén AI-aanroep per watch-check is acceptabel qua kosten/latency; er is geen batching nodig
- De frequentie-opties (KANTOORUREN, DAGELIJKS) zijn voldoende; meer granulariteit is niet nodig
- Alleen Robbert gebruikt deze feature (single-user), geen multi-user-scheiding nodig
- De webpagina's die gecheckt worden zijn publiek toegankelijk (geen login/cookies vereist)
- `htmlToPlainText()` met een simpele regex-strip (zelfde aanpak als WindTools) is voldoende; complexe JS-gerenderde pagina's worden niet ondersteund

## Eindsamenvatting

Ik heb nu genoeg context. Hier is de eindsamenvatting:

---

## Eindsamenvatting SF-1491: Langdurige zoekopdrachten (watches)

### Wat is gebouwd

Een complete "watches"-feature waarmee de assistent periodiek webpagina's kan controleren op een bepaalde conditie (bijv. "laat weten als aaltjes tegen slakken weer beschikbaar zijn").

**Backend (nieuwe `watches`-module, 11 bestanden):**
- `Watch`-model met id, titel, URL, instructie, frequentie (KANTOORUREN/DAGELIJKS), status (ONBEKEND/GEVONDEN/NIET_GEVONDEN), statusText, lastChecked, active
- Repository-poort met Firestore- en in-memory-implementatie (zelfde patroon als reminders)
- REST-endpoints: `GET/POST/DELETE /api/v1/watches`
- Scheduler met @Scheduled poller die per watch checkt of 'ie aan de beurt is (KANTOORUREN: ma-vr 09-17 max 1x/uur; DAGELIJKS: max 1x/24u)
- AI-beoordeling via eigen `watchChatClient` (tool-loos): haalt pagina op, stuurt tekst + instructie naar AI, parseert respons als GEVONDEN/NIET_GEVONDEN + statustekst
- Push-notificatie bij transitie naar GEVONDEN, daarna automatisch deactiveren
- Eigen `htmlToPlainText()` (de WindTools-variant is internal en valt buiten module-grens)

**Flutter app:**
- Nieuwe "Watches"-tab op index 4 in bottom-nav (6 tabs totaal, "Meer" schuift naar 5)
- `watches_screen.dart`: lijst met per watch titel/status-icoon/laatst gecheckt, FAB voor aanmaken (titel, URL, instructie, frequentie-dropdown), swipe-to-delete met bevestiging

### Keuzes

- Geen batching van AI-aanroepen — één call per watch is acceptabel (single-user)
- Simpele regex-strip voor HTML→tekst (geen complexe JS-pagina-ondersteuning)
- Poll-interval configureerbaar via `ra.watches.poll-interval-ms` (default 5 min)
- Deterministische fallback onder `RA_MOCK_AI`: status blijft ONBEKEND

### Getest

- Backend: 11 nieuwe tests (5 service-CRUD, 6 scheduler/AI-parsing/htmlToPlainText)
- Flutter: home_screen_test aangepast voor 6 tabs
- Volledige testsuite groen: 315 backend tests, 36 Flutter tests

### Bewust niet gedaan (buiten scope)

- Push-deep-link naar Watches-tab
- Bewerken van bestaande watches (alleen aanmaken/verwijderen)
- Handmatig triggeren van een check

---
