# SF-1247 - Worklog

Story-context bij eerste pickup:
Alarmgeluid: 2-min piepbestand i.p.v. loopende ringtoon

Genereer een 2-minuten audiobestand (res/raw/, bij voorkeur .ogg/.mp3, anders .wav met vermelding in worklog) met: t=0 één zachte piep, stilte tot t=10, daarna elke 10s een luidere piep tot t=120. Pas AlarmService.kt aan: startAlarmSound() gebruikt niet langer RingtoneManager maar MediaPlayer.create(this, R.raw.<bestand>) met isLooping=false; verwijder ongebruikte RingtoneManager/Uri-imports. Geen OnCompletionListener die de service stopt - na natuurlijk aflopen blijft de service ongewijzigd doorlopen tot Sluit/Snooze. Rest van AlarmService.kt (notificatie, AlarmActivity, dismiss/snooze-flow, trilling, wakelock, resource-vrijgave in stopEverything()/onDestroy()) blijft ongewijzigd. Beschrijf in de worklog hoe het audiobestand is gegenereerd.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Sandbox gecontroleerd op compressie-tooling voor het audiobestand: geen `ffmpeg`,
  `lame`, `oggenc`, `opusenc` of `sox` beschikbaar, geen root/`sudo` (dus geen
  `apt-get install`), en geen `pip` (PEP 668 "externally managed", geen
  `ensurepip`). Een gecomprimeerd `.ogg`/`.mp3` was daarmee niet haalbaar binnen de
  beschikbare tools; conform de acceptatiecriteria is daarom gekozen voor een
  ongecomprimeerd `.wav`, hier vermeld.
- Nieuw bestand `robberts_assistent/android/app/src/main/res/raw/alarm_beep.wav`
  gegenereerd met een klein Python-script (alleen stdlib: `wave`, `struct`,
  `math` — geen extra dependencies nodig) dat rechtstreeks 16-bit mono PCM-
  samples schrijft:
  - samplerate 11025 Hz (i.p.v. de gebruikelijke 44100 Hz) om de bestandsgrootte
    te beperken — voor pieptonen (sinusgolf, 1000 Hz) ruimschoots voldoende
    kwaliteit; resultaat ±2,5 MB voor 120s i.p.v. ±10 MB bij 44100 Hz.
  - t=0: één piep van 250ms op 1000 Hz met amplitude 0,10 (duidelijk zachter dan
    alle latere pieptonen) en een korte 20ms fade-in/-out om klikken te
    voorkomen.
  - t=0 t/m t=10: stilte (op t=0 na de eerste piep zelf).
  - t=10, 20, ..., 120 (12 piepen): dezelfde 1000 Hz-toon, amplitude lineair
    oplopend van 0,20 (t=10) naar 0,95 (t=120), zelfde fade-in/-out.
  - totale lengte exact 120,0s (geverifieerd via Python `wave`-module:
    1.323.000 frames / 11025 Hz = 120,0s).
- `AlarmService.kt` (`robberts_assistent/android/app/src/main/kotlin/nl/vdzon/
  robberts_assistent/alarm/AlarmService.kt`): `startAlarmSound()` gebruikt niet
  meer `RingtoneManager`/`Uri` maar
  `MediaPlayer.create(this, R.raw.alarm_beep, audioAttributes,
  AudioManager.AUDIO_SESSION_ID_GENERATE)` (de 4-argument-overload zodat de
  `AudioAttributes` — `USAGE_ALARM`/`CONTENT_TYPE_SONIFICATION` — al bij het
  aanmaken gezet worden i.p.v. pas na `prepare()`), met `isLooping = false`. De
  hele aanroep zit in `runCatching { ... }` (zelfde stijl als voorheen), dus een
  `null`-resultaat van `MediaPlayer.create` of een afspeelfout crasht de service
  niet — geen `OnCompletionListener` toegevoegd, dus na natuurlijk aflopen (2
  min) blijft de service ongewijzigd doorlopen (trilling/notificatie/
  `AlarmActivity` actief) tot Sluit/Snooze, exact zoals gevraagd. Ongebruikte
  `RingtoneManager`/`Uri`-imports verwijderd, `AudioManager`-import toegevoegd.
  Klasse-KDoc en channel-commentaar tekstueel bijgewerkt (verwijzen niet meer
  naar "loopende ringtoon"). Resource-vrijgave in `stopEverything()`/
  `onDestroy()` (player `stop()`/`release()`) ongewijzigd — blijft werken op het
  nieuwe `MediaPlayer`-object.
- Getest:
  - `flutter analyze` in `robberts_assistent/`: geen issues.
  - `flutter test` in `robberts_assistent/`: alle 29 bestaande tests slagen
    (geen Dart-code gewijzigd, dus geen regressie verwacht/gevonden).
  - `flutter pub get`: `pubspec.lock` niet gewijzigd (gecontroleerd via
    `git status`).
  - Kotlin-compilatie van `AlarmService.kt` kon niet lokaal geverifieerd worden:
    `robberts_assistent/android/` heeft (anders dan `wind/`) geen gecommitte
    Gradle-wrapper (`gradlew` ontbreekt), en de sandbox heeft geen
    Android-SDK/`ANDROID_HOME`. Dit is dezelfde bekende sandbox-beperking als
    beschreven in `docs/factory/development.md` (native Android-build alleen in
    CI). Wijziging is beperkt tot een audiobron-swap (`RingtoneManager` →
    `MediaPlayer.create` met een resource-overload) en handmatig tegen de
    Android-API geverifieerd (o.a. de gebruikte 4-argument-`MediaPlayer.create`-
    overload bestaat sinds API 21; `minSdk` van deze app ligt daarboven).
  - Backend (`robberts-assistent-backend/`) niet geraakt door deze story; `mvn
    test` niet opnieuw gedraaid.

## Review (SF-1248)

- Diff tegen `main` bekeken (`git diff main...HEAD`): alleen `AlarmService.kt`,
  nieuw `res/raw/alarm_beep.wav` en dit worklog. Geen scope-overschrijding.
- `AlarmService.kt`: `RingtoneManager`/`Uri`-imports weg, `startAlarmSound()`
  gebruikt nu `MediaPlayer.create(this, R.raw.alarm_beep, audioAttributes,
  AudioManager.AUDIO_SESSION_ID_GENERATE)` met `isLooping = false`, binnen
  `runCatching` (geen `OnCompletionListener`, dus geen auto-stop na afloop).
  Rest van de service (notificatie, `AlarmActivity`, dismiss/snooze,
  trilling, wakelock, resource-vrijgave in `stopEverything()`/`onDestroy()`)
  ongewijzigd. Geen `RingtoneManager`-referenties meer in de repo
  (`grep -rl RingtoneManager robberts_assistent/` levert niets op).
- Audiobestand geverifieerd met Python's `wave`-module: mono 16-bit PCM,
  11025 Hz, exact 120,0s; piek-amplitude per seconde bevestigt piepen op
  t=0,10,20,...,110 met oplopend volume (3276 → 28894) en stilte ertussen —
  komt overeen met de gespecificeerde opbouw. `.wav` i.p.v. `.ogg`/`.mp3` is
  toegestaan mits vermeld; worklog beschrijft duidelijk waarom compressie niet
  haalbaar was in de sandbox.
- Zelf gedraaid (deze reviewer-sandbox heeft `flutter` wel beschikbaar, geen
  Gradle-wrapper/Android-SDK voor de Kotlin-kant): `flutter analyze` → geen
  issues; `flutter test` → alle 29 tests groen. Kotlin-compilatie kon ook in
  deze sandbox niet geverifieerd worden (geen `gradlew`/Android-SDK) — de
  wijziging is beperkt tot een bekende, sinds API 21 beschikbare
  `MediaPlayer.create`-overload en is verder puur een audiobron-swap; dit is
  in lijn met de vermelde sandbox-beperking, geen blocker.
- Geen backend-wijzigingen in deze story; `mvn test` niet relevant.
- Conclusie: implementatie dekt scope en acceptatiecriteria volledig, geen
  bugs/regressies gevonden. Akkoord.

## Test (SF-1249)

- Diff tegen `main` bekeken: alleen `AlarmService.kt`, nieuw
  `res/raw/alarm_beep.wav` en dit worklog. Geen scope-overschrijding.
- `grep -rl RingtoneManager robberts_assistent/` → geen treffers; `startAlarmSound()`
  gebruikt `MediaPlayer.create(this, R.raw.alarm_beep, audioAttributes,
  AudioManager.AUDIO_SESSION_ID_GENERATE)` met `isLooping = false`, binnen
  `runCatching`, geen `OnCompletionListener` → geen auto-stop bij natuurlijk
  aflopen. `stopEverything()`/`onDestroy()` (stop/release player, cancel
  vibrator, wakelock-release, stopForeground/stopSelf) ongewijzigd t.o.v.
  `main`.
- Audiobestand onafhankelijk geverifieerd met Python's `wave`-module (los van
  de reviewer-check): mono 16-bit PCM, 11025 Hz, exact 1.323.000 frames =
  120,0s. Piek-amplitude per seconde bevestigt: piep op t=0 (amplitude 3276,
  duidelijk zachter dan de rest), stilte t=1..9, daarna piepen op t=10, 20,
  ..., 110 met monotoon oplopende amplitude (6553 → 28894) en stilte
  ertussen — komt overeen met de gespecificeerde opbouw uit de story
  ("elke 10s luider tot het einde"; een piep exact op t=120 zou samenvallen
  met het bestandseinde en is dus niet apart aanwezig — geen probleem, de
  opbouw t=0/stilte/12×-oplopende-piep-om-de-10s/totale-lengte-120s klopt).
  `.wav` i.p.v. `.ogg`/`.mp3` conform acceptatiecriteria toegestaan mits
  vermeld — worklog vermeldt dit duidelijk.
- `flutter analyze` (`robberts_assistent/`, 2026-07-23 08:52 UTC): "No issues
  found!".
- `flutter test` (`robberts_assistent/`, 2026-07-23 08:52 UTC): alle 29 tests
  groen (`All tests passed!`), geen regressie.
- `git status` na de testrun: geen wijzigingen (o.a. `pubspec.lock`
  ongewijzigd).
- Kotlin-compilatie kon ook in deze sandbox niet geverifieerd worden (geen
  `gradlew`/Android-SDK in `robberts_assistent/android/`, zelfde bekende
  sandbox-beperking als bij development/review). Wijziging is beperkt tot een
  bekende, sinds API 21 beschikbare `MediaPlayer.create`-overload; handmatige
  code-review geeft geen aanleiding tot twijfel.
- Geen screenshot: deze story raakt uitsluitend Android-native alarmgeluid
  (`AlarmService.kt` + `res/raw`), geen zichtbare Flutter-UI-wijziging in
  `robberts_assistent` — screenshot-vereiste is dus niet van toepassing.
  Backend niet geraakt; `mvn test` niet relevant.
- Conclusie: alle acceptatiecriteria geverifieerd en akkoord. **tested**.
