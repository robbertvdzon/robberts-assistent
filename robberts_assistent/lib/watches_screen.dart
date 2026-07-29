import 'package:flutter/material.dart';

import 'api_client.dart';

/// Beheer van langlopende zoekopdrachten: houd een webpagina in de gaten en krijg een seintje
/// zodra datgene waar je op wacht er staat (bv. "de aaltjes zijn weer op voorraad").
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

  /// Id's waarvan op dit moment een "nu controleren" loopt (spinner op die rij).
  final _checking = <String>{};

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
      if (!mounted) return;
      setState(() => _watches = watches);
    } catch (e) {
      if (mounted) setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _snack(String message) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _addOrEdit([Watch? existing]) async {
    final result = await showDialog<WatchFormResult>(
      context: context,
      builder: (_) => WatchDialog(existing: existing),
    );
    if (result == null) return;
    try {
      if (existing == null) {
        await widget.api.createWatch(
          title: result.title,
          url: result.url,
          instruction: result.instruction,
          frequency: result.frequency,
          pushOnFound: result.pushOnFound,
        );
      } else {
        await widget.api.updateWatch(
          id: existing.id,
          title: result.title,
          url: result.url,
          instruction: result.instruction,
          frequency: result.frequency,
          pushOnFound: result.pushOnFound,
          active: existing.active,
        );
      }
    } catch (e) {
      _snack('Opslaan mislukt: $e');
      return;
    }
    await _load();
  }

  Future<void> _checkNow(Watch watch) async {
    setState(() => _checking.add(watch.id));
    try {
      final updated = await widget.api.checkWatch(watch.id);
      if (!mounted) return;
      setState(() {
        _watches = [
          for (final w in _watches)
            if (w.id == updated.id) updated else w,
        ];
      });
    } catch (e) {
      _snack('Controleren mislukt: $e');
    } finally {
      if (mounted) setState(() => _checking.remove(watch.id));
    }
  }

  Future<void> _togglePaused(Watch watch) async {
    try {
      await widget.api.updateWatch(
        id: watch.id,
        title: watch.title,
        url: watch.url,
        instruction: watch.instruction,
        frequency: watch.frequency,
        pushOnFound: watch.pushOnFound,
        active: !watch.active,
      );
    } catch (e) {
      _snack('Bijwerken mislukt: $e');
      return;
    }
    await _load();
  }

  Future<void> _delete(Watch watch) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Zoekopdracht verwijderen?'),
        content: Text('"${watch.title}" wordt definitief verwijderd.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annuleren')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Verwijderen')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.deleteWatch(watch.id);
    } catch (e) {
      _snack('Verwijderen mislukt: $e');
      return;
    }
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Zoekopdrachten'),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : _watches.isEmpty
                  ? const Center(
                      child: Padding(
                        padding: EdgeInsets.all(24),
                        child: Text(
                          'Nog geen zoekopdrachten.\nTik op + om een pagina in de gaten te laten houden.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.black54),
                        ),
                      ),
                    )
                  : ListView(children: _watches.map(_tile).toList()),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _addOrEdit(),
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _tile(Watch watch) {
    final busy = _checking.contains(watch.id);
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      // Gevonden = visueel gemarkeerd.
      color: watch.found ? Colors.green.shade50 : null,
      child: ListTile(
        onTap: () => _addOrEdit(watch),
        leading: Icon(
          watch.found
              ? Icons.check_circle
              : watch.active
                  ? Icons.travel_explore
                  : Icons.pause_circle_outline,
          color: watch.found ? Colors.green.shade700 : Colors.deepPurple,
        ),
        title: Text(
          watch.title,
          style: TextStyle(fontWeight: watch.found ? FontWeight.bold : FontWeight.normal),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(watch.lastStatus ?? 'Nog niet gecontroleerd.'),
            Text(
              _footer(watch),
              style: const TextStyle(fontSize: 12, color: Colors.black54),
            ),
            if (watch.lastError != null)
              Text(
                'Fout: ${watch.lastError}',
                style: TextStyle(fontSize: 12, color: Colors.red.shade700),
              ),
          ],
        ),
        isThreeLine: true,
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            busy
                ? const Padding(
                    padding: EdgeInsets.all(12),
                    child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : IconButton(
                    tooltip: 'Nu controleren',
                    icon: const Icon(Icons.refresh),
                    onPressed: () => _checkNow(watch),
                  ),
            IconButton(
              tooltip: watch.active ? 'Pauzeren' : 'Hervatten',
              icon: Icon(watch.active ? Icons.pause : Icons.play_arrow),
              onPressed: () => _togglePaused(watch),
            ),
            IconButton(
              tooltip: 'Verwijderen',
              icon: const Icon(Icons.delete_outline),
              onPressed: () => _delete(watch),
            ),
          ],
        ),
      ),
    );
  }

  String _footer(Watch watch) {
    final parts = <String>[
      watch.lastCheckedAt == null ? 'nog niet gecontroleerd' : 'gecontroleerd ${_moment(watch.lastCheckedAt!)}',
      watchFrequencyLabel(watch.frequency),
      if (!watch.active) 'gepauzeerd',
    ];
    return parts.join(' · ');
  }

  String _moment(DateTime when) {
    final hh = when.hour.toString().padLeft(2, '0');
    final mm = when.minute.toString().padLeft(2, '0');
    return '${when.day}-${when.month}-${when.year} $hh:$mm';
  }
}

/// Nederlandse omschrijving van een frequentie-code van de backend.
String watchFrequencyLabel(String frequency) =>
    frequency == 'KANTOORUREN' ? 'elk uur tijdens kantooruren' : 'één keer per dag';

/// Ingevulde waarden uit [WatchDialog].
class WatchFormResult {
  final String title;
  final String url;
  final String instruction;
  final String frequency;
  final bool pushOnFound;

  const WatchFormResult({
    required this.title,
    required this.url,
    required this.instruction,
    required this.frequency,
    required this.pushOnFound,
  });
}

/// Aanmaak-/bewerkdialoog voor één zoekopdracht.
class WatchDialog extends StatefulWidget {
  const WatchDialog({super.key, this.existing});

  final Watch? existing;

  @override
  State<WatchDialog> createState() => _WatchDialogState();
}

class _WatchDialogState extends State<WatchDialog> {
  late final TextEditingController _title = TextEditingController(text: widget.existing?.title ?? '');
  late final TextEditingController _url = TextEditingController(text: widget.existing?.url ?? '');
  late final TextEditingController _instruction =
      TextEditingController(text: widget.existing?.instruction ?? '');
  late String _frequency = widget.existing?.frequency ?? 'DAGELIJKS';
  late bool _pushOnFound = widget.existing?.pushOnFound ?? true;
  String? _validation;

  @override
  void dispose() {
    _title.dispose();
    _url.dispose();
    _instruction.dispose();
    super.dispose();
  }

  void _submit() {
    final title = _title.text.trim();
    final url = _url.text.trim();
    if (title.isEmpty) {
      setState(() => _validation = 'Vul een titel in.');
      return;
    }
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      setState(() => _validation = 'Vul een link in die met http:// of https:// begint.');
      return;
    }
    Navigator.pop(
      context,
      WatchFormResult(
        title: title,
        url: url,
        instruction: _instruction.text.trim(),
        frequency: _frequency,
        pushOnFound: _pushOnFound,
      ),
    );
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
              controller: _title,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Titel'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _url,
              decoration: const InputDecoration(labelText: 'Link (URL)'),
              keyboardType: TextInputType.url,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _instruction,
              minLines: 2,
              maxLines: 4,
              decoration: const InputDecoration(
                labelText: 'Waar moet hij op letten?',
                hintText: 'meld het als de aaltjes weer op voorraad zijn',
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _frequency,
              decoration: const InputDecoration(labelText: 'Hoe vaak kijken?'),
              items: const [
                DropdownMenuItem(value: 'KANTOORUREN', child: Text('Elk uur tijdens kantooruren')),
                DropdownMenuItem(value: 'DAGELIJKS', child: Text('Eén keer per dag')),
              ],
              onChanged: (v) => setState(() => _frequency = v ?? 'DAGELIJKS'),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Pushbericht zodra het gevonden is'),
              value: _pushOnFound,
              onChanged: (v) => setState(() => _pushOnFound = v),
            ),
            if (_validation != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(_validation!, style: TextStyle(color: Colors.red.shade700)),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(onPressed: _submit, child: const Text('Opslaan')),
      ],
    );
  }
}
