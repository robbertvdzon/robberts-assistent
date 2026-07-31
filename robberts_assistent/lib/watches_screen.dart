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
  bool _running = false;
  String? _error;
  var _loadSequence = 0;

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
    final loadSequence = ++_loadSequence;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final watches = await widget.api.listWatches();
      if (mounted && loadSequence == _loadSequence) {
        setState(() => _watches = watches);
      }
    } catch (e) {
      if (mounted && loadSequence == _loadSequence) {
        setState(() => _error = 'Zoekopdrachten laden mislukt: $e');
      }
    } finally {
      if (mounted && loadSequence == _loadSequence) {
        setState(() => _loading = false);
      }
    }
  }

  /// Laat de backend nu meteen alle actieve zoekopdrachten controleren en toont het resultaat.
  /// De bestaande lijst blijft tijdens de run zichtbaar; beide AppBar-knoppen zijn zolang uit.
  ///
  /// Een run start niet zolang er nog een `_load()` loopt: die zou door de opgehoogde
  /// `_loadSequence` zijn eigen `_loading = false` overslaan en het scherm permanent in de
  /// laadspinner laten hangen. Voor de zekerheid zet de `finally` `_loading` hier ook zelf
  /// terug, zolang deze run nog de nieuwste is.
  Future<void> _runNow() async {
    if (_running || _loading) return;
    final loadSequence = ++_loadSequence;
    setState(() => _running = true);
    try {
      final watches = await widget.api.runWatchesNow();
      if (mounted && loadSequence == _loadSequence) {
        setState(() {
          _watches = watches;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Nu controleren mislukt: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _running = false;
          if (loadSequence == _loadSequence) {
            _loading = false;
          }
        });
      }
    }
  }

  Future<void> _add() async {
    final input = await showDialog<_WatchInput>(
      context: context,
      builder: (_) => const _WatchDialog(),
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

  Future<void> _edit(Watch watch) async {
    final input = await showDialog<_WatchInput>(
      context: context,
      builder: (_) => _WatchDialog(watch: watch),
    );
    if (input == null) return;
    try {
      await widget.api.updateWatch(
        id: watch.id,
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
        ).showSnackBar(SnackBar(content: Text('Wijzigen mislukt: $e')));
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
          onPressed: _running ? null : _load,
          icon: const Icon(Icons.refresh),
        ),
        IconButton(
          tooltip: 'Alle zoekopdrachten nu controleren',
          onPressed: (_running || _loading) ? null : _runNow,
          icon: _running
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.play_circle_outline),
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
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: 'Bewerk ${watch.title}',
                          onPressed: () => _edit(watch),
                          icon: const Icon(Icons.edit_outlined),
                        ),
                        IconButton(
                          tooltip: 'Verwijder ${watch.title}',
                          onPressed: () => _delete(watch),
                          icon: const Icon(Icons.delete_outline),
                        ),
                      ],
                    ),
                  ),
                )
                .toList(),
          ),
    floatingActionButton: FloatingActionButton(
      tooltip: 'Nieuwe zoekopdracht',
      // Tijdens een run uit: `_add` doet zelf een `_load()` en zou het runresultaat wegdrukken.
      onPressed: _running ? null : _add,
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

class _WatchDialog extends StatefulWidget {
  const _WatchDialog({this.watch});

  final Watch? watch;

  @override
  State<_WatchDialog> createState() => _WatchDialogState();
}

class _WatchDialogState extends State<_WatchDialog> {
  late final TextEditingController _title;
  late final TextEditingController _url;
  late final TextEditingController _instruction;
  late String _frequency;
  late bool _notify;
  String? _validationError;

  @override
  void initState() {
    super.initState();
    final watch = widget.watch;
    _title = TextEditingController(text: watch?.title);
    _url = TextEditingController(text: watch?.url);
    _instruction = TextEditingController(text: watch?.instruction);
    _frequency = watch?.frequency ?? 'DAGELIJKS';
    _notify = watch?.notifyOnFound ?? true;
  }

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
    title: Text(widget.watch == null ? 'Nieuwe zoekopdracht' : 'Zoekopdracht bewerken'),
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
            isExpanded: true,
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
