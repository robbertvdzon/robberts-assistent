import 'dart:async';
import 'dart:convert';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart' show kIsWeb, visibleForTesting;
import 'package:flutter/material.dart' show ValueNotifier;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'api_client.dart';

// Moet gelijk zijn aan `BriefingScheduler.PUSH_TYPE` in de backend.
const _briefingPushType = 'briefing';
const _watchPushType = 'watch';
// Moet gelijk zijn aan het `type` dat `nutrition.MealService` meestuurt.
const _mealPushType = 'meal';
// Moet gelijk zijn aan `wellbeing.CheckInPromptScheduler.PUSH_TYPE`.
const _checkInPushType = 'checkin';

// Payload van de doorlopende melding bij een lopend dutje (zie [FcmService.showNapNotification]).
const _napPushType = 'nap';

enum DeepLinkTarget { today, watches, food, checkIn, nap, jobs }

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
  static var _notifId = DateTime.now().millisecondsSinceEpoch & 0x7fffffff;
  static ApiClient? _api;

  /// Niet-`null` zodra een tik op een push (of een koude start ervandaan) om navigatie vraagt.
  /// `HomeScreen` luistert hierop, navigeert naar het doel en consumeert de waarde.
  static final deepLinkTarget = ValueNotifier<DeepLinkTarget?>(null);

  static void _handleTap(RemoteMessage message) {
    handlePushData(message.data);
  }

  static DeepLinkTarget? targetForPushData(Map<String, dynamic> data) =>
      switch (data['type']) {
        _briefingPushType => DeepLinkTarget.today,
        _watchPushType => DeepLinkTarget.watches,
        _mealPushType => DeepLinkTarget.food,
        _checkInPushType => DeepLinkTarget.checkIn,
        _napPushType => DeepLinkTarget.nap,
        'jobs' => DeepLinkTarget.jobs,
        _ => null,
      };

  static void handlePushData(Map<String, dynamic> data) {
    final id = data['notificationId'];
    final api = _api;
    if (id is String && api != null) unawaited(_markRead(api, id));
    final target = targetForPushData(data);
    if (target != null) deepLinkTarget.value = target;
  }

  static Future<void> _markRead(ApiClient api, String id) async {
    try {
      await api.markNotificationRead(id);
    } catch (_) {
      // Navigeren blijft werken als de gelezenstatus tijdelijk niet opgeslagen kan worden.
    }
  }

  @visibleForTesting
  static void handleLocalPayload(String? payload) {
    if (payload == null) return;
    try {
      final decoded = jsonDecode(payload);
      if (decoded is Map) {
        handlePushData(Map<String, dynamic>.from(decoded));
        return;
      }
    } catch (_) {}
    // Oude meldingen en de doorlopende dutje-melding hebben alleen een type.
    handlePushData({'type': payload});
  }

  static const _napNotificationId = 900001;

  /// Doorlopende melding zolang er een dutje loopt; een tik opent het scherm om af te melden.
  /// Doet niets op web of zolang de meldingen niet zijn opgezet.
  static Future<void> showNapNotification(DateTime start) async {
    if (kIsWeb || !_done) return;
    final time =
        '${start.hour.toString().padLeft(2, '0')}:${start.minute.toString().padLeft(2, '0')}';
    try {
      await _local.show(
        _napNotificationId,
        'Dutje sinds $time',
        'Tik als je wakker bent.',
        const NotificationDetails(
          android: AndroidNotificationDetails(
            'dutje',
            'Dutje',
            channelDescription: 'Zichtbaar zolang er een dutje loopt',
            importance: Importance.low,
            priority: Priority.low,
            ongoing: true,
            autoCancel: false,
            onlyAlertOnce: true,
          ),
        ),
        payload: _napPushType,
      );
    } catch (_) {
      // De melding is een geheugensteun; zonder werkt het dutje ook.
    }
  }

  static Future<void> cancelNapNotification() async {
    if (kIsWeb || !_done) return;
    try {
      await _local.cancel(_napNotificationId);
    } catch (_) {}
  }

  @visibleForTesting
  static Future<void> handleInitialLocalNotification([
    FlutterLocalNotificationsPlugin? localNotifications,
  ]) async {
    final details = await (localNotifications ?? _local)
        .getNotificationAppLaunchDetails();
    if (details?.didNotificationLaunchApp != true) return;
    handleLocalPayload(details?.notificationResponse?.payload);
  }

  static Future<void> setup(ApiClient api) async {
    _api = api;
    if (kIsWeb || _done) {
      return; // FCM-web vereist extra config; alleen Android voor nu.
    }
    _done = true;
    try {
      await Firebase.initializeApp();

      // Lokale notificaties + high-importance kanaal (zodat ook FCM-achtergrondmeldingen op dit
      // kanaal geluid maken en op het lockscreen verschijnen).
      await _local.initialize(
        const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        ),
        onDidReceiveNotificationResponse: (response) {
          handleLocalPayload(response.payload);
        },
      );
      await handleInitialLocalNotification();
      await _local
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >()
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
      messaging.onTokenRefresh.listen(
        (t) => api.registerFcmToken(t).catchError((_) {}),
      );

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
          payload: jsonEncode(message.data),
        );
      });
    } catch (_) {
      _done = false; // volgende keer opnieuw proberen
    }
  }
}
