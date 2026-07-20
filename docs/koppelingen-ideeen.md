# Externe koppelingen — ideeën & status

Kandidaat-koppelingen om de assistent nuttiger te maken. Elke koppeling volgt hetzelfde
patroon als de rest: **module + port + stub/fallback + een `@Tool`** zodat de agent 'm kan
gebruiken (zie [CLAUDE.md](../CLAUDE.md) §5).

Onderscheid: **pull** (agent haalt op als je iets vraagt) vs **push** (de wereld tikt jóú aan,
proactief) — dat laatste maakt van een vraag-baak een echte assistent.

Legenda: ✅ = al gebouwd · 🔜 = kandidaat · ⏸️ = geprobeerd, lukt nu niet, later opnieuw · ❌ = bewust niet (Robberts keuze of blijvend blokkerend).

## Weer & buiten (kite / moestuin / strand)
- ✅ Wind/kite — actuele meting + voorspelling IJmuiden (windfinder + Open-Meteo)
- ✅ Regen/weersvoorspelling — Open-Meteo (gratis, geen key), locatie moestuin (Luttik Cie 12,
  Heemskerk), incl. "gaat het komende uren regenen"
- ✅ Getijden — Rijkswaterstaat WaterWebservices, locatie IJmuiden buitenhaven (fietsen op het
  strand, kite)
- ✅ UV / pollen / luchtkwaliteit — Open-Meteo Air-Quality-API, locatie moestuin (Luttik Cie 12,
  Heemskerk)

## Agenda & taken
- ✅ Google Agenda — read-only (afspraken lezen/zoeken)
- ✅ Google Docs — read-only
- ✅ Reminders & alarms — eigen lijst, push-melding of echte wekker
- 🔜 Agenda **schrijven** — upgrade van de bestaande read-only koppeling (agent zet zelf afspraken)
- 🔜 Google Tasks of Todoist — echte takenlijst naast de ene notitie
- ✅ Afvalkalender — HVC Groep (keyless, postcode+huisnummer als config), geen automatische
  reminders nog (🔜 upgrade: "morgen groene bak buiten")

## Communicatie / mail
- ✅ Telegram — uitgaand (meldingen)
- 🔜 Mail one.com — IMAP read-only (hoofdmail; app-wachtwoord in secrets)
- 🔜 Sytec Gmail — Gmail-API read-only
- 🔜 Slack read — kanalen/DM's lezen, samenvatten
- 🔜 Google Contacts — namen ophalen voor "telegram naar X"

## Sport & gezondheid
- ✅ Strava — activiteiten/training (OAuth refresh-token, net als Google). Sinds 30 juni 2026
  vereist Strava een actief betaald abonnement om als developer een app te mogen registreren
  (Robbert heeft Premium, dus geen probleem)
- ❌ Garmin — geen fatsoenlijke publieke API; developer-program staat sowieso gepauzeerd en is
  uberhaupt alleen voor bedrijven, niet voor persoonlijk gebruik

## Huis & tuin
- ✅ Moestuin-AI-chat — foto's + tekst → vision-antwoord
- 🔜 Home Assistant — sensoren, verwarming, lampen; kan de agent ook *triggeren* (thuis-cluster)
- ✅ Robotmaaier (Husqvarna Automower) — `client_credentials`-app-key/secret via
  developer.husqvarnagroup.cloud, status + starten/parkeren
- 🔜 Energieprijzen / slimme meter — dynamische stroomprijs, "goedkoop uur om te laden"

## Onderweg
- 🔜 NS / 9292 — treintijden en vertragingen; `NS_PRIMARY_KEY` staat in secrets.env en **werkt**
  (departures-endpoint getest, HTTP 200) — klaar om de module te bouwen
- ❌ PostNL / DHL — voorlopig niet (Robberts keuze)
- ❌ Reistijd/verkeer (Google Maps) — voorlopig niet, Google Maps Platform vereist een
  Cloud-project met facturering (Robberts keuze: te duur)

## Werk / info
- ✅ FCM push — meldingen naar de telefoon
- ✅ Software Factory — stories + actiepunten (die op Robberts actie wachten), via de bridge-API
  van de software-factory-dashboard-backend (cluster-intern)
- ✅ Nieuws / RSS — laatste NOS-koppen (keyless, standaard feed feeds.nos.nl/nosnieuwsalgemeen)

## Overig
- 🔜 Spotify — nu spelend / afspelen
- 🔜 Recepten & boodschappen → notitie/lijst

---

## Aanrader-volgorde (mijn inschatting)
Alle keyless kandidaten uit deze lijst zijn gebouwd: ~~weer/regen~~ ✅, ~~getijden~~ ✅,
~~luchtkwaliteit/UV/pollen~~ ✅, ~~nieuws/RSS~~ ✅, ~~afvalkalender~~ ✅ (HVC Groep, Heemskerk).

Volgende stappen (vereisen wel een secret/token of extra werk):
1. **NS/9292** — key staat al in secrets.env en werkt; klaar om te bouwen.
2. **Afvalkalender → auto-reminders** — upgrade: automatisch een reminder zetten de avond vóór
   ophaaldag, gebruikt de bestaande reminders-koppeling.
3. **Agenda-schrijven** (upgrade) — maakt de agent proactief
4. **Mail (IMAP one.com)** en **Home Assistant** — zwaardere maar zeer waardevolle volgende stap

Bewust niet (nu): Google Maps, PostNL, DHL (Robberts keuze). Garmin: developer-program is dicht
voor nieuwe (persoonlijke) aanvragen — voorlopig geen weg vooruit.

De actuele status van de al-gebouwde koppelingen is live te zien in de assistent-app onder
**Koppelingen** (met een test-knop), gevoed door `GET /api/v1/couplings` +
`POST /api/v1/couplings/test` (module `couplings`).
