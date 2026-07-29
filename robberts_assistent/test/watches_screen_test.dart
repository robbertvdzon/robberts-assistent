import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/watches_screen.dart';

Watch _watch({
  String id = '1',
  String title = 'aaltjes tegen slakken',
  String frequency = 'DAGELIJKS',
  bool active = true,
  bool found = false,
  DateTime? lastCheckedAt,
  String? lastStatus = 'nog steeds uitverkocht',
  String? lastError,
}) =>
    Watch(
      id: id,
      title: title,
      url: 'https://voorbeeld.nl/aaltjes',
      instruction: 'meld het als de aaltjes weer op voorraad zijn',
      frequency: frequency,
      pushOnFound: true,
      active: active,
      lastCheckedAt: lastCheckedAt,
      lastStatus: lastStatus,
      found: found,
      lastError: lastError,
    );

class _FakeApiClient extends ApiClient {
  _FakeApiClient([List<Watch>? watches]) : watches = watches ?? [_watch()];

  List<Watch> watches;
  final created = <Map<String, dynamic>>[];
  final updated = <Map<String, dynamic>>[];
  final deleted = <String>[];
  final checked = <String>[];
  Completer<Watch>? checkCompleter;

  @override
  Future<List<Watch>> listWatches() async => watches;

  @override
  Future<Watch> createWatch({
    required String title,
    required String url,
    required String instruction,
    required String frequency,
    required bool pushOnFound,
  }) async {
    created.add({
      'title': title,
      'url': url,
      'instruction': instruction,
      'frequency': frequency,
      'pushOnFound': pushOnFound,
    });
    return _watch(title: title);
  }

  @override
  Future<Watch> updateWatch({
    required String id,
    required String title,
    required String url,
    required String instruction,
    required String frequency,
    required bool pushOnFound,
    required bool active,
  }) async {
    updated.add({'id': id, 'title': title, 'active': active, 'frequency': frequency});
    return _watch(id: id, title: title, active: active, frequency: frequency);
  }

  @override
  Future<void> deleteWatch(String id) async {
    deleted.add(id);
    watches = watches.where((w) => w.id != id).toList();
  }

  @override
  Future<Watch> checkWatch(String id) {
    checked.add(id);
    final completer = checkCompleter;
    if (completer != null) return completer.future;
    return Future.value(_watch(id: id, lastStatus: 'nu weer op voorraad', found: true));
  }
}

class _FailingApiClient extends ApiClient {
  @override
  Future<List<Watch>> listWatches() async => throw Exception('kapot');
}

Widget _wrap(ApiClient api) => MaterialApp(home: WatchesScreen(api: api));

void main() {
  testWidgets('toont per zoekopdracht de titel, status en het laatste controlemoment', (tester) async {
    final api = _FakeApiClient([
      _watch(lastCheckedAt: DateTime(2026, 7, 29, 10, 5)),
    ]);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('aaltjes tegen slakken'), findsOneWidget);
    expect(find.text('nog steeds uitverkocht'), findsOneWidget);
    expect(find.textContaining('gecontroleerd 29-7-2026 10:05'), findsOneWidget);
    expect(find.textContaining('één keer per dag'), findsOneWidget);
  });

  testWidgets('markeert een gevonden zoekopdracht visueel', (tester) async {
    final api = _FakeApiClient([_watch(found: true, lastStatus: 'nu weer op voorraad', active: false)]);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.byIcon(Icons.check_circle), findsOneWidget);
    expect(find.text('nu weer op voorraad'), findsOneWidget);
  });

  testWidgets('toont een foutmelding op de zoekopdracht zelf', (tester) async {
    final api = _FakeApiClient([_watch(lastError: 'Pagina gaf HTTP 500')]);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.textContaining('Fout: Pagina gaf HTTP 500'), findsOneWidget);
  });

  testWidgets('nu controleren toont een spinner en werkt daarna de status bij', (tester) async {
    final completer = Completer<Watch>();
    final api = _FakeApiClient()..checkCompleter = completer;

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Nu controleren'));
    await tester.pump();

    expect(api.checked, ['1']);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    completer.complete(_watch(lastStatus: 'nu weer op voorraad', found: true));
    await tester.pumpAndSettle();

    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('nu weer op voorraad'), findsOneWidget);
  });

  testWidgets('pauzeren zet active op false', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Pauzeren'));
    await tester.pumpAndSettle();

    expect(api.updated.single['active'], false);
  });

  testWidgets('een gepauzeerde zoekopdracht is te hervatten', (tester) async {
    final api = _FakeApiClient([_watch(active: false)]);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.textContaining('gepauzeerd'), findsOneWidget);

    await tester.tap(find.byTooltip('Hervatten'));
    await tester.pumpAndSettle();

    expect(api.updated.single['active'], true);
  });

  testWidgets('verwijderen vraagt eerst om bevestiging', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byTooltip('Verwijderen'));
    await tester.pumpAndSettle();
    expect(find.text('Zoekopdracht verwijderen?'), findsOneWidget);

    await tester.tap(find.text('Annuleren'));
    await tester.pumpAndSettle();
    expect(api.deleted, isEmpty);

    await tester.tap(find.byTooltip('Verwijderen'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Verwijderen'));
    await tester.pumpAndSettle();
    expect(api.deleted, ['1']);
  });

  testWidgets('aanmaken via de + knop stuurt titel, url, instructie, frequentie en push mee', (tester) async {
    final api = _FakeApiClient([]);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.textContaining('Nog geen zoekopdrachten'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.add));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextField, 'Titel'), 'aaltjes tegen slakken');
    await tester.enterText(find.widgetWithText(TextField, 'Link (URL)'), 'https://voorbeeld.nl/aaltjes');
    await tester.enterText(
      find.widgetWithText(TextField, 'Waar moet hij op letten?'),
      'meld het als de aaltjes weer op voorraad zijn',
    );
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(api.created.single, {
      'title': 'aaltjes tegen slakken',
      'url': 'https://voorbeeld.nl/aaltjes',
      'instruction': 'meld het als de aaltjes weer op voorraad zijn',
      'frequency': 'DAGELIJKS',
      'pushOnFound': false,
    });
  });

  testWidgets('opslaan zonder geldige url toont een validatiemelding en sluit niet', (tester) async {
    final api = _FakeApiClient([]);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byIcon(Icons.add));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextField, 'Titel'), 'zonder link');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(find.textContaining('http://'), findsOneWidget);
    expect(api.created, isEmpty);
  });

  testWidgets('toont een foutmelding als het laden faalt', (tester) async {
    await tester.pumpWidget(_wrap(_FailingApiClient()));
    await tester.pump();

    expect(find.textContaining('kapot'), findsOneWidget);
  });
}
