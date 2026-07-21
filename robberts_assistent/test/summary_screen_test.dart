import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/summary_screen.dart';

class _FakeApiClient extends ApiClient {
  List<BriefingSection> sections = [];
  final calledActions = <BriefingAction>[];

  @override
  Future<List<BriefingSection>> getBriefing() async => sections;

  @override
  Future<void> runBriefingAction(BriefingAction action) async {
    calledActions.add(action);
  }
}

void main() {
  testWidgets('toont de titel en tekst van elke briefingsectie', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(key: 'kite', title: 'Kiten', text: 'Morgen: 🟢 24kn', items: []),
        BriefingSection(key: 'beach', title: 'Strandfietsen', text: 'Morgen: 🟢 (10 kn, droog, laagwater om 08:00)', items: []),
        BriefingSection(key: 'moestuin', title: 'Moestuin', text: 'Alles goed.', items: []),
      ];

    await tester.pumpWidget(MaterialApp(home: SummaryScreen(api: api)));
    await tester.pump();

    expect(find.text('Kiten'), findsOneWidget);
    expect(find.text('Morgen: 🟢 24kn'), findsOneWidget);
    expect(find.text('Strandfietsen'), findsOneWidget);
    expect(find.text('Morgen: 🟢 (10 kn, droog, laagwater om 08:00)'), findsOneWidget);
    expect(find.text('Moestuin'), findsOneWidget);
    expect(find.text('Alles goed.'), findsOneWidget);
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

    await tester.pumpWidget(MaterialApp(home: SummaryScreen(api: api)));
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

    await tester.pumpWidget(MaterialApp(home: SummaryScreen(api: api)));
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

    await tester.pumpWidget(MaterialApp(home: SummaryScreen(api: api)));
    await tester.pump();

    expect(find.text('Morgen: 🟢 24kn NW'), findsOneWidget);
    expect(find.text('Overmorgen: 🟡 12kn Z'), findsOneWidget);
    expect(find.text('Morgen: 🟢 24kn NW\nOvermorgen: 🟡 12kn Z'), findsNothing);
  });

  testWidgets('toont een foutmelding als het ophalen van de briefing faalt', (tester) async {
    final api = _FailingApiClient();

    await tester.pumpWidget(MaterialApp(home: SummaryScreen(api: api)));
    await tester.pump();

    expect(find.textContaining('kapot'), findsOneWidget);
  });
}

class _FailingApiClient extends ApiClient {
  @override
  Future<List<BriefingSection>> getBriefing() async => throw Exception('kapot');
}
