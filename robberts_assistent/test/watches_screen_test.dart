import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/watches_screen.dart';

class _FakeApiClient extends ApiClient {
  List<Watch> watches = [];
  List<String> deleted = [];
  Map<String, dynamic>? lastCreate;
  Map<String, dynamic>? lastUpdate;

  @override
  Future<List<Watch>> listWatches() async => watches;

  @override
  Future<Watch> createWatch({
    required String title,
    required String url,
    required String instruction,
    required WatchFrequency frequency,
    required bool notifyOnFound,
  }) async {
    lastCreate = {
      'title': title,
      'url': url,
      'instruction': instruction,
      'frequency': frequency,
      'notifyOnFound': notifyOnFound,
    };
    return _watch(id: 'new', title: title, url: url, instruction: instruction, frequency: frequency, notifyOnFound: notifyOnFound);
  }

  @override
  Future<Watch> updateWatch({
    required String id,
    required String title,
    required String url,
    required String instruction,
    required WatchFrequency frequency,
    required bool notifyOnFound,
  }) async {
    lastUpdate = {
      'id': id,
      'title': title,
      'url': url,
      'instruction': instruction,
      'frequency': frequency,
      'notifyOnFound': notifyOnFound,
    };
    return _watch(id: id, title: title, url: url, instruction: instruction, frequency: frequency, notifyOnFound: notifyOnFound);
  }

  @override
  Future<void> deleteWatch(String id) async => deleted.add(id);
}

Watch _watch({
  required String id,
  String title = 'aaltjes tegen slakken',
  String url = 'https://example.com',
  String instruction = 'geef een seintje als ze weer beschikbaar zijn',
  WatchFrequency frequency = WatchFrequency.dagelijks,
  bool notifyOnFound = true,
  WatchStatus status = WatchStatus.nietGevonden,
  String statusText = 'nog steeds uitverkocht',
  bool active = true,
}) =>
    Watch(
      id: id,
      title: title,
      url: url,
      instruction: instruction,
      frequency: frequency,
      notifyOnFound: notifyOnFound,
      status: status,
      statusText: statusText,
      active: active,
    );

void main() {
  testWidgets('toont titel en status van een zoekopdracht', (tester) async {
    final api = _FakeApiClient()..watches = [_watch(id: 'a', title: 'aaltjes', statusText: 'nog steeds uitverkocht')];

    await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
    await tester.pump();

    expect(find.text('aaltjes'), findsOneWidget);
    expect(find.text('nog steeds uitverkocht'), findsOneWidget);
  });

  testWidgets('toont een lege-staat-melding zonder zoekopdrachten', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
    await tester.pump();

    expect(find.textContaining('Nog geen zoekopdrachten'), findsOneWidget);
  });

  testWidgets('nieuwe zoekopdracht aanmaken via de dialoog', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
    await tester.pump();

    await tester.tap(find.byIcon(Icons.add));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextField, 'Titel'), 'nieuw product');
    await tester.enterText(find.widgetWithText(TextField, 'Url'), 'https://winkel.nl/product');
    await tester.enterText(find.widgetWithText(TextField, 'Instructie'), 'zeg het als het weer op voorraad is');

    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(api.lastCreate, isNotNull);
    expect(api.lastCreate!['title'], 'nieuw product');
    expect(api.lastCreate!['url'], 'https://winkel.nl/product');
    expect(api.lastCreate!['frequency'], WatchFrequency.dagelijks);
    expect(api.lastCreate!['notifyOnFound'], true);
  });

  testWidgets('tik op een zoekopdracht opent het bewerk-dialoog vooraf ingevuld', (tester) async {
    final api = _FakeApiClient()..watches = [_watch(id: 'a', title: 'aaltjes', url: 'https://example.com/a')];

    await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
    await tester.pump();

    await tester.tap(find.text('aaltjes'));
    await tester.pumpAndSettle();

    expect(find.text('Zoekopdracht bewerken'), findsOneWidget);
    expect(find.widgetWithText(TextField, 'Titel'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(api.lastUpdate, isNotNull);
    expect(api.lastUpdate!['id'], 'a');
    expect(api.lastUpdate!['title'], 'aaltjes');
  });

  testWidgets('verwijderen vraagt eerst een bevestiging', (tester) async {
    final api = _FakeApiClient()..watches = [_watch(id: 'a', title: 'te verwijderen')];

    await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
    await tester.pump();

    await tester.tap(find.byIcon(Icons.delete_outline));
    await tester.pumpAndSettle();

    expect(find.byType(AlertDialog), findsOneWidget);
    expect(api.deleted, isEmpty);

    await tester.tap(find.widgetWithText(TextButton, 'Verwijderen'));
    await tester.pumpAndSettle();

    expect(api.deleted, ['a']);
  });
}
