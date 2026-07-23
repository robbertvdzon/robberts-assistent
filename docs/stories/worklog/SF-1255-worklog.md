# SF-1255 - Worklog

Story-context bij eerste pickup:
Vertraag trillen 30 sec in AlarmService

Pas `robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/alarm/AlarmService.kt`
aan zodat `startVibration()` pas 30 seconden na `ACTION_START` wordt aangeroepen, via een
`Handler.postDelayed` (bewaar Handler + Runnable als instance-property). Geluid
(`startAlarmSound()`) en full-screen scherm (`launchAlarmActivity()`) blijven direct starten,
ongewijzigd. In `stopEverything()` (aangeroepen bij `ACTION_DISMISS`, `ACTION_SNOOZE` en
`onDestroy()`) moet de nog niet afgevuurde vertraagde trilling worden geannuleerd
(`handler.removeCallbacks(...)`), naast de bestaande `vibrator?.cancel()` voor een reeds
gestarte trilling. Trillingspatroon ongewijzigd. 30 seconden als vaste constante.

Stappenplan:
- [x] issue en target-docs lezen (`development.md`, `technical-spec.md`)
- [x] wijziging doorvoeren in `AlarmService.kt`
- [x] relevante tests draaien (`flutter analyze` + `flutter test` in `robberts_assistent/`)
- [x] worklog bijwerken met resultaat

Done / rationale:
- `AlarmService.kt`: nieuwe instance-properties `vibrationHandler` (`Handler(Looper
  .getMainLooper())`) en `startVibrationRunnable` (`Runnable { startVibration() }`).
  `ACTION_START` roept nu `vibrationHandler.postDelayed(startVibrationRunnable,
  VIBRATION_DELAY_MS)` aan i.p.v. direct `startVibration()`; `VIBRATION_DELAY_MS = 30_000L`
  als vaste companion-constante. `stopEverything()` roept eerst
  `vibrationHandler.removeCallbacks(startVibrationRunnable)` aan (naast de bestaande
  `vibrator?.cancel()`), zodat een nog niet afgevuurde vertraagde trilling niet alsnog
  start nadat het alarm al gestopt is (Sluit/Snooze/`onDestroy()` lopen allemaal via
  `stopEverything()`). Geluid en full-screen-activiteit-start blijven ongewijzigd direct
  in `ACTION_START`. Geen wijziging aan `AlarmReceiver`, `AlarmScheduling`, `BootReceiver`,
  `AlarmActivity` of de Flutter-laag.
- Kotlin-compilatie kon in deze sandbox niet geverifieerd worden: `robberts_assistent/android/`
  heeft geen `gradlew`/Android-SDK (zelfde bekende sandbox-beperking als bij SF-1247, zie
  dat worklog). De wijziging gebruikt alleen standaard, al langer beschikbare API's
  (`android.os.Handler`/`Looper`, al elders in de codebase indirect via andere Android-
  componenten), dus geen aanleiding tot twijfel over compileerbaarheid.
- `flutter analyze` (`robberts_assistent/`): "No issues found!" — Flutter-laag is niet
  gewijzigd, dus verwacht groen.
- `flutter test` (`robberts_assistent/`): alle 29 tests groen ("All tests passed!"), geen
  regressie.
- `git status` na de testrun: geen ongerelateerde wijzigingen (o.a. `pubspec.lock`
  ongewijzigd).
- Geen instrumentatietest voor de 30-seconden-vertraging toegevoegd, conform de
  aanname in de story (handmatige verificatie op toestel/emulator volstaat).
