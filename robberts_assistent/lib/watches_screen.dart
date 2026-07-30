import 'package:flutter/material.dart';

import 'api_client.dart';

/// Overzicht van langdurige zoekopdrachten (watches): toont status, laatst gecheckt, en biedt
/// CRUD via dialogen (aanmaken/verwijderen).
class WatchesScreen extends StatefulWidget {
  const WatchesScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<WatchesScreen> createState() => _WatchesScreenState();
}

class _WatchesScreenState extends State<WatchesScreen> {
  List<Watch> _watches = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final watches = await widget.api.listWatches();
      setState(() => _watches = watches);
    } catch (e) {
      setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _add() async {
    final result = await showDialog<_NewWatch>(
      context: context,
      builder: (_) => const _AddWatchDialog(),
    );
    if (result == null) return;
    try {
      await widget.api.createWatch(
        title: result.title,
        url: result.url,
        instruction: result.instruction,
        frequency: result.frequency,
      );
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Opslaan mislukt: $e')));
      return;
    }
    await _load();
  }

  Future<void> _delete(Watch watch) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Watch verwijderen'),
        content: Text("'${watch.title}' definitief verwijderen?"),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuleren')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Verwijderen')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await widget.api.deleteWatch(watch.id);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Verwijderen mislukt: $e')));
      return;
    }
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Watches'),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : _watches.isEmpty
                  ? const _Empty('Nog geen watches.\nTik op + om er een te maken.')
                  : ListView(
                      children: _watches.map((w) => _WatchTile(watch: w, onDelete: () => _delete(w))).toList(),
                    ),
      floatingActionButton: FloatingActionButton(
        onPressed: _add,
        child: const Icon(Icons.add),
      ),
    );
  }
}

class _WatchTile extends StatelessWidget {
  const _WatchTile({required this.watch, required this.onDelete});
  final Watch watch;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: _statusIcon(watch.status),
      title: Text(watch.title),
      subtitle: Text(_subtitle()),
      trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: onDelete),
      isThreeLine: watch.statusText != null,
    );
  }

  Widget _statusIcon(String status) {
    switch (status) {
      case 'GEVONDEN':
        return const Icon(Icons.check_circle, color: Colors.green);
      case 'NIET_GEVONDEN':
        return const Icon(Icons.cancel, color: Colors.red);
      default:
        return const Icon(Icons.help_outline, color: Colors.grey);
    }
  }

  String _subtitle() {
    final parts = <String>[];
    if (watch.statusText != null && watch.statusText!.isNotEmpty) {
      parts.add(watch.statusText!);
    }
    if (watch.lastChecked != null) {
      final lc = watch.lastChecked!;
      parts.add('Laatst: ${lc.day}-${lc.month} ${lc.hour.toString().padLeft(2, '0')}:${lc.minute.toString().padLeft(2, '0')}');
    }
    final freq = watch.frequency == 'KANTOORUREN' ? 'Kantooruren' : 'Dagelijks';
    parts.add(freq);
    if (!watch.active) parts.add('(inactief)');
    return parts.join(' · ');
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(text, textAlign: TextAlign.center, style: const TextStyle(color: Colors.black54)),
        ),
      );
}

class _NewWatch {
  final String title;
  final String url;
  final String instruction;
  final String frequency;
  _NewWatch(this.title, this.url, this.instruction, this.frequency);
}

class _AddWatchDialog extends StatefulWidget {
  const _AddWatchDialog();
  @override
  State<_AddWatchDialog> createState() => _AddWatchDialogState();
}

class _AddWatchDialogState extends State<_AddWatchDialog> {
  final _titleController = TextEditingController();
  final _urlController = TextEditingController();
  final _instructionController = TextEditingController();
  String _frequency = 'DAGELIJKS';

  @override
  void dispose() {
    _titleController.dispose();
    _urlController.dispose();
    _instructionController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Nieuwe watch'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _titleController,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Titel', hintText: 'bv. Aaltjes beschikbaar'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(labelText: 'URL', hintText: 'https://...'),
              keyboardType: TextInputType.url,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _instructionController,
              decoration: const InputDecoration(
                labelText: 'Instructie',
                hintText: 'bv. Geef GEVONDEN als "op voorraad" op de pagina staat',
              ),
              maxLines: 3,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _frequency,
              decoration: const InputDecoration(labelText: 'Frequentie'),
              items: const [
                DropdownMenuItem(value: 'DAGELIJKS', child: Text('Dagelijks')),
                DropdownMenuItem(value: 'KANTOORUREN', child: Text('Kantooruren (ma-vr 9-17)')),
              ],
              onChanged: (v) => setState(() => _frequency = v ?? 'DAGELIJKS'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(
          onPressed: () {
            final title = _titleController.text.trim();
            final url = _urlController.text.trim();
            final instruction = _instructionController.text.trim();
            if (title.isEmpty || url.isEmpty || instruction.isEmpty) return;
            Navigator.pop(context, _NewWatch(title, url, instruction, _frequency));
          },
          child: const Text('Opslaan'),
        ),
      ],
    );
  }
}
