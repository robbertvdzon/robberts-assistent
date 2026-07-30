import 'package:flutter/material.dart';

import 'api_client.dart';

/// Beheer van zoekopdrachten (watches): de backend controleert elke actieve zoekopdracht
/// periodiek (kantooruren/dagelijks), haalt de pagina op en laat de AI beoordelen of aan de
/// instructie is voldaan. Deze lijst toont per zoekopdracht de titel en de actuele status;
/// aanmaken/bewerken via een dialoog, verwijderen met bevestiging (zelfde patroon als
/// `conversations_screen.dart`).
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
      if (mounted) setState(() => _watches = watches);
    } catch (e) {
      if (mounted) setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _add() async {
    final result = await showDialog<_WatchFormResult>(
      context: context,
      builder: (_) => const _WatchDialog(),
    );
    if (result == null) return;
    try {
      await widget.api.createWatch(
        title: result.title,
        url: result.url,
        instruction: result.instruction,
        frequency: result.frequency,
        notifyOnFound: result.notifyOnFound,
      );
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Opslaan mislukt: $e')));
    }
  }

  Future<void> _edit(Watch watch) async {
    final result = await showDialog<_WatchFormResult>(
      context: context,
      builder: (_) => _WatchDialog(existing: watch),
    );
    if (result == null) return;
    try {
      await widget.api.updateWatch(
        id: watch.id,
        title: result.title,
        url: result.url,
        instruction: result.instruction,
        frequency: result.frequency,
        notifyOnFound: result.notifyOnFound,
      );
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Opslaan mislukt: $e')));
    }
  }

  Future<void> _confirmDelete(Watch watch) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Zoekopdracht verwijderen'),
        content: Text('Weet je zeker dat je "${watch.title}" wilt verwijderen?'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Annuleren')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Verwijderen')),
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
        title: const Text('Zoekopdrachten'),
        actions: [IconButton(tooltip: 'Herladen', onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : _watches.isEmpty
                  ? const _Empty('Nog geen zoekopdrachten.\nTik op + om er een te maken.')
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView(
                        children: _watches.map((w) => _watchTile(w)).toList(),
                      ),
                    ),
      floatingActionButton: FloatingActionButton(onPressed: _add, child: const Icon(Icons.add)),
    );
  }

  Widget _watchTile(Watch watch) {
    return ListTile(
      leading: Icon(_statusIcon(watch.status), color: _statusColor(watch.status)),
      title: Text(watch.title),
      subtitle: Text(_subtitle(watch)),
      onTap: () => _edit(watch),
      trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _confirmDelete(watch)),
    );
  }

  String _subtitle(Watch watch) {
    final status = switch (watch.status) {
      WatchStatus.gevonden => watch.statusText.isEmpty ? 'Nu beschikbaar' : watch.statusText,
      WatchStatus.nietGevonden => watch.statusText.isEmpty ? 'Nog niet gevonden' : watch.statusText,
      WatchStatus.onbekend => watch.statusText.isEmpty ? 'Nog niet gecontroleerd' : watch.statusText,
    };
    final suffix = watch.active ? '' : ' · gestopt';
    return '$status$suffix';
  }

  IconData _statusIcon(WatchStatus status) => switch (status) {
        WatchStatus.gevonden => Icons.check_circle,
        WatchStatus.nietGevonden => Icons.hourglass_empty,
        WatchStatus.onbekend => Icons.help_outline,
      };

  Color _statusColor(WatchStatus status) => switch (status) {
        WatchStatus.gevonden => Colors.green,
        WatchStatus.nietGevonden => Colors.deepPurple,
        WatchStatus.onbekend => Colors.grey,
      };
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

class _WatchFormResult {
  final String title;
  final String url;
  final String instruction;
  final WatchFrequency frequency;
  final bool notifyOnFound;
  _WatchFormResult(this.title, this.url, this.instruction, this.frequency, this.notifyOnFound);
}

class _WatchDialog extends StatefulWidget {
  const _WatchDialog({this.existing});
  final Watch? existing;

  @override
  State<_WatchDialog> createState() => _WatchDialogState();
}

class _WatchDialogState extends State<_WatchDialog> {
  late final _titleController = TextEditingController(text: widget.existing?.title ?? '');
  late final _urlController = TextEditingController(text: widget.existing?.url ?? '');
  late final _instructionController = TextEditingController(text: widget.existing?.instruction ?? '');
  late WatchFrequency _frequency = widget.existing?.frequency ?? WatchFrequency.dagelijks;
  late bool _notifyOnFound = widget.existing?.notifyOnFound ?? true;

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
      title: Text(widget.existing == null ? 'Nieuwe zoekopdracht' : 'Zoekopdracht bewerken'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _titleController,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Titel'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(labelText: 'Url'),
              keyboardType: TextInputType.url,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _instructionController,
              decoration: const InputDecoration(labelText: 'Instructie'),
              maxLines: 3,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<WatchFrequency>(
              initialValue: _frequency,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Check-frequentie'),
              items: WatchFrequency.values
                  .map((f) => DropdownMenuItem(value: f, child: Text(f.label, overflow: TextOverflow.ellipsis)))
                  .toList(),
              onChanged: (v) => setState(() => _frequency = v ?? WatchFrequency.dagelijks),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Pushmelding als het gevonden is'),
              value: _notifyOnFound,
              onChanged: (v) => setState(() => _notifyOnFound = v),
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
            Navigator.pop(context, _WatchFormResult(title, url, instruction, _frequency, _notifyOnFound));
          },
          child: const Text('Opslaan'),
        ),
      ],
    );
  }
}
