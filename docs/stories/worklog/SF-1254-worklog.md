# SF-1254 - Worklog

Story-context bij eerste pickup:
Vertraag trillen 30 sec in AlarmService

Pas robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/alarm/AlarmService.kt aan zodat startVibration() pas 30 seconden na ACTION_START wordt aangeroepen, via een Handler.postDelayed (bewaar Handler + Runnable als instance-property). Geluid (startAlarmSound()) en full-screen scherm (launchAlarmActivity()) blijven direct starten, ongewijzigd. In stopEverything() (aangeroepen bij ACTION_DISMISS, ACTION_SNOOZE en onDestroy) moet de nog niet afgevuurde vertraagde trilling worden geannuleerd (handler.removeCallbacks(...)), naast de bestaande vibrator?.cancel() voor een reeds gestarte trilling, zodat er op geen enkel moment na het stoppen van het alarm alsnog getrild wordt. Trillingspatroon (VibrationEffect.createWaveform, longArrayOf(0, 800, 600), repeat=0) blijft ongewijzigd. 30 seconden als vaste constante, niet configureerbaar. Geen wijzigingen aan AlarmReceiver, AlarmScheduling, BootReceiver, AlarmActivity of de Flutter-laag.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
