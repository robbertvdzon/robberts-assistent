import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/watches_screen.dart';

class _FakeApiClient extends ApiClient {
  final watches = <Watch>[];
  bool loadFails = false;
  bool createFails = false;
  String? createdTitle;
  String? createdUrl;
  String? createdInstruction;
  String? createdFrequency;
  bool? createdNotify;

  @override
  Future<List<Watch>> listWatches() async {
    if (loadFails) throw Exception('backend niet bereikbaar');
    return List.of(watches);
  }

  @override
  Future<void> createWatch({
    required String title,
    required String url,
    required String instruction,
    required String frequency,
    required bool notifyOnFound,
  }) async {
    if (createFails) throw Exception('opslaan kapot');
    createdTitle = title;
    createdUrl = url;
    createdInstruction = instruction;
    createdFrequency = frequency;
    createdNotify = notifyOnFound;
    watches.add(
      Watch(
        id: 'nieuw',
        title: title,
        url: url,
        instruction: instruction,
        frequency: frequency,
        notifyOnFound: notifyOnFound,
        status: 'NOG_NIET_GECONTROLEERD',
        statusDescription: 'Nog niet gecontroleerd.',
        active: true,
      ),
    );
  }

  @override
  Future<void> deleteWatch(String id) async {
    watches.removeWhere((watch) => watch.id == id);
  }
}

Widget _wrap(ApiClient api) => MaterialApp(home: WatchesScreen(api: api));

void main() {
  testWidgets('toont titel en leesbare status en kan verwijderen', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..watches.add(
        const Watch(
          id: '1',
          title: 'Concertkaartjes',
          url: 'https://example.com',
          instruction: 'Zoek twee kaarten',
          frequency: 'DAGELIJKS',
          notifyOnFound: true,
          status: 'NIET_GEVONDEN',
          statusDescription: 'Nog geen twee kaarten beschikbaar.',
          active: true,
        ),
      );

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Concertkaartjes'), findsOneWidget);
    expect(find.text('Nog geen twee kaarten beschikbaar.'), findsOneWidget);

    await tester.tap(find.byTooltip('Verwijder Concertkaartjes'));
    await tester.pumpAndSettle();

    expect(api.watches, isEmpty);
    expect(find.textContaining('Nog geen zoekopdrachten'), findsOneWidget);
  });

  testWidgets(
    'maakt watch aan met afzonderlijke velden, frequentie en pushvoorkeur',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(900, 1000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = _FakeApiClient();
      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      await tester.tap(find.byTooltip('Nieuwe zoekopdracht'));
      await tester.pumpAndSettle();
      final fields = find.byType(TextField);
      await tester.enterText(fields.at(0), 'Concertkaartjes');
      await tester.enterText(fields.at(1), 'https://example.com/tickets');
      await tester.enterText(fields.at(2), 'Zoek twee kaarten naast elkaar');
      await tester.tap(find.text('Dagelijks'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Kantooruren').last);
      await tester.pumpAndSettle();
      await tester.tap(find.byType(Switch));
      await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
      await tester.pumpAndSettle();

      expect(api.createdTitle, 'Concertkaartjes');
      expect(api.createdUrl, 'https://example.com/tickets');
      expect(api.createdInstruction, 'Zoek twee kaarten naast elkaar');
      expect(api.createdFrequency, 'KANTOORUREN');
      expect(api.createdNotify, isFalse);
      expect(find.text('Concertkaartjes'), findsOneWidget);
      expect(find.text('Nog niet gecontroleerd.'), findsOneWidget);
    },
  );

  testWidgets('weigert lege velden en ongeldige url met duidelijke melding', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(_wrap(_FakeApiClient()));
    await tester.pump();
    await tester.tap(find.byTooltip('Nieuwe zoekopdracht'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pump();
    expect(find.text('Vul een titel in.'), findsOneWidget);

    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), 'Titel');
    await tester.enterText(fields.at(1), 'example.com');
    await tester.enterText(fields.at(2), 'Zoek iets');
    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pump();
    expect(
      find.text('Vul een geldige absolute HTTP(S)-URL in.'),
      findsOneWidget,
    );

    await tester.enterText(fields.at(1), 'https://example.com');
    await tester.enterText(fields.at(2), '');
    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pump();
    expect(find.text('Vul een zoekinstructie in.'), findsOneWidget);
  });

  testWidgets('toont backendfouten bij laden en opslaan', (tester) async {
    final failingLoad = _FakeApiClient()..loadFails = true;
    await tester.pumpWidget(_wrap(failingLoad));
    await tester.pump();
    expect(find.textContaining('backend niet bereikbaar'), findsOneWidget);

    final failingCreate = _FakeApiClient()..createFails = true;
    await tester.pumpWidget(
      _wrap(failingCreate),
      duration: const Duration(milliseconds: 1),
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Nieuwe zoekopdracht'));
    await tester.pumpAndSettle();
    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), 'Titel');
    await tester.enterText(fields.at(1), 'https://example.com');
    await tester.enterText(fields.at(2), 'Zoek iets');
    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Opslaan mislukt'), findsOneWidget);
  });
}
