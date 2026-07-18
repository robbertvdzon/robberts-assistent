# Robberts Assistent — Fundament, koppelingen & implementatieplan

Dit document beschrijft het technische **fundament** onder de assistent: alle
externe koppelingen bewezen en aangesloten, zodat er daarna alleen nog **skills**
op gestapeld hoeven te worden. Elke koppeling is een dunne, testbare doorsnede
achter een **port** (interface), zodat skills afhangen van een capability en niet
van een leverancier.

Zie ook `PLAN.md` (visie, apps, "Hey Google"-aanpak). Dit doc verdiept het
backend-fundament en de koppelingen.

---

## 1. Doel

- Eén keer een skelet bouwen waarin **agenda, docs, database, push, alarm en
  Telegram** allemaal werken.
- Daarna is elke nieuwe functie een **skill** bovenop dezelfde ports — geen
  nieuwe plumbing meer.
- **De AI-agent is de test-harness**: elke koppeling is een `@Tool`, dus de hele
  stack is met natuurlijke taal te testen ("stuur me een push over 10 min",
  "wanneer moet ik naar de tandarts", "telegram me mijn vakanties dit jaar").

---

## 2. Vastgelegde keuzes

### 2.1 Database = Firestore (niet Postgres/Neon voor nieuwe modules)
- **Reden**: OpenShift stateless houden; serverless (geen idle-CPU-kosten zoals
  bij Neon); gratis tier volstaat bij één gebruiker; **bundelt met FCM** in één
  Firebase-project + één service-account.
- Achter een **repository-port**, dus swap-baar als het tegenvalt.
- Bestaande `notes` blijft voorlopig op Postgres; eventueel later migreren. Als
  Firestore bevalt kunnen de andere apps ook mee.

### 2.2 Google-toegang = OAuth "offline access" refresh-token (optie A)
- `vdzon.com` staat bij one.com → **geen** Google Workspace → service-account /
  domain-wide delegation (optie B) is onmogelijk. A is de juiste én enige weg voor
  een gewoon Google-account.
- **Werking**:
  - **Refresh-token** (langlevend) → in `secrets.env`. Eén keer instellen.
  - **Access-token** (~1 uur geldig) → **in memory**, met vervaltijd. De backend
    maakt 'm on-demand aan uit de refresh-token en hergebruikt 'm tot hij (bijna)
    verlopen is. Niet persistent opslaan nodig; na herstart haalt de eerste call
    gewoon een verse access-token op.

  ```kotlin
  class GoogleOAuthService(secrets: AppSecrets) {
      private val refreshToken = secrets.googleOAuthRefreshToken   // uit secrets.env
      private var cached: AccessToken? = null                       // in memory
      fun accessToken(): String {
          val now = Instant.now()
          if (cached == null || cached!!.expiresAt.isBefore(now.plusSeconds(60))) {
              cached = exchangeRefreshTokenForAccessToken(refreshToken)  // 1 call naar Google
          }
          return cached!!.value
      }
  }
  ```
- **Solide, mits**: het OAuth-consent-scherm op **"In production"** staat. In
  "Testing" verloopt de refresh-token na **7 dagen**; in production niet (blijft
  geldig door de tijd heen). Bij het eerste consent zie je één keer een
  "unverified app"-waarschuwing — voor persoonlijk gebruik doorklikken.
- **Robuustheid**: `GoogleOAuthService` vangt `invalid_grant` af en stuurt dan een
  **Telegram-alert** ("Google-koppeling verlopen, opnieuw inloggen"), zodat het
  nooit stil faalt. Herstel = eenmalig opnieuw consenten (2 min), geen code-wijziging.

### 2.3 Push = FCM (Firebase Cloud Messaging)
- Zelfde Firebase-project als Firestore → één service-account-JSON dekt DB én push.
- **Firebase ≠ Firestore**: FCM is de push-dienst, geen database. We gebruiken
  Firestore als DB én FCM als push, uit hetzelfde project.

### 2.4 Telegram
- Bestaande bot (token + chat-id). Voor het fundament alleen **uitgaand**
  (`send`). Tweerichtings (reply via polling) is een latere skill.

### 2.5 Test-harness = de AI-agent
- Elke koppeling wordt een Spring AI `@Tool` (zoals bestaande `NotesTools` /
  `WindTools` in `AiConfig`). Zo test je met één zin een hele keten.

---

## 3. Architectuur (backend, Spring Modulith)

```
nl.vdzon.robbertsassistent
├── google/       CalendarClient (agenda lezen), DocsClient (doc read-only),
│                 GoogleOAuthService (refresh-token -> access-token, in-memory cache,
│                 invalid_grant -> Telegram-alert)
├── notifier/     Notifier (interface: send(message))
├── telegram/     TelegramNotifier : Notifier   (uitgaand bericht naar chat-id)
├── push/         FcmNotifier : Notifier + FcmTokenController (app registreert token)
├── store/        FirestoreConfig (Firebase Admin SDK, service-account-JSON)
├── reminders/    ReminderRepository (port) + FirestoreReminderRepository,
│                 RemindersService, RemindersController (app leest lijst),
│                 ReminderScheduler (@Scheduled: due reminders -> Notifier)
└── assistant/ai/ ReminderTools, CalendarTools, DocsTools  (nieuwe @Tool-beans)
+ @EnableScheduling op RobbertsAssistentBackendApplication
```

**Ports (interfaces) en hun stubs** — alles draait groen vóór er secrets zijn:

| Port | Echte impl | Stub (zonder secret) |
|---|---|---|
| `Notifier` | `TelegramNotifier`, `FcmNotifier` | `LoggingNotifier` (logt het bericht) |
| `ReminderRepository` | `FirestoreReminderRepository` | `InMemoryReminderRepository` |
| `CalendarClient` | Google Calendar API | `StubCalendarClient` (vaste afspraken) |
| `DocsClient` | Google Docs API | `StubDocsClient` (vaste tekst) |

Ontbrekend secret → stub-fallback, exact zoals het bestaande `effectiveMockAi`-
patroon. Code, tests en CI blijven groen; live gaat de koppeling zonder code-wijziging.

---

## 4. App (Flutter, `robberts_assistent`)

- **Lokaal alarm**: op een tijdstip of on-demand, gaat af ook als de app dicht is
  (`flutter_local_notifications` + full-screen intent over lockscreen). Reschedule
  na reboot (`RECEIVE_BOOT_COMPLETED`); exact alarm in Doze (`exactAllowWhileIdle`,
  Android 12+ `USE_EXACT_ALARM`).
- **FCM-ontvangst**: een data-push kan een lokaal alarm/notificatie triggeren. De
  app registreert zijn FCM-token bij de backend (`push`-module).
- **Reminders-scherm**: leest de reminders via `RemindersController` (REST) en
  toont ze; nieuwe reminders/alarmen zijn hier ook zichtbaar.
- Push-notificatie zelf kan de wind-app al (`NotificationCompat`) — patroon herbruiken.

---

## 5. Nieuwe secrets (`AppSecrets` / `secrets.env`)

```
# Google Firebase (Firestore + FCM) — service-account-JSON
RA_FIREBASE_CREDENTIALS_FILE=      # pad naar service-account.json
RA_FIREBASE_PROJECT_ID=
# Google OAuth (Agenda + Docs) — refresh-token flow (optie A)
RA_GOOGLE_OAUTH_CLIENT_ID=
RA_GOOGLE_OAUTH_CLIENT_SECRET=
RA_GOOGLE_OAUTH_REFRESH_TOKEN=
# Telegram
RA_TELEGRAM_BOT_TOKEN=
RA_TELEGRAM_CHAT_ID=
```
Elke ontbrekende groep → bijbehorende Notifier/Client valt terug op zijn stub.

---

## 6. Skill-ideeën (de "leuke laag" bovenop het fundament)

Deze veranderen niets aan het fundament — het zijn skills op dezelfde ports.

### Sterk voor de eerste versie (veel effect, weinig extra werk)
- **Proactief kite-alarm** — scheduler checkt de windvoorspelling (tool bestaat al)
  en pusht alleen bij een echt kite-window ("Do 14–17u: 18 kn ZW"). Kruis met de
  agenda → alleen als je vrij bent.
- **Ochtend-briefing via Telegram** — 07:00 één bericht: afspraken vandaag + wind +
  openstaande reminders. Raakt alle koppelingen; bouwt voort op `SummaryService`.
- **Agenda → automatische reminders** — 's nachts de agenda scannen; voor
  (gemarkeerde) afspraken automatisch een alarm X min vooraf in Firestore zetten.
- **Docs als kennisbank** — één Google Doc met wifi-code, router-reset, kite-
  onderhoud, paklijsten. "Wat is de wifi-code?" → agent leest het doc en antwoordt.

### Leuk voor daarna
- **Tweerichtings-Telegram assistent** — volledige assistent in je chat, app hoeft
  niet open; evt. voice-notes → transcriberen → agent.
- **Slimme wekker** — alarm past zich aan je eerste afspraak aan.
- **Monitoring-agents** (uit PLAN.md, nu mogelijk): zonnepanelen (dag-kWh / alarm bij
  ingezakte opbrengst), NAS-backup (alarm bij mislukte/oude backup), OpenShift-status.
- **Moestuin + weer** — vorstwaarschuwing; water-reminder die zichzelf overslaat bij
  regen.
- **Meeting-prep uit gekoppelde docs** — agent leest het doc bij een afspraak en geeft
  3 regels voorbereiding.
- **Doc-gedreven todo's** — een lijst-doc syncen naar Firestore-reminders.

---

## 7. Test-scenario's via de AI-agent

| Zin tegen de agent | Tools | Koppeling getest |
|---|---|---|
| "stuur me een push over 10 minuten" | `ReminderTools.create` -> scheduler -> `FcmNotifier` | Firestore + scheduler + FCM |
| "wanneer moet ik naar de tandarts?" | `CalendarTools.findEvents` | Agenda |
| "zoek X op in mijn google docs" | `DocsTools.read` | Docs |
| "telegram me mijn vakanties dit jaar" | `CalendarTools.list` + `TelegramNotifier.send` | Agenda + Telegram |
| "welke reminders heb ik?" | `ReminderTools.list` (+ app-scherm) | Firestore + app-UI |

---

## 8. Implementatieplan (gefaseerd)

Elke fase is los opleverbaar en test-baar. Fase 0 heeft **geen** secrets nodig.

### Fase 0 — Fundament zonder secrets  *(runt en test groen)*
Backend:
- [ ] `@EnableScheduling` op `RobbertsAssistentBackendApplication`.
- [ ] `AppSecrets` + `secrets.example.env` uitbreiden met de nieuwe keys (allemaal optioneel).
- [ ] Ports definiëren: `Notifier`, `ReminderRepository`, `CalendarClient`, `DocsClient`.
- [ ] Stubs: `LoggingNotifier`, `InMemoryReminderRepository`, `StubCalendarClient`, `StubDocsClient`.
- [ ] `reminders`-module: `Reminder`-model, `RemindersService`, `RemindersController` (GET lijst, POST aanmaken, DELETE), `ApiModels`.
- [ ] `ReminderScheduler` (`@Scheduled` elke minuut → due reminders → `Notifier`, markeer verzonden).
- [ ] AI-tools: `ReminderTools`, `CalendarTools`, `DocsTools` op de ports; registreren in `AiConfig.defaultTools(...)`.
- [ ] Tests: unit per service/tool + `ModulithArchitectureTest` blijft groen.

App:
- [ ] Lokaal-alarm-capability (native Android, full-screen over lockscreen; op tijd + on-demand).
- [ ] Reminders-scherm dat de REST-lijst toont.

**Oplevering**: "backend beslist → alarm/push" skelet werkt lokaal end-to-end met stubs.
**Test**: agent "stuur me een push over 1 minuut" → scheduler vuurt → `LoggingNotifier` logt; app kan een alarm zetten dat afgaat.

### Fase 1 — Telegram live  *(secret: bot-token + chat-id — bestaat al)*
- [ ] `TelegramNotifier : Notifier` (Telegram Bot API `sendMessage`).
- [ ] Bean-selectie: Telegram als token aanwezig, anders `LoggingNotifier`.
- [ ] Test: agent "stuur me een telegram" → bericht komt binnen.

**Oplevering**: eerste echte uitgaande push end-to-end via de scheduler.

### Fase 2 — Firestore + FCM  *(secret: Firebase service-account-JSON)*
- [ ] `store`-module: `FirestoreConfig` (Firebase Admin SDK).
- [ ] `FirestoreReminderRepository : ReminderRepository` (vervangt in-memory).
- [ ] `push`-module: `FcmNotifier : Notifier` + `FcmTokenController` (app registreert token).
- [ ] App: `firebase_messaging`, `google-services.json`, token registreren, data-push → lokaal alarm.
- [ ] Test: reminder overleeft herstart (Firestore); push komt op de telefoon; alarm gaat af.

**Oplevering**: reminders persistent + echte push naar het toestel.

### Fase 3 — Google Agenda + Docs  *(secret: OAuth client + refresh-token)*
- [ ] `google`-module: `GoogleOAuthService` (refresh → access, in-memory cache, `invalid_grant` → Telegram-alert).
- [ ] `CalendarClient` (afspraken lijst/zoeken) — vervangt stub.
- [ ] `DocsClient` (doc lezen op id) — vervangt stub.
- [ ] Test: "wanneer moet ik naar de tandarts", "zoek X in docs", "telegram mijn vakanties".

**Oplevering**: alle vier koppelingen live; volledige stack met natuurlijke taal testbaar.

### Fase 4 — De leuke laag (skills)
- [ ] Kite-window proactieve agent (wind + agenda + push).
- [ ] Ochtend-briefing agent (agenda + wind + reminders + `SummaryService`).
- [ ] Agenda → automatische reminders.
- [ ] Docs-kennisbank Q&A.
- [ ] (Later: tweerichtings-Telegram, slimme wekker, monitoring-agents, moestuin, meeting-prep.)

---

## 9. Setup-checklist voor Robbert (buiten de code)

- [ ] Firebase-project aanmaken; Firestore aanzetten; service-account-JSON downloaden.
- [ ] `google-services.json` in de Flutter-app zetten (FCM).
- [ ] OAuth-client (Desktop) aanmaken; consent screen op **"In production"** (anders
      verloopt de refresh-token na 7 dagen); eenmalig consent met scopes
      `calendar.readonly` + `documents.readonly` → refresh-token ophalen.
- [ ] Telegram bot-token + chat-id opzoeken.
- [ ] Waarden in `secrets.env` zetten (zie §5).

---

## 10. Open beslissingen

- **Werkwijze**: volgen we de story/factory-flow (nieuwe `SF-xxx` + worklog in
  `docs/stories/worklog/`, factory commit zelf), of direct op een branch bouwen?
- **Scope Fase 0**: backend-skeleton én app-alarm/reminders-scherm samen, of eerst
  alleen de backend?
