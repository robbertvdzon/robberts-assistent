# SF-1534 - langdurige zoekopdracht (opus 4.5)

## Story

langdurige zoekopdracht (opus 4.5)

<!-- refined-by-factory -->

## Samenvatting

Een nieuwe "Watches"-functionaliteit waarmee langdurige zoekopdrachten kunnen worden opgevoerd. Bijvoorbeeld: "geef een seintje als aaltjes tegen slakken weer beschikbaar zijn op deze webshop." De gebruiker geeft een titel, URL en instructie op, plus hoe vaak gecheckt moet worden (kantooruren of dagelijks). Bij beschikbaarheid volgt een pushmelding.

## Scope

**Backend** (`watches`-module):
- Datamodel `Watch` met velden: id, title, url, instruction, frequency (KANTOORUREN/DAGELIJKS), status (ONBEKEND/GEVONDEN/NIET_GEVONDEN), active, lastChecked, createdAt, updatedAt
- `WatchRepository` (Firestore/in-memory fallback, zelfde patroon als ReminderRepository)
- `WatchesController` met CRUD-endpoints: GET/POST/DELETE `/api/v1/watches`, PATCH `/api/v1/watches/{id}/toggle`
- `WatchScheduler` met `@Scheduled(fixedDelayString = "${ra.watches.poll-interval-ms:300000}")` — poll actieve watches; pure "is aan de beurt?"-functie bepaalt of nu gecheckt moet worden
- `watchChatClient` (tool-loos, BriefingAiConfig-patroon) ontvangt pagina-tekst + instructie, antwoordt met "GEVONDEN: <reden>" of "NIET GEVONDEN: <status>"
- Eigen `htmlToPlainText()`-kopie (ModulithArchitectureTest-bewaking)
- Bij transitie naar GEVONDEN: `PushService.sendToAll(title, body, mapOf("type" to "watch"))`, daarna `active = false`
- `WatchCouplingProbe` voor het Koppelingen-scherm

**Frontend** (`robberts_assistent`):
- Nieuw `watches_screen.dart`: lijst met watches (titel + status), FAB voor nieuwe watch, swipe-acties (verwijderen, toggle actief)
- Aanmaakdialoog met velden: titel, URL, instructie, frequentie-dropdown (Kantooruren/Dagelijks)
- Nieuwe tab op index 4 in `home_screen.dart` (vóór "Meer", die naar index 5 schuift)
- Deep-link vanuit FCM-push (`data['type'] == 'watch'`) naar de Watches-tab

## Acceptance criteria

- [ ] Een gebruiker kan via de Watches-tab een nieuwe watch aanmaken met titel, URL, instructie en frequentie
- [ ] Frequentie KANTOORUREN checkt alleen ma-vr 09:00-17:00 (elk uur); DAGELIJKS checkt eenmaal per dag
- [ ] De backend haalt de pagina-HTML op, converteert naar platte tekst en laat de AI beoordelen
- [ ] Bij transitie van NIET_GEVONDEN naar GEVONDEN: pushmelding + watch wordt automatisch inactief
- [ ] De Watches-tab toont per watch: titel en huidige status
- [ ] Swipe-links biedt "Verwijderen" (met bevestiging) en "Pauzeren/Hervatten"
- [ ] Tik op de FCM-push (type=watch) opent de Watches-tab
- [ ] Zonder AI-credentials (`RA_MOCK_AI`) krijgt een watch status ONBEKEND (geen crash)
- [ ] De watch-koppeling verschijnt op het Koppelingen-scherm met configured/mode/test-status

## Aannames

- De AI mag de pagina vrij interpreteren; er is geen exacte CSS-selector-matching nodig
- De backend hoeft niet te verifiëren of de URL bereikbaar is bij aanmaken
- Eén pushmelding per watch-hit volstaat (daarna automatisch inactief)
- De gebruiker kan een inactieve watch handmatig weer activeren via de toggle
- Geen bewerken van bestaande watches in deze iteratie; alleen aanmaken/verwijderen/toggle

## Eindsamenvatting

Nu heb ik alle relevante informatie. Hier is de eindsamenvatting:

---

## Eindsamenvatting SF-1534: Watches-feature (langdurige zoekopdrachten)

### Wat is gebouwd

Een volledig nieuwe "Watches"-functionaliteit waarmee gebruikers langdurige zoekopdrachten kunnen opvoeren. Denk aan: "geef een seintje als aaltjes tegen slakken weer beschikbaar zijn op deze webshop."

**Backend (`watches`-module):**
- Datamodel `Watch` met titel, URL, instructie, frequentie (KANTOORUREN/DAGELIJKS) en status (ONBEKEND/GEVONDEN/NIET_GEVONDEN)
- Repository met Firestore/in-memory fallback (zelfde patroon als reminders)
- REST-endpoints: GET/POST/DELETE `/api/v1/watches`, PATCH `/{id}/toggle`
- Scheduler die elke 5 minuten pollt; pure `isDue()`-functie bepaalt of een watch aan de beurt is (kantooruren = ma-vr 09-17 elk uur, dagelijks = 24h interval)
- AI-integratie: pagina ophalen, HTML→platte tekst, AI beoordeelt of aan de instructie is voldaan
- Bij hit: pushmelding + watch automatisch inactief
- `WatchCouplingProbe` voor het Koppelingen-scherm

**Frontend:**
- Nieuwe `watches_screen.dart` met watchlijst, FAB voor aanmaken, swipe-acties (pauzeren/verwijderen)
- Zesde tab in de bottom-navigatie (vóór "Meer")
- FCM deep-link: tik op push opent de Watches-tab

### Keuzes

- Eigen `htmlToPlainText()`-kopie i.p.v. gedeelde utility (ModulithArchitectureTest-bewaking)
- Geen URL-validatie bij aanmaken; de AI mag de pagina vrij interpreteren
- Eén pushmelding per watch-hit, daarna automatisch inactief (handmatig te heractiveren)

### Getest

- Backend: 339 tests geslaagd, waaronder specifieke tests voor `isDue`-logica (12 tests), repository (10 tests) en HTML-naar-tekst-conversie (14 tests)
- Frontend: 38 tests geslaagd, inclusief 6-tab-verificatie en FCM deep-link

### Bewust niet gedaan

- Bewerken van bestaande watches (alleen aanmaken/verwijderen/toggle in deze iteratie)
- Verificatie of URL bereikbaar is bij aanmaken

---
