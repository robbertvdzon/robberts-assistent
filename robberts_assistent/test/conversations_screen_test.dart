import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/conversations_screen.dart';

class _FakeApiClient extends ApiClient {
  List<AssistantConversationSummary> conversations = [];
  List<AssistantConversationSummary> older = [];
  List<String> archived = [];
  List<String> unarchived = [];
  List<String> deleted = [];

  @override
  Future<List<AssistantConversationSummary>> assistantConversations({
    bool includeArchived = false,
    int? limit,
    int offset = 0,
  }) async {
    if (offset > 0) return older;
    final source = includeArchived ? conversations : conversations.where((c) => !c.archived).toList();
    if (limit != null) return source.take(limit).toList();
    return source;
  }

  @override
  Future<void> archiveConversation(String id) async => archived.add(id);

  @override
  Future<void> unarchiveConversation(String id) async => unarchived.add(id);

  @override
  Future<void> deleteConversation(String id) async => deleted.add(id);
}

AssistantConversationSummary _summary(String id, String title, DateTime updatedAt, {bool archived = false}) =>
    AssistantConversationSummary(conversationId: id, title: title, updatedAt: updatedAt, archived: archived);

void main() {
  testWidgets('toont titel en laatst-bijgewerkt-tijd van eerdere gesprekken', (tester) async {
    final api = _FakeApiClient()
      ..conversations = [
        _summary('abc', 'Windvoorspelling', DateTime(2026, 7, 19, 10, 30)),
      ];

    await tester.pumpWidget(MaterialApp(home: ConversationsScreen(api: api)));
    await tester.pump();

    expect(find.text('Windvoorspelling'), findsOneWidget);
    expect(find.text('19-07-2026 10:30'), findsOneWidget);
  });

  testWidgets('toont een lege-staat-melding zonder gesprekken', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: ConversationsScreen(api: api)));
    await tester.pump();

    expect(find.textContaining('Nog geen gesprekken'), findsOneWidget);
    expect(find.text('Nieuw gesprek'), findsOneWidget);
  });

  testWidgets('toont een "Ouder"-sectie zodra de eerste pagina vol is', (tester) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.binding.setSurfaceSize(const Size(800, 2000));

    final api = _FakeApiClient()
      ..conversations = List.generate(
        10,
        (i) => _summary('c$i', 'Gesprek $i', DateTime(2026, 7, 19, 10, i)),
      )
      ..older = [_summary('older-1', 'Ouder gesprek', DateTime(2026, 7, 1, 10, 0))];

    await tester.pumpWidget(MaterialApp(home: ConversationsScreen(api: api)));
    await tester.pump();

    expect(find.text('Ouder'), findsOneWidget);
    expect(find.text('Ouder gesprek'), findsNothing);

    await tester.tap(find.text('Ouder'));
    await tester.pumpAndSettle();

    expect(find.text('Ouder gesprek'), findsOneWidget);
  });

  testWidgets('toggle "Toon gearchiveerd" herlaadt met gearchiveerde gesprekken', (tester) async {
    final api = _FakeApiClient()
      ..conversations = [
        _summary('a', 'Actief gesprek', DateTime(2026, 7, 19, 10, 0)),
        _summary('b', 'Gearchiveerd gesprek', DateTime(2026, 7, 18, 10, 0), archived: true),
      ];

    await tester.pumpWidget(MaterialApp(home: ConversationsScreen(api: api)));
    await tester.pump();

    expect(find.text('Gearchiveerd gesprek'), findsNothing);

    await tester.tap(find.byTooltip('Toon gearchiveerd'));
    await tester.pump();

    expect(find.text('Gearchiveerd gesprek'), findsOneWidget);
  });

  testWidgets('verwijderen vraagt eerst een bevestiging', (tester) async {
    final api = _FakeApiClient()
      ..conversations = [_summary('a', 'Te verwijderen', DateTime(2026, 7, 19, 10, 0))];

    await tester.pumpWidget(MaterialApp(home: ConversationsScreen(api: api)));
    await tester.pump();

    final slidable = find.byType(Slidable);
    await tester.drag(slidable, const Offset(-400, 0));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Verwijderen'));
    await tester.pumpAndSettle();

    expect(find.byType(AlertDialog), findsOneWidget);
    expect(api.deleted, isEmpty);

    await tester.tap(find.widgetWithText(TextButton, 'Verwijderen'));
    await tester.pumpAndSettle();

    expect(api.deleted, ['a']);
  });
}
