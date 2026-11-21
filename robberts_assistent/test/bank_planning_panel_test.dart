import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/bank_planning_panel.dart';

Map<String, dynamic> emptyPlan(int year) => {
  'year': year,
  'monthlyIncome': null,
  'monthlyExpenses': null,
  'openingReserve': 0,
  'budgets': <String, dynamic>{},
  'items': <Map<String, dynamic>>[],
};

class PlanningApi extends ApiClient {
  Map<String, dynamic> saved = emptyPlan(2027);
  bool failSave = false;
  int saves = 0;
  Completer<Map<String, dynamic>>? pending;
  @override
  Future<Map<String, dynamic>> getJson(String path) async {
    if (pending != null) return pending!.future;
    return {
      ...saved,
      'year': int.parse(Uri.parse(path).queryParameters['year']!),
    };
  }

  @override
  Future<Map<String, dynamic>> putJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    saves++;
    if (failSave) throw StateError('offline');
    saved = Map<String, dynamic>.from(body);
    return saved;
  }
}

const categories = {
  'GROCERIES': 'Boodschappen',
  'EXTRA': 'Overig / extra',
  'INSURANCE': 'Verzekeringen',
  'INTERNAL': 'Interne overboeking',
  'REVIEW': 'Nog te beoordelen',
};
Widget app(PlanningApi api, {int year = 2027}) => MaterialApp(
  theme: ThemeData(colorSchemeSeed: const Color(0xff10767b)),
  home: Scaffold(
    body: SingleChildScrollView(
      child: BankPlanningPanel(
        api: api,
        initialYear: year,
        categories: categories,
      ),
    ),
  ),
);
Future<void> see(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
}

void main() {
  test(
    'amount parser preserves cents and rejects ambiguous or fractional cents',
    () {
      expect(bankPlanCents('1234,56'), 123456);
      expect(bankPlanCents(0.29), 29);
      expect(bankPlanCents('1.005'), isNull);
      expect(bankPlanCents('1.234,56'), isNull);
      expect(bankPlanCents('NaN'), isNull);
      expect(bankPlanCents('-4'), isNull);
      expect(bankPlanCents('10000000.01'), isNull);
      expect(bankPlanCents(''), isNull);
    },
  );
  test(
    'cashflow uses cents, handles early shortfall and never adds budgets as expense',
    () {
      final plan = emptyPlan(2027)
        ..addAll({
          'monthlyIncome': 0.29,
          'monthlyExpenses': 0.10,
          'openingReserve': 0,
          'budgets': {'EXTRA': 500},
          'items': [
            {'id': 'ski', 'month': 2, 'amount': 1.0, 'kind': 'EXPENSE'},
            {'id': 'pay', 'month': 5, 'amount': 0.5, 'kind': 'INCOME'},
          ],
        });
      final rows = bankPlanProjection(plan);
      expect(rows[0].reserve, 19);
      expect(rows[1].reserve, -62);
      expect(rows[4].reserve, 45);
      expect(rows.last.reserve, 178);
      expect(rows[1].expenses, 110);
    },
  );
  test('missing monthly basis cannot produce fake zero-based forecast', () {
    final rows = bankPlanProjection(
      emptyPlan(2027)
        ..['items'] = [
          {'id': 'ski', 'month': 2, 'amount': 3500, 'kind': 'EXPENSE'},
        ],
    );
    expect(
      rows.every(
        (r) => r.reserve == null && r.income == null && r.expenses == null,
      ),
      isTrue,
    );
    expect(rows[1].plannedExpenses, 350000);
  });
  testWidgets('empty plan asks for basis and shows no projected balance', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(app(PlanningApi()));
    await tester.pumpAndSettle();
    expect(find.text('Nog geen jaarbedragen ingevuld.'), findsOneWidget);
    expect(find.textContaining('Vul inkomsten én uitgaven'), findsOneWidget);
    expect(find.text('Verwacht verloop inclusief jaarbedragen'), findsNothing);
  });
  testWidgets(
    'saves amount and annual flag, survives a reload, then confirmed delete',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1000, 1800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = PlanningApi();
      await tester.pumpWidget(app(api));
      await tester.pumpAndSettle();
      await see(tester, find.text('Jaarbedrag toevoegen'));
      await tester.tap(find.text('Jaarbedrag toevoegen'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Waarvoor?'),
        'Wintersport',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Bedrag'),
        '3500,25',
      );
      await tester.tap(find.byType(SwitchListTile));
      await tester.tap(find.text('Opslaan'));
      await tester.pumpAndSettle();
      expect(api.saved['items'], hasLength(1));
      expect((api.saved['items'] as List).single['amount'], 3500.25);
      expect((api.saved['items'] as List).single['annual'], isTrue);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      await tester.pumpWidget(app(api));
      await tester.pumpAndSettle();
      expect(find.text('Wintersport'), findsOneWidget);
      await see(tester, find.byTooltip('Wintersport verwijderen'));
      await tester.tap(find.byTooltip('Wintersport verwijderen'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Annuleren'));
      await tester.pumpAndSettle();
      expect(api.saved['items'], hasLength(1));
      await tester.tap(find.byTooltip('Wintersport verwijderen'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Verwijderen'));
      await tester.pumpAndSettle();
      expect(api.saved['items'], isEmpty);
    },
  );
  testWidgets('failed save keeps input for retry and does not claim saved', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = PlanningApi()..failSave = true;
    await tester.pumpWidget(app(api));
    await tester.pumpAndSettle();
    await see(tester, find.text('Budget instellen'));
    await tester.tap(find.text('Budget instellen'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Maximaal per maand'),
      '450',
    );
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Niet opgeslagen.'), findsOneWidget);
    expect(find.text('€ 450,00 per maand'), findsOneWidget);
    expect(api.saved['budgets'], isEmpty);
    api.failSave = false;
    await see(tester, find.text('Opnieuw opslaan'));
    await tester.tap(find.text('Opnieuw opslaan'));
    await tester.pumpAndSettle();
    expect(api.saved['budgets']['EXTRA'], 450);
    expect(find.textContaining('Niet opgeslagen.'), findsNothing);
  });
  testWidgets(
    '320px layout with yearly item and full forecast has no overflow',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = PlanningApi()
        ..saved = (emptyPlan(2027)
          ..addAll({
            'monthlyIncome': 1000,
            'monthlyExpenses': 900,
            'openingReserve': 0,
            'items': [
              {
                'id': 'ski',
                'title': 'Wintersport met het hele gezin',
                'month': 2,
                'amount': 3500,
                'kind': 'EXPENSE',
                'certainty': 'ESTIMATE',
                'annual': true,
              },
            ],
          }));
      await tester.pumpWidget(app(api));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.textContaining('Vanaf februari'), findsOneWidget);
      await see(tester, find.text('Maandbasis instellen'));
      await tester.tap(find.text('Maandbasis instellen'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.tap(find.text('Annuleren'));
      await tester.pumpAndSettle();
      await see(
        tester,
        find.byTooltip('Wintersport met het hele gezin aanpassen'),
      );
      await tester.tap(
        find.byTooltip('Wintersport met het hele gezin aanpassen'),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.tap(find.text('Annuleren'));
      await tester.pumpAndSettle();
    },
  );
  testWidgets(
    'annual carry is identified as estimate and not written by viewing',
    (tester) async {
      final api = PlanningApi()
        ..saved = (emptyPlan(2027)..['inheritedFromYear'] = 2026);
      await tester.pumpWidget(app(api));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('overgenomen uit 2026 als schatting'),
        findsOneWidget,
      );
      expect(api.saves, 0);
    },
  );
  testWidgets('outdated load cannot overwrite a different year', (
    tester,
  ) async {
    final api = PlanningApi()..pending = Completer<Map<String, dynamic>>();
    final old = api.pending!;
    await tester.pumpWidget(app(api, year: 2027));
    await tester.pump();
    api.pending = null;
    await tester.pumpWidget(app(api, year: 2028));
    await tester.pumpAndSettle();
    old.complete(emptyPlan(2027)..['inheritedFromYear'] = 1999);
    await tester.pumpAndSettle();
    expect(find.text('2028'), findsOneWidget);
    expect(find.textContaining('overgenomen uit 1999'), findsNothing);
  });
}
