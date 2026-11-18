import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/food_week_screen.dart';

class _FakeApiClient extends ApiClient {
  double? savedWeight;
  List<Insight> insights = const [];
  final weights = <WeightPoint>[
    WeightPoint(date: DateTime(2026, 11, 2), weightKg: 82.0),
  ];

  @override
  Future<WeekOverview> getWeekOverview() async => WeekOverview(
    days: [
      OverviewDay(date: DateTime(2026, 11, 16), eatenKcal: 0),
      OverviewDay(
        date: DateTime(2026, 11, 17),
        eatenKcal: 1840,
        burnedKcal: 2590,
        steps: 9420,
        sleepMinutes: 400,
        mealCount: 3,
      ),
    ],
    avgEatenKcal: 1840,
    avgBurnedKcal: 2590,
    avgBalanceKcal: -750,
    avgProteinG: 78,
    avgFiberG: 24,
    avgVegetableG: 210,
    basisShare: const {'GEWOGEN': 40, 'GESCHAT': 60},
    unchangedShare: 66,
    weights: weights,
  );

  @override
  Future<void> setWeight(double weightKg) async {
    savedWeight = weightKg;
    weights.add(WeightPoint(date: DateTime(2026, 11, 18), weightKg: weightKg));
  }

  @override
  Future<List<Insight>> getInsights() async => insights;
}

void main() {
  testWidgets('weekoverzicht toont dagen, gemiddelden en de balans', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: FoodWeekScreen(api: _FakeApiClient())),
    );
    await tester.pumpAndSettle();

    expect(find.text('ma 16'), findsOneWidget);
    expect(find.text('di 17'), findsOneWidget);
    expect(find.text('9420 stappen · 6 u 40 slaap'), findsOneWidget);
    expect(
      find.text('–'),
      findsNWidgets(2),
    ); // maandag: niets gegeten, geen verbruik bekend
    expect(
      find.text(
        'Op afgeronde dagen at je gemiddeld 750 kcal minder dan je verbruikte.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('Eiwit 78 g · vezels 24 g · groente 210 g'),
      findsOneWidget,
    );
    expect(find.text('40% gewogen'), findsOneWidget);
    expect(find.text('2-11: 82,0 kg'), findsOneWidget);
  });

  testWidgets('gewicht met komma wordt opgeslagen en verschijnt in de lijst', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await tester.pumpWidget(MaterialApp(home: FoodWeekScreen(api: api)));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byType(TextField));
    await tester.enterText(find.byType(TextField), '81,4');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(api.savedWeight, 81.4);
    expect(find.text('18-11: 81,4 kg'), findsOneWidget);
  });

  testWidgets('onzin als gewicht wordt niet opgeslagen', (tester) async {
    final api = _FakeApiClient();
    await tester.pumpWidget(MaterialApp(home: FoodWeekScreen(api: api)));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byType(TextField));
    await tester.enterText(find.byType(TextField), '8');
    await tester.tap(find.text('Opslaan'));
    await tester.pump();

    expect(api.savedWeight, isNull);
    expect(find.textContaining('Vul je gewicht in'), findsOneWidget);
  });

  testWidgets('verbanden tonen de tekst en of er genoeg waarnemingen zijn', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..insights = const [
        Insight(
          title: 'Slaap en energie',
          text: 'Na 7 uur slaap of meer: gemiddeld 3,9.',
          clear: true,
        ),
        Insight(
          title: 'Lunch en energie in de middag',
          text: 'Na een lunch boven 700 kcal: 2,8.',
          clear: false,
        ),
      ];
    await tester.pumpWidget(MaterialApp(home: InsightsScreen(api: api)));
    await tester.pumpAndSettle();

    expect(find.text('Slaap en energie'), findsOneWidget);
    expect(find.text('genoeg waarnemingen'), findsOneWidget);
    expect(find.text('nog te weinig waarnemingen'), findsOneWidget);
  });

  testWidgets('zonder verbanden staat er dat er nog te weinig gegevens zijn', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: InsightsScreen(api: _FakeApiClient())),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('Nog niet genoeg gegevens'), findsOneWidget);
  });
}
