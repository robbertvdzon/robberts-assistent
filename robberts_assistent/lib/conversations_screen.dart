import 'package:flutter/material.dart';

import 'api_client.dart';
import 'assistant_screen.dart';

/// Lijst van eerdere assistent-gesprekken (titel + laatst bijgewerkt), met een actie om een
/// nieuw gesprek te starten. Tikken op een gesprek opent [AssistantScreen] met dat
/// `conversationId`; "nieuw gesprek" opent hetzelfde scherm zonder id (wordt aangemaakt bij het
/// eerste bericht). Analoog aan de tab-structuur van de andere schermen in [HomeScreen].
class ConversationsScreen extends StatefulWidget {
  const ConversationsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<ConversationsScreen> createState() => _ConversationsScreenState();
}

class _ConversationsScreenState extends State<ConversationsScreen> {
  List<AssistantConversationSummary>? _conversations;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final conversations = await widget.api.assistantConversations();
      if (mounted) setState(() => _conversations = conversations);
    } catch (e) {
      if (mounted) setState(() => _error = 'Gesprekken laden mislukt: $e');
    }
  }

  Future<void> _openConversation(String? conversationId) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => AssistantScreen(api: widget.api, conversationId: conversationId),
      ),
    );
    if (mounted) await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: RefreshIndicator(
        onRefresh: _load,
        child: _body(),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openConversation(null),
        icon: const Icon(Icons.add),
        label: const Text('Nieuw gesprek'),
      ),
    );
  }

  Widget _body() {
    if (_error != null) {
      return ListView(
        children: [
          Padding(
            padding: const EdgeInsets.all(24),
            child: Text(_error!, style: const TextStyle(color: Colors.red)),
          ),
        ],
      );
    }
    final conversations = _conversations;
    if (conversations == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (conversations.isEmpty) {
      return ListView(
        children: const [
          Padding(
            padding: EdgeInsets.all(24),
            child: Center(
              child: Text(
                'Nog geen gesprekken. Start hieronder een nieuw gesprek met de assistent.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.black54),
              ),
            ),
          ),
        ],
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.only(bottom: 88),
      itemCount: conversations.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final conversation = conversations[index];
        return ListTile(
          leading: const Icon(Icons.chat_bubble_outline),
          title: Text(conversation.title),
          subtitle: Text(_formatUpdatedAt(conversation.updatedAt)),
          onTap: () => _openConversation(conversation.conversationId),
        );
      },
    );
  }

  String _formatUpdatedAt(DateTime dateTime) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(dateTime.day)}-${two(dateTime.month)}-${dateTime.year} ${two(dateTime.hour)}:${two(dateTime.minute)}';
  }
}
