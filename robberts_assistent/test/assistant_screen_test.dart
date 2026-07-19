import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/assistant_screen.dart';

class _FakeApiClient extends ApiClient {
  AssistantChatReply? nextReply;
  String? lastMessage;
  String? lastConversationId;
  List<AssistantAttachment>? lastPhotos;
  AssistantConversationDetail? conversationToLoad;

  @override
  Future<AssistantChatReply> assistantChat({
    required String message,
    String? conversationId,
    List<AssistantAttachment> photos = const [],
  }) async {
    lastMessage = message;
    lastConversationId = conversationId;
    lastPhotos = photos;
    return nextReply!;
  }

  @override
  Future<AssistantConversationDetail> assistantConversation(String id) async => conversationToLoad!;

  @override
  Future<Uint8List> fetchAssistantPhoto(String photoId) async => Uint8List.fromList([1, 2, 3]);
}

void main() {
  testWidgets('typed bericht sturen toont vraag en antwoord in de historie, zonder conversationId voor een nieuw gesprek', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..nextReply = const AssistantChatReply(conversationId: 'conv-1', title: 'Wind', reply: 'Het waait 4 bft');

    await tester.pumpWidget(MaterialApp(home: AssistantScreen(api: api)));
    await tester.pump();

    await tester.tap(find.text('Chatten'));
    await tester.pump();

    await tester.enterText(find.byType(TextField), 'wat is de wind');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();
    await tester.pump();

    expect(api.lastMessage, 'wat is de wind');
    expect(api.lastConversationId, isNull);
    expect(find.text('wat is de wind'), findsOneWidget);
    expect(find.text('Het waait 4 bft'), findsOneWidget);
  });

  testWidgets('vervolgvraag in hetzelfde gesprek stuurt het bijgewerkte conversationId mee', (tester) async {
    final api = _FakeApiClient()
      ..nextReply = const AssistantChatReply(conversationId: 'conv-1', title: 'Wind', reply: 'antwoord');

    await tester.pumpWidget(MaterialApp(home: AssistantScreen(api: api, conversationId: 'conv-1')));
    await tester.pump();

    await tester.tap(find.text('Chatten'));
    await tester.pump();

    await tester.enterText(find.byType(TextField), 'en morgen?');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();
    await tester.pump();

    expect(api.lastConversationId, 'conv-1');
  });

  testWidgets('met een conversationId wordt de bestaande historie (titel + berichten) geladen', (tester) async {
    final api = _FakeApiClient()
      ..conversationToLoad = const AssistantConversationDetail(
        conversationId: 'conv-1',
        title: 'Windvoorspelling',
        messages: [
          AssistantConversationMessage(id: 'm1', role: 'user', text: 'hoi', imageIds: []),
          AssistantConversationMessage(id: 'm2', role: 'assistant', text: 'hallo!', imageIds: []),
        ],
      );

    await tester.pumpWidget(MaterialApp(home: AssistantScreen(api: api, conversationId: 'conv-1')));
    await tester.pump();
    await tester.pump();

    expect(find.text('Windvoorspelling'), findsOneWidget);
    expect(find.text('hoi'), findsOneWidget);
    expect(find.text('hallo!'), findsOneWidget);
  });
}
