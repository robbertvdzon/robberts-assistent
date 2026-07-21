# Functional Spec

Functionele afspraken per skill/app. Overzicht en architectuur: root `CLAUDE.md`.

## Doel

Een persoonlijke assistent die Robbert helpt met uiteenlopende taken, opgebouwd uit losse
**skills** in één backend, aangesproken door apps en door een AI-agent. De AI-agent is ook de
test-harness: skills zijn als `@Tool` aan de agent gehangen, dus per zin te testen.

## Skills (backend)

- **Notities** — één auto-opslaande notitie-string; lezen/overschrijven via REST en via de
  agent (`NotesTools`).
- **Wind / kite-check** — de agent haalt actuele wind + voorspelling bij IJmuiden op
  (windfinder + Open-Meteo, `WindTools`) en beantwoordt kite-vragen.
- **Reminders** — een reminder met tekst + tijdstip; een `@Scheduled`-agent controleert elke
  minuut welke "due" zijn en pusht ze via de Notifier. Aanmaken via REST en via de agent
  ("zet een reminder over 10 minuten"). Zichtbaar in de app.
- **Moestuin-AI-chat** — de gebruiker stuurt tekst + één of meer foto's; de backend slaat de
  foto's op, laat een vision-AI antwoorden (plant/ziekte/verzorging herkennen) en bewaart de
  chat. Multi-turn: doorpraten binnen één conversatie.
- **Google Agenda** (read-only) — de agent leest Robberts agenda ("wanneer moet ik naar de
  tandarts", "vakanties dit jaar").
- **Google Docs** (read-only) — de agent leest een doc op id en beantwoordt vragen eruit.
- **Dagelijkse samenvatting** — oorspronkelijke samenvatting-skill (`summary`); sinds de
  Morgen-briefing (hieronder) niet meer aangesloten op een app-scherm.
- **Morgen-briefing** — dagelijks (pluggable) overzicht met zes secties: kite-kans voor morgen
  (aanlandige wind in knopen + richting bij Wijk aan Zee, werkdag/feestdag/vakantie-onderscheid,
  weergave 🟢/🟡/🔴 per dagdeel), strandfietskans voor morgen (eigen kaart, per dagdeel een
  bolletje MET onderbouwing: wind, regen en getij, zodat het oordeel navolgbaar is — kiten en
  strandfietsen waren tot SF-1192 één samengevoegde kaart), afspraken komende 7 dagen (alle
  agenda's, met per afspraak of er al een reminder ~1u vooraf staat en zo niet een één-tap-actie
  om er één aan te maken), een AI-samenvatting "wat moet ik komende week echt doen?" (op basis
  van reminders + de notitie), een moestuin-placeholder, en een systeem-checkrapport
  (zonnepanelen en backups: dummy-data; OpenShift-gezondheid, robotmaaier en Software Factory:
  live via de bestaande koppelingen). Een AI-aanroep bepaalt per check of er "aandacht nodig" is
  (geen hardcoded drempel); is dat zo, dan verschijnt er ook een korte vermelding in de
  18:00-pushtekst, anders blijft die sectie buiten de push (strandfietsen draagt sowieso nooit
  bij aan de push). Nieuwe secties kunnen later worden toegevoegd zonder de kernservice te
  wijzigen (SPI-patroon, zie `docs/factory/technical-spec.md`). NL-feestdagen worden
  algoritmisch berekend; een vakantiedag wordt gedetecteerd als hele-dag agenda-item. Dagelijks
  om 18:00 (Europe/Amsterdam) gaat er automatisch één FCM-push uit met een korte samenvatting;
  een tik erop opent het "Morgen"-scherm (de app-tab die voorheen "Samenvatting" heette).

## Push / meldingen

- **Telegram** (uitgaand): reminders/alerts gaan naar Robberts Telegram-groep.
- **FCM**: push naar de app; gebruikt voor reminders/alarms én de dagelijkse
  18:00-Morgen-briefingpush. App-kant (lokaal alarm, reminders-scherm, FCM-ontvangst,
  deep-link naar de Morgen-tab) is gebouwd.

## Apps

- **robberts_assistent** — dagelijkse Morgen-briefing (eerste tab, "Morgen") + chat met de
  assistent, in persistente,
  benoemde gesprekken (gesprekkenlijst → chatscherm, foto's via camera/galerij). Gesprekken zijn
  te archiveren (reversibel) en te verwijderen (met bevestiging); de lijst toont eerst de 10
  meest recente, oudere onder een uitklapbare "Ouder"-sectie. Een gebruiker-breed geheugen
  (feiten/voorkeuren) wordt automatisch bijgewerkt na elke chat-beurt en gebruikt als context in
  latere gesprekken; te bekijken/bewerken via "Meer" → "Geheugen". Google-login.
- **groentetuin (moestuin)** — login → moestuin-chat: foto's maken/kiezen + vraag → AI-antwoord,
  doorpraten.
- **notities** — één auto-opslaande notitie. Google-login.
- **wind** — "Hey Google, vraag Wind …" → onzichtbare trampoline die het antwoord uitspreekt
  (TTS) + als notificatie post (leesbaar op Garmin-horloge).

## Gedrag / acceptatie (terugkerend)

- Alles achter Google-login (allowlist `robbert@vdzon.com`); REST-endpoints zijn auth-gated
  (`/healthz` open, `/api/v1/ping` als geauthenticeerde test).
- Elke koppeling werkt zonder secret op een fallback (stub/in-memory/mock) → app en tests
  altijd groen; met secret gaat de echte koppeling live zonder code-wijziging.
- De agent gebruikt een tool zodra de vraag daarom vraagt en verzint geen gegevens die met
  een tool op te halen zijn.
