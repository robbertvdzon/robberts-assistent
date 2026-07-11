import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart';

import 'api_client.dart';

class ChatMessage {
  const ChatMessage({required this.fromUser, required this.text});

  final bool fromUser;
  final String text;
}

enum _Mode { voice, chat }

/// De assistent: "Praat met de assistent" (spraak in, gesproken antwoord terug) met een
/// wisselknop naar chat (getypt, alleen tekst). Beide modi delen dezelfde [_history], zodat
/// wisselen heen-en-terug niets van het gesprek verliest.
class AssistantScreen extends StatefulWidget {
  const AssistantScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen> {
  final _history = <ChatMessage>[];
  final _speech = SpeechToText();
  final _tts = FlutterTts();
  final _chatController = TextEditingController();
  final _scrollController = ScrollController();

  _Mode _mode = _Mode.voice;
  var _listening = false;
  var _speechAvailable = false;
  var _busy = false;
  String _partialTranscript = '';
  String? _error;

  @override
  void initState() {
    super.initState();
    _tts.setLanguage('nl-NL');
    _initSpeech();
  }

  Future<void> _initSpeech() async {
    final available = await _speech.initialize(
      onStatus: (status) {
        if (status == 'done' || status == 'notListening') {
          if (mounted) setState(() => _listening = false);
        }
      },
      onError: (error) {
        if (mounted) {
          setState(() {
            _listening = false;
            _error = 'Spraakherkenning: ${error.errorMsg}';
          });
        }
      },
    );
    if (mounted) setState(() => _speechAvailable = available);
  }

  @override
  void dispose() {
    _speech.stop();
    _tts.stop();
    _chatController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _startListening() async {
    if (!_speechAvailable || _listening) return;
    setState(() {
      _error = null;
      _partialTranscript = '';
      _listening = true;
    });
    await _speech.listen(
      listenOptions: SpeechListenOptions(localeId: 'nl_NL'),
      onResult: (result) async {
        setState(() => _partialTranscript = result.recognizedWords);
        if (result.finalResult && result.recognizedWords.trim().isNotEmpty) {
          final heard = result.recognizedWords.trim();
          setState(() {
            _listening = false;
            _partialTranscript = '';
          });
          await _send(heard, speakReply: true);
        }
      },
    );
  }

  Future<void> _stopListening() async {
    await _speech.stop();
    if (mounted) setState(() => _listening = false);
  }

  Future<void> _sendTyped() async {
    final text = _chatController.text.trim();
    if (text.isEmpty) return;
    _chatController.clear();
    await _send(text, speakReply: false);
  }

  Future<void> _send(String text, {required bool speakReply}) async {
    setState(() {
      _history.add(ChatMessage(fromUser: true, text: text));
      _busy = true;
      _error = null;
    });
    _scrollToBottom();
    try {
      final body = await widget.api.postJson('/api/v1/assistant/message', {'text': text});
      final reply = body['text']?.toString() ?? '';
      setState(() => _history.add(ChatMessage(fromUser: false, text: reply)));
      _scrollToBottom();
      if (speakReply && reply.isNotEmpty) {
        await _tts.speak(reply);
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: SegmentedButton<_Mode>(
            segments: const [
              ButtonSegment(value: _Mode.voice, label: Text('Praten'), icon: Icon(Icons.mic)),
              ButtonSegment(value: _Mode.chat, label: Text('Chatten'), icon: Icon(Icons.chat_bubble_outline)),
            ],
            selected: {_mode},
            onSelectionChanged: (selection) {
              if (_listening) _stopListening();
              setState(() => _mode = selection.first);
            },
          ),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(_error!, style: const TextStyle(color: Colors.red)),
          ),
        Expanded(child: _history.isEmpty ? _emptyState() : _historyList()),
        if (_mode == _Mode.voice) _voiceControls() else _chatControls(),
      ],
    );
  }

  Widget _emptyState() => Center(
    child: Text(
      _mode == _Mode.voice
          ? 'Tik op de microfoon en stel je vraag.'
          : 'Typ hieronder een vraag aan de assistent.',
      style: const TextStyle(color: Colors.black54),
    ),
  );

  Widget _historyList() => ListView.builder(
    controller: _scrollController,
    padding: const EdgeInsets.all(16),
    itemCount: _history.length,
    itemBuilder: (context, index) {
      final message = _history[index];
      return Align(
        alignment: message.fromUser ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 4),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.75),
          decoration: BoxDecoration(
            color: message.fromUser ? Colors.deepPurple : Colors.grey.shade200,
            borderRadius: BorderRadius.circular(14),
          ),
          child: Text(
            message.text,
            style: TextStyle(color: message.fromUser ? Colors.white : Colors.black87),
          ),
        ),
      );
    },
  );

  Widget _voiceControls() => Padding(
    padding: const EdgeInsets.all(24),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_partialTranscript.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text('"$_partialTranscript"', style: const TextStyle(fontStyle: FontStyle.italic)),
          ),
        FloatingActionButton.large(
          onPressed: !_speechAvailable || _busy ? null : (_listening ? _stopListening : _startListening),
          backgroundColor: _listening ? Colors.red : null,
          child: Icon(_listening ? Icons.stop : Icons.mic),
        ),
        const SizedBox(height: 8),
        Text(
          !_speechAvailable
              ? 'Spraakherkenning niet beschikbaar.'
              : _listening
              ? 'Ik luister…'
              : 'Praat met de assistent',
        ),
      ],
    ),
  );

  Widget _chatControls() => Padding(
    padding: EdgeInsets.only(
      left: 12,
      right: 12,
      bottom: 12 + MediaQuery.of(context).viewInsets.bottom,
      top: 4,
    ),
    child: Row(
      children: [
        Expanded(
          child: TextField(
            controller: _chatController,
            decoration: const InputDecoration(hintText: 'Typ een vraag…'),
            onSubmitted: (_) => _sendTyped(),
            enabled: !_busy,
          ),
        ),
        const SizedBox(width: 8),
        IconButton.filled(
          onPressed: _busy ? null : _sendTyped,
          icon: const Icon(Icons.send),
        ),
      ],
    ),
  );
}
