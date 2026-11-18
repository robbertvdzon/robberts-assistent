import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/checkin_screen.dart';

class _FakeApiClient extends ApiClient {
  Map<String, Object?>? recorded;

  @override
  Future<CheckIn> recordCheckIn({
    int? hunger,
    int? energy,
    String note = '',
    String source = 'APP',
    String? mealId,
  }) async {
    recorded = {
      'hunger': hunger,
      'energy': energy,
      'note': note,
      'source': source,
    };
    return CheckIn(id: 'c1', at: DateTime(2026, 11, 17, 15, 30));
  }
}

void main() {
  testWidgets('twee tikken en Klaar slaat honger en energie op', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await tester.pumpWidget(
      MaterialApp(
        home: CheckInScreen(api: api, source: 'MELDING'),
      ),
    );

    // Zonder keuze valt er niets op te slaan.
    expect(
      tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
      isNull,
    );

    await tester.tap(find.text('Slap'));
    await tester.tap(find.text('1 moe'));
    await tester.pump();
    await tester.tap(find.text('Klaar'));
    await tester.pump();

    expect(api.recorded, {
      'hunger': 4,
      'energy': 1,
      'note': '',
      'source': 'MELDING',
    });
  });

  testWidgets(
    'alleen energie kiezen mag ook, en opnieuw tikken haalt de keuze weg',
    (tester) async {
      final api = _FakeApiClient();
      await tester.pumpWidget(MaterialApp(home: CheckInScreen(api: api)));

      await tester.tap(find.text('Trek'));
      await tester.pump();
      await tester.tap(find.text('Trek'));
      await tester.tap(find.text('5 fit'));
      await tester.pump();
      await tester.tap(find.text('Klaar'));
      await tester.pump();

      expect(api.recorded, {
        'hunger': null,
        'energy': 5,
        'note': '',
        'source': 'APP',
      });
    },
  );

  test('omschrijving van een check-in voor de tijdlijn', () {
    final at = DateTime(2026, 11, 17, 15, 30);
    expect(
      describeCheckIn(CheckIn(id: 'a', at: at, hunger: 1, energy: 4)),
      'trek · energie 4',
    );
    expect(describeCheckIn(CheckIn(id: 'b', at: at, hunger: 0)), 'geen honger');
    expect(describeCheckIn(CheckIn(id: 'c', at: at, energy: 2)), 'energie 2');
    expect(
      describeCheckIn(CheckIn(id: 'd', at: at, hunger: 2)),
      'beetje honger',
    );
    expect(describeCheckIn(CheckIn(id: 'e', at: at, hunger: 3)), 'honger');
    expect(
      describeCheckIn(CheckIn(id: 'f', at: at, hunger: 4)),
      'slap van de honger',
    );
  });

  testWidgets('beetje honger is een eigen keuze tussen trek en honger', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await tester.pumpWidget(MaterialApp(home: CheckInScreen(api: api)));

    expect(find.text('Trek'), findsOneWidget);
    expect(find.text('Honger'), findsNWidgets(2)); // het kopje en de keuze
    await tester.tap(find.text('Beetje honger'));
    await tester.pump();
    await tester.tap(find.text('Klaar'));
    await tester.pump();

    expect(api.recorded!['hunger'], 2);
  });
}
