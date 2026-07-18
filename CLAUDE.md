# Robbert's Assistent

Persoonlijke assistent van Robbert: één Kotlin/Spring-Boot-backend op OpenShift met
modulaire **skills**, en meerdere Flutter/Android-apps als kanalen ernaartoe. Dit bestand
is het instappunt voor een AI-agent — lees het eerst, en daarna de specifieke docs onderaan.

Taal in code-commentaar, docs, commits en UI: **Nederlands**.

---

## 1. Wat is dit?

Een backend die via skills allerlei taken doet (notities, wind/kite-check, reminders met
alarm, moestuin-foto-chat, dagelijkse samenvatting) en die door apps + een AI-agent
aangesproken wordt. De AI-agent (OpenAI via Spring AI, met `@Tool`-functies) is tegelijk
de belangrijkste **test-harness**: bijna elke skill is als tool aan de agent gehangen, dus
je test een hele keten met één zin ("zet een reminder over 10 min", "wanneer moet ik naar
de tandarts", "telegram me mijn vakanties").

Ontwerp-uitgangspunt: OpenShift blijft **stateless**; state leeft extern (Postgres/Neon,
Firestore, Firebase Storage). Elke externe koppeling zit achter een **port** met een
**stub/in-memory fallback**, zodat de app en tests groen draaien zónder secrets, en een
koppeling live gaat door enkel de secret te zetten (zelfde patroon als `effectiveMockAi`).

---

## 2. Repo-structuur

Mono-repo. GitHub Actions-workflows triggeren op hun eigen subfolder.

```
robberts-assistent/
├── robberts-assistent-backend/   ← Kotlin/Spring Boot/Spring Modulith backend
├── robberts_assistent/           ← Flutter app: dagelijkse samenvatting + chat-assistent (web + APK)
├── groentetuin/                  ← Flutter app: moestuin-AI-chat (web op moestuin.vdzonsoftware.nl + APK)
├── notities/                     ← Flutter app: één auto-opslaande notitie (APK)
├── wind/                         ← Flutter/native PoC: "Hey Google" App Actions → wind-antwoord (APK)
├── deploy/                       ← kustomize-manifests (base + preview-overlay) + sealed secret
├── docs/
│   ├── factory/                  ← factory-agent-context (specs, dev, deploy, agents/)
│   ├── stories/                  ← per-story worklogs (SF-xxxx)
│   ├── foundation-couplings.md   ← ontwerp + implementatieplan van de koppelingen-laag
│   ├── setup-guide-details.md    ← console-setup (Firebase/Google/Cloudflare) met concrete waarden
│   └── robbert-todo.md           ← status + handmatige stappen (Robbert)
├── .github/workflows/            ← per component: image-build + apk-build + backend-verify
└── CLAUDE.md                     ← dit bestand
```

---

## 3. Tech stack

- **Backend:** Kotlin, Spring Boot 3.5, **Spring Modulith**, Java 21, Maven. Spring AI
  (`spring-ai-openai`, handmatige bean-wiring) met **OpenAI gpt-4o-mini** (vision-capable).
  firebase-admin (Firestore + Cloud Storage). JdbcTemplate + Flyway.
- **Apps:** Flutter (stable), Dart `>=3.0.0 <4.0.0`. `wind/` heeft native Kotlin (App
  Actions-trampoline-activities). Web-apps draaien als nginx-container (Flutter web build).
- **Data:** Postgres/Neon (notities); **Firestore** (reminders + chat-historie, named
  database `robberts-assistent` in Google-project `tuinbewatering`); **Firebase Storage**
  (moestuin-foto's, bucket `tuinbewatering.firebasestorage.app`, map `moestuin/`).
- **Auth:** Google-login → eigen HMAC-sessie-token (allowlist `robbert@vdzon.com`).
- **Push:** Telegram (uitgaand) + FCM (gepland, app-kant nog te bouwen).
- **Deploy:** OpenShift single-node thuis, **GitOps via ArgoCD**, images op `ghcr.io`,
  externe toegang via **Cloudflare Tunnel**, secrets via **Sealed Secrets**.

---

## 4. Backend — Spring Modulith modules

Package-root `nl.vdzon.robbertsassistent`. Elke directe subpackage = een module; grenzen
worden afgedwongen door `ModulithArchitectureTest`. Koppelingen zitten achter een port met
fallback (zie §5).

| Module | Doel |
|---|---|
| `config` | `AppSecrets` + `AppSecretsLoader` (leest `secrets.env` lokaal, env-vars in prod). |
| `auth` | Google ID-token verifiëren → HMAC-sessie-token; `requireAuthorization()` gate. |
| `health` | `/healthz` (open) + `/api/v1/ping` (auth, testendpoint). |
| `notes` | Eén notitie-string in Postgres (JdbcTemplate + Flyway `V1`). |
| `summary` | Dagelijkse samenvatting. |
| `assistant` | Chat-assistent. `assistant/ai/`: `AiConfig` (ChatClients + model-keuze), tools (`NotesTools`, `WindTools`, `ReminderTools`, `CalendarTools`, `DocsTools`), `MockChatModel`. |
| `reminders` | Reminder-model + repository-port (Firestore/in-memory), REST-controller, `@Scheduled ReminderScheduler` (due → `Notifier`). |
| `gardenchat` | Moestuin-AI-chat: multipart (tekst + foto's) → vision-AI; conversaties in Firestore, foto's in Firebase Storage; multi-turn. |
| `google` | `CalendarClient` + `DocsClient` (echt via OAuth refresh-token, of stubs) + `GoogleOAuthService`. |
| `firebase` | `FirebaseProvider`: gedeelde FirebaseApp → named Firestore-db + Storage-bucket. |
| `notifier` | `Notifier`-port; `TelegramNotifier` (echt) of `LoggingNotifier` (fallback). |

Twee `ChatClient`-beans: `assistantChatClient` (`@Primary`, met tools) en `gardenChatClient`
(`@Qualifier`, vision, eigen system-prompt).

---

## 5. Koppelingen + het stub/fallback-patroon

Elke koppeling is actief zodra de bijbehorende secret gezet is; anders fallback. Config via
`AppSecrets` (keys hieronder). In prod komen deze uit de **Sealed Secret** via `envFrom`.

| Koppeling | Actief bij | Fallback |
|---|---|---|
| OpenAI (chat + vision) | `RA_OPENAI_API_KEY` | `MockChatModel` (deterministisch) |
| Telegram (Notifier) | `RA_TELEGRAM_BOT_TOKEN` + `RA_TELEGRAM_CHAT_ID` | `LoggingNotifier` |
| Firestore + Storage | `RA_FIREBASE_CREDENTIALS_JSON`(/`_FILE`) + `RA_FIREBASE_PROJECT_ID` (+ `RA_FIREBASE_DATABASE_ID`, `RA_FIREBASE_STORAGE_BUCKET`) | in-memory |
| Google Agenda + Docs | `RA_GOOGLE_OAUTH_CLIENT_ID` + `_SECRET` + `_REFRESH_TOKEN` | `StubCalendarClient` / `StubDocsClient` |
| Google-login | `RA_GOOGLE_CLIENT_ID` (audience) | n.v.t. (vereist) |

Firebase-credentials: **`_JSON`** (inhoud, voor prod/sealed) of **`_FILE`** (pad, lokaal). De
selector-configs vangen init-fouten af en vallen terug op in-memory (geen crashloop).
Preview-omgevingen blanken `RA_FIREBASE_PROJECT_ID` → schrijven niet naar de echte Firestore.

---

## 6. Apps

- **`robberts_assistent/`** — dagelijkse samenvatting + chat-assistent. Google-login (web:
  GIS-knop, mobiel: `signIn()`). Web op OpenShift (`robberts-assistent.vdzonsoftware.nl`) + APK.
- **`groentetuin/`** — moestuin-AI-chat: login → foto's maken/kiezen + tekst → vision-antwoord,
  multi-turn. `ApiClient.gardenChat` (multipart). Web op `moestuin.vdzonsoftware.nl` + APK.
  App-id blijft `nl.vdzon.groentetuin` (interne naam ≠ publieke host "moestuin").
- **`notities/`** — één auto-opslaande notitie, Google-login. Alleen APK.
- **`wind/`** — PoC "Hey Google" → App Actions → native trampoline (TTS + notificatie), praat
  met de backend-chat-assistent voor het windantwoord. Alleen APK.

Web-apps praten same-origin via nginx-proxy `/api/ → robberts-assistent-backend:80` (geen
CORS). APK's praten met `API_BASE_URL=https://robberts-assistent.vdzonsoftware.nl`.

---

## 7. Deploy (GitOps)

- CI (`.github/workflows/`): per component een image-build (backend + per web-app) die naar
  `ghcr.io` pusht en `deploy/base/kustomization.yaml` bumpt; per app een apk-build →
  GitHub Release (vaste tag). `backend-verify.yml` draait `mvn test` op PR + main.
- **ArgoCD** (repo `robberts-infrastructure`) synct `deploy/base` naar namespace
  `robberts-assistent` (prod) en spint per open PR een **preview** op (`deploy/overlays/preview`,
  `robberts-assistent-pr-<n>`, met `RA_PREVIEW_SKIP_GOOGLE_AUTH=true` + `RA_MOCK_AI=true`).
- **Secrets:** `deploy/base/sealed-secret-robberts-assistent.yaml` (Sealed Secrets). Nieuwe
  keys toevoegen met `kubeseal --merge-into` (cert: `robberts-infrastructure/.../cluster-cert.pem`)
  of `deploy/seal-secrets.sh`. Alleen versleutelde vorm in git.
- **Externe host:** Cloudflare Tunnel; nieuwe hostnames handmatig in Cloudflare Zero Trust.

Snelle route deze fase: rechtstreeks op `main` (prod), verifiëren via prod. Story-werk gaat
via de software-factory (branch, worklog in `docs/stories/`, PR → preview).

---

## 8. Conventies

- **Tests:** backend `mvn test` (vanuit `robberts-assistent-backend/`); `ModulithArchitectureTest`
  bewaakt module-grenzen. Apps: `flutter test` + `flutter analyze`.
- **Nieuwe skill:** module = nieuwe subpackage; nieuwe agent-capability = een `@Tool` in
  `assistant/ai/` geregistreerd in `AiConfig`. Koppeling = port + fallback + `AppSecrets`-key.
- **Commits/branches:** Nederlands; factory gebruikt branch-prefix `ai/` en story-worklogs.

---

## 9. Huidige status (juli 2026)

Gebouwd + gedeployed: backend-fundament (auth, notes, summary, assistant + tools), reminders
+ scheduler, moestuin-AI-chat, Google Agenda/Docs (code), Firebase (Firestore + Storage),
Telegram-notifier; apps robberts_assistent + groentetuin/moestuin live met Google-login.

**Live in prod, end-to-end geverifieerd** met echte creds: Firestore (reminders + chat),
Firebase Storage (foto's), Telegram-notifier, echte Google Agenda/Docs (OAuth), vision-chat.

Historische valkuil (opgelost): met firebase-admin erbij crashte de backend op de
**`alpine`**-base met SIGSEGV in gRPC's `netty-tcnative` (BoringSSL is voor glibc gebouwd, niet
musl) → CrashLoopBackOff, waardoor de oude pod bleef draaien en koppelingen op fallback leken te
staan. Opgelost door een **glibc-base** (`eclipse-temurin:21-jre`, zie backend-`Dockerfile`).
Les: een SIGSEGV in native code omzeilt de Java-fail-safe; check `kubectl get pods`/pod-logs.

Nog te bouwen: app-kant **lokaal alarm** + **reminders-scherm** + **FCM-ontvangst** (heeft de
`google-services.json` nodig, staat in `.keys/`).

---

## 10. Meer detail

- `docs/factory/README.md` — index van de factory-docs.
- `docs/factory/functional-spec.md` / `technical-spec.md` — functionele/technische afspraken.
- `docs/factory/development.md` — lokaal bouwen/testen; `deployment.md` — deploy-flow + config.
- `docs/foundation-couplings.md` — ontwerp + gefaseerd implementatieplan van de koppelingen.
- `docs/setup-guide-details.md` — console-setup met concrete waarden (project `tuinbewatering`).
- `PLAN.md` — oorspronkelijke visie (apps, "Hey Google"-aanpak).
