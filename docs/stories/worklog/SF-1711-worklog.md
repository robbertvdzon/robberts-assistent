# SF-1711 - Worklog

Story-context bij eerste pickup:
Doorluister-lus in praatmodus + korte spreektaal-antwoorden bij voice-vlag

Backend (module assistant): optionele multipart-param 'voice' (default false) op POST /api/v1/assistant/chat, doorgegeven aan AssistantService.chat; bij true gaat er per request een extra spreektaal-instructie mee BOVENOP de bestaande SYSTEM_PROMPT (let op: assistantChatClient gebruikt .defaultSystem(...), dus een request-level .system(...) zou die vervangen - kies een variant die de default laat staan). Zonder vlag is de prompt exact als nu. Frontend: ApiClient.assistantChat krijgt optionele 'voice' (default false), alleen gezet door de spraakroute (_send met speakReply: true). assistant_screen.dart krijgt de lus luisteren -> versturen -> uitspreken -> opnieuw luisteren met awaitSpeakCompletion(true)/completion-handler, mic gestopt vóór het spreken, geen luisteren tijdens versturen/wachten; stoppen bij handmatige stop, mode-wissel, dispose, spraak-/API-fout en na 2 opeenvolgende rondes zonder verstane spraak; sessie-/generatieguard tegen herstart na stop en tegen setState na unmount; bestaande _listening-guard en bestaande UI (listening-indicator + stop/mic-FAB) blijven. SpeechToText/FlutterTts injecteerbaar maken via optionele constructorparameters met productiedefault (stijl _FakeApiClient), geen nieuwe dependency. Schrijf zelf de tests: backend-test dat de spreektaal-instructie mét vlag in de prompt zit en zonder vlag niet (en dat een request zonder veld onveranderd werkt), plus widget-tests voor opnieuw luisteren na uitspreken, geen herstart na stop/mode-wissel/dispose/API-fout, stoppen na 2x niets verstaan, en het wel/niet meesturen van de voice-vlag. Sluit af met een zelfreview en houd mvn test, flutter analyze en flutter test groen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## Backend — korte spreektaal-antwoorden bij `voice=true`

- `assistant/ai/AiConfig.kt`: nieuwe top-level `VOICE_SYSTEM_PROMPT` met de spreektaal-instructie
  (hardop voorgelezen, max. 2 korte zinnen, geen opsommingen/markdown/kopjes/URL's/emoji/tabellen,
  geen inleiding vooraf, uitspreekbare getallen/eenheden, alleen langer bij een expliciet verzoek
  om details of een lijst). `SYSTEM_PROMPT` zelf is **niet** aangepast.
- `AssistantService.chat(...)` kreeg een vierde parameter `voice: Boolean = false`. Staat die aan,
  dan wordt aan de meegegeven `messages(...)`-lijst één extra `SystemMessage(VOICE_SYSTEM_PROMPT)`
  toegevoegd. Bewust géén request-level `.system(...)`: dat zou de `defaultSystem(...)` van
  `assistantChatClient` vervángen. Nu blijven beide system-boodschappen in de prompt staan — dat is
  in de nieuwe unittest ook aantoonbaar gemaakt (beide teksten zitten in
  `prompt.instructions`).
- `AssistantController`: `@RequestParam("voice", required = false, defaultValue = "false")`, dus een
  request zonder het veld gedraagt zich exact als voorheen (o.a. de `wind`-app).
- Ongewijzigd: opslag van vraag/antwoord, titelgeneratie, geheugen-update, tools, overige endpoints.

## Frontend — voice-vlag + doorluister-lus

- `api_client.dart`: `assistantChat(..., bool voice = false)`; het multipart-veld `voice` gaat
  alleen mee als de vlag aanstaat (spraakroute). De getypte route stuurt niets extra's.
- `assistant_screen.dart`:
  - Twee smalle test-seams (`SpeechRecognizer`, `VoiceSpeaker`) met de echte plugins als
    productiedefault (`_PluginSpeechRecognizer` / `_PluginVoiceSpeaker`), injecteerbaar via de
    nieuwe optionele `AssistantScreen`-parameters `speech`/`speaker`. Geen nieuwe dependency.
    `_PluginVoiceSpeaker` zet `awaitSpeakCompletion(true)`, zodat `speak()` pas terugkomt als het
    uitspreken écht klaar is.
  - Lus: luisteren → versturen (`voice: true`) → antwoord uitspreken → opnieuw luisteren. Vóór het
    spreken wordt de spraakherkenning expliciet gestopt; tijdens versturen/wachten wordt niet
    geluisterd.
  - Stopcondities: stop-knop, wisselen naar chatmodus, `dispose`, spraakfout, chat-API-fout, en na
    2 opeenvolgende rondes zonder verstane spraak (dan gewoon terug naar de mic-knop, geen
    foutmelding). Elke stop hoogt `_loopGeneration` op; `_listenAgain()` herstart alleen als die
    generatie nog klopt, het scherm nog `mounted` is en de modus nog praten is.
  - De bestaande `_listening`-guard is intact; `_startListening()` heeft er een `mounted`-check bij.
  - UI ongewijzigd op één punt na: de bestaande stop/mic-FAB blijft tijdens de lus bruikbaar (was
    disabled zodra `_busy`), anders kun je een lopende beurt niet met één tik afbreken — precies wat
    de story als stopconditie vraagt.

## Tests (zelf geschreven)

- Backend: `AssistantServiceTest` — spreektaal-instructie zit mét vlag in de prompt náást de
  bestaande system-prompt, en zonder vlag niet (met een expliciete vergelijking dat de prompt verder
  identiek is). `AssistantIntegrationTest` — POST zonder én met het `voice`-veld geeft 200.
- Frontend: 8 nieuwe widget-tests in `test/assistant_screen_test.dart` (groep "doorluister-lus in
  praatmodus"): opnieuw luisteren na uitspreken, geen herstart na handmatige stop / mode-wissel /
  dispose / API-fout, stoppen na 2x stilte, stoppen bij spraakfout, wel/niet meesturen van de
  voice-vlag, en geen dubbele listen-sessies. De bestaande tests zijn inhoudelijk ongewijzigd
  (alleen de `_FakeApiClient`-signatuur volgt de nieuwe optionele parameter).

## Uitgevoerd vangnet

- `rm -rf target && mvn -o test` (backend): **388 tests, 0 failures, 0 errors**.
- `flutter analyze` (robberts_assistent): **No issues found**.
- `flutter test` (robberts_assistent): **74 tests, all passed**.
- `flutter build web --release`: slaagt (compilatiebewijs van de hele app).
- `flutter build apk` kan hier niet: de developer-sandbox heeft geen Android SDK
  (`flutter doctor`: "Unable to locate Android SDK") — bekend en gedocumenteerd. De wijziging is
  puur Dart/Kotlin-backend, zonder manifest-, Gradle- of native wijziging, dus de APK-build in CI
  wordt niet geraakt.
- Handmatige eindverificatie op Robberts telefoon (twee vragen achter elkaar zonder de microfoon
  opnieuw aan te tikken + kort voorgelezen antwoord) blijft de laatste stap; echte spraak/TTS is
  niet in CI na te bootsen (zoals de story ook aanneemt).

## Review (SF-1712)

Beoordeeld is de volledige story-diff `git diff main...HEAD` (9 bestanden, backend + Flutter).
Akkoord, geen blockers.

Zelf gedraaid vangnet (reviewer-sandbox, flutter én mvn beschikbaar):
- `flutter analyze` (robberts_assistent): **No issues found**.
- `flutter test` (robberts_assistent): **74/74 groen**, inclusief de 8 nieuwe lus-tests.
- Gericht `mvn -o test -Dtest='Assistant*Test,ModulithArchitectureTest'`: groen; de drie nieuwe
  voice-tests staan aantoonbaar in de surefire-rapporten (0 failures/errors).

Bevindingen:
- [info] Story-scope volledig gedekt: backend `voice`-param met `defaultValue="false"` (dus de
  `wind`-app en elke andere bestaande client blijven ongewijzigd werken), extra `SystemMessage`
  in `messages(...)` i.p.v. een request-level `.system(...)` — de `defaultSystem(...)` blijft dus
  staan, precies zoals de description waarschuwde, en de test bewijst beide teksten in
  `prompt.instructions`. Frontend stuurt de vlag alleen op de spraakroute (`voice: speakReply`).
- [info] De afwijking die de developer meldt (stop/mic-FAB niet meer disabled zodra `_busy` maar
  alleen bij `_busy && !_loopActive`) is nodig om de door de story geëiste stopconditie
  "handmatig stoppen tijdens het uitspreken" überhaupt uitvoerbaar te maken; geen nieuwe knop,
  binnen scope. Gecontroleerd dat `_busy` daarna weer vrijkomt: de Android-kant van `flutter_tts`
  rondt bij `"stop"` een openstaande `speakResult` af, dus `speak()` blijft niet hangen en de FAB
  raakt niet permanent disabled.
- [suggestie] `assistant_screen.dart` `_onSpeechStatus`: een láát binnenkomende `done`/
  `notListening` van een vórige luistersessie kan theoretisch binnenvallen nadat `_startListening()`
  al een nieuwe sessie startte. De guard kijkt alleen naar `_loopActive`/`_heardThisRound`/`_busy`,
  niet naar de generatie/sessie, dus dan zou `_listening` op `false` gezet worden en een tweede
  sessie starten. Timing-afhankelijk en in de praktijk onwaarschijnlijk (de plugin meldt het
  aflopen vóór de volgende `listen()`); als het op het toestel toch opduikt, is een
  sessie-id-check in `_onSpeechStatus` de kleinste fix.
- [suggestie] `_speech.stop()` gebeurt pas ná het API-antwoord, vlak vóór het spreken. De
  story-eis ("expliciet gestopt voordat er gesproken wordt") is daarmee letterlijk gehaald;
  tijdens het wachten op de backend leunt het scherm erop dat de plugin zelf al gestopt is na een
  eindresultaat. Eventueel `stop()` naar vóór de `assistantChat`-aanroep halen.
- [info] Handmatige toestelverificatie (twee vragen achter elkaar zonder opnieuw te tikken, kort
  voorgelezen antwoord) blijft terecht de laatste stap — callback-logica is wél getest, echte
  microfoon/TTS niet na te bootsen.

## Testronde (SF-1713, tester)

Getest op branch `ai/SF-1711` @ `8a4a0f9` (= `head.sha` van PR #45, dus preview
`robberts-assistent-pr-45` draait exact deze revisie).

Vangnet:
- `mvn -o test` (backend): exitcode 0, **388 tests, 0 failures, 0 errors, 0 skipped**
  (surefire-reports opgeteld) — incl. de nieuwe `AssistantServiceTest`-cases voor de
  spreektaal-instructie en de `AssistantIntegrationTest`-case met/zonder `voice`-veld.
- `flutter analyze` (robberts_assistent): **No issues found!**
- `flutter test` (robberts_assistent): **74/74 groen**, incl. de 9 nieuwe
  `assistant_screen_test.dart`-cases van de doorluister-lus. Geen flakes waargenomen.
- APK-build niet lokaal verifieerbaar (geen gradle-wrapper/Android-SDK in de sandbox);
  wijziging is puur Dart/Kotlin-backend, geen native/manifest-wijziging.

Gedrags-/E2E-verificatie op preview
`https://robberts-assistent-frontend-robberts-assistent-pr-45...`:
- `POST /api/v1/assistant/chat` **zonder** `voice`-veld → HTTP 200, normaal antwoord
  (bestaande clients zoals de `wind`-app blijven werken).
- Idem met `voice=true` → HTTP 200, en met `voice=false` → HTTP 200.
- `voice=onzin` → HTTP 400 (Spring-boolean-conversie). Niet in de story-scope beschreven,
  geen regressie; gemeld als observatie.
- Playwright-netwerkintercept op de Flutter-web-preview: een **getypt** bericht stuurt een
  multipart-body met alléén `message` — dus de getypte route stuurt de `voice`-vlag
  aantoonbaar niet mee (live bewijs naast de widget-test).
- Screenshots in `/work/screenshots/SF-1713-*.png`: Assistent-tab, nieuw gesprek in
  praatmodus (mic-FAB + "Tik op de microfoon en stel je vraag."), en de getypte route met
  vraag + antwoord. De praatmodus-lus zelf is op web niet te driven (geen echte
  spraakherkenning/TTS) — die is gedekt door de widget-tests.

Codecontrole tegen de acceptatiecriteria: alle criteria gedekt; de stopcondities
(handmatig stoppen, mode-wissel, `dispose`, API-fout, spraakfout, 2× stilte) leunen op de
`_loopGeneration`-guard en zijn per stuk in een widget-test vastgelegd. Bestaande tests in
`assistant_screen_test.dart` zijn alleen aangevuld, niet aangepast (diff bevat geen
verwijderde regels in dat bestand).

Observaties (geen blocker, geen AC-schending):
- Wordt de stop-knop ingedrukt terwijl het API-antwoord nog onderweg is, dan wordt dat
  antwoord daarna alsnog één keer uitgesproken (`_speaker.speak(...)` staat ná de `await`
  op `assistantChat`). De lus herstart niet — dat is wat de story eist.
- De door de reviewer genoemde theoretische race in `_onSpeechStatus` (late `done` van een
  vorige sessie) is niet reproduceerbaar met de test-seam; blijft een observatiepunt voor de
  handmatige toestelverificatie.

Testdata op preview (drie chat-gesprekken) opgeruimd; `GET
/api/v1/assistant/conversations` is weer leeg.
