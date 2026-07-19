import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:speech_to_text/speech_to_text.dart';

import 'api_client.dart';

class ChatMessage {
  const ChatMessage({required this.fromUser, required this.text, this.images = const []});

  final bool fromUser;
  final String text;
  final List<Uint8List> images;
}

enum _Mode { voice, chat }

/// Eén gesprek met de assistent: "Praat met de assistent" (spraak in, gesproken antwoord terug)
/// met een wisselknop naar chat (getypt, met foto-ondersteuning). Zonder [conversationId] wordt bij
/// het eerste bericht een nieuw, persistent gesprek aangemaakt (zie backend `AssistantService`);
/// met [conversationId] wordt eerst de bestaande geschiedenis geladen. Beide modi delen dezelfde
/// [_history], zodat wisselen heen-en-terug niets van het gesprek verliest.
class AssistantScreen extends StatefulWidget {
  const AssistantScreen({super.key, required this.api, this.conversationId});

  final ApiClient api;
  final String? conversationId;

  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen> {
  final _history = <ChatMessage>[];
  final _speech = SpeechToText();
  final _tts = FlutterTts();
  final _chatController = TextEditingController();
  final _scrollController = ScrollController();
  final _picker = ImagePicker();
  final _pending = <AssistantAttachment>[];

  _Mode _mode = _Mode.voice;
  var _listening = false;
  var _speechAvailable = false;
  var _busy = false;
  var _loadingHistory = false;
  String _partialTranscript = '';
  String? _error;
  String? _conversationId;
  String _title = 'Nieuw gesprek';

  @override
  void initState() {
    super.initState();
    _conversationId = widget.conversationId;
    _tts.setLanguage('nl-NL');
    _initSpeech();
    if (_conversationId != null) _loadHistory(_conversationId!);
  }

  /// Laadt de bestaande berichten (incl. foto's) van een eerder gesprek.
  Future<void> _loadHistory(String id) async {
    setState(() => _loadingHistory = true);
    try {
      final detail = await widget.api.assistantConversation(id);
      final messages = <ChatMessage>[];
      for (final m in detail.messages) {
        final images = <Uint8List>[];
        for (final imageId in m.imageIds) {
          try {
            images.add(await widget.api.fetchAssistantPhoto(imageId));
          } catch (_) {
            // Foto (tijdelijk) niet op te halen — bericht blijft zonder foto zichtbaar.
          }
        }
        messages.add(ChatMessage(fromUser: m.fromUser, text: m.text, images: images));
      }
      if (!mounted) return;
      setState(() {
        _history
          ..clear()
          ..addAll(messages);
        _title = detail.title;
      });
      _scrollToBottom();
    } catch (e) {
      if (mounted) setState(() => _error = 'Gesprek laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loadingHistory = false);
    }
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
    if (text.isEmpty && _pending.isEmpty) return;
    _chatController.clear();
    await _send(text, speakReply: false);
  }

  Future<void> _addFromCamera() async {
    final file = await _picker.pickImage(source: ImageSource.camera, imageQuality: 70);
    if (file != null) await _attach([file]);
  }

  Future<void> _addFromGallery() async {
    final files = await _picker.pickMultiImage(imageQuality: 70);
    if (files.isNotEmpty) await _attach(files);
  }

  Future<void> _attach(List<XFile> files) async {
    for (final file in files) {
      final bytes = await file.readAsBytes();
      final contentType = file.mimeType ??
          (file.name.toLowerCase().endsWith('.png') ? 'image/png' : 'image/jpeg');
      _pending.add(AssistantAttachment(bytes: bytes, filename: file.name, contentType: contentType));
    }
    if (mounted) setState(() {});
  }

  void _showAttachSheet() {
    showModalBottomSheet<void>(
      context: context,
      builder: (_) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera),
              title: const Text('Foto maken'),
              onTap: () {
                Navigator.pop(context);
                _addFromCamera();
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Uit galerij kiezen'),
              onTap: () {
                Navigator.pop(context);
                _addFromGallery();
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _send(String text, {required bool speakReply}) async {
    if (text.isEmpty && _pending.isEmpty) return;
    final attachments = List<AssistantAttachment>.from(_pending);
    setState(() {
      _history.add(ChatMessage(
        fromUser: true,
        text: text,
        images: attachments.map((a) => a.bytes).toList(),
      ));
      _pending.clear();
      _busy = true;
      _error = null;
    });
    _scrollToBottom();
    try {
      final result = await widget.api.assistantChat(
        message: text,
        conversationId: _conversationId,
        photos: attachments,
      );
      setState(() {
        _conversationId = result.conversationId;
        _title = result.title;
        _history.add(ChatMessage(fromUser: false, text: result.reply));
      });
      _scrollToBottom();
      if (speakReply && result.reply.isNotEmpty) {
        await _tts.speak(result.reply);
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
    return Scaffold(
      appBar: AppBar(title: Text(_title)),
      body: Column(
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
          if (_loadingHistory) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          Expanded(child: _history.isEmpty ? _emptyState() : _historyList()),
          if (_pending.isNotEmpty) _pendingPreview(),
          if (_mode == _Mode.voice) _voiceControls() else _chatControls(),
        ],
      ),
    );
  }

  Widget _emptyState() => Center(
    child: Text(
      _mode == _Mode.voice
          ? 'Tik op de microfoon en stel je vraag.'
          : 'Typ hieronder een vraag aan de assistent. Je kunt er ook een foto bij sturen.',
      textAlign: TextAlign.center,
      style: const TextStyle(color: Colors.black54),
    ),
  );

  Widget _historyList() => ListView.builder(
    controller: _scrollController,
    padding: const EdgeInsets.all(16),
    itemCount: _history.length,
    itemBuilder: (context, index) => _bubble(context, _history[index]),
  );

  Widget _bubble(BuildContext context, ChatMessage message) => Align(
    alignment: message.fromUser ? Alignment.centerRight : Alignment.centerLeft,
    child: Column(
      crossAxisAlignment: message.fromUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
      children: [
        if (message.images.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Wrap(
              alignment: message.fromUser ? WrapAlignment.end : WrapAlignment.start,
              spacing: 6,
              runSpacing: 6,
              children: message.images
                  .map((b) => ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.memory(b, width: 96, height: 96, fit: BoxFit.cover),
                      ))
                  .toList(),
            ),
          ),
        if (message.text.isNotEmpty)
          Container(
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
      ],
    ),
  );

  Widget _pendingPreview() => SizedBox(
    height: 76,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _pending.length,
        separatorBuilder: (_, __) => const SizedBox(width: 6),
        itemBuilder: (_, i) => Stack(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.memory(_pending[i].bytes, width: 64, height: 64, fit: BoxFit.cover),
            ),
            Positioned(
              right: 0,
              top: 0,
              child: GestureDetector(
                onTap: () => setState(() => _pending.removeAt(i)),
                child: Container(
                  decoration: const BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
                  child: const Icon(Icons.close, size: 16, color: Colors.white),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
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
      left: 4,
      right: 12,
      bottom: 12 + MediaQuery.of(context).viewInsets.bottom,
      top: 4,
    ),
    child: Row(
      children: [
        IconButton(
          onPressed: _busy ? null : _showAttachSheet,
          icon: const Icon(Icons.add_a_photo),
          tooltip: 'Foto toevoegen',
        ),
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
