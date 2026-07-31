# Technical Spec

Architectuur, stack en codeconventies. Volledig overzicht + modulelijst: root `CLAUDE.md`.

## Stack & versies

- **Backend:** Kotlin, Spring Boot 3.5, **Spring Modulith**, Java 21, Maven. Spring AI
  (`spring-ai-openai` + `spring-ai-client-chat`, handmatige bean-wiring, geen
  auto-configuratie-starter) met **OpenAI gpt-5.5** (vision-capable). `firebase-admin`
  (Firestore + Cloud Storage). JdbcTemplate + Flyway. Package-root `nl.vdzon.robbertsassistent`.
- **Apps:** Flutter (stable), Dart `>=3.0.0 <4.0.0`. `wind/` daarnaast native Kotlin (App
  Actions-trampoline-activities, TTS, notificaties). Web-apps: Flutter web → nginx-container.
- **Android:** applicationId's `nl.vdzon.*` (o.a. `nl.vdzon.groentetuin`, `nl.vdzon.robberts_assistent`);
  gedeelde release-keystore (Google Sign-In hangt aan de SHA-1).

## Architectuur (backend)

- **Spring Modulith**: elke directe subpackage onder `robbertsassistent` is een module;
  `ModulithArchitectureTest` dwingt de grenzen af. Cross-module verwijzingen alleen naar types
  in de base-package van de andere module.
- **Koppelingen achter ports met fallback.** Een selector-`@Configuration` kiest per koppeling
  de echte implementatie (als de secret gezet is) of de fallback (stub/in-memory/mock).
  Voorbeelden: `Notifier` (Telegram/Logging), `ReminderRepository` + `ConversationRepository`
  (Firestore/in-memory), `PhotoStorage` (Firebase Storage/in-memory), `CalendarClient` +
  `DocsClient` (Google/stub). Firebase-init-fouten worden afgevangen → fallback, geen crashloop.
- **Pluggable SPI-patroon** voor uitbreidbare lijsten: een module definieert een interface
  (`CouplingProbe`, `NightlyCheck`, `BriefingSectionProvider`), elke leverende module registreert
  een `@Component`-implementatie ervan, en Spring injecteert automatisch de volledige
  `List<...>` in de verzamelende service. Nieuwe leverancier toevoegen ⇒ nieuwe `@Component`,
  geen wijziging in de verzamelende service.
- **Briefingstatuscontract:** `BriefingSection` heeft achterwaarts compatibele optionele velden
  `status` (`GOED`, `LET_OP`, `NIET`) en `tileLabel`; ontbrekende velden in bestaande
  Firestore-cache-JSON deserialiseren als `null`. Kite en strandfietsen kiezen uit hun ene
  `AssessmentResult` groen boven geel boven rood (bij gelijkstand het vroegste dagdeel). Afval
  leidt tekst en tegel af uit dezelfde eenmalig opgehaalde zevendagenplanning, met daggrenzen in
  `Europe/Amsterdam`. Bronfouten leveren bewust beide tegelvelden als `null`.
- **Config:** `AppSecrets` + `AppSecretsLoader` lezen `secrets.env` (lokaal) of env-vars (prod,
  uit de Sealed Secret via `envFrom`). Ontbrekende secret ⇒ fallback (zie `effectiveMockAi`).
- **AI-agent:** twee `ChatClient`-beans in `assistant/ai/AiConfig` — `assistantChatClient`
  (`@Primary`, met alle `@Tool`-beans) en `gardenChatClient` (`@Qualifier`, vision, eigen
  system-prompt). `MockChatModel` in preview/tests (deterministisch, geen kosten/netwerk).
  Andere modules kunnen een eigen lichte, tool-loze `ChatClient`-bean toevoegen die de gedeelde
  `ChatModel` hergebruikt (bv. `briefing.BriefingAiConfig.weekTasksChatClient` en
  `watches.WatchAiConfig.watchChatClient`), zodat mock/echt automatisch
  meeloopt met `AppSecrets.effectiveMockAi` zonder eigen schakelaar.
- **Data:** notities, reminders, langdurige zoekopdrachten (`watches`) + chat-conversaties
  (incl. `archived`-veld) + gebruiker-breed geheugen (`assistant-memory`) in Firestore (named
  database `robberts-assistent`, project `tuinbewatering`); moestuin-foto's in Firebase Storage
  (`tuinbewatering.firebasestorage.app`, map `moestuin/`).
- **Watches:** `GET`/`POST /api/v1/watches`, `PUT`/`DELETE /api/v1/watches/{id}` en
  `POST /api/v1/watches/run-now` zijn geauthenticeerd. `WatchStoreConfig` kiest de Firestore-collectie `watches` of
  `InMemoryWatchRepository`. Bewerken valideert dezelfde vijf invoervelden als aanmaken en reset
  de opdracht naar actief en `NOG_NIET_GECONTROLEERD`, zodat de gewijzigde criteria opnieuw
  gecontroleerd worden. `WatchRunner` gebruikt één fixed-delay poller
  (`ra.watches.poll-interval-ms`, standaard 300000 ms); de pure
  `WatchSchedule.isDue` rekent in `Europe/Amsterdam`. Naast `poll(now)` heeft
  `WatchRunner` een `runNow(now)` die de `isDue`-filtering overslaat en alle
  opdrachten met `active == true` via dezelfde private `check(watch, now)`
  controleert (inactieve — waaronder alles op `GEVONDEN` — worden overgeslagen);
  `POST /api/v1/watches/run-now` draait die run synchroon af en geeft de
  bijgewerkte lijst terug. Er is bewust geen server-side lock tegen gelijktijdige
  runs: de `compareAndSet` hieronder dekt overlap met de poller af, dubbele runs
  voorkomen is een UI-verantwoordelijkheid. `JdkWatchPageFetcher`
  accepteert alleen succesvolle HTTP-responses, begrenst de HTML op 1.000.000
  bytes en de geëxtraheerde tekst op 20.000 tekens. `watchChatClient` heeft geen
  tools en `WatchAssessmentParser` accepteert alleen `GEVONDEN` of `NIET
  GEVONDEN` plus een omschrijving. Netwerk-, HTTP-, AI- en parsefouten worden
  als `ONBEKEND` opgeslagen en bij een volgende geplande beurt opnieuw
  geprobeerd. De chat-kant loopt via `assistant/ai/WatchTools` (`listWatches`,
  `createWatch`, `updateWatch` bovenop `WatchService`, geregistreerd in
  `AiConfig.defaultTools(...)`); die tools hergebruiken de bestaande service en
  validatie, vangen `WatchValidationException` af als Nederlandse tekst en
  zoeken een opdracht via het begin van het id (`startsWith`, zelfde patroon als
  `ReminderTools.deleteReminder`). Niet-meegegeven update-velden zijn optionele
  `@ToolParam(required = false)`-parameters met neutrale defaults en worden
  overgenomen van de bestaande watch, omdat `WatchService.update` alle velden
  verwacht. Verwijderen is bewust niet als tool ontsloten.
- **Gelijktijdigheid watches:** een pollresultaat wordt met
  `WatchRepository.compareAndSet(expected, updated)` alleen opgeslagen wanneer
  de actuele opdracht nog exact gelijk is aan het gelezen snapshot. In-memory
  gebeurt dit atomisch met `ConcurrentHashMap.replace`; Firestore gebruikt een
  transactie. Ook een gebruikerswijziging gebruikt deze CAS. Daardoor kunnen overlappende pollers
  geen gevonden status terugdraaien of dubbele push versturen en kan een lopende controle een
  tussentijds gewijzigde of verwijderde opdracht niet overschrijven/herstellen. Alleen de winnende
  overgang naar `GEVONDEN` mag de optionele FCM-push met `data.type=watch`
  versturen.

## Web-apps

- `Dockerfile`: Flutter web build (met `--dart-define` voor `GOOGLE_CLIENT_ID`, `API_BASE_URL`,
  `SKIP_GOOGLE_AUTH`) → nginx-unprivileged. `nginx.conf` proxyt `/api/` same-origin naar
  `robberts-assistent-backend:80` (geen CORS) en serveert de Flutter-app.
- Google-login: web via de GIS-knop (`google_sign_in_web`), mobiel via `signIn()`. `ApiClient`
  ruilt het Google-ID-token in voor een sessie-token en stuurt dat als Bearer mee.
- `robberts_assistent` heeft een expliciet centraal light-`ColorScheme` en `CardTheme` in
  `lib/main.dart`. `AppLogo`, `SectionHeading` en `StatusPill` zijn de gedeelde bouwstenen voor
  respectievelijk het SVG-beeldmerk, sectiekoppen en toegankelijke statusweergave (kleur plus
  woord). `flutter_svg` rendert `assets/icon/logo.svg`; `flutter_launcher_icons` genereert de
  Android- en webiconen uit het overeenkomstige PNG.
- De Vandaag-tab parseert onbekende briefingstatussen als `null` en rendert maximaal drie geldige
  statussecties met een niet-leeg `tileLabel` als even brede tegels. De exacte bolkleuren zijn
  `#0CA30C`, `#FAB219` en `#D03B3B`; tegel-semantiek bevat statuswoord, titel, label en een
  uitvoerbare tikactie. Eén geselecteerde tegel toont zijn bestaande sectiekaart direct onder de
  rij en wordt zo nodig met `Scrollable.ensureVisible` in beeld gebracht; getegelde secties worden
  uit de permanente kaartenlijst gefilterd. Geldige statussecties na de limiet blijven gewone
  kaarten.
- Pushnavigatie gebruikt `FcmService.deepLinkTarget`: `briefing` gaat naar de hoofdbestemming
  Vandaag en sluit eerst routes boven de app-shell; `watch` pusht een verse
  `WatchesScreen`-route. Daarmee zijn deeplinks niet gekoppeld aan veranderlijke tab-indexen.

## Codeconventies

- Nederlands in commentaar/docs/commits/UI.
- Nieuwe skill = nieuwe module (subpackage) + evt. een `@Tool` in `assistant/ai/`, geregistreerd
  in `AiConfig`. Nieuwe koppeling = port + fallback + `AppSecrets`-key + secrets-documentatie.
- Match de bestaande stijl per module (JdbcTemplate voor Postgres, port-selector voor koppelingen).

## Bekende valkuilen

- **Modulith-grenzen:** een verweesde `.class` in `target/` (na hernoemen/verwijderen van een
  class) kan een dubbele bean geven — draai `mvn clean test` bij vreemde bean-conflicten.
- **Firebase-credentials in prod:** gebruik `RA_FIREBASE_CREDENTIALS_JSON` (inhoud), niet
  `_FILE` (pad bestaat niet in de container).
- **Sealed Secrets:** nieuwe keys mergen met `kubeseal --merge-into`; bij een verlopen
  `cluster-cert.pem` ontsleutelt de controller de secret niet en blijven koppelingen op fallback
  (cert verversen met `kubeseal --fetch-cert`).
- **Google-vision** weigert ongeldige/te kleine afbeeldingen (`image_parse_error`) — test met
  echte foto's.
