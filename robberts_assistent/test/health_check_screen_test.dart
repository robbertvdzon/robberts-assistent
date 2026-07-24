import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/health_check_screen.dart';

final _fixedUpdatedAt = DateTime(2026, 7, 21, 17, 30);

class _FakeApiClient extends ApiClient {
  List<BriefingSection> sections = [];
  DateTime updatedAt = _fixedUpdatedAt;

  @override
  Future<BriefingData> getBriefing() async => BriefingData(sections: sections, updatedAt: updatedAt);
}

class _FailingApiClient extends ApiClient {
  @override
  Future<BriefingData> getBriefing() async => throw Exception('kapot');
}

Widget _wrap(ApiClient api) => MaterialApp(home: Scaffold(body: HealthCheckScreen(api: api)));

void main() {
  testWidgets('toont per onderdeel een kop met de ruwe statusregel(s) in selecteerbare tekst', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(key: 'kite', title: 'Kiten', text: 'Morgen: 🟢 24kn', items: []),
        BriefingSection(
          key: 'system-status',
          title: 'Systeemstatus',
          text: 'AI-samenvatting die niet getoond moet worden.',
          items: [
            BriefingItem(text: 'huidig vermogen=100 W, gisteren opgewekt=12 kWh.', heading: 'Zonnepanelen'),
            BriefingItem(text: '(nog geen koppeling, placeholder) geen fouten gemeld.', heading: 'Backups'),
            BriefingItem(
              text: 'gezond=true, versie=4.16.3, beschikbare update=geen, gedegradeerde operators=geen.',
              heading: 'OpenShift',
            ),
            BriefingItem(
              text: 'Robotmaaier Maaier: activiteit=MAAIT, status=OK, errorCode=0, verbonden=true.',
              heading: 'Robotmaaier',
            ),
            BriefingItem(text: 'geen stories gevonden.', heading: 'Software Factory'),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    // Alleen de systeemstatus-sectie, niet de andere briefingsecties.
    expect(find.text('Kiten'), findsNothing);

    for (final heading in ['Zonnepanelen', 'Backups', 'OpenShift', 'Robotmaaier', 'Software Factory']) {
      expect(find.text(heading), findsOneWidget);
    }
    expect(find.textContaining('huidig vermogen=100 W'), findsOneWidget);
    expect(find.textContaining('gezond=true'), findsOneWidget);
    expect(find.textContaining('AI-samenvatting die niet getoond moet worden'), findsNothing);

    expect(find.byType(SelectableText), findsWidgets);
  });

  testWidgets('toont een melding als er geen systeemstatus-sectie is', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(key: 'kite', title: 'Kiten', text: 'Morgen: 🟢 24kn', items: []),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Geen systeemstatus beschikbaar.'), findsOneWidget);
  });

  testWidgets('toont een foutmelding als het ophalen van de briefing faalt', (tester) async {
    await tester.pumpWidget(_wrap(_FailingApiClient()));
    await tester.pump();

    expect(find.textContaining('kapot'), findsOneWidget);
  });
}
