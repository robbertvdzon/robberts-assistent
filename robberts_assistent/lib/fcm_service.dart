import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart' show ValueNotifier;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'api_client.dart';

// Moet gelijk zijn aan `BriefingScheduler.PUSH_TYPE` in de backend.
const _briefingPushType = 'briefing';

// Index van de Morgen-tab in HomeScreen's NavigationBar/IndexedStack.
const _morgenTabIndex = 0;

/// Achtergrond-handler (verplicht top-level voor firebase_messaging). Als de app op de achtergrond
/// of gesloten is toont Android de FCM-notification-payload zelf op het opgegeven kanaal.
@pragma('vm:entry-point')
Future<void> _backgroundHandler(RemoteMessage message) async {}

// Moet gelijk zijn aan het kanaal-id dat de backend meestuurt (zie PushService).
const _channelId = 'assistent_meldingen';
const _channelName = 'Assistent-meldingen';

/// Zet FCM op (alleen Android): permissie vragen, token registreren bij de backend, en een
/// high-importance notificatie-kanaal aanmaken. Binnenkomende push wordt als echte
/// systeem-notificatie getoond (heads-up, lockscreen, spiegelt naar een gekoppeld horloge) —
/// ook wanneer de app open is.
class FcmService {
  static bool _done = false;
  static final _local = FlutterLocalNotificationsPlugin();
  static var _notifId = 0;

  /// Niet-`null` zodra een tik op een push (of een koude start ervandaan) om een tabwissel vraagt;
  /// `HomeScreen` luistert hierop, schakelt naar die tab en zet 'm daarna terug op `null`.
  static final deepLinkTab = ValueNotifier<int?>(null);

  static void _handleTap(RemoteMessage message) {
    if (message.data['type'] == _briefingPushType) {
      deepLinkTab.value = _morgenTabIndex;
    }
  }

  static Future<void> setup(ApiClient api) async {
    if (kIsWeb || _done) return; // FCM-web vereist extra config; alleen Android voor nu.
    _done = true;
    try {
      await Firebase.initializeApp();

      // Lokale notificaties + high-importance kanaal (zodat ook FCM-achtergrondmeldingen op dit
      // kanaal geluid maken en op het lockscreen verschijnen).
      await _local.initialize(
        const InitializationSettings(android: AndroidInitializationSettings('@mipmap/ic_launcher')),
      );
      await _local
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(
            const AndroidNotificationChannel(
              _channelId,
              _channelName,
              description: 'Meldingen van je assistent',
              importance: Importance.high,
            ),
          );

      FirebaseMessaging.onBackgroundMessage(_backgroundHandler);
      final messaging = FirebaseMessaging.instance;
      await messaging.requestPermission();

      final token = await messaging.getToken();
      if (token != null) {
        await api.registerFcmToken(token).catchError((_) {});
      }
      messaging.onTokenRefresh.listen((t) => api.registerFcmToken(t).catchError((_) {}));

      // Tik op de melding terwijl de app op de achtergrond staat (niet gesloten).
      FirebaseMessaging.onMessageOpenedApp.listen(_handleTap);
      // Koude start: de app is geopend dóór op de melding te tikken.
      final initialMessage = await messaging.getInitialMessage();
      if (initialMessage != null) _handleTap(initialMessage);

      // Voorgrond: FCM toont niets zelf → wij posten een echte notificatie op het kanaal.
      FirebaseMessaging.onMessage.listen((message) {
        final n = message.notification;
        final title = n?.title ?? message.data['title'] ?? 'Melding';
        final body = n?.body ?? message.data['body'] ?? '';
        _local.show(
          _notifId++,
          title,
          body,
          const NotificationDetails(
            android: AndroidNotificationDetails(
              _channelId,
              _channelName,
              importance: Importance.high,
              priority: Priority.high,
            ),
          ),
        );
      });
    } catch (_) {
      _done = false; // volgende keer opnieuw proberen
    }
  }
}
