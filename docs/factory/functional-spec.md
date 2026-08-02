# Functional Spec

Functionele afspraken per skill/app. Overzicht en architectuur: root `CLAUDE.md`.

## Doel

Een persoonlijke assistent die Robbert helpt met uiteenlopende taken, opgebouwd uit losse
**skills** in één backend, aangesproken door apps en door een AI-agent. De AI-agent is ook de
test-harness: skills zijn als `@Tool` aan de agent gehangen, dus per zin te testen.

## Skills (backend)

- **Notities** — één auto-opslaande notitie-string; lezen/overschrijven via REST en via de
  agent (`NotesTools`). Sinds SF-1808 bewaart de backend bovendien **elke opgeslagen versie**:
  bij elke save (ook die via de agent) komt er een versie-record bij, tenzij de tekst identiek is
  aan de vorige versie — zo levert de autosave elke 10 seconden geen stapel dubbels op. Het
  versie-overzicht (nieuwste eerst, maximaal 200, zonder tekst) en de tekst van één versie zijn
  op te vragen; een onbekende versie geeft "niet gevonden". Elke nacht om 03:30 ruimt de backend
  op: van de laatste 7 dagen blijft alles bewaard, van alles daarvóór blijft per kalenderdag
  alleen de laatste versie over. De notitie zelf en het opslagformaat (platte markdown) blijven
  ongewijzigd.
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
  tik erop opent het scherm "Vandaag" (de app-tab die eerst "Samenvatting", "Morgen" en daarna
  "Upcoming" heette; het systeem-checkrapport staat als "Health check" onder "Meer"). Sinds
  SF-1275 toont de
  Software Factory-check binnen het systeem-checkrapport alleen nog stories met een fout of een
  lopende (niet-gemergede) fase, i.p.v. alle stories.
  Sinds SF-1621 is de weerdata in de briefing storingsbestendig: een mislukte opvraging bij
  Open-Meteo wordt automatisch een paar keer opnieuw geprobeerd, en lukt het dan nog niet, dan
  toont de briefing de laatst opgehaalde voorspelling (tot 12 uur oud) met de toevoeging
  `(gegevens van HH:MM)` achter de normale inhoud van de secties weerkaart, kiten en
  strandfietsen; zijn wind- en weerdata allebei verouderd, dan wordt het oudste ophaalmoment
  getoond. Pas als er helemaal niets bruikbaars is, verschijnt de bestaande foutmelding
  ("Kon Open-Meteo (-wind) niet ophalen (HTTP …)"), ongewijzigd van tekst. De drie secties delen
  bovendien dezelfde opgehaalde gegevens, zodat één briefing-opbouw nog maar twee opvragingen doet
  in plaats van zes. Verouderde data verandert niets aan de tegels of de 18:00-push, en de
  weerkaart-afbeelding wordt ook op verouderde gegevens normaal opgebouwd.
  Bovenaan Vandaag staan direct onder het bijgewerkt-tijdstip maximaal de eerste drie geldige
  statustegels in briefingvolgorde. Een tegel is geldig als zowel een bekende status als een niet-
  leeg label aanwezig is. Kiten toont windkracht en -richting van het gunstigste dagdeel,
  strandfietsen het gunstigste oordeel en afval het eerstvolgende korte baktype (of `geen`). Groen
  betekent goed, geel let op en rood niet; status wordt ook als woord uitgesproken en getoond. Een
  tik toont precies één volledig sectiedetail onder de tegelrij. Getegelde secties staan niet ook
  permanent als kaart; statussecties na de eerste drie, overige secties en onbetrouwbare/
  foutsecties wel.
- **Langdurige zoekopdrachten** — een opdracht bevat titel, absolute HTTP(S)-URL,
  zoekinstructie en een pushvoorkeur. Er is geen frequentiekeuze (meer): elke actieve opdracht
  wordt overdag maximaal uurlijks gecontroleerd — het lokale uur (Europe/Amsterdam) moet in
  08:00–22:59 liggen, ook in het weekend, en er moet minstens een uur zijn verstreken sinds de
  vorige controle. Tussen 23:00 en 07:59 gebeurt er niets. De backend beoordeelt begrensde, server-gerenderde paginatekst
  met een losse tool-loze AI-client. Fouten geven `ONBEKEND` en worden later opnieuw geprobeerd.
  Vóór de eerste controle is de status `NOG_NIET_GECONTROLEERD`; succesvolle controles leveren
  `NIET_GEVONDEN` of `GEVONDEN`, steeds met een leesbare omschrijving en laatste controletijd.
  Een vondst blijft zichtbaar, deactiveert de opdracht en geeft optioneel precies één watch-push.
  Een opdracht kan achteraf worden aangepast; de gewijzigde opdracht wordt actief, krijgt weer de
  status `NOG_NIET_GECONTROLEERD` en wordt bij de eerstvolgende beurt opnieuw beoordeeld.
  Naast het vaste schema kan de gebruiker vanuit de app alle nog lopende opdrachten in één keer
  meteen laten controleren ("nu draaien"); dat gebruikt per opdracht exact hetzelfde gedrag als
  een geplande controle, slaat gedeactiveerde (o.a. al gevonden) opdrachten over en is een no-op
  als er niets actiefs is. Een mislukte controle stopt de rest van de run niet.
  Sinds SF-1595 kan de gebruiker de zoekopdrachten ook via de assistent-chat in gewone taal
  opvragen ("welke zoekopdrachten lopen er?"), aanmaken ("houd deze pagina in de gaten en zeg het
  als er X op staat") en aanpassen ("wijzig de zoekinstructie van zoekopdracht \<titel\>"), met
  dezelfde regels en dezelfde gegevens als het Zoekopdrachten-scherm; zonder zoekopdrachten volgt
  een nette melding in plaats van een lege lijst, en een ongeldige URL of lege instructie levert een
  leesbare Nederlandse foutmelding op in plaats van een fout. Een via de chat aangepaste opdracht
  wordt — net als bij bewerken in de app — weer actief en opnieuw gecontroleerd; het antwoord meldt
  dat expliciet. Verwijderen via de chat kan bewust niet, dat blijft het Zoekopdrachten-scherm.
  Verwijderen haalt een opdracht blijvend uit overzicht en planning, ook wanneer er gelijktijdig
  nog een controle loopt. Pagina's achter login/cookies/captcha en uitsluitend via JavaScript
  geladen inhoud vallen buiten dit gedrag.
- **App-start-logging** (sinds SF-1704, diagnostisch) — elke app-start legt één regeltje vast in de
  backend: waar de start vandaan kwam (`ASSISTANT`, `LAUNCHER`, `OTHER` of `UNKNOWN`) en wat er
  precies aan gegevens meekwam (referrer, action, categories, extras, platform, app-versie). Dat is
  nodig omdat nog niet zeker is wát Google Assistent/Gemini meestuurt; met die gegevens kan de
  herkenning later scherper gezet worden. De backend bepaalt zelf tijdstip en id (de klok van het
  toestel wordt niet vertrouwd), bewaart de laatste starts 30 dagen en schrijft per opgeslagen start
  precies één logregel `APP_LAUNCH source=… platform=… referrer=… action=… categories=… extras=…`.
  Uitlezen gaat bewust via de backend-log (`oc logs … | grep APP_LAUNCH`) — er is met opzet geen
  app-scherm voor. Een onbekende of ontbrekende bron is geen fout maar `UNKNOWN`; juist dán wil je
  de regel hebben. Zonder sessie-token slaat de app het melden stil over, en een mislukte melding
  mag de app nooit ophouden of laten crashen.

## Push / meldingen

- **Telegram** (uitgaand): reminders/alerts gaan naar Robberts Telegram-groep.
- **FCM**: push naar de app; gebruikt voor reminders/alarms, gevonden zoekopdrachten én de dagelijkse
  18:00-Morgen-briefingpush. App-kant (lokaal alarm, reminders-scherm, FCM-ontvangst,
  deep-links naar Vandaag en het Zoekopdrachten-scherm) is gebouwd.

## Apps

- **robberts_assistent** — bottom-nav met 4 tabs: Vandaag (dagelijkse Morgen-briefing zonder
  systeemstatus), Assistent, Taken (het bestaande Herinneringen-scherm) en Meer. Het
  systeem-checkrapport in ruwe, selecteerbare vorm ("Health check", sinds SF-1267/SF-1268) en
  Zoekopdrachten, Koppelingen, Nachtchecks, Geheugen en Updates zijn routes onder Meer. De
  assistent gebruikt persistente,
  benoemde gesprekken (gesprekkenlijst → chatscherm, foto's via camera/galerij). Gesprekken zijn
  te archiveren (reversibel) en te verwijderen (met bevestiging); de lijst toont eerst de 10
  meest recente, oudere onder een uitklapbare "Ouder"-sectie. Een gebruiker-breed geheugen
  (feiten/voorkeuren) wordt automatisch bijgewerkt na elke chat-beurt en gebruikt als context in
  latere gesprekken; te bekijken/bewerken via "Meer" → "Geheugen". Het chat-invoerveld start sinds
  SF-1732 op één regel en groeit mee met de ingetypte tekst tot maximaal vijf regels; daarna scrollt
  de tekst binnen het veld. Enter maakt een nieuwe regel in plaats van te versturen — versturen gaat
  uitsluitend via de verzendknop rechts — en de foto- en verzendknop blijven onderaan staan terwijl
  het veld groeit. Meerregelige tekst wordt met behoud van de regeleindes verstuurd; alleen
  spaties aan het begin en eind vallen weg. Sinds SF-1767 kan in dat veld ook een **afbeelding uit
  het klembord** worden geplakt — meestal een screenshot, via de plak-knop van het
  Android-toetsenbord (Gboard). De afbeelding verschijnt als gewone bijlage bij het bericht, precies
  als een foto uit de galerij, en gaat mee zodra Robbert verstuurt; de omweg via de galerij is dus
  niet meer nodig. Tekst plakken blijft ongewijzigd. Staat er geen bruikbare afbeelding op het
  klembord (geen data of een ander formaat dan PNG/JPEG), dan komt er geen bijlage bij en verschijnt
  hooguit één korte melding onderin. In de webversie blijft plakken beperkt tot tekst.
  In **praatmodus** is het gesprek
  sinds SF-1711 doorlopend: na een voorgelezen antwoord luistert de app automatisch weer, zonder dat
  Robbert de microfoon opnieuw hoeft aan te tikken. Dat stopt zodra hij zelf op de stop-/mic-knop
  tikt, naar chatmodus wisselt of het scherm verlaat, bij een spraak- of chat-fout (de bestaande
  foutmelding blijft staan), en na twee luisterrondes achter elkaar waarin niets verstaanbaars
  binnenkwam (dan gewoon terug naar de mic-knop, zonder foutmelding). Een antwoord dat wordt
  voorgelezen is kort en in gewone spreektaal (maximaal twee korte zinnen, geen lijstjes/opmaak/
  URL's/emoji, getallen uitspreekbaar geschreven); getypt chatten blijft even uitgebreid als
  voorheen. "Zoekopdrachten"
  beheert langdurige websitezoekopdrachten en toont hun leesbare actuele status.
  De aanmaak- en bewerkdialoog valideert titel, absolute HTTP(S)-URL en instructie vóór verzenden; de backend
  herhaalt die validatie. De lijst wordt herladen bij openen, via de reload-knop en na een
  watch-push; een knop ernaast laat alle actieve opdrachten meteen controleren, toont zolang
  een voortgangsindicatie met uitgeschakelde knoppen (geen dubbele run) en meldt een fout in een
  `SnackBar` zonder de lijst kwijt te raken; een ouder, later voltooid laadverzoek mag nieuwere gegevens niet overschrijven.
  Briefing-pushes sluiten een eventueel geopende Meer-route en openen Vandaag; watch-pushes openen
  een verse Zoekopdrachten-route. Statussen worden met kleur én een woordelijke pil getoond.
  Start Robbert de app met "Hé Google, start Robberts assistent app", dan opent de app sinds
  SF-1704 meteen een **nieuw gesprek in praatmodus** dat al probeert te luisteren, zodat hij direct
  zijn vraag kan stellen; is spraak niet beschikbaar of is de microfoonpermissie geweigerd, dan
  toont het scherm gewoon de bestaande foutmelding en de mic-knop. Start hij de app op de gewone
  manier, dan verandert er niets aan het gedrag. Elke start (ook op web, dan met `platform = "web"`
  en bron `UNKNOWN`) wordt op de achtergrond bij de backend gemeld, zie "App-start-logging".
  Google-login.
- **groentetuin (moestuin)** — login → moestuin-chat: foto's maken/kiezen + vraag → AI-antwoord,
  doorpraten.
- **notities** — één auto-opslaande notitie. Google-login. De app is donker (zwarte
  achtergrond, witte tekst, ook op het inlogscherm) en de notitie is sinds SF-1801 echt op te
  maken terwijl je typt: een smalle balk bovenin met precies vijf knoppen — vet, cursief,
  onderstrepen, opsomming en 'opmaak wissen' — en wat je ziet is wat je krijgt. Onder water
  blijft de notitie één platte markdown-tekst (`**vet**`, `*cursief*`, `<u>onderstreept</u>`,
  `- ` voor bullets), zodat de assistent en de dagelijkse briefing er net als voorheen bij
  kunnen; alle overige tekst en opmaak (kopjes, tabellen, links, lege regels) blijft letterlijk
  staan, dus tekst die de assistent zelf toevoegt raakt niet beschadigd. Automatisch opslaan,
  de Opslaan-knop, de statusregel en uitloggen werken ongewijzigd. Sinds SF-1808 staan links in
  die balk een **Ongedaan maken**- en een **Opnieuw**-knop (uitgegrijsd als er niets te doen valt;
  vlak na het openen van de notitie dus allebei, zodat één keer undo de notitie nooit leegmaakt),
  en opent de knop **Versies** in de AppBar een lijst van eerdere versies met Nederlandse datum en
  tijd (`vandaag 11:30`, `gisteren 11:30`, `ma 28 jul 09:05`). Tikken op een regel toont die oude
  tekst alleen-lezen; **Terugzetten** vraagt eerst om bevestiging en zet de tekst daarna terug in
  de editor — dat terugzetten is met de undo-knop ongedaan te maken en wordt via de gewone
  autosave als nieuwe versie opgeslagen.
- **wind** — "Hey Google, vraag Wind …" → onzichtbare trampoline die het antwoord uitspreekt
  (TTS) + als notificatie post (leesbaar op Garmin-horloge).

## Gedrag / acceptatie (terugkerend)

- Alles achter Google-login (allowlist `robbert@vdzon.com`); REST-endpoints zijn auth-gated
  (`/healthz` open, `/api/v1/ping` als geauthenticeerde test).
- Elke koppeling werkt zonder secret op een fallback (stub/in-memory/mock) → app en tests
  altijd groen; met secret gaat de echte koppeling live zonder code-wijziging.
- De agent gebruikt een tool zodra de vraag daarom vraagt en verzint geen gegevens die met
  een tool op te halen zijn.
