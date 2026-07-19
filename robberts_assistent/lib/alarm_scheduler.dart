import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import 'api_client.dart';

/// Plant de alarms lokaal in via Android AlarmManager (flutter_local_notifications `zonedSchedule`):
/// exact + allowWhileIdle, als full-screen alarm met alarmgeluid — gaat dus af ook als de telefoon
/// slaapt/vergrendeld is, en na een reboot herstelt de boot-receiver de planning. Herhalende alarms
/// worden voor de eerstvolgende paar keren ingepland; bij elke sync opnieuw berekend.
class AlarmScheduler {
  static final _local = FlutterLocalNotificationsPlugin();
  static bool _tzInit = false;

  static const _channelId = 'assistent_alarms';
  static const _channelName = 'Alarms';
  static const _idBase = 100000; // eigen id-range voor alarm-occurrences
  static const _alarmPayload = 'alarm';

  /// Haalt de alarms bij de backend op en (her)plant ze lokaal.
  static Future<void> sync(ApiClient api) async {
    if (kIsWeb) return;
    try {
      if (!_tzInit) {
        tzdata.initializeTimeZones();
        tz.setLocalLocation(tz.getLocation('Europe/Amsterdam'));
        _tzInit = true;
      }
      await _local.initialize(
        const InitializationSettings(android: AndroidInitializationSettings('@mipmap/ic_launcher')),
      );
      final android = _local.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      await android?.createNotificationChannel(
        const AndroidNotificationChannel(
          _channelId,
          _channelName,
          description: 'Wekkers/alarms van je assistent',
          importance: Importance.max,
          playSound: true,
        ),
      );
      await android?.requestNotificationsPermission();
      await android?.requestExactAlarmsPermission();

      final alarms = await api.listAlarms();

      // Vorige alarm-planningen wissen.
      for (final p in await _local.pendingNotificationRequests()) {
        if (p.payload == _alarmPayload) await _local.cancel(p.id);
      }

      // (Her)plannen.
      var id = _idBase;
      final now = DateTime.now();
      for (final alarm in alarms.where((a) => a.active)) {
        for (final when in _nextOccurrences(alarm, now)) {
          await _schedule(id++, alarm.message, when);
        }
      }
    } catch (_) {
      // Best-effort: bij een fout (permissie/netwerk) proberen we het bij de volgende sync opnieuw.
    }
  }

  static List<DateTime> _nextOccurrences(Alarm alarm, DateTime now) {
    final recurrence = alarm.recurrence;
    if (recurrence == null) {
      return alarm.time.isAfter(now) ? [alarm.time] : const [];
    }
    // Naar het eerstvolgende toekomstige tijdstip, daarna een paar vooruit (overleeft app-dicht).
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

  static Future<void> _schedule(int id, String message, DateTime when) async {
    await _local.zonedSchedule(
      id,
      'Alarm',
      message,
      tz.TZDateTime.from(when, tz.local),
      const NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          _channelName,
          importance: Importance.max,
          priority: Priority.max,
          category: AndroidNotificationCategory.alarm,
          fullScreenIntent: true,
          audioAttributesUsage: AudioAttributesUsage.alarm,
          ongoing: true,
          autoCancel: false,
        ),
      ),
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
      payload: _alarmPayload,
    );
  }
}
