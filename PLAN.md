# Robbert's Assistent — plan

## 1. Visie

Een persoonlijke assistent die overal bij helpt, bestaande uit:

- **Eén backend** ("assistant-core") op OpenShift: monoliet, Kotlin/Spring Boot, Postgres.
  Modulair opgebouwd uit **skills** (losse eenheden per domein: wind, todo, zonnepanelen,
  OpenShift-status, NAS-backup-status, tandarts-reminders, moestuin, etc.), zodat er
  makkelijk een nieuwe skill bij kan zonder de rest te raken.
- **Meerdere Android/Flutter-apps** als kanalen naar die backend, elk met een eigen
  verantwoordelijkheid (zie hoofdstuk 3).
- **Achtergrondagents** (cron-jobs in de backend) die proactief checken en een bericht
  sturen via Telegram of een notificatie, als er iets is dat aandacht nodig heeft.
- **Telegram** als bestaand, tweerichtings-kanaal (patroon herbruikbaar uit softwarefactory).
- **"Hey Google" / Gemini for Home** als hands-free ingang naar de apps.

## 2. Google Assistant / Gemini — gekozen aanpak

Vrije-vorm "praat met mijn eigen dienst"-integraties (Actions on Google, conversational
actions) zijn door Google dichtgezet voor nieuwe/publieke ontwikkelaars. Wat wél
self-service beschikbaar is: **Android App Actions**.

- Elke Android-app krijgt automatisch het generieke `OPEN_APP_FEATURE`-intent: "Hey Google,
  open de wind-app" werkt zonder configuratie.
- Met een `shortcuts.xml` per app kun je **capabilities** toevoegen: koppelingen tussen een
  gesproken zin (built-in intent of een zelf gedefinieerd custom intent, evt. met
  parameters/slots) en een deep link/activity in je eigen app. Zo kan "Hey Google, vraag
  de wind-app wat de voorspelling is" direct een specifieke actie in de app starten.
- Geen Google-devicecertificering, geen smart-home account-linking, geen Home Assistant
  nodig. Alles zit in de eigen APK.
- Beperking: dit gaat via een activity-launch, niet via een puur gesproken antwoord zonder
  UI. Oplossing: een **transparante trampoline-activity** (`Theme.Translucent.NoDisplay`
  of vergelijkbaar) die niets toont, direct TTS afvuurt en een notificatie post, en
  zichzelf meteen weer sluit. Voelt zo goed als hands-free aan.
- Notificaties op de telefoon komen automatisch mee naar het Garmin-horloge via Garmin
  Connect's smart-notifications-spiegeling — daar hoeft niets extra's voor gebouwd te
  worden.
- Bewaard voor later, niet nodig voor de PoC: Home Assistant als bridge (MCP Client-
  integratie) voor het geval er ooit toch een écht open, doorlopend gesprek via "Hey
  Google" nodig is. Zie hoofdstuk 6.

## 3. De apps

### 3.1 Wind-app
Losstaande, kleine app voor windinfo (kite-check). Meerdere capabilities:
- huidige windsnelheid (in knopen)
- windsnelheid afgelopen uur
- voorspelling

Bij aanroep (handmatig of via "Hey Google"): spreekt het antwoord uit via TTS én stuurt
een notificatie (leesbaar op het Garmin-horloge). Geen zichtbare UI nodig — trampoline-
activity per capability.

### 3.2 Todo-app
- Handmatig: gewone lijst-UI, items aanvinken/bewerken/verwijderen.
- Via "Hey Google": custom intent met tekst-parameter ("voeg [x] toe aan mijn todo-lijst")
  → trampoline-activity die het item direct wegschrijft en kort bevestigt (TTS/toast).
- Alleen voor Robbert, geen deel-functionaliteit nodig.

### 3.3 Assistent-app
- Alleen het **openen** loopt via "Hey Google, start de assistent" (het gratis
  `OPEN_APP_FEATURE`-intent, geen App Actions-configuratie nodig).
- Zodra open: volledig eigen, vrije chat-UI tegen de assistant-core-backend (zelfde
  LLM-aanpak als de Telegram-assistent uit softwarefactory). Geen Google-beperkingen meer
  van toepassing.
- Later: eigen spraakmodule (Android `SpeechRecognizer` + TTS) binnen de app, losstaand
  van Google Assistant — puur voor gemak, niet voor de "Hey Google"-koppeling.
- Kent (later) alle andere apps/skills en kan er namens de gebruiker mee praten.

## 4. PoC — scope

Doel van de PoC: bewijzen dat de "Hey Google" → App Actions → eigen app-keten werkt,
inclusief hands-free gevoel (TTS + notificatie, geen zichtbaar scherm) en meerdere
capabilities in één app. **Geen backend nodig** — alles gemockt/hardcoded in de app zelf.

Scope:
1. **Eén Flutter-app: "Wind" PoC.**
   - Android-project met `shortcuts.xml` en minimaal 2 capabilities:
     - "huidige windsnelheid" → hardcoded/random waarde
     - "voorspelling" → hardcoded tekst
   - Trampoline-activity (native Android, aangeroepen vanuit de Flutter/Android-shell)
     die:
     - TTS het antwoord uitspreekt
     - een Android-notificatie post met dezelfde tekst
     - zichzelf direct sluit (geen zichtbaar scherm)
   - Handmatig openen van de app toont gewoon een simpel schermpje met dezelfde waarden
     (zodat de app ook zonder stem te testen is).
2. **Testen:**
   - Via Google's App Actions test-tool (`adb shell am start` met de test-intent, of de
     ingebouwde App Actions testflow in Android Studio).
   - Via echte "Hey Google, vraag Wind ..." op de telefoon.
   - Controleren of de notificatie op het Garmin-horloge verschijnt.
3. **Bewust buiten scope voor de PoC:**
   - Backend/OpenShift/Postgres — komt later, als de skills echte data nodig hebben.
   - Todo-app en Assistent-app — volgen na de PoC, zelfde patroon.
   - Echte weerdata/API — pas nodig zodra de keten bewezen werkt.
   - Telegram-koppeling, achtergrondagents — latere fase, zie hoofdstuk 6.

## 5. Technische aandachtspunten voor de PoC

- Flutter zelf heeft geen directe API voor App Actions/shortcuts.xml — dit is Android-
  platformspecifiek (`android/app/src/main/res/xml/shortcuts.xml` +
  `AndroidManifest.xml`-verwijzing). De trampoline-activity is waarschijnlijk het
  makkelijkst als een kleine natieve Android-activity (Kotlin) naast de Flutter-app, niet
  als Flutter-scherm — een Flutter-engine opstarten kost te veel tijd voor een snelle,
  onzichtbare reactie.
- TTS: Android `TextToSpeech`-API, direct vanuit de trampoline-activity, geen Flutter
  nodig.
- Notificatie: gewone `NotificationCompat`, geen speciale Garmin-integratie nodig.
- App Actions capabilities moeten getest worden met een package die een geldige
  `applicationId` heeft en (voor productie) via Play Console geregistreerd staat; voor
  lokaal testen volstaat de App Actions test-tool zonder publicatie.

## 6. Later (na de PoC)

- Assistant-core backend op OpenShift (Kotlin/Spring Boot, Postgres) met skill-registry.
- Todo-app en Assistent-app volgens hetzelfde App Actions-patroon.
- Achtergrondagents per skill (zonnepanelen, OpenShift-status, NAS-backup, moestuin,
  tandarts) als cron-jobs die proactief een Telegram-bericht sturen.
- Telegram-integratie herbruiken uit softwarefactory (tweerichtings, reply via polling).
- Eventueel Home Assistant als bridge (MCP Client-integratie) mocht er toch behoefte
  komen aan een écht open, doorlopend "Hey Google"-gesprek in plaats van losse
  capabilities per app.
- Uitbreiden van skills: elke nieuwe skill = nieuwe capability + endpoint in
  assistant-core, zonder de rest te raken.
