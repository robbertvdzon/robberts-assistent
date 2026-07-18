# Gedetailleerde setup-handleiding (consoles)

Klik-voor-klik voor de handmatige stappen. Concrete waarden voor jouw setup staan
er al in. Overzicht/volgorde: zie [robbert-todo.md](robbert-todo.md).

Vaste waarden die je vaker nodig hebt:
- Web-app hostname: **`moestuin.vdzonsoftware.nl`**
- Android package (Groentetuin): **`nl.vdzon.groentetuin`**
- Release-keystore **SHA-1**: **`17:72:4A:D7:50:20:57:E7:AB:51:12:F6:1F:58:5E:74:E6:BB:0D:F0`**
  (alias `robberts-assistent` — zelfde keystore als de andere apps)
- Backend-URL (bestaat al): `https://robberts-assistent.vdzonsoftware.nl`

> Tip: doe alles in **één** Google Cloud-project (hetzelfde project waar de bestaande
> OAuth-clients in staan). Firebase is óók een Google Cloud-project, dus koppel Firebase
> aan dat bestaande project — dan delen OAuth, Firestore, Storage en FCM één project.

---

## 1. Cloudflare — `moestuin.vdzonsoftware.nl` bereikbaar maken

Je cluster gebruikt een **gedeelde Cloudflare Tunnel**; je voegt alleen een public
hostname toe die naar dezelfde plek wijst als de bestaande assistent-app. De OpenShift-
Route voor `moestuin.vdzonsoftware.nl` heb ik al aangemaakt, dus routing binnen het
cluster klopt al — Cloudflare hoeft het verkeer alleen bij het cluster te krijgen.

1. Ga naar **Cloudflare dashboard → Zero Trust → Networks → Tunnels**.
2. Open de tunnel die nu ook `robberts-assistent.vdzonsoftware.nl` bedient
   (tab **Public Hostnames**).
3. Kijk hoe de bestaande entry voor `robberts-assistent.vdzonsoftware.nl` is ingevuld
   (Subdomain/Domain + **Service**, bv. `https://...` of `http://...` naar de OpenShift-
   router/ingress).
4. Klik **Add a public hostname** en maak een **identieke** entry, alleen met
   Subdomain = `moestuin` (Domain = `vdzonsoftware.nl`). **Service-waarde exact
   overnemen** van de assistent-entry — zelfde cluster, zelfde router, dus zelfde target.
   - Staat er bij de bestaande een `noTLSVerify`/origin-instelling aan? Neem die ook over.
5. **DNS**: als het toevoegen van de hostname niet automatisch een DNS-record aanmaakt
   (dat gebeurt alleen als `vdzonsoftware.nl` op Cloudflare-nameservers staat), voeg dan
   bij je DNS-provider hetzelfde soort record toe als voor `robberts-assistent` — een
   **CNAME** `moestuin.vdzonsoftware.nl → <zelfde target als robberts-assistent>`.
   Kortom: **spiegel exact wat `robberts-assistent.vdzonsoftware.nl` doet.**

**[TEST]** `https://moestuin.vdzonsoftware.nl/healthz` geeft `ok` → tunnel + route werken.
(Werkt de assistent-hostname wel maar deze niet, dan zit het verschil in DNS of de
Service-waarde — vergelijk 1-op-1 met de assistent-entry.)

---

## 2. Google OAuth — inloggen in de Groentetuin-app

De web-app hergebruikt de **bestaande OAuth-web-client** (dezelfde die al
`robberts-assistent.vdzonsoftware.nl` als origin heeft). De APK heeft een **eigen
Android-client** nodig.

### 2a. Web-login (JavaScript origin toevoegen)
1. **Google Cloud Console → APIs & Services → Credentials**.
2. Open onder **OAuth 2.0 Client IDs** de **Web application**-client die al bij de
   assistent-app hoort (die met `robberts-assistent.vdzonsoftware.nl` bij *Authorized
   JavaScript origins*).
3. Bij **Authorized JavaScript origins** → **Add URI** →
   `https://moestuin.vdzonsoftware.nl` → **Save**.
   (Geen redirect-URI nodig; Google Identity Services gebruikt alleen de origin.)

### 2b. APK-login (nieuwe Android-client)
1. Zelfde Credentials-pagina → **Create Credentials → OAuth client ID**.
2. **Application type: Android**.
3. **Package name:** `nl.vdzon.groentetuin`
4. **SHA-1 certificate fingerprint:**
   `17:72:4A:D7:50:20:57:E7:AB:51:12:F6:1F:58:5E:74:E6:BB:0D:F0`
5. **Create**. (Android-clients hebben geen secret; de koppeling is package + SHA-1.)

**[TEST]** Open de web-app of installeer de APK (release `groentetuin-latest`) → inloggen
met Google → je komt in de moestuin-chat. Werkt login nog niet? Even 5 min wachten
(Google propageert traag) en controleer origin/package/SHA-1 exact.

---

## 3. Firebase — Firestore + Storage + FCM

### 3a. Project koppelen
1. **console.firebase.google.com → Add project**.
2. Kies **"Add Firebase to an existing Google Cloud project"** en selecteer het project
   met je OAuth-clients (zo blijft alles in één project). Analytics mag uit.

### 3b. Firestore (database)
1. Linkermenu **Build → Firestore Database → Create database**.
2. **Production mode** → locatie **`eur3` (europe-west)** → Enable.

### 3c. Storage (foto's)
1. **Build → Storage → Get started**.
2. **Production mode** → zelfde locatie → Done.
3. Noteer de **bucket-naam** (staat bovenaan, meestal `<project-id>.appspot.com`).
   > Vraagt Firebase om je op het **Blaze**-abonnement te zetten (nieuw vereist voor
   > Cloud Storage): dat mag — de gratis tier-limieten gelden nog steeds, bij jouw
   > volume betaal je ~niets. Zet eventueel een budget-alert.

### 3d. Service-account-JSON (voor de backend)
1. **Project settings (tandwiel) → Service accounts**.
2. **Generate new private key → Generate key** → er downloadt een **JSON-bestand**.
3. Dit bestand + het **Project ID** (staat onder General) geef je aan mij.

### 3e. `google-services.json` (voor FCM in de app)
1. **Project settings → General → Your apps → Add app → Android**.
2. Package name van de app die de push moet ontvangen — voor het alarm is dat de
   **assistent-app**: `nl.vdzon.robberts_assistent` (SHA-1 mag je dezelfde als hierboven
   invullen). Registreer → **Download `google-services.json`** → geef 'm aan mij.

**[TEST]** (nadat ik de creds heb gesealed) "zet een reminder over 2 min" → reminder
overleeft een backend-herstart (Firestore). Een moestuin-foto blijft na herstart bewaard
(Storage). Voor FCM: het alarm-app-werk (stap 5 in de todo).

---

## 4. Google OAuth — Agenda + Docs lezen (refresh-token)

Hiervoor is de makkelijkste weg een **Web**-OAuth-client + de OAuth Playground.

### 4a. OAuth-client voor de Playground
1. **Credentials → Create Credentials → OAuth client ID → Web application**.
2. Naam bv. `assistent-backend-agenda-docs`.
3. **Authorized redirect URIs → Add URI:** `https://developers.google.com/oauthplayground`
4. **Create** → noteer **Client ID** + **Client secret**.
5. **OAuth consent screen** (linkermenu) → **Publishing status → In production**
   (Publish app). ⚠️ Anders verloopt je refresh-token na 7 dagen. De "unverified app"-
   waarschuwing tijdens het inloggen mag je gewoon doorklikken (het is voor jezelf).
   Zorg dat de scopes uit 4b toegevoegd zijn of dat de app op "External / In production"
   staat met jou als eigenaar.

### 4b. Refresh-token ophalen via de Playground
1. Ga naar **developers.google.com/oauthplayground**.
2. Rechtsboven **tandwiel** → vink **"Use your own OAuth credentials"** aan → plak je
   **Client ID + Client secret**.
3. Links bij **Step 1** plak deze twee scopes (regel voor regel) in het "Input your own
   scopes"-veld:
   ```
   https://www.googleapis.com/auth/calendar.readonly
   https://www.googleapis.com/auth/documents.readonly
   ```
4. **Authorize APIs** → log in met je Google-account → sta toe.
5. **Step 2 → Exchange authorization code for tokens**.
6. Kopieer de **`refresh_token`** die verschijnt (begint met `1//...`).

Geef mij: **Client ID**, **Client secret**, **refresh_token**.

**[TEST]** (nadat ik gesealed heb) via de assistent: "wanneer moet ik naar de tandarts?"
en "zoek <iets> op in mijn google doc <doc-id>" → echte antwoorden i.p.v. de stub-tekst.

---

## 5. Wat je mij uiteindelijk aanlevert (verzamellijst)

1. Firebase **service-account-JSON** + **Project ID** + **Storage-bucketnaam**
2. **`google-services.json`** (assistent-app)
3. Google OAuth (Agenda/Docs): **Client ID** + **Client secret** + **refresh_token**
4. (Cloudflare + Google-app-login-origins/Android-client doe je zelf; laat me weten als
   iets niet werkt, dan kijken we samen.)

Zodra ik 1–3 heb: ik seal ze via GitOps, wire de Firestore/Storage-impls, en we draaien
per onderdeel de **[TEST]** hierboven.
