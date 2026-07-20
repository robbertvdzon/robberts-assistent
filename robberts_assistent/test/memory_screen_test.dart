import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/memory_screen.dart';

class _FakeApiClient extends ApiClient {
  List<MemoryItem> items = [];
  List<String> created = [];
  Map<String, String> updated = {};
  List<String> deleted = [];

  @override
  Future<List<MemoryItem>> listMemory() async => items;

  @override
  Future<MemoryItem> createMemoryItem(String text) async {
    created.add(text);
    return MemoryItem(id: 'new', text: text, updatedAt: DateTime(2026, 7, 20));
  }

  @override
  Future<MemoryItem> updateMemoryItem(String id, String text) async {
    updated[id] = text;
    return MemoryItem(id: id, text: text, updatedAt: DateTime(2026, 7, 20));
  }

  @override
  Future<void> deleteMemoryItem(String id) async => deleted.add(id);
}

MemoryItem _item(String id, String text) => MemoryItem(id: id, text: text, updatedAt: DateTime(2026, 7, 19));

void main() {
  testWidgets('toont de tekst van elk geheugen-item', (tester) async {
    final api = _FakeApiClient()..items = [_item('a', 'houdt van kiten')];

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
    await tester.pump();

    expect(find.text('houdt van kiten'), findsOneWidget);
  });

  testWidgets('toont een lege-staat-melding zonder geheugen-items', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
    await tester.pump();

    expect(find.textContaining('Nog geen geheugen-items'), findsOneWidget);
  });

  testWidgets('toevoegen opent een dialoog en roept createMemoryItem aan', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
    await tester.pump();

    await tester.tap(find.byType(FloatingActionButton));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'nieuw feit');
    await tester.tap(find.widgetWithText(TextButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(api.created, ['nieuw feit']);
  });

  testWidgets('tikken op een item opent een bewerk-dialoog en roept updateMemoryItem aan', (tester) async {
    final api = _FakeApiClient()..items = [_item('a', 'oude tekst')];

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
    await tester.pump();

    await tester.tap(find.text('oude tekst'));
    await tester.pumpAndSettle();

    final field = find.byType(TextField);
    await tester.enterText(field, 'nieuwe tekst');
    await tester.tap(find.widgetWithText(TextButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(api.updated['a'], 'nieuwe tekst');
  });

  testWidgets('verwijderen vraagt eerst een bevestiging', (tester) async {
    final api = _FakeApiClient()..items = [_item('a', 'te verwijderen')];

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
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
