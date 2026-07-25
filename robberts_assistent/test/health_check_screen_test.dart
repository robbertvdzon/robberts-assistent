import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/health_check_screen.dart';

final _fixedUpdatedAt = DateTime(2026, 7, 21, 17, 30);

class _FakeApiClient extends ApiClient {
  List<BriefingSection> sections = [];
  DateTime updatedAt = _fixedUpdatedAt;
  int refreshCalls = 0;
  bool refreshShouldThrow = false;
  // Zonder completer lost refreshHealthCheck meteen op — voor de spinner-test moet de aanroeper
  // zelf kunnen bepalen wanneer de refresh 'klaar' is, zodat de laad-state observeerbaar is.
  Completer<BriefingData>? refreshCompleter;

  @override
  Future<BriefingData> getHealthCheck() async => BriefingData(sections: sections, updatedAt: updatedAt);

  @override
  Future<BriefingData> refreshHealthCheck() {
    refreshCalls++;
    if (refreshShouldThrow) return Future.error(Exception('ververs-fout'));
    final completer = refreshCompleter;
    if (completer != null) return completer.future;
    return Future.value(BriefingData(sections: sections, updatedAt: updatedAt));
  }
}

class _FailingApiClient extends ApiClient {
  @override
  Future<BriefingData> getHealthCheck() async => throw Exception('kapot');
}

Widget _wrap(ApiClient api) => MaterialApp(home: Scaffold(body: HealthCheckScreen(api: api)));

void main() {
  testWidgets('toont per onderdeel een kop met de ruwe statusregel(s) in selecteerbare tekst', (tester) async {
    final api = _FakeApiClient()
      ..sections = const [
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
            BriefingItem(text: 'geen lopende of error-stories.', heading: 'Software Factory'),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    for (final heading in ['Zonnepanelen', 'Backups', 'OpenShift', 'Robotmaaier', 'Software Factory']) {
      expect(find.text(heading), findsOneWidget);
    }
    expect(find.textContaining('huidig vermogen=100 W'), findsOneWidget);
    expect(find.textContaining('gezond=true'), findsOneWidget);
    expect(find.textContaining('AI-samenvatting die niet getoond moet worden'), findsNothing);

    expect(find.byType(SelectableText), findsWidgets);
  });

  testWidgets('toont de updatedAt-tijdstip van de (gecachete) systeemstatus', (tester) async {
    final api = _FakeApiClient()..updatedAt = DateTime(2026, 7, 21, 9, 5);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Bijgewerkt om 09:05'), findsOneWidget);
  });

  testWidgets('reload-knop roept refreshHealthCheck aan en toont tijdens het laden een spinner', (tester) async {
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

  testWidgets('toont een melding als er geen systeemstatus-sectie is', (tester) async {
    final api = _FakeApiClient()..sections = const [];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Geen systeemstatus beschikbaar.'), findsOneWidget);
  });

  testWidgets('toont een foutmelding als het ophalen van de systeemstatus faalt', (tester) async {
    await tester.pumpWidget(_wrap(_FailingApiClient()));
    await tester.pump();

    expect(find.textContaining('kapot'), findsOneWidget);
  });
}
