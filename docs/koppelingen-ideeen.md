# Externe koppelingen — ideeën & status

Kandidaat-koppelingen om de assistent nuttiger te maken. Elke koppeling volgt hetzelfde
patroon als de rest: **module + port + stub/fallback + een `@Tool`** zodat de agent 'm kan
gebruiken (zie [CLAUDE.md](../CLAUDE.md) §5).

Onderscheid: **pull** (agent haalt op als je iets vraagt) vs **push** (de wereld tikt jóú aan,
proactief) — dat laatste maakt van een vraag-baak een echte assistent.

Legenda: ✅ = al gebouwd · 🔜 = kandidaat.

## Weer & buiten (kite / moestuin / strand)
- ✅ Wind/kite — actuele meting + voorspelling IJmuiden (windfinder + Open-Meteo)
- ✅ Regen/weersvoorspelling — Open-Meteo (gratis, geen key), incl. "gaat het komende uren regenen"
- 🔜 Getijden — Rijkswaterstaat (fietsen op het strand, kite)
- 🔜 UV / pollen / luchtkwaliteit — Open-Meteo levert dit ook

## Agenda & taken
- ✅ Google Agenda — read-only (afspraken lezen/zoeken)
- ✅ Google Docs — read-only
- ✅ Reminders & alarms — eigen lijst, push-melding of echte wekker
- 🔜 Agenda **schrijven** — upgrade van de bestaande read-only koppeling (agent zet zelf afspraken)
- 🔜 Google Tasks of Todoist — echte takenlijst naast de ene notitie
- 🔜 Afvalkalender → automatische reminders ("morgen groene bak buiten")

## Communicatie / mail
- ✅ Telegram — uitgaand (meldingen)
- 🔜 Mail one.com — IMAP read-only (hoofdmail; app-wachtwoord in secrets)
- 🔜 Sytec Gmail — Gmail-API read-only
- 🔜 Slack read — kanalen/DM's lezen, samenvatten
- 🔜 Google Contacts — namen ophalen voor "telegram naar X"

## Sport & gezondheid
- 🔜 Strava — activiteiten/training (OAuth refresh-token, net als Google)
- 🔜 Garmin — slaap/stappen/HR (let op: geen fatsoenlijke publieke API, onofficieel/lastig)

## Huis & tuin
- ✅ Moestuin-AI-chat — foto's + tekst → vision-antwoord
- 🔜 Home Assistant — sensoren, verwarming, lampen; kan de agent ook *triggeren* (thuis-cluster)
- 🔜 Robotmaaier — status/starten (afhankelijk van merk-API, bv. Husqvarna Automower)
- 🔜 Energieprijzen / slimme meter — dynamische stroomprijs, "goedkoop uur om te laden"

## Onderweg
- 🔜 NS / 9292 — treintijden en vertragingen
- 🔜 PostNL / DHL — track & trace (PostNL-API is vaak zakelijk = lastiger)
- 🔜 Reistijd/verkeer — Google Maps ("wanneer vertrekken voor je afspraak")

## Werk / info
- ✅ FCM push — meldingen naar de telefoon
- 🔜 Software Factory — build/deploy-status, stories, worklogs
- 🔜 Nieuws / RSS — personal-news-feed in de dagelijkse samenvatting

## Overig
- 🔜 Spotify — nu spelend / afspelen
- 🔜 Recepten & boodschappen → notitie/lijst

---

## Aanrader-volgorde (mijn inschatting)
1. **Weer/regen** (Open-Meteo, keyless) — meeste synergie, minste gedoe
2. **Afvalkalender → auto-reminders** — direct dagelijks nut, gebruikt bestaande reminders
3. **Agenda-schrijven** (upgrade) — maakt de agent proactief
4. **Mail (IMAP one.com)** en **Home Assistant** — zwaardere maar zeer waardevolle volgende stap

De actuele status van de al-gebouwde koppelingen is live te zien in de assistent-app onder
**Koppelingen** (met een test-knop), gevoed door `GET /api/v1/couplings` +
`POST /api/v1/couplings/test` (module `couplings`).
