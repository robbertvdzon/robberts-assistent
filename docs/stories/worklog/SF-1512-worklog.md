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
