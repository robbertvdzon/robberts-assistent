import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'api_client.dart';

/// Plant de alarms lokaal in als **echte wekker** via een native Android-laag (AlarmManager
/// `setAlarmClock` → full-screen [AlarmActivity] + loopende ringtoon met Sluit/Snooze).
///
/// De flow: deze klasse haalt de alarms bij de backend op, rekent de eerstvolgende voorkomens uit
/// en geeft een platte lijst (id, tekst, tijdstip) door aan de native kant via een MethodChannel.
/// Alles daarna — afgaan, geluid, over het lockscreen tonen, snoozen, opnieuw inplannen na reboot —
/// gebeurt native (zie `android/app/src/main/kotlin/.../alarm/`).
///
/// `setAlarmClock` is altijd exact (ook in Doze) en heeft géén SCHEDULE_EXACT_ALARM-permissie nodig,
/// dus de vroegere "exacte alarms staan uit"-situatie kan hier niet meer optreden.
class AlarmScheduler {
  static const _channel = MethodChannel('nl.vdzon.robberts_assistent/alarm');
  static const _idBase = 100000;

  /// Behouden voor de UI (schedules_screen). Met `setAlarmClock` zijn alarms altijd exact, dus dit
  /// is altijd `true`; de gele "exact alarm"-banner verschijnt niet meer.
  static bool? exactAllowed = true;

  /// Vroeger vroeg dit de exact-alarm-permissie; niet meer nodig. Vraagt nu (best-effort) de
  /// notificatie-permissie, want de full-screen wekker verschijnt via een notification-kanaal.
  static Future<bool> requestExactPermission() async {
    if (kIsWeb) return true;
    await _requestNotificationsPermission();
    exactAllowed = true;
    return true;
  }

  static Future<void> _requestNotificationsPermission() async {
    final android = FlutterLocalNotificationsPlugin()
        .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    await android?.requestNotificationsPermission();
  }

  /// Haalt de alarms bij de backend op en (her)plant ze native. Berekent per alarm de eerstvolgende
  /// voorkomens (recurring: de komende 6) en stuurt een platte lijst naar de native laag, die eerst
  /// alle vorige planningen wist.
  static Future<void> sync(ApiClient api) async {
    if (kIsWeb) return;
    await _requestNotificationsPermission();

    final alarms = await api.listAlarms();
    final now = DateTime.now();

    final payload = <Map<String, Object>>[];
    var id = _idBase;
    for (final alarm in alarms.where((a) => a.active)) {
      for (final when in _nextOccurrences(alarm, now)) {
        payload.add({
          'id': id++,
          'message': alarm.message,
          'triggerAtMillis': when.millisecondsSinceEpoch,
        });
      }
    }

    try {
      await _channel.invokeMethod('scheduleAll', {'alarms': payload});
    } on MissingPluginException {
      // Native laag niet beschikbaar (bv. oude build) — stil negeren.
    } on PlatformException {
      // Inplannen mislukt — stil negeren, volgende sync probeert het opnieuw.
    }
  }

  static List<DateTime> _nextOccurrences(Alarm alarm, DateTime now) {
    final recurrence = alarm.recurrence;
    if (recurrence == null) {
      return alarm.time.isAfter(now) ? [alarm.time] : const [];
    }
    var t = alarm.time;
    while (!t.isAfter(now)) {
      t = recurrence.nextAfter(t);
    }
    final result = <DateTime>[];
    for (var i = 0; i < 6; i++) {
      result.add(t);
      t = recurrence.nextAfter(t);
    }
    return result;
  }
}
