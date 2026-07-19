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
- **Config:** `AppSecrets` + `AppSecretsLoader` lezen `secrets.env` (lokaal) of env-vars (prod,
  uit de Sealed Secret via `envFrom`). Ontbrekende secret ⇒ fallback (zie `effectiveMockAi`).
- **AI-agent:** twee `ChatClient`-beans in `assistant/ai/AiConfig` — `assistantChatClient`
  (`@Primary`, met alle `@Tool`-beans) en `gardenChatClient` (`@Qualifier`, vision, eigen
  system-prompt). `MockChatModel` in preview/tests (deterministisch, geen kosten/netwerk).
- **Data:** notities in Postgres (JdbcTemplate + Flyway `V1`); reminders + chat-conversaties in
  Firestore (named database `robberts-assistent`, project `tuinbewatering`); moestuin-foto's in
  Firebase Storage (`tuinbewatering.firebasestorage.app`, map `moestuin/`).

## Web-apps

- `Dockerfile`: Flutter web build (met `--dart-define` voor `GOOGLE_CLIENT_ID`, `API_BASE_URL`,
  `SKIP_GOOGLE_AUTH`) → nginx-unprivileged. `nginx.conf` proxyt `/api/` same-origin naar
  `robberts-assistent-backend:80` (geen CORS) en serveert de Flutter-app.
- Google-login: web via de GIS-knop (`google_sign_in_web`), mobiel via `signIn()`. `ApiClient`
  ruilt het Google-ID-token in voor een sessie-token en stuurt dat als Bearer mee.

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
