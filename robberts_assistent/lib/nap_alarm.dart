import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

import 'api_client.dart';
import 'fcm_service.dart';

/// De wekker bij een dutje. Gebruikt dezelfde native wekker als de alarms (zie
/// `alarm_scheduler.dart`), maar met een eigen vaste plek, zodat het opnieuw inplannen van de
/// alarms hem niet wist. Op web is er geen wekker.
class NapAlarm {
  static const _channel = MethodChannel('nl.vdzon.robberts_assistent/alarm');

  static Future<void> schedule(DateTime at) =>
      _invoke('scheduleNap', {'triggerAtMillis': at.millisecondsSinceEpoch});

  static Future<void> cancel() => _invoke('cancelNap');

  /// Het moment waarop de wekker met "Sluit" is uitgezet, of `null`. Eenmalig uit te lezen.
  static Future<DateTime?> takeDismissedAt() async {
    final millis = await _invoke('takeNapDismissedAt');
    return millis is int ? DateTime.fromMillisecondsSinceEpoch(millis) : null;
  }

  static Future<Object?> _invoke(String method, [Object? arguments]) async {
    if (kIsWeb) return null;
    try {
      return await _channel.invokeMethod<Object?>(method, arguments);
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }
}

/// Brengt de telefoon in lijn met het dutje dat bij de backend loopt, bij het openen van de app
/// en na elk antwoord van de assistent (die kan een dutje starten of afmelden):
/// is de wekker met "Sluit" uitgezet, dan eindigt het dutje op dat moment; loopt er geen dutje
/// meer, dan verdwijnen de wekker en de melding. Mag de app nooit ophouden.
Future<void> reconcileNap(ApiClient api) async {
  try {
    final dismissedAt = await NapAlarm.takeDismissedAt();
    final open = (await api.getNaps(DateTime.now())).open;
    if (open == null) {
      await NapAlarm.cancel();
      await FcmService.cancelNapNotification();
      return;
    }
    if (dismissedAt != null && dismissedAt.isAfter(open.start)) {
      await api.updateNap(open.id, end: dismissedAt, clearAlarm: true);
      await FcmService.cancelNapNotification();
      return;
    }
    // Een dutje dat via de chat is gestart heeft de wekker nog niet op de telefoon staan. Opnieuw
    // inplannen van dezelfde tijd is onschadelijk; een gesnoozede wekker ligt al na alarmAt.
    final alarmAt = open.alarmAt;
    if (alarmAt != null && alarmAt.isAfter(DateTime.now())) {
      await NapAlarm.schedule(alarmAt);
    }
    await FcmService.showNapNotification(open.start);
  } catch (_) {
    // Volgende keer opnieuw.
  }
}
