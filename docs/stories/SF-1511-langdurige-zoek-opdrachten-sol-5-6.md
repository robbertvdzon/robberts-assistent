# SF-1511 - langdurige zoek opdrachten (sol 5.6)

## Story

langdurige zoek opdrachten (sol 5.6)

<!-- refined-by-factory -->

## Samenvatting

Voeg een apart tabblad toe voor langdurige zoekopdrachten op websites.
De gebruiker kan per opdracht een titel, webadres, zoekinstructie, controlefrequentie en meldingsvoorkeur vastleggen.
Het overzicht toont per opdracht de actuele status.
Zodra het gezochte wordt gevonden, stopt de opdracht en volgt desgewenst een pushmelding.

## Scope

- Voeg aan de backend een zelfstandige module `watches` toe voor opslag, beoordeling en periodieke uitvoering van zoekopdrachten.
- Een zoekopdracht bevat minimaal:
  - een unieke id;
  - titel;
  - geldige absolute HTTP(S)-URL;
  - instructie die beschrijft wat op de pagina gezocht moet worden;
  - frequentie `KANTOORUREN` of `DAGELIJKS`;
  - keuze of bij een vondst een pushmelding wordt verstuurd;
  - status `NOG_NIET_GECONTROLEERD`, `NIET_GEVONDEN`, `GEVONDEN` of `ONBEKEND`;
  - een leesbare statusomschrijving;
  - tijdstip van de laatste controle;
  - actief/inactief-status.
- Bied geauthenticeerde REST-operaties voor aanmaken, opvragen en verwijderen van zoekopdrachten. Titel, URL en instructie zijn verplicht.
- Bewaar opdrachten in Firestore met een in-memory fallback wanneer Firebase niet beschikbaar is, volgens de bestaande repositoryconfiguratiepatronen.
- Gebruik één configureerbare periodieke poller met `ra.watches.poll-interval-ms` en standaardwaarde `300000`. Een afzonderlijke, deterministisch testbare functie bepaalt per opdracht of deze aan de beurt is.
- `KANTOORUREN` betekent maandag tot en met vrijdag, van 09:00 inclusief tot 17:00 exclusief in `Europe/Amsterdam`, maximaal eenmaal per uur.
- `DAGELIJKS` betekent maximaal eenmaal per kalenderdag in `Europe/Amsterdam`.
- Haal de webpagina op met de bestaande JDK-HTTP-aanpak, zet server-gerenderde HTML om naar begrensde platte tekst en laat een aparte tool-loze `watchChatClient` de pagina tegen de instructie beoordelen.
- Laat de beoordelaar antwoorden met regel 1 `GEVONDEN` of `NIET GEVONDEN` en regel 2 een korte Nederlandstalige statusomschrijving. Parse dit defensief; een netwerkfout, AI-fout of afwijkend antwoord geeft status `ONBEKEND` en blijft bij een volgende geplande controle opnieuw geprobeerd worden.
- Bij de eerste overgang naar `GEVONDEN` wordt de opdracht inactief. Alleen wanneer meldingen zijn ingeschakeld, wordt daarbij precies één push verstuurd met `data.type = watch`.
- Voeg in `robberts_assistent` vóór de tab `Meer` een zesde tab `Zoekopdrachten` toe. De bestaande tabs behouden hun betekenis: `Upcoming` blijft index 0, `Health check` index 1 en `Assistent` de standaardtab op index 2; `Meer` schuift naar index 5.
- Het nieuwe scherm toont de titel en leesbare status van alle opdrachten en biedt aanmaken en verwijderen. Bij aanmaken worden titel, URL, instructie, frequentie en pushvoorkeur afzonderlijk ingevoerd.
- Een tik op een watch-push opent de tab `Zoekopdrachten`.
- Werk de relevante factory- en overzichtsdocumentatie bij met het nieuwe gedrag.

## Acceptance criteria

- Een gebruiker kan via de tab `Zoekopdrachten` een opdracht aanmaken met afzonderlijke waarden voor titel, URL, instructie, frequentie en pushvoorkeur.
- Lege titel of instructie en een lege, ongeldige of niet-HTTP(S)-URL worden met een duidelijke validatiemelding geweigerd.
- Een opgeslagen opdracht verschijnt in het overzicht met de titel en de meest recente leesbare status; vóór de eerste controle is duidelijk zichtbaar dat nog niet is gecontroleerd.
- De opdrachten blijven na een backendherstart behouden wanneer Firestore beschikbaar is; zonder Firebase start en werkt de applicatie met de in-memory fallback.
- Een kantoorurenopdracht wordt uitsluitend op maandag tot en met vrijdag tussen 09:00 en 17:00 lokale tijd gecontroleerd en nooit vaker dan eenmaal per uur.
- Een dagelijkse opdracht wordt nooit vaker dan eenmaal per lokale kalenderdag gecontroleerd.
- Een nieuwe opdracht wordt bij de eerste poll waarop zij volgens haar frequentie aan de beurt is gecontroleerd.
- Na een succesvolle beoordeling worden status, statusomschrijving en laatste controletijd opgeslagen en in het overzicht teruggegeven.
- Netwerkfouten, niet-succesvolle HTTP-antwoorden, AI-fouten en onherkenbare AI-antwoorden stoppen de poller niet, veroorzaken geen push en leveren status `ONBEKEND` op zodat later opnieuw wordt geprobeerd.
- Bij de eerste overgang naar `GEVONDEN` wordt de opdracht inactief en daarna niet opnieuw gecontroleerd.
- Met push ingeschakeld wordt bij die overgang precies één push verstuurd; met push uitgeschakeld wordt geen push verstuurd. In beide gevallen blijft de gevonden status zichtbaar.
- Een tik op de push met type `watch` opent de tab `Zoekopdrachten`.
- Een opdracht kan worden verwijderd en verdwijnt daarna uit het overzicht en uit de planning.
- De twee parallelle tablijsten in `HomeScreen` blijven gelijk: er zijn zes schermen en zes navigatiebestemmingen, de standaardtab blijft `Assistent` en de bestaande briefing-deeplink blijft `Upcoming` openen.
- Backendtests dekken minimaal validatie, opslagfallback, frequentiegrenzen, opnieuw proberen na fouten, defensief parsen en éénmalige push/deactivatie. Flutter-widgettests dekken minimaal aanmaken, weergeven, verwijderen, foutweergave, de zes navigatietabs en de watch-deeplink.
- De volledige backendtestset en architectuurtest slagen; de Flutter-code doorstaat analyse en tests in een ondersteunde omgeving.

## Aannames

- De te controleren pagina is zonder login, cookies, captcha of andere gebruikersinteractie bereikbaar.
- De gezochte informatie staat in de door de server geleverde pagina-inhoud; inhoud die uitsluitend via JavaScript wordt geladen valt buiten deze story.
- Een gevonden opdracht is afgerond en wordt niet automatisch hervat. Voor een nieuwe controleperiode maakt de gebruiker een nieuwe opdracht.
- De app is voor één gebruiker bedoeld; een ingeschakelde melding gaat daarom naar alle geregistreerde apparaten van die gebruiker.

## Eindsamenvatting

De story levert langdurige websitezoekopdrachten end-to-end op:

- Een zelfstandige backendmodule met geauthenticeerde REST-operaties, validatie, Firestore-opslag en in-memory fallback.
- Configureerbare controles tijdens kantooruren of dagelijks, volgens `Europe/Amsterdam`.
- Begrensde verwerking van server-gerenderde webpagina’s en beoordeling door een aparte, tool-loze AI-client.
- Fouttolerante statussen en precies één optionele push bij een vondst, waarna de opdracht stopt.
- Bescherming tegen gelijktijdige polls, verwijderen tijdens een controle en verouderde resultaten.
- Een nieuwe Flutter-tab `Zoekopdrachten` voor aanmaken, bekijken en verwijderen. De bestaande tabvolgorde, standaardtab en briefing-deeplink blijven intact.
- Watch-pushes openen de juiste tab, ook bij een koude start, en verversen de getoonde gegevens.

Belangrijke keuzes waren hergebruik van de bestaande Firestore/fallback-, auth-, push- en ChatClient-patronen, deterministisch testbare planning en compare-and-set-opslag om dubbele pushes en overschrijvingen door parallelle polls te voorkomen.

Getest en akkoord bevonden: 325 backendtests, inclusief architectuurtest, zonder failures/errors/skips; 43 Fluttertests; Flutter-analyse; backend-packagebuild en Flutter-web-releasebuild. De APK-build kon niet worden uitgevoerd omdat de container geen Android SDK bevatte. Pagina’s die login, cookies, captcha, gebruikersinteractie of uitsluitend JavaScript-geladen inhoud vereisen, vallen bewust buiten de scope.
