import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart' show kIsWeb;

import 'api_client.dart';

/// Achtergrond-handler (verplicht top-level voor firebase_messaging). Als de app op de achtergrond
/// of gesloten is toont Android de notification-payload zelf, dus hier is niets nodig.
@pragma('vm:entry-point')
Future<void> _backgroundHandler(RemoteMessage message) async {}

/// Zet FCM op (alleen Android): vraagt notificatie-permissie, haalt het device-token op en
/// registreert het bij de backend zodat de agent er push naartoe kan sturen. [onForegroundMessage]
/// wordt aangeroepen bij een push terwijl de app open is (Android toont die dan niet zelf).
class FcmService {
  static bool _done = false;

  static Future<void> setup(
    ApiClient api,
    void Function(String title, String body) onForegroundMessage,
  ) async {
    if (kIsWeb || _done) return; // FCM-web vereist extra config; alleen Android voor nu.
    _done = true;
    try {
      await Firebase.initializeApp();
      FirebaseMessaging.onBackgroundMessage(_backgroundHandler);
      final messaging = FirebaseMessaging.instance;
      await messaging.requestPermission();

      final token = await messaging.getToken();
      if (token != null) {
        await api.registerFcmToken(token).catchError((_) {});
      }
      messaging.onTokenRefresh.listen((t) => api.registerFcmToken(t).catchError((_) {}));

      FirebaseMessaging.onMessage.listen((message) {
        final n = message.notification;
        final title = n?.title ?? message.data['title'] ?? 'Melding';
        final body = n?.body ?? message.data['body'] ?? '';
        onForegroundMessage(title, body);
      });
    } catch (_) {
      _done = false; // volgende keer opnieuw proberen
    }
  }
}
