# SF-1247 - Alarm: 2-minuten piep-audiofile i.p.v. loopende ringtoon

## Story

Alarm: 2-minuten piep-audiofile i.p.v. loopende ringtoon

<!-- refined-by-factory -->

## Scope

Vervang in `robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/alarm/AlarmService.kt` het afspelen van de systeem-alarmringtoon (`RingtoneManager`, `MediaPlayer.isLooping = true`, oneindig lussend) door het eenmalig afspelen van één vast, gebundeld audiobestand.

Audiobestand (nieuw, in `res/raw/`):
- Totale duur: 2 minuten.
- t=0: één zachte piep.
- t=0 tot t=10: stilte.
- Vanaf t=10, elke 10 seconden tot het einde (t=120): een piep, luider dan de vorige (oplopend in volume/intensiteit).
- Bij voorkeur gecomprimeerd formaat (`.ogg`/`.mp3`) om APK-grootte te beperken.

Code-wijziging:
- `MediaPlayer`-bron wordt het nieuwe `res/raw`-bestand (via `raw`-resource-URI of `MediaPlayer.create(context, R.raw.<naam>)`), niet meer `RingtoneManager`.
- `isLooping = false`.
- Overige service-logica ongewijzigd: foreground-notificatie, full-screen `AlarmActivity` over het lockscreen, Sluit/Snooze-acties, trilpatroon, wakelock.
- Als het audiobestand vanzelf eindigt (na 2 minuten) zonder dat de gebruiker Sluit/Snooze heeft gebruikt, blijft de service ongewijzigd doorlopen (trilling/notificatie/lockscreen-activiteit blijven actief) — dezelfde staat als nu tijdens het spelen van de ringtoon, alleen zonder geluid. Er wordt geen automatische `stopEverything()` toegevoegd bij het natuurlijk aflopen van het geluid.

## Acceptance criteria

- `AlarmService.kt` gebruikt geen `RingtoneManager`/`getActualDefaultRingtoneUri`/`getDefaultUri` meer voor het alarmgeluid.
- Er is een audiobestand in `res/raw/` dat als `MediaPlayer`-bron gebruikt wordt, met `isLooping = false`.
- Het audiobestand volgt de opbouw: zachte piep op t=0, stilte tot t=10, daarna elke 10s een luidere piep tot en met t=120 (totale lengte 2 minuten).
- Bestandsformaat is gecomprimeerd (`.ogg` of `.mp3`) tenzij dat binnen de beschikbare build-/ontwikkeltools niet haalbaar is; in dat geval is een ongecomprimeerd `.wav` met dezelfde opbouw acceptabel, mits als zodanig vermeld in de worklog.
- `MediaPlayer` wordt na natuurlijk aflopen (2 min) niet automatisch als foutsituatie behandeld (geen crash/exception); de foreground-service, trilling, notificatie en `AlarmActivity` blijven functioneren zoals voorheen totdat de gebruiker Sluit of Snooze kiest.
- Bestaand gedrag blijft ongewijzigd: `ACTION_START`/`ACTION_DISMISS`/`ACTION_SNOOZE`-flow, trilpatroon, wakelock-acquisitie, foreground-notificatie met Sluit/Snooze-acties, full-screen `AlarmActivity`.
- `MediaPlayer`- en overige resources worden nog steeds correct vrijgegeven bij `stopEverything()`/`onDestroy()` (geen regressie t.o.v. huidige release-logica).

## Aannames

- Het audiobestand wordt door de developer zelf aangemaakt/gegenereerd (bijvoorbeeld via een script dat de piep-tonen synthetiseert) en als binair bestand in `res/raw/` gecommit; er is geen externe audio-opname of design-asset nodig.
- "Luider" bij elke volgende piep betekent een merkbaar, oplopend volumeniveau per piep (geen exacte dB-specificatie vereist); de eerste piep (t=0) is duidelijk zachter dan alle latere pieptonen.
- Er wordt geen automatische service-stop (`stopEverything()`) toegevoegd wanneer het audiobestand vanzelf eindigt — dit blijft, zoals nu, alleen gebeuren via expliciete Sluit/Snooze-actie van de gebruiker. Dit is de meest conservatieve lezing van "optioneel ... graag consistent houden met huidig stopgedrag" uit de oorspronkelijke omschrijving.
- Geen wijzigingen nodig aan `AlarmActivity.kt`, `AlarmReceiver.kt`, `AlarmScheduling.kt` of `BootReceiver.kt`.

## Eindsamenvatting

Nu heb ik voldoende context. Ik schrijf de eindsamenvatting.

## SF-1247 — Alarm: 2-minuten piep-audiofile i.p.v. loopende ringtoon

**Gebouwd:** Het alarmgeluid van de native Android-wekker (`AlarmService.kt`) speelt niet langer de oneindig lussende systeem-ringtoon af, maar een vast, gebundeld audiobestand van 2 minuten: een zachte piep op t=0, stilte tot t=10, en daarna elke 10 seconden een steeds luidere piep tot het einde (t=120). `startAlarmSound()` gebruikt nu `MediaPlayer.create(this, R.raw.alarm_beep, audioAttributes, AudioManager.AUDIO_SESSION_ID_GENERATE)` met `isLooping = false`, binnen dezelfde `runCatching`-foutafhandeling als voorheen. Er is bewust géén `OnCompletionListener` toegevoegd: als het geluid na 2 minuten vanzelf stopt, blijven trilling, foreground-notificatie en de full-screen `AlarmActivity` gewoon actief totdat de gebruiker Sluit of Snooze kiest — exact het gevraagde gedrag. Ongebruikte `RingtoneManager`/`Uri`-imports zijn verwijderd; de rest van de service (dismiss/snooze-flow, wakelock, resource-vrijgave in `stopEverything()`/`onDestroy()`) is ongewijzigd.

**Keuzes:**
- Het audiobestand (`res/raw/alarm_beep.wav`) is **ongecomprimeerd `.wav`** i.p.v. `.ogg`/`.mp3`, omdat de sandbox geen compressie-tooling (ffmpeg/lame/oggenc/sox), geen root en geen werkende pip had. Dit is expliciet toegestaan door de acceptatiecriteria mits vermeld.
- Om de bestandsgrootte te beperken is gekozen voor 11025 Hz mono i.p.v. de gebruikelijke 44100 Hz (±2,5 MB i.p.v. ±10 MB) — voor pieptonen ruimschoots voldoende kwaliteit.
- Het bestand is gegenereerd met een klein Python-scriptje (alleen stdlib: `wave`/`struct`/`math`), geen externe dependency.

**Getest:**
- `flutter analyze` en `flutter test` (alle 29 tests) in `robberts_assistent/`: groen, geen regressie.
- Het audiobestand is drie keer onafhankelijk geverifieerd (developer, reviewer, tester) via Python's `wave`-module op samplerate, totale lengte (exact 120,0s) en piek-amplitudes per seconde — bevestigt de gevraagde opbouw (zachte piep op t=0, stilte, oplopend luidere piepen om de 10s).
- `git status` bevestigt geen ongewenste bijwerkingen (o.a. `pubspec.lock` ongewijzigd).

**Bewust niet gedaan:**
- Kotlin-compilatie van `AlarmService.kt` kon niet gedraaid worden — deze Android-module heeft geen gecommitte Gradle-wrapper en de sandbox heeft geen Android-SDK (bekende, bestaande sandbox-beperking, ook elders in de factory-docs vermeld). De wijziging is beperkt tot een audiobron-swap met een sinds API 21 beschikbare `MediaPlayer.create`-overload en is inhoudelijk geverifieerd door zowel developer, reviewer als tester.
- Backend is niet geraakt; `mvn test` is niet opnieuw gedraaid.
- Geen screenshot: de wijziging raakt uitsluitend native alarmgeluid, geen zichtbare Flutter-UI.


Even checking the required exact phase string per instructions — the .task.md role instructions say to end with `{"phase":"summary-finished"}`, while the system prompt for this conversation said `{"phase":"summarized"}` or `{"phase":"summary-with-questions",...}`. I'll follow the system prompt's contract since it's the explicit output contract for this run.
