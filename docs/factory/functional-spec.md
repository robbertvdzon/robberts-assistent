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
- **Langdurige zoekopdrachten** — een opdracht bevat titel, absolute HTTP(S)-URL,
  zoekinstructie, frequentie (kantooruren of dagelijks) en een pushvoorkeur. Kantooruren is
  maandag t/m vrijdag 09:00–17:00 Europe/Amsterdam, maximaal uurlijks; dagelijks is maximaal
  eenmaal per lokale kalenderdag. De backend beoordeelt begrensde, server-gerenderde paginatekst
  met een losse tool-loze AI-client. Fouten geven `ONBEKEND` en worden later opnieuw geprobeerd.
  Vóór de eerste controle is de status `NOG_NIET_GECONTROLEERD`; succesvolle controles leveren
  `NIET_GEVONDEN` of `GEVONDEN`, steeds met een leesbare omschrijving en laatste controletijd.
  Een vondst blijft zichtbaar, deactiveert de opdracht en geeft optioneel precies één watch-push.
  Een opdracht kan achteraf worden aangepast; de gewijzigde opdracht wordt actief, krijgt weer de
  status `NOG_NIET_GECONTROLEERD` en wordt volgens de gekozen frequentie opnieuw beoordeeld.
  Naast het vaste schema kan de gebruiker vanuit de app alle nog lopende opdrachten in één keer
  meteen laten controleren ("nu draaien"); dat gebruikt per opdracht exact hetzelfde gedrag als
  een geplande controle, slaat gedeactiveerde (o.a. al gevonden) opdrachten over en is een no-op
  als er niets actiefs is. Een mislukte controle stopt de rest van de run niet.
  Verwijderen haalt een opdracht blijvend uit overzicht en planning, ook wanneer er gelijktijdig
  nog een controle loopt. Pagina's achter login/cookies/captcha en uitsluitend via JavaScript
  geladen inhoud vallen buiten dit gedrag.

## Push / meldingen

- **Telegram** (uitgaand): reminders/alerts gaan naar Robberts Telegram-groep.
- **FCM**: push naar de app; gebruikt voor reminders/alarms, gevonden zoekopdrachten én de dagelijkse
  18:00-Morgen-briefingpush. App-kant (lokaal alarm, reminders-scherm, FCM-ontvangst,
  deep-links naar de Upcoming- en Zoekopdrachten-tab) is gebouwd.

## Apps

- **robberts_assistent** — bottom-nav met 6 tabs: dagelijkse Morgen-briefing zonder
  systeemstatus (eerste tab, "Upcoming"), systeem-checkrapport in ruwe, selecteerbare vorm
  (tweede tab, "Health check", sinds SF-1267/SF-1268) + chat met de assistent, in persistente,
  benoemde gesprekken (gesprekkenlijst → chatscherm, foto's via camera/galerij). Gesprekken zijn
  te archiveren (reversibel) en te verwijderen (met bevestiging); de lijst toont eerst de 10
  meest recente, oudere onder een uitklapbare "Ouder"-sectie. Een gebruiker-breed geheugen
  (feiten/voorkeuren) wordt automatisch bijgewerkt na elke chat-beurt en gebruikt als context in
  latere gesprekken; te bekijken/bewerken via "Meer" → "Geheugen". De tab "Zoekopdrachten"
  vóór "Meer" beheert langdurige websitezoekopdrachten en toont hun leesbare actuele status.
  De aanmaak- en bewerkdialoog valideert titel, absolute HTTP(S)-URL en instructie vóór verzenden; de backend
  herhaalt die validatie. De lijst wordt herladen bij openen, via de reload-knop en na een
  watch-push; een knop ernaast laat alle actieve opdrachten meteen controleren, toont zolang
  een voortgangsindicatie met uitgeschakelde knoppen (geen dubbele run) en meldt een fout in een
  `SnackBar` zonder de lijst kwijt te raken; een ouder, later voltooid laadverzoek mag nieuwere gegevens niet overschrijven.
  Google-login.
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
