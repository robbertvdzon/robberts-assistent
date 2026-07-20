import 'package:flutter/material.dart';

import 'api_client.dart';

/// Lijst van geheugen-items (feiten/voorkeuren/context die de assistent automatisch bijhoudt en
/// meegeeft aan latere gesprekken): tonen, toevoegen (dialoog), bewerken (tik op item, dialoog) en
/// verwijderen (icoon-knop met bevestiging).
class MemoryScreen extends StatefulWidget {
  const MemoryScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<MemoryScreen> createState() => _MemoryScreenState();
}

class _MemoryScreenState extends State<MemoryScreen> {
  List<MemoryItem>? _items;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final items = await widget.api.listMemory();
      if (mounted) setState(() => _items = items);
    } catch (e) {
      if (mounted) setState(() => _error = 'Geheugen laden mislukt: $e');
    }
  }

  Future<void> _add() async {
    final text = await _promptForText(title: 'Geheugen-item toevoegen');
    if (text == null || text.isEmpty) return;
    try {
      await widget.api.createMemoryItem(text);
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = 'Toevoegen mislukt: $e');
    }
  }

  Future<void> _edit(MemoryItem item) async {
    final text = await _promptForText(title: 'Geheugen-item bewerken', initialValue: item.text);
    if (text == null || text.isEmpty || text == item.text) return;
    try {
      await widget.api.updateMemoryItem(item.id, text);
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = 'Bewerken mislukt: $e');
    }
  }

  Future<void> _delete(MemoryItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Geheugen-item verwijderen'),
        content: Text('Weet je zeker dat je "${item.text}" wilt verwijderen?'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Annuleren')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Verwijderen')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.deleteMemoryItem(item.id);
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = 'Verwijderen mislukt: $e');
    }
  }

  Future<String?> _promptForText({required String title, String initialValue = ''}) {
    final controller = TextEditingController(text: initialValue);
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(hintText: 'Bijv. "houdt van kiten bij wind > 15 knopen"'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Annuleren')),
          TextButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Geheugen'),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: RefreshIndicator(onRefresh: _load, child: _body()),
      floatingActionButton: FloatingActionButton(onPressed: _add, child: const Icon(Icons.add)),
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
    final items = _items;
    if (items == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (items.isEmpty) {
      return ListView(
        children: const [
          Padding(
            padding: EdgeInsets.all(24),
            child: Center(
              child: Text(
                'Nog geen geheugen-items. De assistent onthoudt zelf feiten/voorkeuren uit'
                ' gesprekken, of voeg er hieronder zelf een toe.',
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
      itemCount: items.length,
      separatorBuilder: (context, index) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final item = items[index];
        return ListTile(
          leading: const Icon(Icons.psychology_outlined),
          title: Text(item.text),
          onTap: () => _edit(item),
          trailing: IconButton(
            tooltip: 'Verwijderen',
            icon: const Icon(Icons.delete_outline),
            onPressed: () => _delete(item),
          ),
        );
      },
    );
  }
}
