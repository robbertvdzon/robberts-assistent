import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/summary_screen.dart';

final _fixedUpdatedAt = DateTime(2026, 7, 21, 17, 30);

class _FakeApiClient extends ApiClient {
  List<BriefingSection> sections = [];
  DateTime updatedAt = _fixedUpdatedAt;
  final calledActions = <BriefingAction>[];
  int refreshCalls = 0;
  bool refreshShouldThrow = false;
  // Zonder completer lost refreshBriefing meteen op — voor de spinner-test moet de aanroeper zelf
  // kunnen bepalen wanneer de refresh 'klaar' is, zodat de laad-state observeerbaar is.
  Completer<BriefingData>? refreshCompleter;

  @override
  Future<BriefingData> getBriefing() async => BriefingData(sections: sections, updatedAt: updatedAt);

  @override
  Future<BriefingData> refreshBriefing() {
    refreshCalls++;
    if (refreshShouldThrow) return Future.error(Exception('ververs-fout'));
    final completer = refreshCompleter;
    if (completer != null) return completer.future;
    return Future.value(BriefingData(sections: sections, updatedAt: updatedAt));
  }

  @override
  Future<void> runBriefingAction(BriefingAction action) async {
    calledActions.add(action);
  }
}

/// SummaryScreen heeft (net als in de app) geen eigen Scaffold — die zit op HomeScreen-niveau —
/// maar de reload-knop toont een SnackBar bij een fout, wat een omringende Scaffold vereist.
Widget _wrap(ApiClient api) => MaterialApp(home: Scaffold(body: SummaryScreen(api: api)));

void main() {
  testWidgets('toont de titel en tekst van elke briefingsectie', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(key: 'kite', title: 'Kiten', text: 'Morgen: 🟢 24kn', items: []),
        BriefingSection(key: 'beach', title: 'Strandfietsen', text: 'Morgen: 🟢 (10 kn, droog, laagwater om 08:00)', items: []),
        BriefingSection(key: 'moestuin', title: 'Moestuin', text: 'Alles goed.', items: []),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Kiten'), findsOneWidget);
    expect(find.text('Morgen: 🟢 24kn'), findsOneWidget);
    expect(find.text('Strandfietsen'), findsOneWidget);
    expect(find.text('Morgen: 🟢 (10 kn, droog, laagwater om 08:00)'), findsOneWidget);
    expect(find.text('Moestuin'), findsOneWidget);
    expect(find.text('Alles goed.'), findsOneWidget);
  });

  testWidgets('toont de updatedAt-tijdstip van de (gecachete) briefing', (tester) async {
    final api = _FakeApiClient()..updatedAt = DateTime(2026, 7, 21, 9, 5);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Bijgewerkt om 09:05'), findsOneWidget);
  });

  testWidgets('reload-knop roept refreshBriefing aan en toont tijdens het laden een spinner', (tester) async {
    final completer = Completer<BriefingData>();
    final api = _FakeApiClient()..refreshCompleter = completer;

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.byIcon(Icons.refresh), findsOneWidget);

    await tester.tap(find.byIcon(Icons.refresh));
    await tester.pump();

    // Tijdens het laden: geen indrukbare refresh-knop meer, wel een spinner.
    expect(find.byIcon(Icons.refresh), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsWidgets);
    expect(api.refreshCalls, 1);

    completer.complete(BriefingData(sections: api.sections, updatedAt: api.updatedAt));
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.refresh), findsOneWidget);
  });

  testWidgets('reload-knop toont een foutmelding als refreshen mislukt', (tester) async {
    final api = _FakeApiClient()..refreshShouldThrow = true;

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byIcon(Icons.refresh));
    await tester.pumpAndSettle();

    expect(find.textContaining('Verversen mislukt'), findsOneWidget);
    expect(find.byIcon(Icons.refresh), findsOneWidget);
  });

  testWidgets('een item met imageUrl rendert een afbeelding i.p.v. platte tekst', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'weather-map',
          title: 'Weerkaart',
          text: '',
          items: [
            BriefingItem(text: 'Ochtend: 24 kn (ZW), regen', imageUrl: '/api/v1/briefing/weather-map/ochtend'),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Weerkaart'), findsOneWidget);
    expect(find.text('Ochtend: 24 kn (ZW), regen'), findsOneWidget);
    final image = tester.widget<Image>(find.byType(Image));
    final provider = image.image as NetworkImage;
    expect(provider.url, endsWith('/api/v1/briefing/weather-map/ochtend?v=${_fixedUpdatedAt.millisecondsSinceEpoch ~/ 1000}'));
  });

  testWidgets('een andere updatedAt na refresh geeft een andere cache-bust-query-param', (tester) async {
    final refreshedAt = _fixedUpdatedAt.add(const Duration(minutes: 5));
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'weather-map',
          title: 'Weerkaart',
          text: '',
          items: [
            BriefingItem(text: 'Ochtend: 24 kn (ZW), regen', imageUrl: '/api/v1/briefing/weather-map/morgen'),
          ],
        ),
      ]
      ..refreshCompleter = null;
    api.updatedAt = _fixedUpdatedAt;

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    final beforeUrl = (tester.widget<Image>(find.byType(Image)).image as NetworkImage).url;

    api.updatedAt = refreshedAt;
    await tester.tap(find.byIcon(Icons.refresh));
    await tester.pumpAndSettle();

    final afterUrl = (tester.widget<Image>(find.byType(Image)).image as NetworkImage).url;

    expect(beforeUrl, isNot(equals(afterUrl)));
    expect(afterUrl, endsWith('?v=${refreshedAt.millisecondsSinceEpoch ~/ 1000}'));
  });

  testWidgets('afspraak zonder reminder toont een werkende actieknop', (tester) async {
    final action = const BriefingAction(
      label: 'Reminder 1 uur van tevoren aanmaken',
      endpoint: '/api/v1/briefing/agenda-reminder',
      payload: {'summary': 'Standup', 'startAt': '2026-07-22T08:00:00Z'},
    );
    final api = _FakeApiClient()
      ..sections = [
        BriefingSection(
          key: 'agenda',
          title: 'Agenda (7 dagen)',
          text: 'Standup (geen reminder)',
          items: [
            BriefingItem(text: 'Ma 22 jul 08:00 — Standup (⚠️ nog geen reminder)', action: action),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Reminder 1 uur van tevoren aanmaken'), findsOneWidget);

    await tester.tap(find.text('Reminder 1 uur van tevoren aanmaken'));
    await tester.pump();

    expect(api.calledActions, [action]);
  });

  testWidgets('afspraak mét reminder toont geen actieknop', (tester) async {
    final api = _FakeApiClient()
      ..sections = [
        const BriefingSection(
          key: 'agenda',
          title: 'Agenda (7 dagen)',
          text: 'Tandarts (reminder staat)',
          items: [
            BriefingItem(text: 'Ma 22 jul 08:00 — Tandarts (✅ reminder staat)'),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.byType(TextButton), findsNothing);
  });

  testWidgets('meerregelige kite-tekst wordt per regel apart weergegeven', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'kite',
          title: 'Kiten',
          text: 'Morgen: 🟢 24kn NW\nOvermorgen: 🟡 12kn Z',
          items: [],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Morgen: 🟢 24kn NW'), findsOneWidget);
    expect(find.text('Overmorgen: 🟡 12kn Z'), findsOneWidget);
    expect(find.text('Morgen: 🟢 24kn NW\nOvermorgen: 🟡 12kn Z'), findsNothing);
  });

  testWidgets('toont een foutmelding als het ophalen van de briefing faalt', (tester) async {
    final api = _FailingApiClient();

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.textContaining('kapot'), findsOneWidget);
  });
}

class _FailingApiClient extends ApiClient {
  @override
  Future<BriefingData> getBriefing() async => throw Exception('kapot');
}
