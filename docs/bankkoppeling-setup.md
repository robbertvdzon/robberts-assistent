# Bankzaken: implementatie en ingebruikname

Status 21 november 2026: implementatie staat op main. De productieconfiguratie
wordt via GitOps geïnstalleerd; afzonderlijke banktoestemming in de assistent is
nog nodig voordat transacties kunnen worden opgehaald.

## Wat er klaarstaat

In de assistent staat onder **Meer → Bankzaken** een maandselectie met transacties,
uitgaven, ontvangen geld, categorieën, ‘Nog te beoordelen’ en ‘Overig / extra’.
Je kunt categorieën per betaling corrigeren en eigen herkenningsregels toevoegen
of verwijderen zonder codewijzigingen. Regels matchen een exacte tegenrekening
en/of een deel van de naam of omschrijving; de eerste passende regel wint.
Handmatig gecorrigeerde transacties behouden hun categorie bij opnieuw ophalen.

Vaste regels en jouw handmatige indelingen gaan voor AI. De resterende betalingen
worden via de bestaande Agent Runtime in de achtergrond beoordeeld. De AI krijgt
alleen opgeschoonde tegenpartij/omschrijving en bedragen, met tijdelijke groeps-id's.
IBANs, lange identificaties en bankcredentials gaan niet mee. Onvoldoende zekere
uitkomsten en gemengde winkels/betaalplatformen worden een concrete vraag in het
scherm. Je kunt antwoorden met een categorie en optioneel een herkenningsregel
onthouden. De gekozen tekst en tegenrekening begrenzen die regel; bedragen blijven
altijd afkomstig uit de banktransacties.

**Alle transacties rubriceren** verwerkt alle opgeslagen maanden. Een nieuwe import
start de verwerking ook automatisch. De AI verwerkt maximaal 100 verschillende betaalpatronen per batch, met maximaal
3 batches parallel (begrensd op 4). Herhalingen worden binnen één patroon samengevoegd.
Resultaten worden opgeslagen zodra een batch klaar is; een langzame batch houdt
snellere batches niet tegen. Mislukte batches laten eerder opgeslagen resultaten
staan en kunnen opnieuw worden opgepakt. Het scherm toont de voortgang en wordt
tijdens het rubriceren bijgewerkt. AI-fouten behouden bestaande regels en bevestigingen en
kunnen opnieuw worden geprobeerd.

De browser gebruikt nu deelbare routes, bijvoorbeeld
`/#/meer/bankzaken?month=2026-11`. Refresh, direct openen en maandwisseling behouden
de bestemming; hoofdtabs en schermen onder Meer hebben eigen routes.

De backend gebruikt de AIS-API van Enable Banking voor ABN AMRO NL (personal).
De knop koppelen start toestemming; de callback wisselt de code in voor een
sessie en haalt meteen beschikbare geboekte transacties op. Iedere rekening
behoudt zijn eigen toestemming. Een volgend toestemmingstraject voegt rekeningen
toe of vernieuwt bestaande rekeningen, zonder de overige rekeningen te wissen.
De sessie blijft uitsluitend op de server. Er is geen betaalfunctie.

De portalregistratie is actief met toegang beperkt tot expliciet gekoppelde eigen
rekeningen. Portal-linking activeert de app voor die rekeningen. Voor transacties
in de assistent is daarna **nog een afzonderlijke toestemming via Bankzaken**
nodig; de portal-callbackcode wordt hiervoor niet hergebruikt.

## Berekeningen en grenzen

- Bedragen worden in decimale euro's berekend. Alleen geboekte eurotransacties
  worden verwerkt; afwijkende valuta breken de import af voor controle.
- Interne overboekingen worden herkend aan een tegen-IBAN van een gekoppelde eigen
  rekening of een expliciet bekende eigen IBAN en tellen niet mee bij uitgaven/ontvangsten. De bekende eigen rekeningen kunnen al worden uitgesloten voordat hun API-toestemming is afgerond.
- Ontvangen geld omvat ook terugbetalingen. Een terugbetaling verlaagt ‘extra’
  als die dezelfde categorie heeft; dat kan via een regel of handmatige correctie.
- Onbekende afschrijvingen krijgen ‘Nog te beoordelen’, nooit automatisch ‘extra’.
  Zakelijk, pensioen/sparen, zorg en belastingen hebben aparte categorieën.
  ‘Uitgaven’ omvat alle externe afschrijvingen (dus ook bijvoorbeeld sparen).
- Bankreferenties geven herhaalbare transactiesleutels. Zonder bankreferentie
  gebruikt de import een inhoudsvingerafdruk mét voorkomensnummer, zodat twee
  identieke aankopen niet worden samengevoegd. Het scherm waarschuwt hierbij.
  Als een bank later andere referenties/omschrijvingen levert, is controle op
  dubbele regels nodig; volledige reconciliatie van zulke wijzigingen volgt later.
- Eerste import vraagt maximaal een jaar terug; de bank kan minder leveren.
  Daarna wordt 89 dagen overlappend opgehaald. Eerder opgeslagen maanden blijven
  bewaard. Een oude maand is daarom geen bewijs van volledige rekeninghistorie.
- Er zijn 81 gecontroleerde regels uit de oude app als private startconfiguratie
  overgenomen, inclusief bronrekening, richting en uitsluitingen. De oude brede
  restcategorie is niet overgenomen; optionele uitgaven worden apart beoordeeld.
  Creditcardexports zijn nog niet aangesloten.
- AI-rubricering is aangesloten; er zijn nog geen banktools voor de chat of automatische maandmeldingen. Bij optionele
  dagelijkse synchronisatie zijn maandtotalen na ophalen direct beschikbaar in
  het scherm. Bankgegevens worden niet via globale pushberichten verspreid.

## Configuratie buiten Git

Alle onderstaande configuratie is opt-in. Zonder volledige configuratie toont
Bankzaken ‘wordt voorbereid’; er worden geen bankverzoeken uitgevoerd.

| Variabele | Betekenis |
| --- | --- |
| `RA_BANKING_APP_ID` | App-id uit Enable Banking |
| `RA_BANKING_PRIVATE_KEY_FILE` | Serverpad naar RSA PKCS#8 PEM-privésleutel |
| `RA_BANKING_STORAGE_KEY` | Base64 van een aparte willekeurige AES-sleutel van 32 bytes |
| `RA_BANKING_OWNER` | Vaste identiteit van de bankadministratie |
| `RA_BANKING_LOGIN_EMAILS` | Extra expliciet toegestane Google-loginadressen voor dezelfde eigenaar, kommagescheiden; standaard alleen de eigenaar |
| `RA_BANKING_REDIRECT_URL` | Default `https://robberts-assistent.vdzonsoftware.nl/api/v1/banking/callback` |
| `RA_BANKING_INITIAL_RULES_FILE` | Optioneel serverbestand met private startregels en expliciet bekende eigen IBANs |
| `RA_BANKING_AI_BATCH_SIZE` | Maximaal aantal betaalpatronen per AI-batch; default 100, bereik 1–200 |
| `RA_BANKING_AI_PARALLELISM` | Maximaal aantal gelijktijdige bank-AI-batches; default 3, bereik 1–4 |
| `RA_BANKING_AUTO_SYNC` | Default `false`; `true` haalt dagelijks om 04:15 Amsterdam op |

Firebase moet geconfigureerd zijn voordat echte banktoegang kan starten. Bij een
opslagfout valt deze module **niet** terug op vluchtige opslag. Bankrecords zijn
per eigenaar gescheiden en aanvullend versleuteld met AES-GCM, met het documentpad
als authenticated associated data. Transacties staan per maand; sessies en regels
in aparte versleutelde records. Expliciete loginaliases verwijzen naar dezelfde vaste eigenaar; de algemene app-allowlist geeft niet automatisch banktoegang. De app krijgt alleen een beperkt rekeningmodel,
geen sessie-id of toegangssleutel. Bewaar de AES-sleutel samen met de beveiligde
back-upvoorziening: zonder deze sleutel is opgeslagen bankhistorie niet leesbaar.

Echte bankconfiguratie wordt geweigerd wanneer preview-login zonder Google aan
staat. Gebruik voor automatische previewtests uitsluitend fictieve data en
stub-gateways; verstrek geen productiecredentials aan testagents of Agent Runtime.

Callback-state is cryptografisch willekeurig, 15 minuten geldig en wordt atomair
één keer opgebruikt. Actieve bewerkingen per eigenaar gebruiken een Firestore-lease
om parallelle imports/correcties tussen pods te voorkomen. HTTP-antwoorden voor
bankgegevens zijn `no-store`; de callback heeft `no-referrer` en een beperkte CSP.
Providerfouten en bankresponses worden niet gelogd.

De productiepatch `deploy/base/banking-patch.yaml` installeert de secretmount en
environment-variabelen uit het aparte SealedSecret `robberts-assistent-banking`.
Het secret heeft geen Reflector-annotaties. De preview-overlay verwijdert zowel
het SealedSecret als de mount en schakelt bankconfiguratie expliciet uit.
`deploy/examples/banking-mount.yaml` blijft beschikbaar als los voorbeeld.
Let bij ingebruikname op dat proxy/access-logs callbackqueryparameters niet
bewaren. Laat Firestore-TTL verlopen pending-state opruimen indien geconfigureerd;
verlopen states worden functioneel altijd geweigerd.

## Nog nodig bij terugkomst

1. Resterende eigen rekeningen in de Enable Banking-portal linken.
2. Productie-uitrol met bankconfiguratie controleren (GitOps).
3. Via **Bankzaken → Rekening koppelen / vernieuwen** banktoestemming geven.
4. Toestemming kan per bank één rekening teruggeven. Het scherm toont de
   ontbrekende bekende rekeningen met gerichte koppelknoppen; het API-verzoek noemt
   de gewenste IBAN(s) nu expliciet. Portalregistratie en feitelijk verleende
   sessietoegang zijn twee verschillende stappen.
5. Eerste import controleren: alle rekeningen, periode, aantallen, interne
   overboekingen en categorieën. Controleer de overgenomen regels en beantwoord resterende vragen, met expliciete scheiding tussen nodig en extra.

Officiële documentatie: [API](https://enablebanking.com/docs/api/reference/) en
[Eigen rekeningen activeren](https://enablebanking.com/docs/api/linked-accounts).
