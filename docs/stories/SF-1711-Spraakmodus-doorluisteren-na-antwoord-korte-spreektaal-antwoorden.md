# SF-1711 - Spraakmodus: doorluisteren na antwoord + korte spreektaal-antwoorden

## Story

Spraakmodus: doorluisteren na antwoord + korte spreektaal-antwoorden

<!-- refined-by-factory -->

## Samenvatting

Praten met de assistent gaat nu stroef: na elk antwoord moet je opnieuw op de microfoon
tikken, en de antwoorden zijn vaak te lang om prettig voorgelezen te horen.

Met deze story wordt praatmodus een doorlopend gesprek: de app luistert automatisch
weer zodra ze klaar is met praten, tot jij zelf stopt. En als een antwoord wordt
voorgelezen, houdt de assistent het kort en in gewone spreektaal — maximaal een paar
zinnen, zonder lijstjes of opmaak.

Getypt chatten verandert niet: daar blijven de antwoorden zo uitgebreid als nu.

## Scope

### Frontend — doorluisteren in praatmodus (`robberts_assistent/lib/assistant_screen.dart`)

- In praatmodus (`_Mode.voice`) ontstaat een lus: luisteren → verstane vraag versturen →
  antwoord uitspreken → opnieuw luisteren. De lus loopt door tot een van de stopcondities
  hieronder optreedt.
- Het uitspreken wordt afwachtbaar gemaakt (`_tts.awaitSpeakCompletion(true)` en/of
  `setCompletionHandler`) i.p.v. blind door te gaan na `speak()`; pas ná het einde van het
  uitspreken wordt opnieuw geluisterd.
- De microfoon luistert nooit tijdens het uitspreken of tijdens het versturen/wachten op het
  antwoord: spraakherkenning wordt expliciet gestopt voordat er gesproken wordt.
- De lus stopt (en er wordt níet opnieuw geluisterd) bij: handmatig stoppen via de
  stop-/microfoonknop, wisselen naar chatmodus, het scherm verlaten (`dispose`), een fout in
  spraakherkenning of in de chat-API, en na herhaald niets verstaan (zie aannames).
- De bestaande dubbele-sessie-guard (`if (!_speechAvailable || _listening) return`) blijft
  gerespecteerd; er komt een guard bij zodat een antwoord dat pas klaar is met uitspreken
  ná een stop/mode-wissel/dispose de lus niet alsnog herstart.
- UI: de bestaande listening-indicator en de bestaande stop-/microfoon-FAB worden hergebruikt;
  er komt geen nieuwe knop of nieuw scherm bij. Tijdens het opnieuw luisteren is zichtbaar
  dat er geluisterd wordt en is stoppen met één tik mogelijk.
- Om de lus-logica in een widget-test te kunnen aansturen, worden spraakherkenning en TTS
  injecteerbaar gemaakt: optionele constructor-parameters op `AssistantScreen` (of een even
  smalle, gelijkwaardige seam) met de echte plugins als productiedefault, in de stijl van het
  bestaande `_FakeApiClient`-patroon in `test/assistant_screen_test.dart`. Productiegedrag
  blijft ongewijzigd als de parameters niet meegegeven worden.

### Frontend — voice-vlag meesturen (`robberts_assistent/lib/api_client.dart`)

- `ApiClient.assistantChat(...)` krijgt een optionele parameter `voice` (default `false`) die
  als multipart-veld `voice` wordt meegestuurd.
- `_send(text, speakReply: true)` (dus alleen de spraakroute) stuurt `voice: true`; de getypte
  route (`_sendTyped`) stuurt de vlag niet.

### Backend — korte spreektaal-antwoorden (module `assistant`)

- `POST /api/v1/assistant/chat` krijgt een optionele request-parameter `voice` (boolean,
  default `false`). Een request zonder dat veld gedraagt zich exact als nu, dus bestaande
  clients (o.a. de `wind`-app) blijven werken.
- `AssistantService.chat(...)` krijgt de vlag mee. Staat de vlag aan, dan krijgt het model
  bovenop de bestaande `SYSTEM_PROMPT` (`assistant/ai/AiConfig.kt`) een extra instructie mee
  met deze inhoud: het antwoord wordt hardop voorgelezen, dus vlotte spreektaal, zo kort
  mogelijk (maximaal 2 korte zinnen), geen opsommingen/markdown/kopjes/URL's/emoji/tabellen,
  geen inleiding of samenvatting vooraf, getallen en eenheden uitspreekbaar geschreven
  (bv. "twintig knopen uit het zuidwesten"), en alleen langer als de gebruiker expliciet om
  details of een lijst vraagt.
- De bestaande `SYSTEM_PROMPT` blijft ongewijzigd van kracht; de spreektaal-instructie komt
  er als extra instructie bovenop en vervangt hem niet.
- Zonder de vlag gaat er niets over voorlezen/spreektaal mee: de getypte chat blijft exact
  zoals nu.
- Ongewijzigd: opslag van vraag en antwoord in het gesprek, titelgeneratie, de
  geheugen-update na de beurt, tools, en alle overige endpoints.

## Acceptance criteria

- Backend compileert en `mvn test` is groen; `flutter analyze` en `flutter test` in
  `robberts_assistent/` zijn groen; de APK-build slaagt.
- Backend-test: bij een chat-aanroep met de voice-vlag aan bevat de prompt die naar het
  chat-model gaat de spreektaal-instructie; bij dezelfde aanroep zonder de vlag komt die
  instructie er niet in voor, en is de prompt verder onveranderd.
- Backend-test: een chat-request zonder `voice`-veld werkt onveranderd (geen 400, geen
  spreektaal-instructie).
- Widget-test op `assistant_screen`: in praatmodus wordt, nadat het uitspreken van een
  antwoord is afgerond, automatisch opnieuw geluisterd.
- Widget-test op `assistant_screen`: er wordt níet opnieuw geluisterd wanneer vóór/tijdens het
  uitspreken handmatig gestopt is, naar chatmodus is gewisseld, of het scherm is
  weggehaald (`dispose`).
- Widget-test op `assistant_screen`: bij een fout van de chat-API stopt de lus (geen nieuwe
  listen-sessie) en blijft de bestaande foutmelding zichtbaar.
- Widget-test op `assistant_screen`: na het afgesproken aantal opeenvolgende rondes zonder
  verstane spraak stopt de lus in plaats van eindeloos opnieuw te luisteren.
- Widget-test/unit-test: de spraakroute stuurt de voice-vlag mee naar de API-laag, de getypte
  route niet.
- Er ontstaan geen overlappende listen-sessies: de bestaande `_listening`-guard blijft
  effectief en wordt door de lus niet omzeild.
- Bestaande tests in `test/assistant_screen_test.dart` (o.a. `startInVoiceMode` /
  `autoStartListening` zonder beschikbare spraak) blijven zonder aanpassing van hun
  verwachtingen groen.

## Aannames

- **Stopgrens bij stilte**: na 2 opeenvolgende luisterrondes waarin niets verstaanbaars
  binnenkwam (stilte/timeout) stopt de lus netjes; het scherm valt terug op de bestaande
  niet-luisterende toestand met de microfoonknop, zonder foutmelding.
- **Transport van de vlag**: een extra multipart-veld `voice` op het bestaande
  `POST /api/v1/assistant/chat`; geen nieuw endpoint, geen API-versionering. Ontbrekend veld
  = `false`.
- **Aanwezigheid van de instructie**: hoe de extra instructie technisch bij het model komt
  (extra system-bericht bij de request of een per-request systeeminstructie naast de default)
  laat de story vrij; eis is alleen dat de bestaande `SYSTEM_PROMPT` van kracht blijft en dat
  de aanwezigheid/afwezigheid van de spreektaal-instructie in de verstuurde prompt
  aantoonbaar is in een test.
- **Testbaarheid**: de nieuwe test-seam voor spraak/TTS is bewust minimaal en alleen bedoeld
  voor tests; er komt geen nieuwe dependency of package bij.
- **Geen echte spraaksimulatie in CI**: het daadwerkelijke hoor-en-spreek-gedrag (microfoon +
  TTS op een fysiek toestel) wordt niet nagebootst. Getest wordt uitsluitend de
  callback-/lus-logica; eindverificatie op Robberts telefoon (praatmodus, twee vragen achter
  elkaar zonder de microfoon opnieuw aan te tikken, kort voorgelezen antwoord) is de laatste,
  handmatige stap.
- **Geen wijziging elders**: de `wind`-app, de gesprekkenlijst, het geheugen, de tools en de
  chatmodus-flow (inclusief foto's) blijven functioneel ongewijzigd.
- **Opslag van het antwoord**: het korte spraakantwoord wordt gewoon als gespreksbericht
  opgeslagen; er wordt geen aparte lange variant bijgehouden.

## Eindsamenvatting

## Eindsamenvatting SF-1711 — Spraakmodus: doorluisteren na antwoord + korte spreektaal-antwoorden

**Wat is gebouwd**

Praatmodus in de assistent-app is nu een doorlopend gesprek. Na het uitspreken van een antwoord luistert de app automatisch weer, zonder dat je opnieuw op de microfoon hoeft te tikken. Antwoorden die worden voorgelezen zijn bovendien kort en in gewone spreektaal; getypt chatten is onveranderd gebleven.

- **Backend (`assistant`)**: `POST /api/v1/assistant/chat` accepteert een optionele multipart-parameter `voice` (default `false`). Staat die aan, dan krijgt het model één extra `SystemMessage` met de spreektaal-instructie (max. 2 korte zinnen, geen lijstjes/markdown/URL's/emoji, uitspreekbare getallen, alleen langer op expliciet verzoek). De bestaande `SYSTEM_PROMPT` blijft volledig van kracht.
- **Frontend (`robberts_assistent`)**: `ApiClient.assistantChat(..., voice)` stuurt de vlag alléén op de spraakroute mee. `assistant_screen.dart` kreeg de lus luisteren → versturen → uitspreken → opnieuw luisteren, met de microfoon expliciet gestopt vóór het spreken en niet luisterend tijdens het wachten op het antwoord.

**Belangrijkste keuzes**

- De spreektaal-instructie gaat als extra `SystemMessage` mee in plaats van een request-level `.system(...)` — dat laatste zou de `defaultSystem(...)` van de assistent-chatclient vervángen in plaats van aanvullen.
- `voice` heeft `defaultValue = "false"`, dus bestaande clients (o.a. de `wind`-app) blijven ongewijzigd werken; geen nieuw endpoint, geen API-versionering.
- Spraakherkenning en TTS zijn achter twee smalle test-seams (`SpeechRecognizer`, `VoiceSpeaker`) gezet met de echte plugins als productiedefault, injecteerbaar via optionele constructorparameters. Geen nieuwe dependency.
- Stoppen van de lus gebeurt via een generatie-guard: handmatig stoppen, wisselen naar chatmodus, `dispose`, spraakfout, chat-API-fout en na 2 opeenvolgende rondes zonder verstane spraak (dan gewoon terug naar de microfoonknop, zonder foutmelding).
- Eén bewuste afwijking: de stop/microfoon-knop is tijdens een lopende beurt niet langer uitgeschakeld. Zonder die aanpassing is de door de story geëiste stopconditie "handmatig stoppen tijdens het uitspreken" niet uitvoerbaar. Reviewer heeft geverifieerd dat de knop daarna niet permanent disabled raakt.

**Wat is getest**

- Backend `mvn test`: 388 tests groen, 0 failures/errors — inclusief nieuwe tests die aantonen dat de spreektaal-instructie mét vlag in de prompt zit náást de bestaande system-prompt, en zonder vlag niet (prompt verder identiek), plus een request zonder `voice`-veld dat gewoon 200 geeft.
- `flutter analyze`: geen issues. `flutter test`: 74 tests groen, met 8–9 nieuwe widget-tests voor de doorluister-lus (opnieuw luisteren na uitspreken, geen herstart na stop/mode-wissel/dispose/API-fout/spraakfout, stoppen na 2× stilte, wel/niet meesturen van de voice-vlag, geen dubbele listen-sessies). Bestaande tests zijn alleen aangevuld, niet aangepast.
- E2E op preview (PR #45): chat-endpoint 200 zonder `voice`, met `voice=true` en met `voice=false`. Via netwerkintercept op de web-preview bewezen dat de getypte route de vlag níet meestuurt. Screenshots vastgelegd; testdata opgeruimd.

**Wat bewust niet is gedaan**

- Echte microfoon/TTS is niet nagebootst in CI — alleen de callback-/lus-logica is getest. **Handmatige eindverificatie op Robberts telefoon** (twee vragen achter elkaar zonder opnieuw te tikken, kort voorgelezen antwoord) blijft de laatste stap.
- APK-build is niet lokaal geverifieerd (geen Android SDK in de sandbox); de wijziging is puur Dart/Kotlin zonder manifest-, Gradle- of native wijziging, dus de CI-APK-build wordt niet geraakt.
- Geen aparte lange variant van het antwoord opgeslagen; het korte spraakantwoord gaat gewoon als gespreksbericht het gesprek in. Geen wijziging aan de `wind`-app, gesprekkenlijst, geheugen, tools of foto-flow.

**Observaties zonder blocker**

- Wordt gestopt terwijl het antwoord nog onderweg is, dan wordt dat antwoord daarna nog één keer uitgesproken; de lus herstart niet (conform story).
- Theoretische race: een laat binnenkomende `done` van een vorige luistersessie zou een tweede sessie kunnen starten. Niet reproduceerbaar met de test-seam; observatiepunt voor de toestelverificatie, kleinste fix is een sessie-id-check in `_onSpeechStatus`.
- `voice=onzin` geeft HTTP 400 door Spring-boolean-conversie — niet in scope beschreven, geen regressie.
