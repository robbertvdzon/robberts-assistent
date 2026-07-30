import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';

import 'api_client.dart';

/// Beheer van langdurige zoekopdrachten (watches). Toont een lijst met watches,
/// FAB voor een nieuwe watch, swipe-acties voor verwijderen en toggle actief.
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

  Future<void> _toggle(Watch watch) async {
    try {
      await widget.api.toggleWatch(watch.id);
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Toggle mislukt: $e')));
    }
  }

  Future<void> _delete(Watch watch) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Verwijderen?'),
        content: Text('Weet je zeker dat je "${watch.title}" wilt verwijderen?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuleren')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Verwijderen')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.deleteWatch(watch.id);
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Verwijderen mislukt: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Watches'),
        actions: [IconButton(tooltip: 'Herladen', onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : _watches.isEmpty
                  ? const _Empty()
                  : _buildList(),
      floatingActionButton: FloatingActionButton(
        onPressed: _add,
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildList() {
    return ListView.builder(
      itemCount: _watches.length,
      itemBuilder: (context, index) {
        final watch = _watches[index];
        return Slidable(
          key: ValueKey(watch.id),
          endActionPane: ActionPane(
            motion: const ScrollMotion(),
            children: [
              SlidableAction(
                onPressed: (_) => _toggle(watch),
                backgroundColor: watch.active ? Colors.orange : Colors.green,
                foregroundColor: Colors.white,
                icon: watch.active ? Icons.pause : Icons.play_arrow,
                label: watch.active ? 'Pauzeren' : 'Hervatten',
              ),
              SlidableAction(
                onPressed: (_) => _delete(watch),
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
                icon: Icons.delete,
                label: 'Verwijderen',
              ),
            ],
          ),
          child: ListTile(
            leading: _statusIcon(watch),
            title: Text(watch.title),
            subtitle: Text('${watch.statusLabel} · ${watch.frequencyLabel}${watch.active ? '' : ' · Gepauzeerd'}'),
            trailing: watch.active ? null : const Icon(Icons.pause_circle_outline, color: Colors.grey),
          ),
        );
      },
    );
  }

  Widget _statusIcon(Watch watch) {
    switch (watch.status) {
      case 'GEVONDEN':
        return const Icon(Icons.check_circle, color: Colors.green);
      case 'NIET_GEVONDEN':
        return const Icon(Icons.search, color: Colors.orange);
      default:
        return const Icon(Icons.help_outline, color: Colors.grey);
    }
  }
}

class _Empty extends StatelessWidget {
  const _Empty();
  @override
  Widget build(BuildContext context) => const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Nog geen watches.\nTik op + om er een te maken.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.black54),
          ),
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

  static const _frequencyOptions = {
    'KANTOORUREN': 'Kantooruren (ma-vr 09-17, elk uur)',
    'DAGELIJKS': 'Dagelijks (eenmaal per dag)',
  };

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
              decoration: const InputDecoration(
                labelText: 'Titel',
                hintText: 'Bijv. Aaltjes tegen slakken',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _urlController,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: 'URL',
                hintText: 'https://...',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _instructionController,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Zoekinstructie',
                hintText: 'Bijv. Geef een seintje als dit product op voorraad is',
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _frequency,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Frequentie'),
              items: _frequencyOptions.entries
                  .map((e) => DropdownMenuItem(
                        value: e.key,
                        child: Text(e.value, overflow: TextOverflow.ellipsis),
                      ))
                  .toList(),
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
