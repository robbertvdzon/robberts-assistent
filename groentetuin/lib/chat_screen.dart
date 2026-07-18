import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'api_client.dart';

/// Eén bericht in de chat-UI. [images] zijn de (lokaal getoonde) foto's bij een gebruikersbericht.
class _ChatMessage {
  final bool fromUser;
  final String text;
  final List<Uint8List> images;
  _ChatMessage({required this.fromUser, required this.text, this.images = const []});
}

/// De moestuin-AI-chat: tekst + foto's sturen, antwoord tonen, en blijven doorpraten.
class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _picker = ImagePicker();
  final _controller = TextEditingController();
  final _scroll = ScrollController();
  final List<_ChatMessage> _messages = [];
  final List<GardenAttachment> _pending = [];
  String? _conversationId;
  bool _sending = false;
  String? _error;

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
      _pending.add(GardenAttachment(bytes: bytes, filename: file.name, contentType: contentType));
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

  Future<void> _send() async {
    final text = _controller.text.trim();
    if (text.isEmpty && _pending.isEmpty) return;

    final attachments = List<GardenAttachment>.from(_pending);
    setState(() {
      _messages.add(_ChatMessage(
        fromUser: true,
        text: text,
        images: attachments.map((a) => a.bytes).toList(),
      ));
      _pending.clear();
      _controller.clear();
      _sending = true;
      _error = null;
    });
    _scrollToBottom();

    try {
      final reply = await widget.api.gardenChat(
        message: text,
        conversationId: _conversationId,
        photos: attachments,
      );
      setState(() {
        _conversationId = reply.conversationId;
        _messages.add(_ChatMessage(fromUser: false, text: reply.reply));
      });
    } catch (e) {
      setState(() => _error = 'Versturen mislukt: $e');
    } finally {
      if (mounted) setState(() => _sending = false);
      _scrollToBottom();
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Moestuin-assistent'),
        actions: [
          IconButton(
            onPressed: widget.onLoggedOut,
            icon: const Icon(Icons.logout),
            tooltip: 'Uitloggen',
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _messages.isEmpty
                ? const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24),
                      child: Text(
                        'Stel een vraag over je moestuin.\nJe kunt er foto\'s bij sturen.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.black54),
                      ),
                    ),
                  )
                : ListView.builder(
                    controller: _scroll,
                    padding: const EdgeInsets.all(12),
                    itemCount: _messages.length,
                    itemBuilder: (_, i) => _bubble(_messages[i]),
                  ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          if (_pending.isNotEmpty) _pendingPreview(),
          _inputBar(),
        ],
      ),
    );
  }

  Widget _bubble(_ChatMessage m) {
    final align = m.fromUser ? CrossAxisAlignment.end : CrossAxisAlignment.start;
    final color = m.fromUser ? Colors.green.shade100 : Colors.grey.shade200;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: align,
        children: [
          if (m.images.isNotEmpty)
            Wrap(
              alignment: m.fromUser ? WrapAlignment.end : WrapAlignment.start,
              spacing: 6,
              runSpacing: 6,
              children: m.images
                  .map((b) => ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.memory(b, width: 96, height: 96, fit: BoxFit.cover),
                      ))
                  .toList(),
            ),
          if (m.text.isNotEmpty)
            Container(
              margin: const EdgeInsets.only(top: 4),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              constraints: const BoxConstraints(maxWidth: 320),
              decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(14)),
              child: Text(m.text),
            ),
        ],
      ),
    );
  }

  Widget _pendingPreview() => Container(
        height: 76,
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
      );

  Widget _inputBar() => SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
          child: Row(
            children: [
              IconButton(
                onPressed: _sending ? null : _showAttachSheet,
                icon: const Icon(Icons.add_a_photo),
                tooltip: 'Foto toevoegen',
              ),
              Expanded(
                child: TextField(
                  controller: _controller,
                  minLines: 1,
                  maxLines: 4,
                  textInputAction: TextInputAction.send,
                  decoration: const InputDecoration(
                    hintText: 'Typ een bericht...',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  onSubmitted: (_) => _send(),
                ),
              ),
              const SizedBox(width: 6),
              _sending
                  ? const Padding(
                      padding: EdgeInsets.all(10),
                      child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
                    )
                  : IconButton.filled(onPressed: _send, icon: const Icon(Icons.send)),
            ],
          ),
        ),
      );
}
