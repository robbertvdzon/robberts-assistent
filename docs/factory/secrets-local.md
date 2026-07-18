# Local Secrets

Zet hier nooit echte secret-waardes in. De backend leest secrets uit `secrets.env` in de
repo-root (gitignored) of uit env-vars; het gedocumenteerde sjabloon is
[`secrets.example.env`](../../secrets.example.env). In productie komen dezelfde keys uit de
**Sealed Secret** (`deploy/base/sealed-secret-robberts-assistent.yaml`) via `envFrom`.

**Alles is optioneel op een fallback na:** ontbreekt een groep, dan draait die koppeling op de
stub/in-memory/mock-fallback (zie `technical-spec.md`). Alleen `RA_REMEMBER_SECRET` en
`RA_GOOGLE_CLIENT_ID` zijn vereist om te starten.

| Key(s) | Waarvoor | Zonder → fallback |
|---|---|---|
| `RA_REMEMBER_SECRET` | HMAC-sessie-token | vereist |
| `RA_GOOGLE_CLIENT_ID` | Google-login (audience) | vereist |
| `RA_ALLOWED_EMAILS` | login-allowlist | default `robbert@vdzon.com` |
| `RA_DATABASE_URL` | Postgres (notities) | H2 in-memory |
| `RA_OPENAI_API_KEY` (+ `RA_MOCK_AI`) | chat + vision | `MockChatModel` |
| `RA_TELEGRAM_BOT_TOKEN` + `RA_TELEGRAM_CHAT_ID` | Telegram-push | `LoggingNotifier` |
| `RA_FIREBASE_CREDENTIALS_FILE`(lokaal)/`_JSON`(prod) + `RA_FIREBASE_PROJECT_ID` (+ `_DATABASE_ID`, `_STORAGE_BUCKET`) | Firestore + Storage | in-memory |
| `RA_GOOGLE_OAUTH_CLIENT_ID` + `_SECRET` + `_REFRESH_TOKEN` | Agenda + Docs (read-only) | stubs |
| `RA_PREVIEW_SKIP_GOOGLE_AUTH` / `RA_MOCK_AI` | alleen preview | prod: false |

Concrete waarden + hoe je ze aanmaakt (Google-project `tuinbewatering`, Firebase, OAuth,
Cloudflare): zie `docs/setup-guide-details.md`. Sealen naar prod: `deploy/seal-secrets.sh` of
`kubeseal --merge-into` (zie `deployment.md`).
