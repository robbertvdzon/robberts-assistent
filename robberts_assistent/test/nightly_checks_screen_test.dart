import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/nightly_checks_screen.dart';

class _FakeApiClient extends ApiClient {
  List<NightlyCheck> checks = [];
  String? ranCheckId;

  @override
  Future<List<NightlyCheck>> listNightlyChecks() async => checks;

  @override
  Future<CheckRun> runNightlyCheck(String id) async {
    ranCheckId = id;
    return CheckRun(ranAt: DateTime(2026, 7, 20, 7), ok: true, summary: 'net gedraaid');
  }
}

void main() {
  testWidgets('toont naam, omschrijving en laatste resultaat van een check', (tester) async {
    final api = _FakeApiClient()
      ..checks = [
        NightlyCheck(
          id: 'openshift-health',
          name: 'OpenShift-gezondheid',
          description: 'Clustergezondheid en platform-updates.',
          cronSchedule: '0 0 7 * * *',
          lastRun: CheckRun(ranAt: DateTime(2026, 7, 20, 7), ok: true, summary: 'Cluster gezond'),
        ),
      ];

    await tester.pumpWidget(MaterialApp(home: NightlyChecksScreen(api: api)));
    await tester.pump();

    expect(find.text('OpenShift-gezondheid'), findsOneWidget);
    expect(find.textContaining('Cluster gezond'), findsOneWidget);
  });

  testWidgets('toont een lege-staat-melding zonder checks', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: NightlyChecksScreen(api: api)));
    await tester.pump();

    expect(find.textContaining('Nog geen nightly checks'), findsOneWidget);
  });

  testWidgets('op de speel-knop tikken draait de check en werkt de status bij', (tester) async {
    final api = _FakeApiClient()
      ..checks = [
        NightlyCheck(
          id: 'openshift-health',
          name: 'OpenShift-gezondheid',
          description: 'Clustergezondheid en platform-updates.',
          cronSchedule: '0 0 7 * * *',
        ),
      ];

    await tester.pumpWidget(MaterialApp(home: NightlyChecksScreen(api: api)));
    await tester.pump();
    expect(find.text('Nog niet gedraaid.'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.play_arrow));
    await tester.pump();
    await tester.pump();

    expect(api.ranCheckId, 'openshift-health');
    expect(find.textContaining('net gedraaid'), findsOneWidget);
  });
}
