import 'dart:convert';
import 'dart:typed_data';

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
  List<String> photoIds = const [],
}) => Meal(
  id: 'm1',
  eatenAt: DateTime(2026, 11, 17, 18, 42),
  title: 'Avondeten',
  photoIds: photoIds,
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
  Future<Uint8List> fetchMealPhoto(String id) async => base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
  );

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

  @override
  Future<Meal> setMealEaten(String id, int percent) async => meal = Meal(
    id: meal.id,
    eatenAt: meal.eatenAt,
    title: meal.title,
    photoIds: meal.photoIds,
    note: meal.note,
    status: meal.status,
    kcal: meal.items.fold(0, (sum, i) => sum + i.kcal) * percent ~/ 100,
    items: meal.items,
    eatenPercent: percent,
  );

  ActivityDay? activity;

  @override
  Future<ActivityDay?> getActivityDay(DateTime date) async => activity;

  NapDay naps = const NapDay();
  ({double kg, DateTime? date})? savedWeight;

  @override
  Future<void> setWeight(double weightKg, {DateTime? date}) async {
    savedWeight = (kg: weightKg, date: date);
    activity = ActivityDay(weightKg: weightKg);
  }

  @override
  Future<WeekOverview> getWeekOverview() async => throw Exception('niet nodig');

  @override
  Future<NapDay> getNaps(DateTime date) async => naps;

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
  testWidgets('bestaande maaltijd opent foto schermvullend met zoom en terug', (
    tester,
  ) async {
    final api = _FakeApiClient()..meal = _meal(photoIds: ['zoom-photo']);
    await _pump(tester, FoodScreen(api: api));
    await tester.tap(find.text('Avondeten'));
    await tester.pumpAndSettle();
    expect(find.byType(MealDetailScreen), findsOneWidget);
    await tester.tap(find.byTooltip('Foto vergroten'));
    await tester.pumpAndSettle();
    expect(find.text('Foto van maaltijd'), findsOneWidget);
    final zoom = tester.widget<InteractiveViewer>(
      find.byType(InteractiveViewer),
    );
    expect(zoom.maxScale, greaterThan(1));
    expect(tester.widget<Image>(find.byType(Image).last).fit, BoxFit.contain);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byType(MealDetailScreen), findsOneWidget);
    expect(find.byTooltip('Foto vergroten'), findsOneWidget);
  });

  testWidgets('zonder foto heeft maaltijd geen vergrootactie', (tester) async {
    await _pump(tester, MealDetailScreen(api: _FakeApiClient(), mealId: 'm1'));
    expect(find.byTooltip('Foto vergroten'), findsNothing);
    expect(find.byType(InteractiveViewer), findsNothing);
  });
  testWidgets('dagoverzicht toont totalen en het eetmoment als voorstel', (
    tester,
  ) async {
    await _pump(tester, FoodScreen(api: _FakeApiClient()));

    expect(find.text('Vandaag'), findsOneWidget);
    expect(find.text('560'), findsNWidgets(2)); // dagtotaal en de maaltijd
    expect(find.text('Avondeten'), findsOneWidget);
    expect(find.text('Gekookte aardappelen, Rookworst'), findsOneWidget);
    expect(find.text('Nog niet bevestigd'), findsOneWidget);
    expect(
      find.text('Eiwit 16 g · vezels 4 g · groente 0 g · fruit 0 g'),
      findsOneWidget,
    );
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
      await tester.ensureVisible(find.byTooltip('Versturen'));
      await tester.pump();
      await tester.tap(find.byTooltip('Versturen'));
      await tester.pump();
      await tester.pump();

      expect(api.correction, 'het was een hele vegaworst');
      expect(find.textContaining('Ik bekijk wat je eet'), findsOneWidget);
      await tester.pumpWidget(const SizedBox()); // ruimt de poll-timer op
    },
  );

  testWidgets('bevestigd eetmoment toont zijn rol als referentie of dubbel', (
    tester,
  ) async {
    final api = _FakeApiClient();
    final base = _meal(status: 'BEVESTIGD');
    Meal withRole(String role, String description) => Meal(
      id: base.id,
      eatenAt: base.eatenAt,
      title: base.title,
      photoIds: base.photoIds,
      note: base.note,
      status: base.status,
      kcal: base.kcal,
      items: base.items,
      referenceRole: role,
      referenceDescription: description,
    );
    api.meal = withRole('REFERENTIE', 'klein blauw kopje, tot de rand gevuld');
    await _pump(tester, MealDetailScreen(api: api, mealId: 'm1'));
    expect(
      find.text('Referentiefoto: klein blauw kopje, tot de rand gevuld'),
      findsOneWidget,
    );

    expect(withRole('DUBBEL', 'kopje').referenceLabel, startsWith('Dubbel'));
    expect(withRole('GEEN', '').referenceLabel, isEmpty);
    expect(base.referenceLabel, isEmpty);
  });

  testWidgets('deel opgegeten telt minder mee, de portie blijft staan', (
    tester,
  ) async {
    final api = _FakeApiClient()..meal = _meal(status: 'BEVESTIGD');
    await _pump(tester, MealDetailScreen(api: api, mealId: 'm1'));
    expect(find.text('Samen'), findsOneWidget);
    expect(find.text('560 kcal'), findsOneWidget);

    await tester.ensureVisible(find.byKey(const ValueKey('opgegeten-75')));
    await tester.tap(find.byKey(const ValueKey('opgegeten-75')));
    await tester.pumpAndSettle();

    expect(api.meal.eatenPercent, 75);
    expect(find.text('75% opgegeten'), findsOneWidget);
    expect(find.text('420 kcal'), findsOneWidget);
    // De onderdelen zelf blijven de hele, gewogen portie.
    expect(api.meal.items.first.grams, _potatoes.grams);
  });

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
          hunger: 4,
          energy: 2,
          note: 'na het fietsen',
        ),
      );
    await _pump(tester, FoodScreen(api: api));

    expect(find.text('15:30'), findsOneWidget);
    expect(
      find.text('slap van de honger · energie 2 — na het fietsen'),
      findsOneWidget,
    );
  });

  testWidgets(
    'tijdlijn zet slaap, wandeling, dutje, check-in en eten op tijd',
    (tester) async {
      final api = _FakeApiClient()
        ..activity = ActivityDay(
          sleepMinutes: 450,
          sleepStart: DateTime(2026, 11, 16, 23, 40),
          sleepEnd: DateTime(2026, 11, 17, 7, 10),
          workouts: [
            ActivityWorkout(
              type: 'WALKING',
              minutes: 45,
              kcal: 210,
              start: DateTime(2026, 11, 17, 19, 30),
              end: DateTime(2026, 11, 17, 20, 15),
            ),
          ],
        )
        ..naps = NapDay(
          naps: [
            Nap(
              id: 'n1',
              start: DateTime(2026, 11, 17, 13, 10),
              end: DateTime(2026, 11, 17, 13, 40),
              endEstimated: true,
            ),
          ],
        )
        ..checkIns.add(
          CheckIn(id: 'c1', at: DateTime(2026, 11, 17, 15, 30), hunger: 1),
        );
      // Hoog venster: de lijst bouwt alleen wat in beeld is.
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await _pump(tester, FoodScreen(api: api));

      final texts = [
        'Geslapen tot 07:10 · 7 u 30',
        'Dutje 13:10–13:40 · 30 min (eind geschat)',
        'trek',
        'Avondeten',
        'Wandelen tot 20:15 · 45 min · 210 kcal',
      ];
      for (final text in texts) {
        expect(find.text(text), findsOneWidget, reason: text);
      }
      final tops = [
        for (final text in texts) tester.getTopLeft(find.text(text)).dy,
      ];
      expect(tops, [...tops]..sort(), reason: 'volgorde van tijd');
    },
  );

  testWidgets('een lopend dutje staat bovenaan en opent het afmeldscherm', (
    tester,
  ) async {
    final open = Nap(id: 'n1', start: DateTime(2026, 11, 16, 23, 50));
    final api = _FakeApiClient()..naps = NapDay(open: open);
    await _pump(tester, FoodScreen(api: api));

    await tester.tap(find.text('Dutje sinds 23:50, loopt nog'));
    await tester.pumpAndSettle();
    expect(find.text('Ik ben wakker'), findsOneWidget);
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

    expect(find.text('Verbruikt tot nu toe (Garmin)'), findsOneWidget);
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

    expect(find.text('Verbruikt tot nu toe (Garmin)'), findsNothing);
    await tester.tap(find.text('Garmin koppelen'));
    await tester.pumpAndSettle();

    expect(asked, 1);
    expect(find.text('Garmin koppelen'), findsNothing);
    expect(find.text('2100'), findsOneWidget);
  });

  testWidgets('gewicht invullen vanaf het dagboek en terugzien in de dag', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await _pump(tester, FoodScreen(api: api));

    await tester.tap(find.text('Gewicht'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'veel');
    await tester.tap(find.text('Opslaan'));
    await tester.pump();
    expect(find.textContaining('bijvoorbeeld 81,4'), findsOneWidget);
    expect(api.savedWeight, isNull);

    await tester.enterText(find.byType(TextField), '81,4');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(api.savedWeight?.kg, 81.4);
    expect(api.savedWeight?.date, DateUtils.dateOnly(DateTime.now()));
    expect(find.text('Gewicht 81,4 kg'), findsOneWidget);
  });

  testWidgets('de vier acties openen foto, gevoel en dutje', (tester) async {
    await _pump(tester, FoodScreen(api: _FakeApiClient()));

    await tester.tap(find.text('Gevoel'));
    await tester.pumpAndSettle();
    expect(find.text('Hoe voel je je?'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Dutje'));
    await tester.pumpAndSettle();
    expect(find.text('Start dutje'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Eten'));
    await tester.pumpAndSettle();
    expect(find.byType(MealCaptureScreen), findsOneWidget);
  });
}
