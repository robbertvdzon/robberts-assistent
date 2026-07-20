import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';

import 'api_client.dart';
import 'assistant_screen.dart';

const int _pageSize = 10;

/// Lijst van eerdere assistent-gesprekken (titel + laatst bijgewerkt), met een actie om een
/// nieuw gesprek te starten. Tikken op een gesprek opent [AssistantScreen] met dat
/// `conversationId`; "nieuw gesprek" opent hetzelfde scherm zonder id (wordt aangemaakt bij het
/// eerste bericht). De eerste [_pageSize] (niet-gearchiveerde) gesprekken staan direct in de
/// lijst; oudere gesprekken staan onder een uitklapbare "Ouder"-sectie die pas bij het uitklappen
/// wordt opgehaald. Swipe-links toont Archiveren/Verwijderen; een AppBar-toggle laat gearchiveerde
/// gesprekken alsnog zien.
class ConversationsScreen extends StatefulWidget {
  const ConversationsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<ConversationsScreen> createState() => _ConversationsScreenState();
}

class _ConversationsScreenState extends State<ConversationsScreen> {
  List<AssistantConversationSummary>? _conversations;
  List<AssistantConversationSummary>? _older;
  bool _olderExpanded = false;
  bool _includeArchived = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final conversations = await widget.api.assistantConversations(
        includeArchived: _includeArchived,
        limit: _pageSize,
      );
      if (mounted) {
        setState(() {
          _conversations = conversations;
          _older = null;
          _olderExpanded = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = 'Gesprekken laden mislukt: $e');
    }
  }

  Future<void> _loadOlder() async {
    try {
      final older = await widget.api.assistantConversations(
        includeArchived: _includeArchived,
        offset: _pageSize,
      );
      if (mounted) setState(() => _older = older);
    } catch (e) {
      if (mounted) setState(() => _error = 'Oudere gesprekken laden mislukt: $e');
    }
  }

  void _toggleIncludeArchived() {
    setState(() => _includeArchived = !_includeArchived);
    _load();
  }

  Future<void> _openConversation(String? conversationId) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => AssistantScreen(api: widget.api, conversationId: conversationId),
      ),
    );
    if (mounted) await _load();
  }

  Future<void> _archive(AssistantConversationSummary conversation) async {
    try {
      if (conversation.archived) {
        await widget.api.unarchiveConversation(conversation.conversationId);
      } else {
        await widget.api.archiveConversation(conversation.conversationId);
      }
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = 'Archiveren mislukt: $e');
    }
  }

  Future<void> _confirmDelete(AssistantConversationSummary conversation) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Gesprek verwijderen'),
        content: Text('Weet je zeker dat je "${conversation.title}" wilt verwijderen? Dit kan niet ongedaan gemaakt worden.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Annuleren')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Verwijderen')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.deleteConversation(conversation.conversationId);
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = 'Verwijderen mislukt: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Gesprekken'),
        actions: [
          IconButton(
            icon: Icon(_includeArchived ? Icons.archive : Icons.archive_outlined),
            tooltip: 'Toon gearchiveerd',
            onPressed: _toggleIncludeArchived,
          ),
        ],
      ),
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
    final hasOlder = conversations.length >= _pageSize;
    return ListView(
      padding: const EdgeInsets.only(bottom: 88),
      children: [
        ...conversations.map(_conversationTile),
        if (hasOlder) _olderSection(),
      ],
    );
  }

  Widget _olderSection() {
    return ExpansionTile(
      title: const Text('Ouder'),
      initiallyExpanded: _olderExpanded,
      onExpansionChanged: (expanded) {
        setState(() => _olderExpanded = expanded);
        if (expanded && _older == null) _loadOlder();
      },
      children: [
        if (_olderExpanded && _older == null)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          )
        else
          ...(_older ?? const []).map(_conversationTile),
      ],
    );
  }

  Widget _conversationTile(AssistantConversationSummary conversation) {
    return Slidable(
      key: ValueKey(conversation.conversationId),
      endActionPane: ActionPane(
        motion: const DrawerMotion(),
        extentRatio: 0.5,
        children: [
          SlidableAction(
            onPressed: (_) => _archive(conversation),
            backgroundColor: Colors.blueGrey,
            foregroundColor: Colors.white,
            icon: conversation.archived ? Icons.unarchive : Icons.archive,
            label: conversation.archived ? 'Herstellen' : 'Archiveren',
          ),
          SlidableAction(
            onPressed: (_) => _confirmDelete(conversation),
            backgroundColor: Colors.red,
            foregroundColor: Colors.white,
            icon: Icons.delete,
            label: 'Verwijderen',
          ),
        ],
      ),
      child: ListTile(
        leading: Icon(conversation.archived ? Icons.archive_outlined : Icons.chat_bubble_outline),
        title: Text(conversation.title),
        subtitle: Text(_formatUpdatedAt(conversation.updatedAt)),
        onTap: () => _openConversation(conversation.conversationId),
      ),
    );
  }

  String _formatUpdatedAt(DateTime dateTime) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(dateTime.day)}-${two(dateTime.month)}-${dateTime.year} ${two(dateTime.hour)}:${two(dateTime.minute)}';
  }
}
