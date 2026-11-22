import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api_client.dart';

class WatchesScreen extends StatefulWidget {
  const WatchesScreen({super.key, required this.api});

  final ApiClient api;

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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Nu controleren mislukt: $e')));
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
            padding: EdgeInsets.fromLTRB(
              12,
              8,
              12,
              88 + MediaQuery.viewPaddingOf(context).bottom,
            ),
            children: [for (final watch in _watches) _card(watch)],
          ),
    floatingActionButton: FloatingActionButton(
      heroTag: 'nieuwe-zoekopdracht',
      tooltip: 'Nieuwe zoekopdracht',
      // Tijdens een run uit: `_add` doet zelf een `_load()` en zou het runresultaat wegdrukken.
      onPressed: _running ? null : _add,
      child: const Icon(Icons.add),
    ),
  );

  Future<void> _acknowledge(Watch watch) async {
    try {
      final watches = await widget.api.acknowledgeWatch(watch.id);
      if (mounted) setState(() => _watches = watches);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Bevestigen mislukt: $e')));
      }
    }
  }

  Future<void> _openPage(Watch watch) async {
    final uri = Uri.tryParse(watch.url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      // Niet te openen: de zoekopdracht zelf blijft bruikbaar.
    }
  }

  /// Eén zoekopdracht: bovenaan in woorden wat de stand is, daaronder wat de pagina zei en hoe
  /// lang het al zo is. Een vondst blijft opvallen tot hij met 'Gezien' is bevestigd.
  Widget _card(Watch watch) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    final state = watchState(watch, DateTime.now());
    return Card(
      key: ValueKey('zoekopdracht-${watch.id}'),
      margin: const EdgeInsets.symmetric(vertical: 6),
      color: watch.needsAcknowledge ? const Color(0xFFE9F6E9) : null,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 6, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    watch.title,
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 16,
                    ),
                  ),
                ),
                IconButton(
                  tooltip: 'Bewerk ${watch.title}',
                  visualDensity: VisualDensity.compact,
                  onPressed: () => _edit(watch),
                  icon: const Icon(Icons.edit_outlined),
                ),
                IconButton(
                  tooltip: 'Verwijder ${watch.title}',
                  visualDensity: VisualDensity.compact,
                  onPressed: () => _delete(watch),
                  icon: const Icon(Icons.delete_outline),
                ),
              ],
            ),
            Row(
              children: [
                Icon(state.icon, size: 20, color: state.color),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    state.headline,
                    style: TextStyle(
                      color: state.color,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Text(watch.statusDescription),
            ),
            if (watch.note.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4, right: 8),
                child: Text(watch.note),
              ),
            if (state.detail.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 6, right: 8),
                child: Text(state.detail, style: muted),
              ),
            Wrap(
              spacing: 4,
              children: [
                if (watch.needsAcknowledge)
                  Padding(
                    padding: const EdgeInsets.only(top: 6, right: 4),
                    child: FilledButton.icon(
                      key: ValueKey('gezien-${watch.id}'),
                      onPressed: () => _acknowledge(watch),
                      icon: const Icon(Icons.check, size: 18),
                      label: const Text('Gezien'),
                    ),
                  ),
                TextButton.icon(
                  onPressed: () => _openPage(watch),
                  icon: const Icon(Icons.open_in_new, size: 18),
                  label: const Text('Pagina openen'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Hoe een zoekopdracht er in woorden voor staat.
class WatchState {
  const WatchState(this.icon, this.color, this.headline, this.detail);

  final IconData icon;
  final Color color;
  final String headline;

  /// Hoe lang het al zo is en wanneer er voor het laatst is gekeken.
  final String detail;
}

const _watchMonths = [
  'jan',
  'feb',
  'mrt',
  'apr',
  'mei',
  'jun',
  'jul',
  'aug',
  'sep',
  'okt',
  'nov',
  'dec',
];

String _day(DateTime d) => '${d.day} ${_watchMonths[d.month - 1]}';

String _clock(DateTime d) =>
    '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

/// "vandaag", "sinds gisteren", "3 dagen", "2 weken", "3 maanden".
String watchDuration(DateTime since, DateTime now) {
  final days = DateTime(
    now.year,
    now.month,
    now.day,
  ).difference(DateTime(since.year, since.month, since.day)).inDays;
  if (days <= 0) return 'vandaag';
  if (days == 1) return '1 dag';
  if (days < 14) return '$days dagen';
  if (days < 60) return '${days ~/ 7} weken';
  return '${days ~/ 30} maanden';
}

WatchState watchState(Watch watch, DateTime now) {
  const green = Color(0xFF0A6D0A);
  const grey = Color(0xFF4B545E);
  const amber = Color(0xFF8A5C05);
  final checked = watch.lastCheckedAt;
  final lastCheck = checked == null
      ? ''
      : 'Laatst gecontroleerd: ${_day(checked)} ${_clock(checked)}.';
  switch (watch.status) {
    case 'GEVONDEN':
      final when = watch.foundAt ?? checked;
      final on = when == null ? '' : ' op ${_day(when)}';
      if (watch.needsAcknowledge) {
        return WatchState(
          Icons.check_circle,
          green,
          'Gevonden$on',
          watch.foundAt != null && watch.notifyOnFound
              ? 'Je krijgt elke ochtend een herinnering tot je op Gezien tikt.'
              : 'De zoekopdracht is gestopt. Tik op Gezien om hem af te handelen.',
        );
      }
      return WatchState(
        Icons.check_circle_outline,
        grey,
        'Gevonden$on en gezien',
        'De zoekopdracht is gestopt. Sla hem opnieuw op om weer te zoeken.',
      );
    case 'NIET_GEVONDEN':
      final since = watch.searchingSince;
      return WatchState(
        Icons.search,
        grey,
        'Nog niet gevonden',
        [
          if (since != null)
            watchDuration(since, now) == 'vandaag'
                ? 'Zoekt sinds vandaag.'
                : 'Zoekt al ${watchDuration(since, now)} (sinds ${_day(since)}).',
          lastCheck,
          'Wordt elk uur gecontroleerd tussen 8 en 22 uur.',
        ].where((s) => s.isNotEmpty).join(' '),
      );
    case 'ONBEKEND':
      final failing = watch.failingSince;
      return WatchState(
        Icons.error_outline,
        amber,
        'Controleren lukt niet',
        [
          if (failing != null)
            watchDuration(failing, now) == 'vandaag'
                ? 'Mislukt sinds vandaag.'
                : 'Mislukt al ${watchDuration(failing, now)} (sinds ${_day(failing)}).',
          lastCheck,
          'Wordt elk uur opnieuw geprobeerd.',
        ].where((s) => s.isNotEmpty).join(' '),
      );
    default:
      return const WatchState(
        Icons.schedule,
        grey,
        'Nog niet gecontroleerd',
        'De eerste controle volgt binnen een paar minuten (tussen 8 en 22 uur).',
      );
  }
}

class _WatchInput {
  final String title;
  final String url;
  final String instruction;
  final bool notifyOnFound;

  const _WatchInput(this.title, this.url, this.instruction, this.notifyOnFound);
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
  late bool _notify;
  String? _validationError;

  @override
  void initState() {
    super.initState();
    final watch = widget.watch;
    _title = TextEditingController(text: watch?.title);
    _url = TextEditingController(text: watch?.url);
    _instruction = TextEditingController(text: watch?.instruction);
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
    Navigator.pop(context, _WatchInput(title, url, instruction, _notify));
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      widget.watch == null ? 'Nieuwe zoekopdracht' : 'Zoekopdracht bewerken',
    ),
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
