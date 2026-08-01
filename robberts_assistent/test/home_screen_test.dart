import 'package:flutter/foundation.dart'
    show debugDefaultTargetPlatformOverride;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/app_logo.dart';
import 'package:robberts_assistent/conversations_screen.dart';
import 'package:robberts_assistent/couplings_screen.dart';
import 'package:robberts_assistent/fcm_service.dart';
import 'package:robberts_assistent/health_check_screen.dart';
import 'package:robberts_assistent/home_screen.dart';
import 'package:robberts_assistent/memory_screen.dart';
import 'package:robberts_assistent/more_screen.dart';
import 'package:robberts_assistent/nightly_checks_screen.dart';
import 'package:robberts_assistent/summary_screen.dart';
import 'package:robberts_assistent/updates_screen.dart';
import 'package:robberts_assistent/watches_screen.dart';

/// Stub-ApiClient die alleen de door de vier hoofdschermen aangeroepen methodes overschrijft
/// met lege/harmloze resultaten, zodat er geen echte netwerkcalls in de test plaatsvinden.
class _FakeApiClient extends ApiClient {
  var watchLoadCount = 0;
  bool returnChangingWatch = false;

  @override
  Future<Map<String, dynamic>> getJson(String path) async => {'items': []};

  @override
  Future<List<AssistantConversationSummary>> assistantConversations({
    bool includeArchived = false,
    int? limit,
    int offset = 0,
  }) async => [];

  @override
  Future<List<Reminder>> listReminders() async => [];

  @override
  Future<List<Alarm>> listAlarms() async => [];

  @override
  Future<List<Watch>> listWatches() async {
    watchLoadCount++;
    if (!returnChangingWatch) return [];
    return [
      Watch(
        id: 'watch-1',
        title: 'Concertkaartjes',
        url: 'https://example.com',
        instruction: 'Zoek twee kaarten',
        notifyOnFound: true,
        status: watchLoadCount == 1 ? 'NIET_GEVONDEN' : 'GEVONDEN',
        statusDescription: watchLoadCount == 1
            ? 'Nog niet beschikbaar.'
            : 'Nu beschikbaar.',
        active: watchLoadCount == 1,
      ),
    ];
  }

  @override
  Future<List<Coupling>> listCouplings() async => [];

  @override
  Future<List<NightlyCheck>> listNightlyChecks() async => [];

  @override
  Future<String> getMemoryText() async => '';

  @override
  Future<BriefingData> getBriefing() async =>
      BriefingData(sections: const [], updatedAt: DateTime(2026, 7, 31, 12));

  @override
  Future<BriefingData> getHealthCheck() async =>
      BriefingData(sections: const [], updatedAt: DateTime(2026, 7, 31, 12));
}

void main() {
  // UpdatesScreen vraagt via een MethodChannel de geïnstalleerde versie op (native, niet
  // beschikbaar in widget-tests); mock die zodat navigeren ernaartoe niet crasht.
  const updaterChannel = MethodChannel('nl.vdzon.robberts_assistent/updater');
  TestWidgetsFlutterBinding.ensureInitialized();
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(updaterChannel, (call) async => -1);

  testWidgets(
    'bottom-nav telt precies 4 tabs en Meer toont alle overige schermen',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {}),
        ),
      );
      await tester.pump();

      expect(find.byType(NavigationDestination), findsNWidgets(4));
      expect(find.text('Vandaag'), findsOneWidget);
      expect(find.text('Assistent'), findsOneWidget);
      expect(find.text('Taken'), findsOneWidget);
      expect(find.text('Meer'), findsOneWidget);
      expect(tester.widget<AppLogo>(find.byType(AppLogo)).size, 30);

      await tester.tap(find.text('Meer'));
      await tester.pump();

      expect(find.byType(MoreScreen), findsOneWidget);
      expect(find.text('Health check'), findsOneWidget);
      expect(find.text('Zoekopdrachten'), findsOneWidget);
      expect(find.text('Koppelingen'), findsOneWidget);
      expect(find.text('Nachtchecks'), findsOneWidget);
      expect(find.text('Geheugen'), findsOneWidget);
      expect(find.text('Updates'), findsOneWidget);
    },
  );

  testWidgets(
    'start standaard op de tab Assistent (ConversationsScreen), niet Vandaag',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {}),
        ),
      );
      await tester.pump();

      expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        1,
      );
      expect(tester.widget<IndexedStack>(find.byType(IndexedStack)).index, 1);
      expect(find.byType(ConversationsScreen), findsOneWidget);
      expect(find.byType(SummaryScreen), findsNothing);
      expect(find.byType(HealthCheckScreen), findsNothing);
    },
  );

  testWidgets('tik op een briefing-push schakelt naar Vandaag', (tester) async {
    addTearDown(() => FcmService.deepLinkTarget.value = null);
    await tester.pumpWidget(
      MaterialApp(
        home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {}),
      ),
    );
    await tester.pump();
    expect(
      tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
      1,
    );

    FcmService.handlePushData({'type': 'briefing'});
    await tester.pump();

    expect(
      tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
      0,
    );
    expect(find.byType(SummaryScreen), findsOneWidget);
    expect(FcmService.deepLinkTarget.value, null);
  });

  testWidgets('briefing-push sluit een Meer-route en toont Vandaag', (
    tester,
  ) async {
    addTearDown(() => FcmService.deepLinkTarget.value = null);
    await tester.pumpWidget(
      MaterialApp(
        home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {}),
      ),
    );
    await tester.pump();

    await tester.tap(find.text('Meer'));
    await tester.pump();
    await tester.tap(find.text('Koppelingen'));
    await tester.pumpAndSettle();
    expect(find.byType(CouplingsScreen), findsOneWidget);

    FcmService.handlePushData({'type': 'briefing'});
    await tester.pumpAndSettle();

    expect(find.byType(CouplingsScreen), findsNothing);
    expect(find.byType(SummaryScreen), findsOneWidget);
    expect(
      tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
      0,
    );
    expect(FcmService.deepLinkTarget.value, null);
  });

  testWidgets('watch-push opent Zoekopdrachten als verse route', (
    tester,
  ) async {
    addTearDown(() => FcmService.deepLinkTarget.value = null);
    final api = _FakeApiClient()..returnChangingWatch = true;
    await tester.pumpWidget(
      MaterialApp(
        home: HomeScreen(api: api, onLoggedOut: () {}),
      ),
    );
    await tester.pump();
    expect(api.watchLoadCount, 0);

    FcmService.handlePushData({'type': 'watch'});
    await tester.pumpAndSettle();

    expect(api.watchLoadCount, 1);
    expect(find.byType(WatchesScreen), findsOneWidget);
    expect(find.text('Nog niet beschikbaar.'), findsOneWidget);
    expect(FcmService.deepLinkTarget.value, null);
  });

  testWidgets(
    'lokale watch-notificatie opent Zoekopdrachten bij een koude start',
    (tester) async {
      const notificationsChannel = MethodChannel(
        'dexterous.com/flutter/local_notifications',
      );
      addTearDown(() {
        FcmService.deepLinkTarget.value = null;
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(notificationsChannel, null);
        debugDefaultTargetPlatformOverride = null;
      });
      AndroidFlutterLocalNotificationsPlugin.registerWith();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(notificationsChannel, (call) async {
            expect(call.method, 'getNotificationAppLaunchDetails');
            return {
              'notificationLaunchedApp': true,
              'notificationResponse': {
                'notificationId': 1,
                'notificationResponseType': 0,
                'payload': 'watch',
              },
            };
          });
      await tester.pumpWidget(
        MaterialApp(
          home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {}),
        ),
      );
      await tester.pump();

      try {
        await FcmService.handleInitialLocalNotification(
          FlutterLocalNotificationsPlugin(),
        );
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
      await tester.pumpAndSettle();

      expect(find.byType(WatchesScreen), findsOneWidget);
      expect(FcmService.deepLinkTarget.value, null);
    },
  );

  testWidgets('lijst-items in Meer navigeren naar het bijbehorende scherm', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await tester.pumpWidget(MaterialApp(home: MoreScreen(api: api)));
    await tester.pump();

    await tester.tap(find.text('Health check'));
    await tester.pumpAndSettle();
    expect(find.byType(HealthCheckScreen), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Zoekopdrachten'));
    await tester.pumpAndSettle();
    expect(find.byType(WatchesScreen), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Koppelingen'));
    await tester.pumpAndSettle();
    expect(find.byType(CouplingsScreen), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Nachtchecks'));
    await tester.pumpAndSettle();
    expect(find.byType(NightlyChecksScreen), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Geheugen'));
    await tester.pumpAndSettle();
    expect(find.byType(MemoryScreen), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Updates'));
    await tester.pumpAndSettle();
    expect(find.byType(UpdatesScreen), findsOneWidget);
  });

  testWidgets('navigatielabels blijven op één regel bij 390px breedte', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {}),
      ),
    );
    await tester.pump();

    expect(find.byType(NavigationDestination), findsNWidgets(4));
    expect(tester.takeException(), isNull);
    for (final label in ['Vandaag', 'Assistent', 'Taken', 'Meer']) {
      final text = tester.widget<Text>(find.text(label));
      expect(text.maxLines, anyOf(isNull, 1));
    }
  });
}
