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
- **Morgen-briefing** — dagelijks (pluggable) overzicht met acht secties: een weerkaart voor
  morgen (één kaartbeeld van de kust IJmuiden–Egmond met daarop twee gekleurde windpijlen,
  verticaal gestapeld aan de linkerkant — oranje = ochtend (07:00), blauw = avond (19:00) — elk
  met windsnelheid in kn en een écht getekend weer-icoon (java.awt-vormen: zon/wolk/regen, geen
  emoji), plus een legenda die kleur aan dagdeel koppelt en onderin een dag-breed weersymbool en de
  hoog-/laagwatertijden van die dag (IJmuiden) als getekende tekst — géén betaalde kaarten-API,
  alleen OpenStreetMap-tegels), kite-kans voor morgen (aanlandige wind in knopen + richting bij Wijk aan
  Zee, werkdag/feestdag/vakantie-onderscheid, weergave 🟢/🟡/🔴 per dagdeel), strandfietskans voor
  morgen (eigen kaart, per dagdeel een bolletje MET onderbouwing: wind, regen en getij-nabijheid
  (de laagwatertijd zelf staat sinds SF-1220/1221 op de weerkaart, niet meer hier), zodat het
  oordeel navolgbaar is — kiten en strandfietsen waren tot SF-1192 één samengevoegde kaart),
  afspraken komende 7 dagen (alle agenda's, met per afspraak of er al een reminder ~1u vooraf
  staat en zo niet een één-tap-actie om er één aan te maken), sinds SF-1297 een afvalsectie
  (welke afvalbak(ken) de komende 7 dagen buiten moeten, per ophaalmoment `dd-MM: <type>`, via
  de bestaande, keyless HVC-koppeling — geen AI-call, deterministisch; leeg venster of een
  koppelingsfout degradeert stil naar een neutrale melding zonder de briefing te laten crashen;
  bij een ophaalmoment morgen verschijnt ook "Zet vanavond de \<bak(ken)\> buiten" in de
  18:00-push), een AI-samenvatting "wat moet ik
  komende week echt doen?" (op basis van reminders + de notitie), een moestuin-placeholder, en een
  systeem-checkrapport (zonnepanelen en backups: dummy-data; OpenShift-gezondheid, robotmaaier en
  Software Factory: live via de bestaande koppelingen). Een AI-aanroep bepaalt per check of er
  "aandacht nodig" is (geen hardcoded drempel); is dat zo, dan verschijnt er ook een korte
  vermelding in de 18:00-pushtekst, anders blijft die sectie buiten de push (strandfietsen en de
  weerkaart dragen sowieso nooit bij aan de push). Sinds SF-1267/SF-1268 levert het
  systeem-checkrapport ook de vijf ruwe, niet-AI-samengevatte per-check statusregels
  (kop + tekst) mee in de briefing-respons — de app toont die apart op een eigen "Health
  check"-tab, zie hieronder; de AI-beoordeling/pushtekst is hierdoor niet gewijzigd. Nieuwe
  secties kunnen later worden toegevoegd zonder de kernservice te wijzigen (SPI-patroon, zie
  `docs/factory/technical-spec.md`).
  NL-feestdagen worden algoritmisch berekend; een vakantiedag wordt gedetecteerd als hele-dag
  agenda-item. Sinds SF-1200 wordt de briefing opgebouwd en gecachet (Firestore); sinds
  SF-1274/SF-1275 zijn "Upcoming" en "Health check" onafhankelijk cachebaar/verversbaar (elk een
  eigen cache + `updatedAt`) en wordt beide elk uur automatisch ververst (was: dagelijks om 17:30,
  een half uur vóór de push). Elk scherm toont zijn eigen gecachete versie meteen (incl. wanneer
  'm is opgehaald) en heeft een eigen reload-knop om 'm handmatig live te verversen, zonder de
  andere tab te raken. Dagelijks om 18:00 (Europe/Amsterdam) gaat er automatisch één FCM-push uit
  met een korte samenvatting (ongewijzigd door SF-1274/SF-1275, bouwt los van de caches op); een
  tik erop opent het "Upcoming"-scherm (de app-tab die eerst "Samenvatting", daarna "Morgen"
  heette; sinds SF-1267/SF-1268 gesplitst in "Upcoming" (deze briefing, zonder systeemstatus) en de
  "Health check"-tab met alleen het systeem-checkrapport in ruwe vorm). Sinds SF-1275 toont de
  Software Factory-check binnen het systeem-checkrapport alleen nog stories met een fout of een
  lopende (niet-gemergede) fase, i.p.v. alle stories.
- **Zoekopdrachten (watches)** — de gebruiker maakt een zoekopdracht aan met titel, url,
  instructie, check-frequentie (kantooruren of 1x/dag) en pushmelding-voorkeur; een
  `@Scheduled`-poller checkt elke actieve zoekopdracht op zijn beurt, haalt de pagina op en laat
  een AI beoordelen of aan de instructie is voldaan (status `ONBEKEND`/`NIET_GEVONDEN`/
  `GEVONDEN` + een korte statuszin). Bij de transitie naar `GEVONDEN` gaat er (als gewenst)
  precies één pushmelding uit en stopt de zoekopdracht met pollen tot de gebruiker 'm opnieuw
  aanpast. Falen van de paginaophaal- of AI-stap voor één zoekopdracht crasht de poller niet en
  raakt andere zoekopdrachten niet. CRUD via REST en zichtbaar in de app.

## Push / meldingen

- **Telegram** (uitgaand): reminders/alerts gaan naar Robberts Telegram-groep.
- **FCM**: push naar de app; gebruikt voor reminders/alarms, de dagelijkse
  18:00-Morgen-briefingpush, en sinds SF-1526 de zoekopdrachten-melding
  (`data["type"] = "watch"`) bij een transitie naar "gevonden". App-kant (lokaal alarm,
  reminders-scherm, FCM-ontvangst, deep-link naar de Upcoming-tab) is gebouwd.

## Apps

- **robberts_assistent** — bottom-nav met 6 tabs: dagelijkse Morgen-briefing zonder
  systeemstatus (eerste tab, "Upcoming"), systeem-checkrapport in ruwe, selecteerbare vorm
  (tweede tab, "Health check", sinds SF-1267/SF-1268) + chat met de assistent, in persistente,
  benoemde gesprekken (gesprekkenlijst → chatscherm, foto's via camera/galerij). Gesprekken zijn
  te archiveren (reversibel) en te verwijderen (met bevestiging); de lijst toont eerst de 10
  meest recente, oudere onder een uitklapbare "Ouder"-sectie. Een gebruiker-breed geheugen
  (feiten/voorkeuren) wordt automatisch bijgewerkt na elke chat-beurt en gebruikt als context in
  latere gesprekken; te bekijken/bewerken via "Meer" → "Geheugen". Sinds SF-1526 een
  "Zoekopdrachten"-tab (CRUD-lijst + dialoog voor titel/url/instructie/frequentie/pushmelding,
  zie de watches-skill hierboven). Google-login.
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
