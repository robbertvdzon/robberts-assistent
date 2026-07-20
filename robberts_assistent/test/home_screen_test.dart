import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/couplings_screen.dart';
import 'package:robberts_assistent/home_screen.dart';
import 'package:robberts_assistent/more_screen.dart';
import 'package:robberts_assistent/nightly_checks_screen.dart';
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

  testWidgets('bottom-nav telt precies 4 tabs en Meer opent MoreScreen', (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: HomeScreen(api: _FakeApiClient(), onLoggedOut: () {})),
    );
    await tester.pump();

    expect(find.byType(NavigationDestination), findsNWidgets(4));
    expect(find.text('Samenvatting'), findsOneWidget);
    expect(find.text('Assistent'), findsOneWidget);
    expect(find.text('Herinneringen'), findsOneWidget);
    expect(find.text('Meer'), findsOneWidget);

    await tester.tap(find.text('Meer'));
    await tester.pump();

    expect(find.byType(MoreScreen), findsOneWidget);
    expect(find.text('Koppelingen'), findsOneWidget);
    expect(find.text('Nachtchecks'), findsOneWidget);
    expect(find.text('Updates'), findsOneWidget);
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

    await tester.tap(find.text('Updates'));
    await tester.pumpAndSettle();
    expect(find.byType(UpdatesScreen), findsOneWidget);
  });
}
