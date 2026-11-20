import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/garden_screen.dart';

class _Api extends ApiClient {
  List<GardenBed> beds = [
    GardenBed(
      id: 'a',
      name: 'Bak 1',
      x: 0.1,
      y: 0.1,
      width: 0.3,
      height: 0.3,
      entries: [
        GardenBedEntry(
          id: 'e1',
          date: DateTime(2026, 4, 1),
          text: 'Wortels gezaaid',
        ),
      ],
    ),
  ];
  final calls = <String>[];

  @override
  Future<GardenPlan> getGardenPlan() async =>
      GardenPlan(aspectRatio: 1.5, beds: beds);

  @override
  Future<GardenBed> getGardenBed(String id) async =>
      beds.firstWhere((b) => b.id == id);

  @override
  Future<GardenBed> createGardenBed(GardenBed bed) async {
    calls.add('nieuw:${bed.name}');
    final created = GardenBed(
      id: 'n',
      name: bed.name,
      x: bed.x,
      y: bed.y,
      width: bed.width,
      height: bed.height,
    );
    beds = [...beds, created];
    return created;
  }

  @override
  Future<GardenBed> updateGardenBed(GardenBed bed) async {
    calls.add(
      'plek:${bed.id}:${bed.x.toStringAsFixed(2)}:${bed.width.toStringAsFixed(2)}',
    );
    beds = [for (final b in beds) b.id == bed.id ? bed : b];
    return bed;
  }

  @override
  Future<GardenBed> addGardenBedEntry(
    String bedId, {
    String text = '',
    List<AssistantAttachment> photos = const [],
  }) async {
    calls.add('vastleggen:$bedId:$text');
    final bed = beds.firstWhere((b) => b.id == bedId);
    final updated = GardenBed(
      id: bed.id,
      name: bed.name,
      x: bed.x,
      y: bed.y,
      width: bed.width,
      height: bed.height,
      entries: [
        GardenBedEntry(id: 'e2', date: DateTime(2026, 7, 1), text: text),
        ...bed.entries,
      ],
    );
    beds = [for (final b in beds) b.id == bedId ? updated : b];
    return updated;
  }

  @override
  Future<GardenChatReply> gardenChat({
    required String message,
    String? conversationId,
    String? bedId,
    List<AssistantAttachment> photos = const [],
  }) async {
    calls.add('vraag:$bedId:$message');
    return const GardenChatReply(
      conversationId: 'c',
      reply: 'In het voorjaar stonden hier wortels.',
    );
  }

  @override
  Future<Uint8List> fetchGardenPhoto(String photoId) async =>
      throw Exception('geen foto');
}

Future<void> _pump(WidgetTester tester, _Api api) async {
  tester.view.physicalSize = const Size(1080, 3000);
  tester.view.devicePixelRatio = 2.5;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(MaterialApp(home: GardenScreen(api: api)));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'plattegrond toont de bakken; tikken opent de bak met zijn geschiedenis',
    (tester) async {
      final api = _Api();
      await _pump(tester, api);

      expect(find.byKey(const ValueKey('plattegrond')), findsOneWidget);
      expect(find.text('1 apr 2026: Wortels gezaaid'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('bak-a')));
      await tester.pumpAndSettle();
      expect(find.text('Geschiedenis'), findsOneWidget);
      expect(find.text('Wortels gezaaid'), findsOneWidget);

      await tester.enterText(find.byKey(const ValueKey('bak-tekst')), 'Nu sla');
      await tester.tap(find.widgetWithText(FilledButton, 'Vastleggen'));
      await tester.pumpAndSettle();
      expect(api.calls, ['vastleggen:a:Nu sla']);
      expect(find.text('1 jul 2026'), findsOneWidget);

      await tester.tap(find.text('Vraag stellen over deze bak'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('moestuin-vraag')),
        'Wat stond hier?',
      );
      await tester.tap(find.byKey(const ValueKey('moestuin-verstuur')));
      await tester.pumpAndSettle();
      expect(api.calls.last, 'vraag:a:Wat stond hier?');
      expect(
        find.text('In het voorjaar stonden hier wortels.'),
        findsOneWidget,
      );
    },
  );

  testWidgets('bak toevoegen, verslepen en vergroten slaat de nieuwe plek op', (
    tester,
  ) async {
    final api = _Api();
    await _pump(tester, api);

    await tester.tap(find.byKey(const ValueKey('bak-toevoegen')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('bak-naam')), 'Kas');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();
    expect(api.calls, ['nieuw:Kas']);
    expect(find.byKey(const ValueKey('bak-n')), findsOneWidget);

    // Na toevoegen staat de plattegrond in de bewerkstand: slepen verplaatst de bak.
    final width = tester
        .getSize(find.byKey(const ValueKey('plattegrond')))
        .width;
    await tester.drag(
      find.byKey(const ValueKey('bak-a')),
      Offset(width * 0.2, 0),
    );
    await tester.pumpAndSettle();
    // Het begin van een sleepbeweging gaat op aan de herkenning ervan; iets minder dan 0,2 dus.
    final moved = double.parse(api.calls.last.split(':')[2]);
    expect(moved, inInclusiveRange(0.2, 0.3));

    await tester.drag(
      find.byKey(const ValueKey('bak-formaat-a')),
      Offset(width * 0.1, 0),
    );
    await tester.pumpAndSettle();
    expect(double.parse(api.calls.last.split(':')[2]), moved);
    expect(double.parse(api.calls.last.split(':')[3]), inInclusiveRange(0.33, 0.4));
  });

  testWidgets(
    'algemene vraag op het tabblad Vragen gaat zonder bak naar de assistent',
    (tester) async {
      final api = _Api();
      await _pump(tester, api);

      await tester.tap(find.text('Vragen'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('moestuin-vraag')),
        'Wanneer knoflook planten?',
      );
      await tester.tap(find.byKey(const ValueKey('moestuin-verstuur')));
      await tester.pumpAndSettle();
      expect(api.calls, ['vraag:null:Wanneer knoflook planten?']);
    },
  );
}
