# SF-1489 - langdurige zoek opdrachten.

## Story

langdurige zoek opdrachten.

<!-- refined-by-factory -->

## Samenvatting

Ik wil de assistent kunnen vragen om een webpagina in de gaten te houden, bijvoorbeeld
om te zien wanneer een uitverkocht product weer op voorraad is. Dat doe ik in een nieuw
tabblad "Zoekopdrachten": ik geef een titel, de link en in gewone taal waar hij op moet
letten.

Op dat scherm zie ik al mijn lopende zoekopdrachten met per stuk de laatste stand van
zaken, bijvoorbeeld "nog steeds uitverkocht" of "nu beschikbaar". Per zoekopdracht kies
ik hoe vaak er gekeken wordt en of ik een pushbericht wil zodra het gevonden is. Ik kan
een zoekopdracht ook zelf direct laten controleren, pauzeren of verwijderen.

## Scope

**Backend — nieuwe Spring-Modulith-module `nl.vdzon.robbertsassistent.watches`**

- Model `Watch`: `id` (UUID), `title`, `url`, `instruction` (vrije tekst, waar moet op gelet
  worden), `frequency` (enum `KANTOORUREN` / `DAGELIJKS`), `pushOnFound` (Boolean),
  `active` (Boolean), `lastCheckedAt`, `lastStatus` (korte NL-statuszin), `found` (Boolean),
  `lastError`, `createdAt`.
- `WatchRepository`-poort met `FirestoreWatchRepository` (collectie `watches`, doc-id = watch-id)
  en `InMemoryWatchRepository`; bean-selectie in `WatchRepositoryConfig` exact volgens
  `ReminderRepositoryConfig` (in-memory zonder Firebase-config, `runCatching`-fallback bij
  init-fout zodat de app nooit crasht).
- `WatchesService`: CRUD + het uitvoeren van één check:
  1. pagina ophalen met de JDK `java.net.http.HttpClient` (injecteerbaar via constructor-default
     zodat tests kunnen faken, zoals `WindTools`), met User-Agent en timeout;
  2. HTML → platte tekst via een eigen `htmlToPlainText`-implementatie **binnen de
     `watches`-module** (script/style verwijderen, tags strippen, entities, whitespace
     comprimeren, aftoppen op ~6000 tekens) — bewuste duplicatie, want `WindTools`'
     variant is `internal` in `assistant/ai` en `ModulithArchitectureTest` bewaakt de grenzen;
  3. instructie + paginatekst naar een nieuwe tool-loze `watchChatClient` (`WatchAiConfig`,
     patroon van `BriefingAiConfig`) met een vast antwoordformaat:
     regel 1 = `GEVONDEN` of `NIET GEVONDEN`, regel 2 = korte Nederlandse statuszin.
  - Defensief parsen: een niet-herkend antwoord levert `found = false` met een neutrale/ruwe
    status (geen exception, geen push) — dit maakt `RA_MOCK_AI`/preview deterministisch.
- `WatchScheduler`: `@Scheduled(fixedDelayString = "\${ra.watches.poll-interval-ms:300000}")`,
  met een pure, testbare "is deze watch aan de beurt?"-functie op basis van `lastCheckedAt`,
  `frequency` en de klok (Europe/Amsterdam). Elke check in een eigen `runCatching`: een
  netwerk-, HTTP- of AI-fout zet alleen `lastError` op díe watch, laat de vorige status staan
  en blokkeert de overige watches niet.
- Push: uitsluitend bij de omslag `niet gevonden → gevonden` en alleen als `pushOnFound`
  aan staat: één `PushService.sendToAll(title, status, mapOf("type" to "watch"))`. Daarna
  wordt de watch op `active = false` gezet (afgerond), zodat er geen herhaalde meldingen komen.
- REST `/api/v1/watches`, volledig achter `authService.requireAuthorization(...)` in de stijl
  van `RemindersController`: `GET` (lijst), `POST` (aanmaken), `PUT /{id}` (bijwerken, incl.
  pauzeren/hervatten via `active`), `DELETE /{id}`, `POST /{id}/check` (direct nu controleren,
  retourneert de bijgewerkte watch).
- De module mag alleen leunen op `firebase`, `push` en `auth`; `ModulithArchitectureTest` blijft groen.

**Frontend — `robberts_assistent`**

- Nieuw `watches_screen.dart`: lijst met per opdracht de titel, de statustekst, het moment van
  laatste controle en een visuele markering wanneer het gevonden is; per rij "nu controleren"
  (met spinner), pauzeren/hervatten en verwijderen (met bevestigingsdialoog). Aanmaken/bewerken
  via dialoog (titel, URL, instructie, frequentiekeuze, push-schakelaar) — stijl van
  `schedules_screen.dart`.
- `ApiClient` krijgt een eigen `// --- Zoekopdrachten ---`-sectie met een `Watch`-modelklasse
  (`fromJson`) in de stijl van `Reminder`, plus de methodes voor lijst/aanmaken/bijwerken/
  verwijderen/nu-controleren.
- `home_screen.dart`: nieuwe tab **op index 4, vóór "Meer"** ("Meer" schuift naar index 5).
  Beide parallelle lijsten bijwerken: `screens` in de `IndexedStack` én
  `NavigationBar.destinations`. Index 0 (Upcoming) blijft ongewijzigd, dus de bestaande
  briefing-deep-link blijft werken; het default-tabblad (`_tab = 2`, Assistent) verandert niet.
- `fcm_service.dart`: `_handleTap` krijgt een tweede tak voor `data['type'] == 'watch'` →
  de Zoekopdrachten-tabindex, via het bestaande `deepLinkTab`-mechanisme.

**Tests (onderdeel van het ontwikkelwerk)**

- Backend: de aan-de-beurt-/frequentielogica (incl. weekend- en uurranden van kantooruren),
  statusparsing (herkend én niet-herkend antwoord), precies één push bij de omslag en géén
  push bij een ongewijzigd-gevonden situatie, repository-bean-selectie, en foutafhandeling
  (falende fetch/AI raakt alleen die watch). `ModulithArchitectureTest` bewaakt de nieuwe grens.
- Frontend: een widget-test voor `watches_screen.dart` (patroon uit
  `conversations_screen_test.dart` / `health_check_screen_test.dart` — er bestaat nog geen
  `schedules_screen_test.dart`) plus aanpassing van `home_screen_test.dart` aan het nieuwe
  aantal/de nieuwe volgorde van tabs.
- `mvn test` (backend) en `flutter test` + `flutter analyze` (app) zijn groen.

**Buiten scope**

- Een headless browser / JavaScript-rendering van pagina's.
- Zoekopdrachten aanmaken vanuit de chat-assistent of via een AI-tool (dat kan een latere story zijn).
- Historie van eerdere checkresultaten (alleen de laatste status wordt bewaard).
- Notificatie via Telegram of e-mail (alleen FCM-push).

## Acceptance criteria

1. In `robberts_assistent` staat een nieuw tabblad "Zoekopdrachten" op index 4, vóór "Meer";
   de bestaande tabs Upcoming (0), Health check (1), Assistent (2) en Herinneringen (3)
   behouden hun plek en de briefing-deep-link naar index 0 blijft werken.
2. Op dat scherm kan ik een zoekopdracht aanmaken met een titel (bijv. "aaltjes tegen slakken"),
   een URL en een instructie in vrije tekst, een frequentie (kantooruren of dagelijks) en een
   push-schakelaar; ik kan een bestaande zoekopdracht bewerken en verwijderen (met bevestiging).
3. Het scherm toont een lijst van zoekopdrachten met per stuk de titel, de laatst bepaalde
   status (bijv. "nog steeds uitverkocht" / "nu beschikbaar") en wanneer er voor het laatst
   gecontroleerd is; een gevonden zoekopdracht is visueel gemarkeerd.
4. Ik kan een zoekopdracht direct laten controleren ("nu controleren"); tijdens het controleren
   is dat zichtbaar en na afloop is de status in de lijst bijgewerkt.
5. Ik kan een zoekopdracht pauzeren en weer hervatten; een gepauzeerde zoekopdracht wordt door
   de achtergrondpoller overgeslagen.
6. De achtergrondpoller controleert elke actieve zoekopdracht volgens zijn frequentie:
   KANTOORUREN = elk uur op ma t/m vr tussen 09:00 en 17:00 (Europe/Amsterdam),
   DAGELIJKS = één keer per dag; buiten die momenten wordt er niet gecontroleerd.
7. Bij de omslag van "niet gevonden" naar "gevonden" wordt, en alleen als de push-schakelaar
   aan staat, precies één pushbericht verstuurd met de titel en de status; daarna is de
   zoekopdracht niet meer actief, zodat er geen herhaalde meldingen komen.
8. Een tik op zo'n pushbericht opent de Zoekopdrachten-tab.
9. Een fout bij het ophalen van de pagina of bij de AI-beoordeling zet alleen een foutmelding
   op die ene zoekopdracht (zichtbaar in de app), laat de vorige status staan, veroorzaakt geen
   push en blokkeert de overige zoekopdrachten niet.
10. Alle `/api/v1/watches`-endpoints vereisen authenticatie, net als de bestaande endpoints.
11. Zonder Firebase-config draait alles op de in-memory-repository (app start, tests groen);
    onder `RA_MOCK_AI` levert een check een deterministische, niet-gevonden uitkomst zonder push.
12. `mvn test` (incl. `ModulithArchitectureTest`), `flutter test` en `flutter analyze` zijn groen.

## Aannames

- **Losse invoervelden** voor titel, URL en instructie — de assistent hoeft geen URL uit één
  lap vrije tekst te vissen. Het voorbeeld uit de story wordt dus ingevuld als titel
  "aaltjes tegen slakken", de URL, en als instructie "meld het als de aaltjes weer op voorraad zijn".
- **Twee frequenties** volstaan (KANTOORUREN en DAGELIJKS), conform de voorbeelden uit de story;
  een vrije cron-expressie is niet nodig.
- **Eén vaste poller** (`@Scheduled` fixedDelay, standaard elke 5 minuten, configureerbaar via
  `ra.watches.poll-interval-ms`) met een pure "is deze aan de beurt?"-functie, i.p.v. dynamische
  per-watch cron-triggers — beter testbaar en simpeler.
- **Beoordelen doet de AI**, niet een tekstuele zoekterm: instructie + paginatekst gaan naar een
  losse tool-loze `watchChatClient` met een vast tweeregelig antwoordformaat, defensief geparsed.
- **Na "gevonden" stopt de zoekopdracht** (`active = false`) — de gebruiker wilde "een seintje",
  niet een terugkerende melding. Hervatten kan handmatig.
- **Alleen server-side HTML** wordt gelezen (gewone GET). Pagina's die hun voorraadstatus pas
  client-side met JavaScript opbouwen kunnen leiden tot "kon niet bepalen" of een foutmelding;
  dat is een geaccepteerde beperking, geen crash.
- **Push via het bestaande FCM-kanaal** (`PushService.sendToAll` met `data["type"] = "watch"`);
  er komt geen nieuwe koppeling, geen nieuw secret en geen nieuwe dependency bij.
- De zoekopdrachten zijn, net als de rest van de app, van één gebruiker — geen multi-user-scheiding.

## Eindsamenvatting

## Eindsamenvatting SF-1489 — Langdurige zoekopdrachten (watches)

**Wat is gebouwd**

Je kunt de assistent nu een webpagina laten bewaken. In `robberts_assistent` staat daarvoor een nieuw tabblad **"Zoekopdrachten"** op index 4 (vóór "Meer"); alle bestaande tabs houden hun plek, dus de briefing-deep-link naar Upcoming blijft werken.

- **Aanmaken/bewerken** via een dialoog: titel, URL, instructie in gewone taal, frequentie (kantooruren of dagelijks) en een push-schakelaar.
- **Overzicht** met per opdracht de titel, de laatst bepaalde status ("nog steeds uitverkocht" / "nu beschikbaar"), het laatste controlemoment, de frequentie, een eventuele foutregel, en een groene markering + vinkje zodra het gevonden is.
- **Per rij**: "Nu controleren" (met spinner), pauzeren/hervatten, en verwijderen met bevestiging.
- **Achtergrondpoller**: kantooruren = maximaal één keer per klokuur, ma t/m vr 09:00–16:59 (Europe/Amsterdam); dagelijks = maximaal één keer per kalenderdag; gepauzeerd wordt overgeslagen.
- **Push**: precies één FCM-bericht bij de omslag "niet gevonden → gevonden", alleen als de schakelaar aan staat; daarna gaat de zoekopdracht op niet-actief zodat je geen herhaalde meldingen krijgt. Een tik op die push opent het Zoekopdrachten-tabblad.

Backend: nieuwe Spring-Modulith-module `watches` (model, repository-poort met Firestore- én in-memory-variant, service, AI-config, scheduler, REST-controller onder `/api/v1/watches`), volledig achter authenticatie en alleen leunend op `firebase`, `push` en `auth`.

**Gemaakte keuzes**

- De pagina wordt met een gewone server-side GET opgehaald, HTML wordt naar platte tekst gestript (afgetopt op ~6000 tekens) en samen met jouw instructie voorgelegd aan een losse, tool-loze AI-client met een vast tweeregelig antwoordformaat. Het antwoord wordt defensief geparsed: iets onherkenbaars levert "niet gevonden" met een neutrale status — nooit een crash en nooit een onterechte push. Daardoor is de preview/mock-modus vanzelf deterministisch en pushvrij.
- Eén vaste poller (elke 5 min, configureerbaar) met een pure "is deze aan de beurt?"-functie, in plaats van dynamische per-opdracht cron-triggers: simpeler en veel beter testbaar.
- Foutafhandeling per zoekopdracht: een netwerk-, HTTP- of AI-fout zet alleen een foutmelding op díe opdracht, laat de vorige status staan, stuurt geen push en blokkeert de andere opdrachten niet.
- Zonder Firebase-config draait alles op de in-memory-repository; de app start altijd, ook bij een init-fout.
- De HTML-naar-tekst-helper is bewust binnen de nieuwe module gedupliceerd, omdat de bestaande variant module-intern is en de architectuurtest de modulegrenzen bewaakt.

**Wat is getest**

- Backend `mvn test`: 333 tests groen (incl. 29 nieuwe watches-tests en de architectuurtest die de nieuwe modulegrens bewaakt).
- App: `flutter test` 48 tests groen, `flutter analyze` zonder issues.
- Handmatig op de preview-omgeving (PR 33): aanmaken/lijst/bijwerken/verwijderen, validatie (foute URL → nette 400, onbekend id → 404), "Nu controleren", pauzeren/hervatten, en een onbereikbare host die alleen op díe opdracht een fout zet. UI-verificatie van de tabvolgorde, de lijstweergave en de dialoog met screenshots.
- Wat niet live te forceren was (auth-afwijzing in preview, de push-omslag, de kantooruren-tijdranden) is via unittests en code-inspectie afgedekt. Alle testdata is na afloop opgeruimd.

**Bewust niet gedaan**

- Geen headless browser / JavaScript-rendering: pagina's die hun voorraadstatus pas client-side opbouwen kunnen "kon niet bepalen" opleveren — geaccepteerde beperking.
- Zoekopdrachten aanmaken vanuit de chat-assistent of via een AI-tool: kan een latere story worden.
- Geen historie van eerdere checkresultaten (alleen de laatste status).
- Geen Telegram of e-mail; alleen FCM-push. Geen nieuwe koppeling, secret of dependency.

**Aandachtspunten voor later (geen blocker)**

- De pagina wordt volledig in geheugen ingelezen vóór het aftoppen; bij een zeer grote pagina onnodig geheugengebruik (zelfde beperking als een bestaande component).
- De URL wordt vanuit de pod opgehaald, dus cluster-interne adressen zijn technisch bereikbaar — acceptabel omdat alles achter login zit en de app single-user is.
- Een handmatig hervatte, al gevonden zoekopdracht wordt gepold zonder ooit opnieuw te kunnen pushen (er is geen omslag meer).
