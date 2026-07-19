import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/conversations_screen.dart';

class _FakeApiClient extends ApiClient {
  List<AssistantConversationSummary> conversations = [];

  @override
  Future<List<AssistantConversationSummary>> assistantConversations() async => conversations;
}

void main() {
  testWidgets('toont titel en laatst-bijgewerkt-tijd van eerdere gesprekken', (tester) async {
    final api = _FakeApiClient()
      ..conversations = [
        AssistantConversationSummary(
          conversationId: 'abc',
          title: 'Windvoorspelling',
          updatedAt: DateTime(2026, 7, 19, 10, 30),
        ),
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
}
