import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/admin_screen.dart';
import 'package:robberts_assistent/api_client.dart';

Meal _meal(String id, String role) => Meal(
  id: id,
  eatenAt: DateTime(2026, 11, 20, 8, 30),
  title: 'Ontbijt $id',
  photoIds: const ['foto'],
  note: '',
  status: 'BEVESTIGD',
  kcal: 400,
  items: const [MealItem(name: 'Kwark', grams: 340, kcal: 200)],
  referenceRole: role,
);

class _Api extends ApiClient {
  List<Meal> meals = [_meal('a', 'REFERENTIE'), _meal('b', 'REFERENTIE')];
  final saved = <String>[];

  @override
  Future<List<Meal>> getReferenceMeals() async => meals;

  @override
  Future<Meal> setMealReference(
    String id, {
    required bool use,
    String? description,
  }) async {
    saved.add('$id:$use');
    final updated = _meal(id, use ? 'REFERENTIE' : 'GEEN');
    meals = [for (final m in meals) m.id == id ? updated : m];
    return updated;
  }

  @override
  Future<Uint8List> fetchMealPhoto(String photoId) async =>
      throw Exception('geen foto');

  @override
  Future<AiModels> getAiModels() async => const AiModels(
    roles: [],
    defaultExecution: AiExecution(
      vendorId: 'anthropic',
      model: 'm',
      mode: 'SUBSCRIPTION',
    ),
    options: [],
  );
}

void main() {
  test('het beheeradres is /admin of #/admin', () {
    expect(isAdminUri(Uri.parse('https://x.nl/admin')), isTrue);
    expect(isAdminUri(Uri.parse('https://x.nl/#/admin')), isTrue);
    expect(isAdminUri(Uri.parse('https://x.nl/')), isFalse);
  });

  testWidgets(
    'beheerpagina op een breed scherm: menu links, maaltijd naast de lijst',
    (tester) async {
      tester.view.physicalSize = const Size(1400, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final api = _Api();
      await tester.pumpWidget(
        MaterialApp(
          home: AdminScreen(api: api, onLoggedOut: () {}),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(NavigationRail), findsOneWidget);
      expect(find.text('Kies links een maaltijd.'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('modelgerecht-a')));
      await tester.pumpAndSettle();
      // De lijst blijft staan; de maaltijd staat ernaast.
      expect(find.byKey(const ValueKey('modelgerecht-b')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('gebruik-als-modelgerecht')));
      await tester.pumpAndSettle();
      expect(api.saved, ['a:false']);
      expect(find.text('Modelgerechten (1)'), findsOneWidget);

      await tester.tap(find.text('AI-modellen'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Kies per toepassing'), findsOneWidget);
    },
  );
}
