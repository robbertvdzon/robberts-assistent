import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/watches_screen.dart';

class _FakeApiClient extends ApiClient {
  final watches = <Watch>[];
  bool loadFails = false;
  bool createFails = false;
  bool updateFails = false;
  bool runFails = false;
  int runCalls = 0;
  List<Watch>? runResult;
  String? createdTitle;
  String? createdUrl;
  String? createdInstruction;
  String? createdFrequency;
  bool? createdNotify;
  String? updatedId;
  String? updatedTitle;
  String? updatedUrl;
  String? updatedInstruction;
  String? updatedFrequency;
  bool? updatedNotify;

  @override
  Future<List<Watch>> listWatches() async {
    if (loadFails) throw Exception('backend niet bereikbaar');
    return List.of(watches);
  }

  @override
  Future<void> updateWatch({
    required String id,
    required String title,
    required String url,
    required String instruction,
    required String frequency,
    required bool notifyOnFound,
  }) async {
    if (updateFails) throw Exception('wijzigen kapot');
    updatedId = id;
    updatedTitle = title;
    updatedUrl = url;
    updatedInstruction = instruction;
    updatedFrequency = frequency;
    updatedNotify = notifyOnFound;
    final index = watches.indexWhere((watch) => watch.id == id);
    watches[index] = Watch(
      id: id,
      title: title,
      url: url,
      instruction: instruction,
      frequency: frequency,
      notifyOnFound: notifyOnFound,
      status: 'NOG_NIET_GECONTROLEERD',
      statusDescription: 'Nog niet gecontroleerd.',
      active: true,
    );
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

  @override
  Future<List<Watch>> runWatchesNow() async {
    runCalls++;
    if (runFails) throw Exception('run-now kapot');
    final result = runResult;
    if (result != null) {
      watches
        ..clear()
        ..addAll(result);
    }
    return List.of(watches);
  }
}

class _ControlledRunApiClient extends ApiClient {
  _ControlledRunApiClient(this.watches);

  final List<Watch> watches;
  final runs = <Completer<List<Watch>>>[];

  @override
  Future<List<Watch>> listWatches() async => List.of(watches);

  @override
  Future<List<Watch>> runWatchesNow() {
    final run = Completer<List<Watch>>();
    runs.add(run);
    return run.future;
  }
}

class _ControlledLoadApiClient extends ApiClient {
  final loads = <Completer<List<Watch>>>[];
  int runCalls = 0;

  @override
  Future<List<Watch>> listWatches() {
    final load = Completer<List<Watch>>();
    loads.add(load);
    return load.future;
  }

  @override
  Future<List<Watch>> runWatchesNow() async {
    runCalls++;
    return [_currentWatch];
  }
}

const _oldWatch = Watch(
  id: '1',
  title: 'Concertkaartjes',
  url: 'https://example.com',
  instruction: 'Zoek twee kaarten',
  frequency: 'DAGELIJKS',
  notifyOnFound: true,
  status: 'NIET_GEVONDEN',
  statusDescription: 'Nog niet beschikbaar.',
  active: true,
);

const _currentWatch = Watch(
  id: '1',
  title: 'Concertkaartjes',
  url: 'https://example.com',
  instruction: 'Zoek twee kaarten',
  frequency: 'DAGELIJKS',
  notifyOnFound: true,
  status: 'GEVONDEN',
  statusDescription: 'Nu beschikbaar.',
  active: false,
);

Widget _wrap(ApiClient api) => MaterialApp(home: WatchesScreen(api: api));

IconButton _iconButton(WidgetTester tester, String tooltip) => tester.widget<IconButton>(
  find.ancestor(of: find.byTooltip(tooltip), matching: find.byType(IconButton)),
);

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

  testWidgets('bewerkt een bestaande watch met vooraf ingevulde velden', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeApiClient()..watches.add(_currentWatch);
    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Bewerk Concertkaartjes'));
    await tester.pumpAndSettle();
    expect(find.text('Zoekopdracht bewerken'), findsOneWidget);
    final fields = find.byType(TextField);
    expect(tester.widget<TextField>(fields.at(0)).controller?.text, 'Concertkaartjes');
    expect(tester.widget<TextField>(fields.at(1)).controller?.text, 'https://example.com');
    expect(tester.widget<TextField>(fields.at(2)).controller?.text, 'Zoek twee kaarten');

    await tester.enterText(fields.at(0), 'Drie concertkaartjes');
    await tester.enterText(fields.at(1), 'https://example.com/nieuw');
    await tester.enterText(fields.at(2), 'Zoek drie kaarten naast elkaar');
    await tester.tap(find.text('Dagelijks'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Kantooruren').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byType(Switch));
    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(api.updatedId, '1');
    expect(api.updatedTitle, 'Drie concertkaartjes');
    expect(api.updatedUrl, 'https://example.com/nieuw');
    expect(api.updatedInstruction, 'Zoek drie kaarten naast elkaar');
    expect(api.updatedFrequency, 'KANTOORUREN');
    expect(api.updatedNotify, isFalse);
    expect(find.text('Drie concertkaartjes'), findsOneWidget);
    expect(find.text('Nog niet gecontroleerd.'), findsOneWidget);
  });

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

  testWidgets('toont een backendfout bij wijzigen', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeApiClient()
      ..updateFails = true
      ..watches.add(_oldWatch);
    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Bewerk Concertkaartjes'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Wijzigen mislukt'), findsOneWidget);
  });

  testWidgets('nu-draaien-knop start één run en toont de bijgewerkte lijst', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..watches.add(_oldWatch)
      ..runResult = [_currentWatch];
    await tester.pumpWidget(_wrap(api));
    await tester.pump();
    expect(find.text('Nog niet beschikbaar.'), findsOneWidget);

    await tester.tap(find.byTooltip('Alle zoekopdrachten nu controleren'));
    await tester.pump();
    await tester.pump();

    expect(api.runCalls, 1);
    expect(find.text('Nu beschikbaar.'), findsOneWidget);
    expect(find.text('Nog niet beschikbaar.'), findsNothing);
  });

  testWidgets('tijdens de run is de knop bezig en start een tweede tik geen tweede run', (
    tester,
  ) async {
    final api = _ControlledRunApiClient([_oldWatch]);
    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Alle zoekopdrachten nu controleren'));
    await tester.pump();

    expect(api.runs, hasLength(1));
    expect(
      find.descendant(
        of: find.byType(AppBar),
        matching: find.byType(CircularProgressIndicator),
      ),
      findsOneWidget,
    );
    expect(_iconButton(tester, 'Alle zoekopdrachten nu controleren').onPressed, isNull);
    expect(_iconButton(tester, 'Zoekopdrachten herladen').onPressed, isNull);
    // De bestaande lijst blijft zichtbaar tijdens de run.
    expect(find.text('Nog niet beschikbaar.'), findsOneWidget);

    await tester.tap(
      find.byTooltip('Alle zoekopdrachten nu controleren'),
      warnIfMissed: false,
    );
    await tester.pump();
    expect(api.runs, hasLength(1));

    api.runs.single.complete([_currentWatch]);
    await tester.pump();

    expect(find.text('Nu beschikbaar.'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(AppBar),
        matching: find.byType(CircularProgressIndicator),
      ),
      findsNothing,
    );
    expect(_iconButton(tester, 'Alle zoekopdrachten nu controleren').onPressed, isNotNull);
    expect(_iconButton(tester, 'Zoekopdrachten herladen').onPressed, isNotNull);
  });

  testWidgets('de nieuwe-zoekopdracht-knop is uit tijdens een run', (
    tester,
  ) async {
    final api = _ControlledRunApiClient([_oldWatch]);
    await tester.pumpWidget(_wrap(api));
    await tester.pump();
    expect(
      tester.widget<FloatingActionButton>(find.byType(FloatingActionButton)).onPressed,
      isNotNull,
    );

    await tester.tap(find.byTooltip('Alle zoekopdrachten nu controleren'));
    await tester.pump();
    expect(
      tester.widget<FloatingActionButton>(find.byType(FloatingActionButton)).onPressed,
      isNull,
    );

    api.runs.single.complete([_currentWatch]);
    await tester.pump();
    expect(
      tester.widget<FloatingActionButton>(find.byType(FloatingActionButton)).onPressed,
      isNotNull,
    );
  });

  testWidgets('nu draaien kan niet tijdens het laden en laat het scherm niet hangen', (
    tester,
  ) async {
    final api = _ControlledLoadApiClient();
    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    // Zolang de eerste load loopt is nu-draaien uit; een tik start geen run.
    expect(api.loads, hasLength(1));
    expect(_iconButton(tester, 'Alle zoekopdrachten nu controleren').onPressed, isNull);
    await tester.tap(
      find.byTooltip('Alle zoekopdrachten nu controleren'),
      warnIfMissed: false,
    );
    await tester.pump();
    expect(api.runCalls, 0);

    // De load rondt gewoon af: geen permanente laadspinner, lijst en knop weer bruikbaar.
    api.loads.single.complete([_oldWatch]);
    await tester.pump();
    expect(find.text('Nog niet beschikbaar.'), findsOneWidget);
    expect(_iconButton(tester, 'Alle zoekopdrachten nu controleren').onPressed, isNotNull);

    // En daarna werkt nu-draaien wel.
    await tester.tap(find.byTooltip('Alle zoekopdrachten nu controleren'));
    await tester.pump();
    await tester.pump();
    expect(api.runCalls, 1);
    expect(find.text('Nu beschikbaar.'), findsOneWidget);
  });

  testWidgets('toont een melding als nu draaien mislukt en houdt de lijst zichtbaar', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..watches.add(_oldWatch)
      ..runFails = true;
    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Alle zoekopdrachten nu controleren'));
    await tester.pump();
    await tester.pump();

    expect(find.textContaining('Nu controleren mislukt'), findsOneWidget);
    expect(find.text('Nog niet beschikbaar.'), findsOneWidget);
    expect(_iconButton(tester, 'Alle zoekopdrachten nu controleren').onPressed, isNotNull);
  });

  testWidgets('een oudere load overschrijft een nieuwere pushrefresh niet', (
    tester,
  ) async {
    final api = _ControlledLoadApiClient();
    await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
    expect(api.loads, hasLength(1));

    await tester.pumpWidget(
      MaterialApp(home: WatchesScreen(api: api, reloadTrigger: 1)),
    );
    expect(api.loads, hasLength(2));

    api.loads[1].complete([_currentWatch]);
    await tester.pump();
    expect(find.text('Nu beschikbaar.'), findsOneWidget);

    api.loads[0].complete([_oldWatch]);
    await tester.pump();
    expect(find.text('Nu beschikbaar.'), findsOneWidget);
    expect(find.text('Nog niet beschikbaar.'), findsNothing);
  });
}
