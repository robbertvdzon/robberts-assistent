import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
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

/// Stub-ApiClient die alleen de door de vier hoofdschermen aangeroepen methodes overschrijft
/// met lege/harmloze resultaten, zodat er geen echte netwerkcalls in de test plaatsvinden.
class _FakeApiClient extends ApiClient {
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
  Future<List<Coupling>> listCouplings() async => [];

  @override
  Future<List<NightlyCheck>> listNightlyChecks() async => [];

  @override
  Future<String> getMemoryText() async => '';
}

void main() {
  // UpdatesScreen vraagt via een MethodChannel de geïnstalleerde versie op (native, niet
  // beschikbaar in widget-tests); mock die zodat navigeren ernaartoe niet crasht.
  const updaterChannel = MethodChannel('nl.vdzon.robberts_assistent/updater');
  TestWidgetsFlutterBinding.ensureInitialized();
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
    updaterChannel,
    (call) async => -1,
  );

  testWidgets('bottom-nav telt precies 5 tabs en Meer opent MoreScreen', (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {})),
    );
    await tester.pump();

    expect(find.byType(NavigationDestination), findsNWidgets(5));
    expect(find.text('Upcoming'), findsOneWidget);
    expect(find.text('Health check'), findsOneWidget);
    expect(find.text('Assistent'), findsOneWidget);
    expect(find.text('Herinneringen'), findsOneWidget);
    expect(find.text('Meer'), findsOneWidget);

    await tester.tap(find.text('Meer'));
    await tester.pump();

    expect(find.byType(MoreScreen), findsOneWidget);
    expect(find.text('Koppelingen'), findsOneWidget);
    expect(find.text('Nachtchecks'), findsOneWidget);
    expect(find.text('Geheugen'), findsOneWidget);
    expect(find.text('Updates'), findsOneWidget);
  });

  testWidgets('start standaard op de tab Assistent (ConversationsScreen), niet Upcoming', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {})),
    );
    await tester.pump();

    expect(tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex, 2);
    expect(tester.widget<IndexedStack>(find.byType(IndexedStack)).index, 2);
    expect(find.byType(ConversationsScreen), findsOneWidget);
    expect(find.byType(SummaryScreen), findsNothing);
    expect(find.byType(HealthCheckScreen), findsNothing);
  });

  testWidgets('tik op de Morgen-briefing-push (FcmService.deepLinkTab) schakelt naar de Upcoming-tab', (
    tester,
  ) async {
    addTearDown(() => FcmService.deepLinkTab.value = null);
    await tester.pumpWidget(
      MaterialApp(home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {})),
    );
    await tester.pump();
    expect(tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex, 2);

    FcmService.deepLinkTab.value = 0;
    await tester.pump();

    expect(tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex, 0);
    expect(find.byType(SummaryScreen), findsOneWidget);
    expect(FcmService.deepLinkTab.value, null);
  });

  testWidgets('lijst-items in Meer navigeren naar het bijbehorende scherm', (tester) async {
    final api = _FakeApiClient();
    await tester.pumpWidget(MaterialApp(home: MoreScreen(api: api)));
    await tester.pump();

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
}
