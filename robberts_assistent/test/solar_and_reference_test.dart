import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/health_check_screen.dart';
import 'package:robberts_assistent/reference_meals_screen.dart';
import 'package:robberts_assistent/solar_screen.dart';

SolarOverview _solar({String verdict = 'GOED', int days = 40}) => SolarOverview(
  days: [
    for (var i = 0; i < days; i++)
      SolarDay(
        // Geen Duration optellen: over de wisseling naar wintertijd schuift de dag dan een uur.
        date: DateTime(2026, 10, 12 + i),
        yieldKwh: 2.0 + i % 3,
        expectedKwh: i < 14 ? null : 2.5,
        sunshineHours: 4.5,
        complete: i < days - 1,
      ),
  ],
  verdict: verdict,
  verdictText:
      'Afgelopen 7 dagen 16,0 kWh opgewekt; bij dit weer verwacht 17,5 kWh (91%).',
  weekPercent: 91,
  currentPowerWatt: 420,
);

Meal _meal(String id, String role, {String description = ''}) => Meal(
  id: id,
  eatenAt: DateTime(2026, 11, 20, 8, 30),
  title: 'Ontbijt $id',
  photoIds: const ['foto'],
  note: '',
  status: 'BEVESTIGD',
  kcal: 400,
  items: const [MealItem(name: 'Kwark', grams: 340, kcal: 200)],
  referenceRole: role,
  referenceDescription: description,
  referencePhotoId: role == 'REFERENTIE' ? 'foto' : '',
);

class _Api extends ApiClient {
  SolarOverview? solar;
  List<BriefingItem> items = const [];
  List<Meal> meals = [];
  final saved = <String>[];

  @override
  Future<SolarOverview> getSolar() async => solar ?? (throw Exception('geen'));

  @override
  Future<BriefingData> getHealthCheck() async => BriefingData(
    sections: [
      BriefingSection(
        key: 'system-status',
        title: 'Systeemstatus',
        text: '',
        items: items,
      ),
    ],
    updatedAt: DateTime(2026, 11, 20, 17),
  );

  @override
  Future<List<Meal>> getReferenceMeals() async => meals;

  @override
  Future<Meal> setMealReference(
    String id, {
    required bool use,
    String? description,
  }) async {
    saved.add('$id:$use:$description');
    final updated = _meal(
      id,
      use ? 'REFERENTIE' : 'GEEN',
      description: description ?? '',
    );
    meals = [for (final m in meals) m.id == id ? updated : m];
    return updated;
  }

  @override
  Future<Uint8List> fetchMealPhoto(String photoId) async =>
      throw Exception('geen foto');
}

Future<void> _pump(WidgetTester tester, Widget screen) async {
  tester.view.physicalSize = const Size(1080, 4200);
  tester.view.devicePixelRatio = 2.5;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(MaterialApp(home: screen));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'health check zegt bovenaan dat alles in orde is en toont een oordeel per onderdeel',
    (tester) async {
      final api = _Api()
        ..items = const [
          BriefingItem(
            text: 'gisteren 2 kWh',
            heading: 'Zonnepanelen',
            status: BriefingStatus.goed,
          ),
          BriefingItem(
            text: 'gezond',
            heading: 'OpenShift',
            status: BriefingStatus.goed,
          ),
        ];
      await _pump(tester, HealthCheckScreen(api: api));

      expect(find.text('Alles in orde'), findsOneWidget);
      expect(find.text('goed'), findsNWidgets(2));
      // Zonder opbrengstgegevens blijft de kaart tekst, zonder grafiek.
      expect(find.byKey(const ValueKey('solar-chart')), findsNothing);
    },
  );

  testWidgets(
    'health check noemt de onderdelen die aandacht vragen en toont de grafiek',
    (tester) async {
      final api = _Api()
        ..solar = _solar()
        ..items = const [
          BriefingItem(
            text: 'gisteren 2 kWh',
            heading: 'Zonnepanelen',
            status: BriefingStatus.goed,
          ),
          BriefingItem(
            text: 'fout',
            heading: 'Robotmaaier',
            status: BriefingStatus.niet,
          ),
          BriefingItem(
            text: 'SF-1 error',
            heading: 'Software Factory',
            status: BriefingStatus.letOp,
          ),
        ];
      await _pump(tester, HealthCheckScreen(api: api));

      expect(
        find.text(
          '2 onderdelen vragen aandacht: Robotmaaier, Software Factory',
        ),
        findsOneWidget,
      );
      expect(find.text('kritiek'), findsOneWidget);
      expect(find.text('let op'), findsOneWidget);
      expect(find.byKey(const ValueKey('solar-chart')), findsOneWidget);

      await tester.tap(find.text('Meer grafieken'));
      await tester.pumpAndSettle();
      expect(find.text('goed · 91% van verwacht'), findsOneWidget);
      expect(find.text('Nu: 420 W'), findsOneWidget);
    },
  );

  testWidgets(
    'zonnepanelenscherm: tikken op de grafiek toont de dag, met maandtotalen',
    (tester) async {
      await _pump(
        tester,
        SolarScreen(
          api: _Api(),
          initial: _solar(verdict: 'LET_OP'),
        ),
      );

      expect(find.text('let op · 91% van verwacht'), findsOneWidget);
      expect(find.text('Tik op een dag voor de waarden'), findsOneWidget);
      expect(find.text('okt 2026'), findsOneWidget);
      expect(find.text('nov 2026'), findsOneWidget);

      final chart = find.byKey(const ValueKey('solar-chart'));
      await tester.tapAt(tester.getTopRight(chart) + const Offset(-4, 40));
      await tester.pump();
      // De laatste dag is vandaag: 40 dagen na 12 okt is 20 nov, en die loopt nog.
      final detail = tester
          .widget<Text>(find.byKey(const ValueKey('solar-chart-detail')))
          .data!;
      expect(detail, contains('vr 20 nov'));
      expect(detail, contains('tot nu toe'));
      expect(detail, contains('verwacht 2,5 kWh'));

      await tester.tap(find.byKey(const ValueKey('solar-period-alles')));
      await tester.pump();
      expect(find.text('Tik op een dag voor de waarden'), findsOneWidget);
    },
  );

  testWidgets(
    'modelgerechten: filteren per rol, uitzetten en de beschrijving opslaan',
    (tester) async {
      final api = _Api()
        ..meals = [
          _meal('a', 'REFERENTIE', description: 'Grote zwarte kom'),
          _meal('b', 'DUBBEL'),
        ];
      await _pump(tester, ReferenceMealsScreen(api: api));

      expect(find.text('Modelgerechten (1)'), findsOneWidget);
      expect(find.text('Dubbel (1)'), findsOneWidget);
      expect(find.text('Niet gebruikt (0)'), findsNothing);
      expect(find.text('Ontbijt a'), findsOneWidget);
      expect(find.text('Kwark 340 g'), findsOneWidget);
      expect(find.text('Ontbijt b'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('modelgerecht-a')));
      await tester.pumpAndSettle();
      expect(find.text('Wordt gebruikt als modelgerecht'), findsOneWidget);

      await tester.enterText(
        find.byKey(const ValueKey('beschrijving')),
        'Zwarte kom, vol',
      );
      await tester.tap(find.text('Beschrijving opslaan'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('gebruik-als-modelgerecht')));
      await tester.pumpAndSettle();

      expect(api.saved, ['a:true:Zwarte kom, vol', 'a:false:Zwarte kom, vol']);
      expect(find.text('Wordt niet gebruikt'), findsOneWidget);

      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('Modelgerechten (0)'), findsOneWidget);
      expect(find.text('Niet gebruikt (1)'), findsOneWidget);
    },
  );
}

// De beheerpagina voor de laptop staat in admin_screen_test.dart.
