import 'package:flutter/material.dart';

import 'api_client.dart';

/// Nederlandse dag-afkortingen (`DateTime.weekday` is 1 = maandag).
const _weekdays = ['ma', 'di', 'wo', 'do', 'vr', 'za', 'zo'];

/// Nederlandse maand-afkortingen (`DateTime.month` is 1 = januari).
const _months = [
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

/// Datum + tijd in lokale tijd en Nederlandse notatie: `vandaag 11:30`,
/// `gisteren 11:30` of `ma 28 jul 09:05`. Bewust een eigen mini-helper: de app
/// heeft geen `intl`-dependency en die komt er voor dit ene regeltje niet bij.
String formatVersionMoment(DateTime savedAt, {DateTime? now}) {
  final local = savedAt.toLocal();
  final today = now?.toLocal() ?? DateTime.now();
  final time =
      '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';

  final day = DateTime(local.year, local.month, local.day);
  final todayDay = DateTime(today.year, today.month, today.day);
  final diff = todayDay.difference(day).inDays;
  if (diff == 0) return 'vandaag $time';
  if (diff == 1) return 'gisteren $time';

  return '${_weekdays[local.weekday - 1]} ${local.day} ${_months[local.month - 1]} $time';
}

/// Toont de bewaarde versies van de notitie (nieuwste eerst). Tikken op een
/// regel opent een alleen-lezen weergave met een `Terugzetten`-knop; die geeft
/// de gekozen tekst terug aan de editor via `Navigator.pop`.
class NoteVersionsScreen extends StatefulWidget {
  const NoteVersionsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<NoteVersionsScreen> createState() => _NoteVersionsScreenState();
}

class _NoteVersionsScreenState extends State<NoteVersionsScreen> {
  var _loading = true;
  String? _error;
  List<NoteVersionSummary> _versions = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final versions = await widget.api.listNoteVersions();
      if (mounted) {
        setState(() {
          _versions = versions;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  Future<void> _open(NoteVersionSummary version) async {
    final restored = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => NoteVersionDetailScreen(
          api: widget.api,
          version: version,
        ),
      ),
    );
    // Teruggezet: de editor (die deze route pushte) krijgt de tekst door.
    if (restored != null && mounted) {
      Navigator.of(context).pop(restored);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Versies')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
          : _versions.isEmpty
          ? const Center(child: Text('Nog geen eerdere versies bewaard.'))
          : ListView.builder(
              itemCount: _versions.length,
              itemBuilder: (context, index) {
                final version = _versions[index];
                return ListTile(
                  leading: const Icon(Icons.history),
                  title: Text(formatVersionMoment(version.savedAt)),
                  onTap: () => _open(version),
                );
              },
            ),
    );
  }
}

/// Alleen-lezen weergave van één oude versie: platte markdown als
/// selecteerbare tekst, geen bewerkbare editor.
class NoteVersionDetailScreen extends StatefulWidget {
  const NoteVersionDetailScreen({super.key, required this.api, required this.version});

  final ApiClient api;
  final NoteVersionSummary version;

  @override
  State<NoteVersionDetailScreen> createState() => _NoteVersionDetailScreenState();
}

class _NoteVersionDetailScreenState extends State<NoteVersionDetailScreen> {
  var _loading = true;
  String? _error;
  String _text = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final text = await widget.api.getNoteVersion(widget.version.id);
      if (mounted) {
        setState(() {
          _text = text;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  Future<void> _confirmRestore() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Versie terugzetten?'),
        content: const Text(
          'De inhoud van de editor wordt vervangen door deze oude versie. '
          'Je kunt dat daarna met Ongedaan maken terugdraaien.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Annuleren'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Ja, terugzetten'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      Navigator.of(context).pop(_text);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(formatVersionMoment(widget.version.savedAt))),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
          : Column(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: SelectableText(_text.isEmpty ? '(lege notitie)' : _text),
                  ),
                ),
                const Divider(height: 1),
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      icon: const Icon(Icons.restore),
                      label: const Text('Terugzetten'),
                      onPressed: _confirmRestore,
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}
