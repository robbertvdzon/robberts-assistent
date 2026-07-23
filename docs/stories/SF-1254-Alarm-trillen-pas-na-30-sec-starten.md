# SF-1254 - Alarm: trillen pas na 30 sec starten

## Story

Alarm: trillen pas na 30 sec starten

<!-- refined-by-factory -->

## Scope

In de native Android alarm-service (`robberts_assistent/android/app/src/main/kotlin/nl/vdzon/robberts_assistent/alarm/AlarmService.kt`) start het trillen (`startVibration()`) momenteel direct bij het afgaan van het alarm, tegelijk met het geluid. Dit wordt aangepast zodat het trillen pas 30 seconden na start van het alarm begint. Geluid en het full-screen alarmscherm blijven ongewijzigd (direct starten, zoals nu).

De vertraging wordt geïmplementeerd met een `Handler`/`postDelayed` (of vergelijkbaar mechanisme) die `startVibration()` na 30 seconden aanroept. Wordt het alarm binnen die 30 seconden gestopt (Sluit, Snooze, of anderszins `stopEverything()`/`onDestroy()`), dan moet de nog niet afgevuurde vertraagde trilling worden geannuleerd (`removeCallbacks` o.i.d.), zodat er niet alsnog getrild wordt nadat het alarm al is uitgezet.

## Acceptance criteria

- Bij het afgaan van een alarm (`ACTION_START`) start het geluid (piep) meteen, zoals nu.
- Het trillen start pas 30 seconden nadat het alarm is afgegaan, niet eerder.
- Wordt het alarm (Sluit/`ACTION_DISMISS`, Snooze/`ACTION_SNOOZE`, of `onDestroy()`) binnen die 30 seconden gestopt, dan trilt het toestel op geen enkel moment (de vertraagde trilling wordt geannuleerd, niet alsnog uitgevoerd na het stoppen).
- Wordt het alarm na de 30 seconden nog niet gestopt, dan start het trillen automatisch met hetzelfde patroon als voorheen (`VibrationEffect.createWaveform` met `longArrayOf(0, 800, 600)`, repeat=0).
- Overig gedrag van `AlarmService` (notificatie, full-screen `AlarmActivity`, wakelock, dismiss/snooze-afhandeling) blijft ongewijzigd.

## Aannames

- De vertraging van 30 seconden is een vaste waarde in code (geen instelbare/configureerbare waarde nodig), analoog aan de bestaande vaste 2-minuten-audioduur uit SF-1247.
- Er is geen wijziging nodig aan de Flutter-laag (`alarm_scheduler.dart`) of aan andere onderdelen van de native alarm-flow (`AlarmReceiver`, `AlarmScheduling`, `BootReceiver`, `AlarmActivity`) — de wijziging is beperkt tot `AlarmService.kt`.
- Er is geen automatische testdekking vereist die een 30-seconden-wachttijd daadwerkelijk uitvoert (geen instrumentatietest); handmatige verificatie op een toestel/emulator volstaat, zoals ook bij SF-1247.

## Eindsamenvatting

Voldoende context. Hier is de eindsamenvatting.

## SF-1254 — Alarm: trillen pas na 30 sec starten

**Gebouwd:** In de native Android-alarmservice (`AlarmService.kt`) start het trillen nu pas 30 seconden nadat het alarm is afgegaan, in plaats van meteen. Geluid en het full-screen alarmscherm starten nog steeds direct, ongewijzigd.

**Implementatie:** Een `Handler(Looper.getMainLooper())` + `Runnable` plant `startVibration()` via `postDelayed` met een vaste constante `VIBRATION_DELAY_MS = 30_000L` (niet configureerbaar, zoals bewust aangenomen). Wordt het alarm binnen die 30 seconden gestopt (Sluit, Snooze, of via `onDestroy()`), dan annuleert `stopEverything()` als allereerste stap de nog niet afgevuurde vertraagde trilling (`removeCallbacks`) vóór de bestaande opruiming — er wordt dus op geen enkel codepad na het stoppen alsnog getrild. Het trillingspatroon zelf (`VibrationEffect.createWaveform`, `longArrayOf(0, 800, 600)`, repeat=0) is ongewijzigd.

**Scope:** Wijziging beperkt tot `AlarmService.kt`; geen aanpassingen aan `AlarmReceiver`, `AlarmScheduling`, `BootReceiver`, `AlarmActivity` of de Flutter-laag.

**Getest:** Grondige code-inspectie tegen alle acceptatiecriteria (directe start geluid/scherm, 30s-vertraging trillen, annulering bij alle drie de stop-paden). `flutter analyze` op `robberts_assistent/` gaf "No issues found!". Kotlin-compilatie kon niet lokaal gedraaid worden — de sandbox heeft geen Gradle-wrapper (bekende, eerder al gerapporteerde beperking, ook bij SF-1247/SF-1255); er zijn alleen standaard Android-API's gebruikt die al elders in de codebase voorkomen.

**Bewust niet gedaan:** Geen instrumentatietest voor de 30s-vertraging (conform expliciete aanname in de story — handmatige verificatie op toestel/emulator volstaat). Geen screenshot, aangezien er geen zichtbare frontend-wijziging is.
