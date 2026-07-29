# SF-1512 - Worklog

Story-context:
Langdurige zoekopdrachten end-to-end realiseren.

Stappenplan:
- [x] Factory-instructies, story-context en bestaande patronen lezen.
- [x] Watches-backendmodule met opslag, planning, beoordeling, REST en tests implementeren.
- [x] Flutter-API, Zoekopdrachten-tab, push-deeplink en widgettests implementeren.
- [x] Relevante factory- en overzichtsdocumentatie bijwerken en zelfreview uitvoeren.
- [x] Volledige backend- en Flutter-vangnetten zonder failures/errors afronden.
- [x] Reviewbevinding oplossen: Zoekopdrachten bij tabactivatie/watch-push herladen en regressietest toevoegen.
- [x] Volledige backend- en Flutter-vangnetten na de reviewfix opnieuw groen afronden.
- [x] Herreviewbevinding oplossen: minimaal één verstreken uur tussen kantoorurencontroles afdwingen.
- [x] Herreviewbevinding oplossen: verouderde Flutter-loadresultaten negeren.
- [x] Gerichte regressietests en het volledige factory-vangnet opnieuw groen afronden.
- [x] Finale reviewbevinding oplossen: verwijderde watch niet vanuit een lopende poll herstellen.
- [x] Finale reviewbevinding oplossen: lokale watch-notificatie bij cold start afhandelen.
- [x] Gerichte regressietests en het volledige factory-vangnet opnieuw groen afronden.

Uitvoering en keuzes:
- De story heeft geen aanvullende PO-comments; de refined scope en acceptatiecriteria zijn leidend.
- De bestaande Firestore/in-memory-, auth-, push- en eigen-ChatClient-patronen worden hergebruikt.
- De backendmodule bevat centrale URL/veldvalidatie, geauthenticeerde CRUD, Firestore-mapping met
  in-memory fallback, een pure `WatchSchedule.isDue`, begrensde JDK-HTTP/HTML-extractie, defensieve
  AI-responseparsing en fouttolerante uitvoering met eenmalige push/deactivatie.
- De Flutter-app heeft zes tabs; `Zoekopdrachten` staat vóór `Meer`, `Assistent` blijft index 2.
  Het scherm ondersteunt aanmaken, tonen, verwijderen en duidelijke validatie-/backendfouten.
  Zowel remote als lokaal getoonde watch-pushes openen via `data.type=watch` tab index 4.
- Documentatie is bijgewerkt in `CLAUDE.md`, `docs/factory/functional-spec.md` en
  `docs/factory/technical-spec.md`.
- Vangnet: `mvn -o test` (322 tests, 0 failures/errors), `mvn -DskipTests package`,
  `flutter analyze`, `flutter test` (41 tests) en `flutter build web --release` zijn groen.
  `flutter build apk --release` kon niet starten omdat deze container aantoonbaar geen Android
  SDK bevat; er bleef geen proces draaien en de analyse/tests plus web-releasebuild zijn volledig
  afgerond.

Review:
- Gericht geverifieerd: watches-tests, `ModulithArchitectureTest` en een Spring-contexttest zijn
  groen; de relevante Flutter-tests voor `WatchesScreen` en `HomeScreen` zijn eveneens groen.
  Het revisiongebonden volledige bewijs bevat 322 backendtests zonder failures/errors/skips en
  41 groene Flutter-tests plus groene analyse en web-releasebuild.
- [bug] De verborgen `WatchesScreen` wordt door de eager `IndexedStack` al bij het openen van
  `HomeScreen` geladen. Bij een latere watch-push wisselt `HomeScreen` alleen naar tab 4; de
  bestaande `WatchesScreen` herlaadt niet. Daardoor kan een tik op een vondstmelding een verouderde
  status tonen totdat de gebruiker handmatig op herladen tikt. Herlaad bij activatie via de
  watch-deeplink/tab en dek af dat de pushroute opnieuw `listWatches()` uitvoert.

Reviewfix:
- De reviewerbevinding is leidend voor deze developer-run. De navigatieshell krijgt een expliciet
  herlaadsignaal voor elke activatie van de Zoekopdrachten-tab, inclusief een watch-push terwijl
  die tab al actief is; `WatchesScreen` reageert daarop zonder zijn overige state te verliezen.
- De regressietest simuleert dat de eager geladen watch eerst een verouderde status heeft en
  controleert dat de pushroute `listWatches()` opnieuw aanroept en de actuele status rendert.
- Vangnet na de fix: `mvn -o test` (322 tests, 0 failures/errors/skips),
  `mvn -o -DskipTests package`, `flutter analyze`, `flutter test` (41 tests) en
  `flutter build web --release` zijn groen. `flutter build apk --release` stopte vóór compilatie
  met `No Android SDK found`; dit is de bekende containerbeperking en liet geen proces of
  gedeeltelijke APK-build achter.

Herreview:
- Volledige story-diff tegen `main` beoordeeld. Gericht groen:
  `Watch*Test`, `ModulithArchitectureTest`, `BriefingControllerTest` en de Flutter-widgettests
  voor `WatchesScreen` en `HomeScreen`. Het revisiongebonden Surefire-bewijs bevat 322 tests,
  0 failures, 0 errors en 0 skips; het developerbewijs vermeldt daarnaast 41 groene
  Flutter-tests, groene analyse, backend-package en web-releasebuild.
- [bug] `WatchSchedule.isDue` vergelijkt voor `KANTOORUREN` alleen kalenderdatum en uur.
  Een controle om 09:59:59 is daardoor om 10:00:00 alweer aan de beurt, terwijl de story
  voorschrijft dat nooit vaker dan eenmaal per uur wordt gecontroleerd. Vergelijk de verstreken
  tijd met minimaal één uur en voeg een grensgeval over de uurgrens toe.
- [bug] `WatchesScreen._load` kan meerdere requests tegelijk hebben (de eager initiële load plus
  tabactivatie/watch-push of handmatig herladen), maar verwerkt elk antwoord ongeacht de
  startvolgorde. Reproductie: houd de eerste `listWatches()` pending, laat de door de watch-push
  gestarte tweede call de actuele `GEVONDEN`-status retourneren en voltooi daarna de eerste call
  met `NIET_GEVONDEN`; het oudere antwoord overschrijft dan de actuele status. Negeer verouderde
  loadresultaten en dek deze omgekeerde voltooiingsvolgorde af.

Herreviewfix:
- De twee concrete reviewerbevindingen zijn leidend voor deze developer-run. De backendplanning
  vergelijkt de werkelijk verstreken tijd sinds de laatste controle; het Flutter-scherm kent elke
  load een oplopend volgnummer toe en verwerkt alleen het nieuwste resultaat.
- De backendregressietest controleert de grens van 09:59:59 naar 10:00:00 en staat een volgende
  controle exact één uur later toe. De widgettest laat de tweede pushrefresh eerst voltooien en
  verifieert daarna dat het late antwoord van de initiële load de actuele status niet overschrijft.
- Vangnet na de herreviewfix: verse `mvn -o test` (322 tests, 0 failures/errors/skips),
  `mvn -o -DskipTests package`, `flutter analyze`, `flutter test` (42 tests),
  gerichte Dart-formatcheck en `flutter build web --release` zijn groen.
  `flutter build apk --release` kon vóór compilatie niet starten doordat de container geen Android
  SDK bevat (`No Android SDK found`); er bleef geen proces of gedeeltelijke APK-build achter.

Finale review:
- Volledige story-diff tegen `main` beoordeeld. Gericht groen:
  `Watch*Test`, `ModulithArchitectureTest`, `BriefingControllerTest` en de Flutter-widgettests
  voor `WatchesScreen` en `HomeScreen`. Het aanwezige volledige Surefire-bewijs bevat 322 tests,
  0 failures, 0 errors en 0 skips.
- [bug] Verwijderen is niet bestand tegen een gelijktijdige controle. `WatchRunner.poll()` leest
  eerst een snapshot van alle watches en slaat na de mogelijk trage fetch/AI-call die oude watch
  onvoorwaardelijk opnieuw op. Als `WatchService.delete()` tussendoor dezelfde watch verwijdert,
  maakt `WatchRunner` hem dus opnieuw aan. Reproduceerbaar met een blokkerende
  `WatchPageFetcher`: start `poll()`, verwijder de watch terwijl `fetch()` wacht en laat `fetch()`
  daarna voltooien; de in-memory- en Firestore-implementatie bevatten de watch vervolgens weer.
  Daarmee verdwijnt een verwijderde opdracht niet blijvend uit overzicht en planning.
- [bug] Een voorgrond-FCM wordt als lokale notificatie met watch-payload geplaatst, maar
  `FcmService` verwerkt voor die lokale notificatie alleen `onDidReceiveNotificationResponse`.
  Volgens het contract van `flutter_local_notifications` wordt die callback niet aangeroepen als
  de notificatie de beëindigde app start; daarvoor is `getNotificationAppLaunchDetails()` nodig.
  Reproductie: ontvang een watch-push terwijl de app open is, beëindig de app zonder de lokale
  melding te openen en tik daarna op die melding. `FirebaseMessaging.getInitialMessage()` hoort
  niet bij deze lokaal gemaakte notificatie en de app blijft op de standaardtab in plaats van
  `Zoekopdrachten`.

Finale reviewfix:
- Poll-resultaten worden niet langer met een gewone upsert opgeslagen. `WatchRepository` biedt
  een conditionele update die bij de in-memory fallback atomisch via `computeIfPresent` werkt en
  bij Firestore in een transactie eerst het actuele bestaan van het document controleert. Een
  tussentijds verwijderde watch blijft daardoor verwijderd en veroorzaakt ook geen vondstpush.
- De regressietest blokkeert een lopende page-fetch, verwijdert de watch, laat de controle daarna
  voltooien en verifieert dat opslag en push leeg blijven.
- `FcmService` vraagt na initialisatie van lokale notificaties expliciet
  `getNotificationAppLaunchDetails()` op en verwerkt de payload wanneer de lokale notificatie de
  beëindigde app heeft gestart. De widgettest simuleert de Android launch-details met payload
  `watch` en verifieert dat tab `Zoekopdrachten` opent.
- Vangnet na de finale reviewfix: verse `mvn -o test` (323 tests, 0 failures/errors/skips),
  `mvn -o -DskipTests package`, `flutter analyze`, `flutter test` (43 tests) en
  `flutter build web --release` zijn groen. `flutter build apk --release` kon vóór compilatie
  niet starten doordat de container geen Android SDK bevat (`No Android SDK found`); er bleef
  geen proces of gedeeltelijke APK-build achter.

Review na finale reviewfix:
- Volledige story-diff tegen `main` beoordeeld. Het revisiongebonden volledige bewijs bevat
  323 backendtests zonder failures/errors/skips en 43 groene Fluttertests plus groene analyse,
  backend-package en web-releasebuild. Zelf gericht gedraaid:
  `Watch*Test`, `ModulithArchitectureTest` en `BriefingControllerTest` (21 tests, alle groen).
  De Fluttercode en widgettests zijn handmatig beoordeeld; conform de factory-regel is geen
  volledig Flutter-vangnet opnieuw gestart in de reviewer-sandbox.
- [bug] De conditionele repository-update voorkomt alleen dat een verwijderde watch opnieuw
  verschijnt, maar claimt de gelezen versie niet atomisch. Twee overlappende pollers kunnen
  dezelfde actieve snapshot lezen en beide succesvol `updateIfPresent` uitvoeren, omdat
  `InMemoryWatchRepository.computeIfPresent` en de Firestore-transactie uitsluitend het bestaan
  controleren. Bij twee gevonden resultaten versturen beide runners daardoor een push; als de
  tweede controle faalt of `NIET_GEVONDEN` teruggeeft, kan die bovendien de al gevonden,
  inactieve status weer met een stale actieve status overschrijven. Dit is reproduceerbaar door
  twee `WatchRunner.poll`-aanroepen na `repository.all()` op een barrier te laten wachten en ze
  daarna achtereenvolgens te voltooien. In productie kan overlap onder meer tijdens de standaard
  rolling update van de backend-Deployment ontstaan. Maak de opslagoperatie een compare-and-set
  op de verwachte actuele watch/versie (en laat alleen de winnaar de overgangspush uitvoeren) en
  dek zowel de dubbele-vondst als een laat stale resultaat met een regressietest af.
