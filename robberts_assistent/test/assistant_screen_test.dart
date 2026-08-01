import 'dart:async';
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
  bool? lastVoice;
  Object? failWith;
  AssistantConversationDetail? conversationToLoad;

  @override
  Future<AssistantChatReply> assistantChat({
    required String message,
    String? conversationId,
    List<AssistantAttachment> photos = const [],
    bool voice = false,
  }) async {
    lastMessage = message;
    lastConversationId = conversationId;
    lastPhotos = photos;
    lastVoice = voice;
    if (failWith != null) throw failWith!;
    return nextReply!;
  }

  @override
  Future<AssistantConversationDetail> assistantConversation(String id) async =>
      conversationToLoad!;

  @override
  Future<Uint8List> fetchAssistantPhoto(String photoId) async =>
      Uint8List.fromList([1, 2, 3]);
}

/// Bestuurbare spraakherkenning: de test bepaalt zelf wanneer er iets verstaan wordt, wanneer een
/// ronde in stilte eindigt en wanneer er een fout optreedt.
class _FakeSpeech implements SpeechRecognizer {
  var available = true;
  var listenCount = 0;
  var stopCount = 0;
  void Function(String words, bool isFinal)? _onResult;
  void Function(String status)? _onStatus;
  void Function(String errorMsg)? _onError;

  @override
  Future<bool> initialize({
    required void Function(String status) onStatus,
    required void Function(String errorMsg) onError,
  }) async {
    _onStatus = onStatus;
    _onError = onError;
    return available;
  }

  @override
  Future<void> listen({
    required void Function(String words, bool isFinal) onResult,
  }) async {
    listenCount++;
    _onResult = onResult;
  }

  @override
  Future<void> stop() async => stopCount++;

  void hear(String words) => _onResult!(words, true);

  /// Luistersessie liep af zonder dat er iets verstaanbaars binnenkwam.
  void hearNothing() => _onStatus!('done');

  void fail(String message) => _onError!(message);
}

/// TTS-dubbel waarbij het uitspreken pas klaar is als de test [finishSpeaking] aanroept.
class _FakeSpeaker implements VoiceSpeaker {
  final spoken = <String>[];
  var stopCount = 0;
  Completer<void>? _current;

  @override
  Future<void> speak(String text) {
    spoken.add(text);
    final completer = Completer<void>();
    _current = completer;
    return completer.future;
  }

  void finishSpeaking() {
    _current?.complete();
    _current = null;
  }

  @override
  Future<void> stop() async => stopCount++;
}

void main() {
  testWidgets(
    'typed bericht sturen toont vraag en antwoord in de historie, zonder conversationId voor een nieuw gesprek',
    (tester) async {
      final api = _FakeApiClient()
        ..nextReply = const AssistantChatReply(
          conversationId: 'conv-1',
          title: 'Wind',
          reply: 'Het waait 4 bft',
        );

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
    },
  );

  testWidgets(
    'vervolgvraag in hetzelfde gesprek stuurt het bijgewerkte conversationId mee',
    (tester) async {
      final api = _FakeApiClient()
        ..nextReply = const AssistantChatReply(
          conversationId: 'conv-1',
          title: 'Wind',
          reply: 'antwoord',
        );

      await tester.pumpWidget(
        MaterialApp(
          home: AssistantScreen(api: api, conversationId: 'conv-1'),
        ),
      );
      await tester.pump();

      await tester.tap(find.text('Chatten'));
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'en morgen?');
      await tester.tap(find.byIcon(Icons.send));
      await tester.pump();
      await tester.pump();

      expect(api.lastConversationId, 'conv-1');
    },
  );

  testWidgets(
    'met een conversationId wordt de bestaande historie (titel + berichten) geladen',
    (tester) async {
      final api = _FakeApiClient()
        ..conversationToLoad = const AssistantConversationDetail(
          conversationId: 'conv-1',
          title: 'Windvoorspelling',
          messages: [
            AssistantConversationMessage(
              id: 'm1',
              role: 'user',
              text: 'hoi',
              imageIds: [],
            ),
            AssistantConversationMessage(
              id: 'm2',
              role: 'assistant',
              text: 'hallo!',
              imageIds: [],
            ),
          ],
        );

      await tester.pumpWidget(
        MaterialApp(
          home: AssistantScreen(api: api, conversationId: 'conv-1'),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(find.text('Windvoorspelling'), findsOneWidget);
      expect(find.text('hoi'), findsOneWidget);
      expect(find.text('hallo!'), findsOneWidget);
    },
  );

  testWidgets('met startInVoiceMode opent het scherm meteen in praatmodus', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: AssistantScreen(
          api: _FakeApiClient(),
          startInVoiceMode: true,
          autoStartListening: true,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Tik op de microfoon en stel je vraag.'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
    // Spraak is in een widget-test niet beschikbaar: dan gewoon de bestaande melding + mic-knop,
    // geen crash.
    expect(find.text('Spraakherkenning niet beschikbaar.'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('zonder startInVoiceMode blijft het scherm in chatmodus', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: AssistantScreen(api: _FakeApiClient())),
    );
    await tester.pump();

    expect(find.byType(TextField), findsOneWidget);
    expect(find.text('Tik op de microfoon en stel je vraag.'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  group('doorluister-lus in praatmodus', () {
    late _FakeApiClient api;
    late _FakeSpeech speech;
    late _FakeSpeaker speaker;

    setUp(() {
      api = _FakeApiClient()
        ..nextReply = const AssistantChatReply(
          conversationId: 'conv-1',
          title: 'Wind',
          reply: 'Twintig knopen uit het zuidwesten.',
        );
      speech = _FakeSpeech();
      speaker = _FakeSpeaker();
    });

    Widget screen() => MaterialApp(
      home: AssistantScreen(
        api: api,
        startInVoiceMode: true,
        autoStartListening: true,
        speech: speech,
        speaker: speaker,
      ),
    );

    /// Eén ronde: vraag verstaan, antwoord opgehaald en aan het uitspreken.
    Future<void> askAndAwaitSpeaking(WidgetTester tester) async {
      speech.hear('wat is de wind');
      await tester.pump();
      await tester.pump();
      expect(speaker.spoken, ['Twintig knopen uit het zuidwesten.']);
    }

    testWidgets('luistert opnieuw zodra het antwoord is uitgesproken', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();
      expect(speech.listenCount, 1);

      await askAndAwaitSpeaking(tester);
      // Nog niet klaar met praten: er wordt (nog) niet opnieuw geluisterd.
      expect(speech.listenCount, 1);

      speaker.finishSpeaking();
      await tester.pump();
      await tester.pump();

      expect(speech.listenCount, 2);
      expect(find.text('Ik luister…'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('luistert niet opnieuw na handmatig stoppen tijdens het uitspreken', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();
      await askAndAwaitSpeaking(tester);

      await tester.tap(find.byIcon(Icons.stop));
      await tester.pump();

      speaker.finishSpeaking();
      await tester.pump();
      await tester.pump();

      expect(speech.listenCount, 1);
      expect(find.byIcon(Icons.mic), findsOneWidget);
    });

    testWidgets('luistert niet opnieuw na wisselen naar chatmodus', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();
      await askAndAwaitSpeaking(tester);

      await tester.tap(find.text('Chatten'));
      await tester.pump();

      speaker.finishSpeaking();
      await tester.pump();
      await tester.pump();

      expect(speech.listenCount, 1);
      expect(find.byType(TextField), findsOneWidget);
    });

    testWidgets('luistert niet opnieuw nadat het scherm is weggehaald', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();
      await askAndAwaitSpeaking(tester);

      await tester.pumpWidget(const MaterialApp(home: SizedBox()));
      await tester.pump();

      speaker.finishSpeaking();
      await tester.pump();
      await tester.pump();

      expect(speech.listenCount, 1);
      expect(tester.takeException(), isNull);
    });

    testWidgets('stopt de lus bij een fout van de chat-API en toont de foutmelding', (
      tester,
    ) async {
      api.failWith = Exception('backend onbereikbaar');

      await tester.pumpWidget(screen());
      await tester.pump();
      speech.hear('wat is de wind');
      await tester.pump();
      await tester.pump();

      expect(speaker.spoken, isEmpty);
      expect(speech.listenCount, 1);
      expect(
        find.textContaining('backend onbereikbaar'),
        findsOneWidget,
      );
      expect(find.byIcon(Icons.mic), findsOneWidget);
    });

    testWidgets('stopt de lus na twee rondes zonder verstane spraak', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();
      expect(speech.listenCount, 1);

      speech.hearNothing();
      await tester.pump();
      expect(speech.listenCount, 2, reason: 'eerste stille ronde: nog een keer proberen');

      speech.hearNothing();
      await tester.pump();

      expect(speech.listenCount, 2);
      expect(find.byIcon(Icons.mic), findsOneWidget);
      expect(find.text('Praat met de assistent'), findsOneWidget);
      expect(find.textContaining('Spraakherkenning:'), findsNothing);
    });

    testWidgets('stopt de lus bij een spraakfout', (tester) async {
      await tester.pumpWidget(screen());
      await tester.pump();

      speech.fail('no-match');
      await tester.pump();

      expect(speech.listenCount, 1);
      expect(find.text('Spraakherkenning: no-match'), findsOneWidget);
    });

    testWidgets('de spraakroute stuurt de voice-vlag mee, de getypte route niet', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();

      speech.hear('wat is de wind');
      await tester.pump();
      await tester.pump();
      expect(api.lastVoice, isTrue);

      // Beurt afronden (klaar met uitspreken), dan overschakelen op typen.
      speaker.finishSpeaking();
      await tester.pump();
      await tester.pump();

      await tester.tap(find.text('Chatten'));
      await tester.pump();
      await tester.enterText(find.byType(TextField), 'en morgen?');
      await tester.tap(find.byIcon(Icons.send));
      await tester.pump();
      await tester.pump();

      expect(api.lastMessage, 'en morgen?');
      expect(api.lastVoice, isFalse);
    });

    testWidgets('er ontstaat geen tweede listen-sessie zolang er al geluisterd wordt', (
      tester,
    ) async {
      await tester.pumpWidget(screen());
      await tester.pump();
      expect(speech.listenCount, 1);

      // Stop-knop indrukken en meteen weer starten levert precies één nieuwe sessie op.
      await tester.tap(find.byIcon(Icons.stop));
      await tester.pump();
      await tester.tap(find.byIcon(Icons.mic));
      await tester.pump();

      expect(speech.listenCount, 2);
    });
  });
}
