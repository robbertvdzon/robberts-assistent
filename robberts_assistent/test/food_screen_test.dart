import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/food_screen.dart';
import 'package:robberts_assistent/health_sync.dart';

const _potatoes = MealItem(
  name: 'Gekookte aardappelen',
  grams: 200,
  kcal: 170,
  fiberG: 3.6,
);
const _sausage = MealItem(
  name: 'Rookworst',
  grams: 125,
  kcal: 390,
  proteinG: 16,
  uncertain: true,
);

Meal _meal({
  String status = 'VOORSTEL',
  List<MealItem> items = const [_potatoes, _sausage],
  String learned = '',
}) => Meal(
  id: 'm1',
  eatenAt: DateTime(2026, 11, 17, 18, 42),
  title: 'Avondeten',
  photoIds: const [],
  note: '',
  status: status,
  kcal: items.fold(0, (sum, i) => sum + i.kcal),
  items: items,
  learned: learned,
);

class _FakeApiClient extends ApiClient {
  Meal meal = _meal();
  List<MealItem>? confirmedItems;
  bool confirmCalled = false;
  String? correction;
  String? createdNote;

  @override
  Future<NutritionDay> getNutritionDay(DateTime date) async => NutritionDay(
    totals: NutritionTotals(
      kcal: meal.kcal,
      proteinG: 16,
      fiberG: 3.6,
      vegetableG: 0,
    ),
    meals: [meal],
  );

  @override
  Future<Meal> getMeal(String id) async => meal;

  ActivityDay? activity;

  @override
  Future<ActivityDay?> getActivityDay(DateTime date) async => activity;

  final checkIns = <CheckIn>[];
  Map<String, Object?>? recorded;

  @override
  Future<List<CheckIn>> getCheckIns(DateTime date) async => checkIns;

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
      'source': source,
      'mealId': mealId,
    };
    return CheckIn(id: 'c1', at: DateTime(2026, 11, 17, 15, 30));
  }

  @override
  Future<Meal> confirmMeal(String id, {List<MealItem>? items}) async {
    confirmCalled = true;
    confirmedItems = items;
    meal = _meal(
      status: items == null ? 'BEVESTIGD' : 'VERBETERD',
      items: items ?? meal.items,
    );
    return meal;
  }

  @override
  Future<Meal> correctMeal(String id, String text) async {
    correction = text;
    meal = _meal(status: 'ANALYSEREN', items: const []);
    return meal;
  }

  @override
  Future<Meal> createMeal({
    List<AssistantAttachment> photos = const [],
    String note = '',
    DateTime? eatenAt,
  }) async {
    createdNote = note;
    return meal;
  }
}

Future<void> _pump(WidgetTester tester, Widget screen) async {
  await tester.pumpWidget(MaterialApp(home: screen));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('dagoverzicht toont totalen en het eetmoment als voorstel', (
    tester,
  ) async {
    await _pump(tester, FoodScreen(api: _FakeApiClient()));

    expect(find.text('Vandaag'), findsOneWidget);
    expect(find.text('560'), findsNWidgets(2)); // dagtotaal en de maaltijd
    expect(find.text('Avondeten'), findsOneWidget);
    expect(find.text('Gekookte aardappelen, Rookworst'), findsOneWidget);
    expect(find.text('Nog niet bevestigd'), findsOneWidget);
    expect(find.text('richtwaarde 30–40'), findsOneWidget);
  });

  testWidgets('Klopt neemt het voorstel ongewijzigd over', (tester) async {
    final api = _FakeApiClient();
    await _pump(tester, MealDetailScreen(api: api, mealId: 'm1'));

    expect(find.text('Volgens mij is dit:'), findsOneWidget);
    expect(find.text('twijfel'), findsOneWidget);
    expect(find.text('560 kcal'), findsOneWidget);

    await tester.tap(find.text('Klopt'));
    await tester.pump();
    await tester.pump();

    expect(api.confirmCalled, isTrue);
    expect(api.confirmedItems, isNull);
    expect(find.text('Dit staat in je dagboek:'), findsOneWidget);
    expect(find.text('Klopt'), findsNothing);
    // De uitgestelde controle op "Onthouden" loopt nog; laat die aflopen.
    await tester.pump(const Duration(seconds: 21));
  });

  testWidgets(
    'onderdeel wegen schaalt de calorieën en slaat op als verbeterd',
    (tester) async {
      final api = _FakeApiClient();
      await _pump(tester, MealDetailScreen(api: api, mealId: 'm1'));

      await tester.tap(find.text('Rookworst'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Gram'), '250');
      await tester.pump();
      await tester.tap(find.text('Gewogen'));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Opslaan').last);
      await tester.pumpAndSettle();

      // 125 g = 390 kcal, dus 250 g = 780 kcal; samen met de aardappelen 950.
      expect(find.text('780'), findsOneWidget);
      expect(find.text('gewogen'), findsOneWidget);
      expect(find.text('950 kcal'), findsOneWidget);

      await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
      await tester.pump();
      await tester.pump();

      final saved = api.confirmedItems!;
      expect(saved.length, 2);
      expect(saved[1].grams, 250);
      expect(saved[1].kcal, 780);
      expect(saved[1].proteinG, 32);
      expect(saved[1].basis, 'GEWOGEN');
      await tester.pump(const Duration(seconds: 21));
    },
  );

  testWidgets(
    'verbetering in woorden gaat naar de backend en toont dat hij bezig is',
    (tester) async {
      final api = _FakeApiClient();
      await _pump(
        tester,
        MealDetailScreen(
          api: api,
          mealId: 'm1',
          pollInterval: const Duration(days: 1),
        ),
      );

      await tester.enterText(
        find.byType(TextField),
        'het was een hele vegaworst',
      );
      await tester.tap(find.byTooltip('Versturen'));
      await tester.pump();
      await tester.pump();

      expect(api.correction, 'het was een hele vegaworst');
      expect(find.textContaining('Ik bekijk wat je eet'), findsOneWidget);
      await tester.pumpWidget(const SizedBox()); // ruimt de poll-timer op
    },
  );

  testWidgets('vastleggen met alleen tekst stuurt de notitie in', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await _pump(tester, MealCaptureScreen(api: api, openCameraOnStart: false));

    expect(
      tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
      isNull,
    );
    await tester.enterText(find.byType(TextField), 'een appel en koffie');
    await tester.pump();
    await tester.ensureVisible(find.text('Klaar'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Klaar'));
    await tester.pump();

    expect(api.createdNote, 'een appel en koffie');
  });

  testWidgets('gevoel voor het eten gaat als check-in mee met het eetmoment', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await _pump(tester, MealCaptureScreen(api: api, openCameraOnStart: false));

    await tester.enterText(find.byType(TextField), 'een appel');
    await tester.ensureVisible(find.text('4'));
    await tester.tap(find.text('Trek'));
    await tester.tap(find.text('4'));
    await tester.pump();
    await tester.ensureVisible(find.text('Klaar'));
    await tester.tap(find.text('Klaar'));
    await tester.pump();

    expect(api.recorded, {
      'hunger': 1,
      'energy': 4,
      'source': 'FOTO',
      'mealId': 'm1',
    });
  });

  testWidgets('dagoverzicht toont de check-ins van die dag', (tester) async {
    final api = _FakeApiClient()
      ..checkIns.add(
        CheckIn(
          id: 'c1',
          at: DateTime(2026, 11, 17, 15, 30),
          hunger: 3,
          energy: 2,
          note: 'na het fietsen',
        ),
      );
    await _pump(tester, FoodScreen(api: api));

    expect(find.text('Hoe je je voelde'), findsOneWidget);
    expect(find.text('15:30'), findsOneWidget);
    expect(
      find.text('slap van de honger · energie 2 — na het fietsen'),
      findsOneWidget,
    );
  });

  testWidgets('toont verbruik, stappen, training en slaap van Garmin', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..activity = const ActivityDay(
        steps: 9420,
        totalKcal: 2590,
        sleepMinutes: 400,
        workouts: [ActivityWorkout(type: 'BIKING', minutes: 65, kcal: 654)],
      );
    await _pump(
      tester,
      FoodScreen(api: api, healthSync: (_) async => HealthSyncStatus.synced),
    );

    expect(find.text('Verbruikt (Garmin)'), findsOneWidget);
    expect(find.text('2590'), findsOneWidget);
    expect(
      find.text('9.420 stappen · 65 min fietsen · 6 u 40 geslapen'),
      findsOneWidget,
    );
    expect(find.text('Garmin koppelen'), findsNothing);
  });

  testWidgets('zonder toestemming staat er een knop om Garmin te koppelen', (
    tester,
  ) async {
    final api = _FakeApiClient();
    var asked = 0;
    await _pump(
      tester,
      FoodScreen(
        api: api,
        healthSync: (_) async => HealthSyncStatus.needsPermission,
        healthAccess: (_) async {
          asked++;
          api.activity = const ActivityDay(totalKcal: 2100);
          return HealthSyncStatus.synced;
        },
      ),
    );

    expect(find.text('Verbruikt (Garmin)'), findsNothing);
    await tester.tap(find.text('Garmin koppelen'));
    await tester.pumpAndSettle();

    expect(asked, 1);
    expect(find.text('Garmin koppelen'), findsNothing);
    expect(find.text('2100'), findsOneWidget);
  });
}
