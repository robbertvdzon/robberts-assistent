import 'package:flutter/material.dart';

import 'api_client.dart';

class WatchesScreen extends StatefulWidget {
  const WatchesScreen({super.key, required this.api, this.reloadTrigger = 0});

  final ApiClient api;
  final int reloadTrigger;

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

  @override
  void didUpdateWidget(covariant WatchesScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.reloadTrigger != oldWidget.reloadTrigger) {
      _load();
    }
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
      if (mounted) setState(() => _error = 'Zoekopdrachten laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _add() async {
    final input = await showDialog<_WatchInput>(
      context: context,
      builder: (_) => const _AddWatchDialog(),
    );
    if (input == null) return;
    try {
      await widget.api.createWatch(
        title: input.title,
        url: input.url,
        instruction: input.instruction,
        frequency: input.frequency,
        notifyOnFound: input.notifyOnFound,
      );
      await _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Opslaan mislukt: $e')));
      }
    }
  }

  Future<void> _delete(Watch watch) async {
    try {
      await widget.api.deleteWatch(watch.id);
      await _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Verwijderen mislukt: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Zoekopdrachten'),
      actions: [
        IconButton(
          tooltip: 'Zoekopdrachten herladen',
          onPressed: _load,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: _loading
        ? const Center(child: CircularProgressIndicator())
        : _error != null
        ? Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(_error!),
            ),
          )
        : _watches.isEmpty
        ? const Center(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Text(
                'Nog geen zoekopdrachten.\nTik op + om er een te maken.',
                textAlign: TextAlign.center,
              ),
            ),
          )
        : ListView(
            children: _watches
                .map(
                  (watch) => ListTile(
                    leading: Icon(
                      watch.status == 'GEVONDEN'
                          ? Icons.task_alt
                          : Icons.travel_explore,
                      color: watch.status == 'GEVONDEN'
                          ? Colors.green
                          : Colors.deepPurple,
                    ),
                    title: Text(watch.title),
                    subtitle: Text(watch.statusDescription),
                    trailing: IconButton(
                      tooltip: 'Verwijder ${watch.title}',
                      onPressed: () => _delete(watch),
                      icon: const Icon(Icons.delete_outline),
                    ),
                  ),
                )
                .toList(),
          ),
    floatingActionButton: FloatingActionButton(
      tooltip: 'Nieuwe zoekopdracht',
      onPressed: _add,
      child: const Icon(Icons.add),
    ),
  );
}

class _WatchInput {
  final String title;
  final String url;
  final String instruction;
  final String frequency;
  final bool notifyOnFound;

  const _WatchInput(
    this.title,
    this.url,
    this.instruction,
    this.frequency,
    this.notifyOnFound,
  );
}

class _AddWatchDialog extends StatefulWidget {
  const _AddWatchDialog();

  @override
  State<_AddWatchDialog> createState() => _AddWatchDialogState();
}

class _AddWatchDialogState extends State<_AddWatchDialog> {
  final _title = TextEditingController();
  final _url = TextEditingController();
  final _instruction = TextEditingController();
  var _frequency = 'DAGELIJKS';
  var _notify = true;
  String? _validationError;

  @override
  void dispose() {
    _title.dispose();
    _url.dispose();
    _instruction.dispose();
    super.dispose();
  }

  void _save() {
    final title = _title.text.trim();
    final url = _url.text.trim();
    final instruction = _instruction.text.trim();
    final uri = Uri.tryParse(url);
    String? error;
    if (title.isEmpty) {
      error = 'Vul een titel in.';
    } else if (uri == null ||
        !uri.isAbsolute ||
        !{'http', 'https'}.contains(uri.scheme.toLowerCase()) ||
        uri.host.isEmpty) {
      error = 'Vul een geldige absolute HTTP(S)-URL in.';
    } else if (instruction.isEmpty) {
      error = 'Vul een zoekinstructie in.';
    }
    if (error != null) {
      setState(() => _validationError = error);
      return;
    }
    Navigator.pop(
      context,
      _WatchInput(title, url, instruction, _frequency, _notify),
    );
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Nieuwe zoekopdracht'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _title,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'Titel'),
          ),
          TextField(
            controller: _url,
            keyboardType: TextInputType.url,
            decoration: const InputDecoration(labelText: 'Webadres'),
          ),
          TextField(
            controller: _instruction,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(labelText: 'Zoekinstructie'),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _frequency,
            decoration: const InputDecoration(labelText: 'Controlefrequentie'),
            items: const [
              DropdownMenuItem(value: 'DAGELIJKS', child: Text('Dagelijks')),
              DropdownMenuItem(
                value: 'KANTOORUREN',
                child: Text('Kantooruren'),
              ),
            ],
            onChanged: (value) =>
                setState(() => _frequency = value ?? 'DAGELIJKS'),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Pushmelding bij vondst'),
            value: _notify,
            onChanged: (value) => setState(() => _notify = value),
          ),
          if (_validationError != null)
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                _validationError!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Annuleren'),
      ),
      FilledButton(onPressed: _save, child: const Text('Opslaan')),
    ],
  );
}
