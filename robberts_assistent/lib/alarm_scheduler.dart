import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import 'api_client.dart';

/// Plant de alarms lokaal in via Android AlarmManager (flutter_local_notifications `zonedSchedule`):
/// als full-screen alarm met alarmgeluid, exact als dat mag (anders inexact → hooguit enkele minuten
/// later). Gaat af ook als de telefoon slaapt/vergrendeld is; de boot-receiver herstelt de planning
/// na een reboot. Herhalende alarms worden voor de eerstvolgende keren ingepland.
class AlarmScheduler {
  static final _local = FlutterLocalNotificationsPlugin();
  static bool _tzInit = false;

  /// `false` als exacte alarms niet toegestaan zijn (dan wordt inexact gebruikt). De UI kan hierop
  /// een hint tonen. `null` zolang er nog niet gesynct is.
  static bool? exactAllowed;

  static const _channelId = 'assistent_alarms';
  static const _channelName = 'Alarms';
  static const _idBase = 100000;
  static const _alarmPayload = 'alarm';

  static AndroidFlutterLocalNotificationsPlugin? get _android =>
      _local.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();

  /// Vraagt (indien nodig) de exact-alarm-permissie; opent op Android 13+ de instelling. Geeft terug
  /// of exacte alarms nu toegestaan zijn.
  static Future<bool> requestExactPermission() async {
    if (kIsWeb) return true;
    final ok = (await _android?.requestExactAlarmsPermission()) ?? true;
    exactAllowed = ok;
    return ok;
  }

  /// Haalt de alarms bij de backend op en (her)plant ze lokaal.
  static Future<void> sync(ApiClient api) async {
    if (kIsWeb) return;
    if (!_tzInit) {
      tzdata.initializeTimeZones();
      tz.setLocalLocation(tz.getLocation('Europe/Amsterdam'));
      _tzInit = true;
    }
    await _local.initialize(
      const InitializationSettings(android: AndroidInitializationSettings('@mipmap/ic_launcher')),
    );
    await _android?.createNotificationChannel(
      const AndroidNotificationChannel(
        _channelId,
        _channelName,
        description: 'Wekkers/alarms van je assistent',
        importance: Importance.max,
        playSound: true,
      ),
    );
    await _android?.requestNotificationsPermission();
    exactAllowed = (await _android?.requestExactAlarmsPermission()) ?? true;

    final alarms = await api.listAlarms();

    // Vorige alarm-planningen wissen.
    for (final p in await _local.pendingNotificationRequests()) {
      if (p.payload == _alarmPayload) await _local.cancel(p.id);
    }

    // (Her)plannen — per alarm afgeschermd, zodat één fout de rest niet blokkeert.
    var id = _idBase;
    final now = DateTime.now();
    for (final alarm in alarms.where((a) => a.active)) {
      for (final when in _nextOccurrences(alarm, now)) {
        try {
          await _schedule(id++, alarm.message, when);
        } catch (_) {
          // negeer dit ene alarm, ga door met de rest
        }
      }
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

  static Future<void> _schedule(int id, String message, DateTime when) async {
    final scheduled = tz.TZDateTime.from(when, tz.local);
    const details = NotificationDetails(
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
    );
    try {
      await _local.zonedSchedule(
        id,
        'Alarm',
        message,
        scheduled,
        details,
        androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
        uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
        payload: _alarmPayload,
      );
    } on PlatformException {
      // Exacte alarms niet toegestaan → inexact (kan enkele minuten later afgaan, maar gaat wél af).
      exactAllowed = false;
      await _local.zonedSchedule(
        id,
        'Alarm',
        message,
        scheduled,
        details,
        androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
        uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
        payload: _alarmPayload,
      );
    }
  }
}
