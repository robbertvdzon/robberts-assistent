import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/couplings_screen.dart';

class _FakeApiClient extends ApiClient {
  @override
  Future<List<Coupling>> listCouplings() async => const [
    Coupling(
      id: 'google',
      name: 'Google',
      description: 'Agenda en documenten',
      configured: true,
      mode: 'echt',
      test: CouplingTest(ok: true, detail: 'Bereikbaar', durationMs: 12),
    ),
    Coupling(
      id: 'telegram',
      name: 'Telegram',
      description: 'Meldingen',
      configured: false,
      mode: 'fallback',
      test: CouplingTest(ok: false, detail: 'Niet bereikbaar', durationMs: 3),
    ),
  ];
}

void main() {
  testWidgets('koppelingstatus is naast kleur ook als woord zichtbaar', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: CouplingsScreen(api: _FakeApiClient())),
    );
    await tester.pump();

    expect(find.text('goed · test ok'), findsOneWidget);
    expect(find.text('kritiek · test fout'), findsOneWidget);
    expect(find.text('goed · echt'), findsOneWidget);
    expect(find.text('let op · fallback'), findsOneWidget);
    expect(find.text('neutraal · niet geconfigureerd'), findsOneWidget);
  });
}
