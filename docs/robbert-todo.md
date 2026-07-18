# Overzicht: status, stappen & testen

Eén centrale checklist. Legenda: **[JIJ]** = jouw console/account-werk · **[IK]** =
ik doe het zodra jij de waarde geeft · **[TEST]** = hoe we het verifiëren.

---

## 1. Wat nu al live is in productie

- ✅ Backend fase 0–3 gedeployed (reminders + scheduler + AI-agent met tools).
- ✅ **Telegram** actief (reminders/alerts → "Robberts Assistent"-groep, via de factory-bot).
- ✅ **AI-agent** werkt met echte OpenAI: reminders zetten, agenda/docs bevragen (nog op stubs).
- ✅ `/api/v1/ping` + **Groentetuin-app** (web-image + APK) gedeployed.
- ✅ **Moestuin-AI-chat** (tekst + foto's → vision-AI) — backend live, app gedeployed.

Draait nog op **fallback** (in-memory / stubs) tot jouw creds er zijn: reminder-opslag,
chat-historie, foto-opslag, en de echte Agenda/Docs. Inloggen in de web-apps/APK's wacht
op de Google-OAuth-setup.

---

## 2. Snelste test NU — zonder enige setup (PR-preview)

De preview-omgeving draait met `SKIP_GOOGLE_AUTH=true`, dus **geen Google-login nodig**.

- **[IK]** Ik open een PR → ArgoCD spint een preview-omgeving op met een eigen URL.
- **[TEST]** In die preview kun je meteen:
  - de **Groentetuin/moestuin-chat** openen, een foto + vraag sturen → **echt AI-antwoord**;
  - via de assistent de reminder-keten testen (zie hieronder).

Zeg "open een PR" en ik regel het. Dit is de manier om alles te zien werken vóórdat je
thuis de accounts regelt.

---

## 3. Per onderdeel: jouw stappen → mijn stappen → testen

### A. Inloggen in de web-apps/APK's in productie (Google + Cloudflare)

- [ ] **[JIJ]** Cloudflare Zero Trust: hostname `moestuin.vdzonsoftware.nl` toevoegen.
- [ ] **[JIJ]** Google Cloud Console — web: `https://moestuin.vdzonsoftware.nl` toevoegen
      aan **Authorized JavaScript origins** van de OAuth-web-client.
- [ ] **[JIJ]** Google Cloud Console — APK: **Android OAuth-client** voor package
      `nl.vdzon.groentetuin` met dezelfde release-SHA-1 als de andere apps.
- **[TEST]** Open `https://moestuin.vdzonsoftware.nl` → login met Google → moestuin-chat.

### B. Firebase — Firestore + Storage + FCM (één project dekt alles)

- [ ] **[JIJ]** Firebase-project aanmaken; **Firestore** aanzetten; **Storage-bucket** aanzetten.
- [ ] **[JIJ]** Service-account-JSON downloaden.
- [ ] **[JIJ]** `google-services.json` downloaden (voor FCM in de app).
- [ ] **[IK]** JSON + project-id sealen (GitOps); de Firestore-impls (reminders + chat-historie)
      en de Firebase-Storage-impl (foto's) achter de bestaande ports zetten; credential-loading
      geschikt maken voor prod (JSON-inhoud i.p.v. bestandspad).
- **[TEST]** "zet een reminder over 2 min" → na backend-herstart bestaat 'ie nog (Firestore);
      een moestuin-foto blijft na herstart bewaard (Storage).

### C. Google Agenda + Docs (backend leest namens jou, read-only)

- [ ] **[JIJ]** OAuth-client (type **Desktop**) aanmaken → `client_id` + `client_secret`.
- [ ] **[JIJ]** Consent screen op **"In production"** (anders verloopt de refresh-token na 7 dagen).
- [ ] **[JIJ]** Eenmalig consent met scopes `calendar.readonly` + `documents.readonly` →
      **refresh-token** ophalen (ik kan je een kant-en-klaar scriptje/link geven).
- [ ] **[IK]** `client_id` + `client_secret` + `refresh_token` sealen → Agenda/Docs van stub naar echt.
- **[TEST]** Via de assistent: "wanneer moet ik naar de tandarts?" en "zoek X in mijn doc" →
      echte antwoorden uit je agenda/docs.

### D. FCM-push + alarm + reminders-scherm in de app (app-werk, geen token nodig)

- [ ] **[IK]** In de assistent-app: **lokaal alarm** (op tijd / on-demand, full-screen over
      lockscreen), **reminders-scherm**, en **FCM-ontvangst** (push → alarm). Gebruikt de
      `google-services.json` uit stap B.
- **[TEST]** Reminder zetten → op de afgesproken tijd gaat het alarm af op je telefoon,
      ook als de app dicht is.

---

## 4. Wat ik van je nodig heb (verzamel dit thuis)

| Van jou | Waarvoor | Ik doe ermee |
|---|---|---|
| Firebase service-account-JSON + project-id | Firestore + Storage | Sealen + impls wiren |
| `google-services.json` | FCM-push | In de app zetten |
| Google OAuth: client_id + secret + refresh_token | Agenda + Docs | Sealen → echt |
| Cloudflare-hostname `moestuin.vdzonsoftware.nl` | Web-app bereikbaar | (console, jij) |
| Google OAuth: web-origin + Android-client (groentetuin) | App-login | (console, jij) |
| (optioneel) eigen Telegram-bot token + chat-id | Eigen bot i.p.v. factory-bot | Omwisselen |

---

## 5. Aanbevolen volgorde

1. **Nu:** ik open een PR-preview → we testen chat + reminders end-to-end zonder setup.
2. **Thuis:** Firebase-project (B) — grootste opbrengst (persistente reminders + chat + foto's).
3. Google OAuth Agenda/Docs (C).
4. Cloudflare + app-login (A) → prod-apps bruikbaar.
5. Ik bouw de app-alarm/FCM-kant (D).

Bij elke stap: jij levert de waarde → ik seal + wire → we draaien de bijbehorende **[TEST]**.
