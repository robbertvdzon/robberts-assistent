import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api_client.dart';

/// De zoekopdracht voor werk: elke ochtend zoekt de AI naar vacatures. Hier staan de gevonden
/// vacatures; per vacature geef je aan of hij interessant is, met een reden. Daarvan leert de
/// volgende zoekronde, en een bedrijf dat je afwijst blijft daarna weg.
class JobsScreen extends StatefulWidget {
  const JobsScreen({super.key, required this.api, this.pollInterval});

  final ApiClient api;

  /// Hoe vaak het scherm opnieuw kijkt zolang er een zoekronde loopt.
  final Duration? pollInterval;

  @override
  State<JobsScreen> createState() => _JobsScreenState();
}

class _JobsScreenState extends State<JobsScreen> {
  static const _groups = [
    ('NIEUW', 'Nieuw'),
    ('INTERESSANT', 'Interessant'),
    ('NIET', 'Niet interessant'),
  ];

  JobOverview? _jobs;
  String? _error;
  String _group = 'NIEUW';
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  void _show(JobOverview jobs) {
    if (!mounted) return;
    setState(() {
      _jobs = jobs;
      _error = null;
    });
    // Een zoekronde duurt minuten; blijf kijken tot hij klaar is.
    _poll?.cancel();
    if (jobs.running) {
      _poll = Timer(widget.pollInterval ?? const Duration(seconds: 10), _load);
    }
  }

  Future<void> _load() async {
    try {
      _show(await widget.api.getJobs());
    } catch (_) {
      if (mounted && _jobs == null) {
        setState(() => _error = 'De vacatures konden niet worden geladen.');
      }
    }
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _run() async {
    try {
      _show(await widget.api.runJobSearch());
    } catch (_) {
      _snack('De zoekronde kon niet worden gestart.');
    }
  }

  Future<void> _rate(JobLead lead, String verdict) async {
    final interesting = verdict == 'INTERESSANT';
    final controller = TextEditingController(text: lead.reason);
    final reason = await showDialog<String>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: Text(interesting ? 'Interessant' : 'Niet interessant'),
        content: TextField(
          key: const ValueKey('vacature-reden'),
          controller: controller,
          autofocus: true,
          minLines: 2,
          maxLines: 4,
          decoration: InputDecoration(
            labelText: 'Waarom?',
            helperText: interesting
                ? 'Bijvoorbeeld: leuke techniek, dichtbij.'
                : 'Bijvoorbeeld: bij dit bedrijf wil ik niet werken, of: te veel reistijd.',
            helperMaxLines: 2,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialog, controller.text.trim()),
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
    if (reason == null) return;
    try {
      _show(await widget.api.rateJob(lead.id, verdict, reason));
    } catch (_) {
      _snack('Het oordeel kon niet worden opgeslagen.');
    }
  }

  Future<void> _open(JobLead lead) async {
    final uri = Uri.tryParse(lead.url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      _snack('De vacature kon niet worden geopend.');
    }
  }

  Future<void> _settings() async {
    final jobs = _jobs;
    if (jobs == null) return;
    final saved = await Navigator.of(context).push<JobOverview>(
      MaterialPageRoute(
        builder: (_) => JobSettingsScreen(api: widget.api, jobs: jobs),
      ),
    );
    if (saved != null) _show(saved);
  }

  String _when(DateTime at) =>
      '${at.day}-${at.month} ${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final jobs = _jobs;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Vacatures'),
        actions: [
          IconButton(
            tooltip: 'Zoekvraag en voorkeuren',
            icon: const Icon(Icons.tune_outlined),
            onPressed: jobs == null ? null : _settings,
          ),
        ],
      ),
      body: jobs == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : RefreshIndicator(onRefresh: _load, child: _list(jobs)),
    );
  }

  Widget _list(JobOverview jobs) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    final shown = jobs.leads.where((l) => l.verdict == _group).toList();
    return ListView(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        16 + MediaQuery.viewPaddingOf(context).bottom,
      ),
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                jobs.running
                    ? 'Bezig met zoeken; dit duurt een paar minuten.'
                    : '${jobs.lastRunNote}${jobs.lastRunAt == null ? '' : ' (${_when(jobs.lastRunAt!)})'}',
                style: muted,
              ),
            ),
            jobs.running
                ? const Padding(
                    padding: EdgeInsets.all(8),
                    child: SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  )
                : TextButton(
                    key: const ValueKey('nu-zoeken'),
                    onPressed: _run,
                    child: const Text('Nu zoeken'),
                  ),
          ],
        ),
        const SizedBox(height: 4),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          children: [
            for (final (verdict, label) in _groups)
              ChoiceChip(
                key: ValueKey('groep-$verdict'),
                label: Text(
                  '$label (${jobs.leads.where((l) => l.verdict == verdict).length})',
                ),
                selected: _group == verdict,
                onSelected: (_) => setState(() => _group = verdict),
              ),
          ],
        ),
        if (shown.isEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 48),
            child: Center(
              child: Text(
                _group == 'NIEUW'
                    ? 'Geen nieuwe vacatures. Er wordt elke ochtend om half negen gezocht.'
                    : 'Nog niets in deze groep.',
                style: muted,
                textAlign: TextAlign.center,
              ),
            ),
          ),
        for (final lead in shown)
          Card(
            key: ValueKey('vacature-${lead.id}'),
            margin: const EdgeInsets.only(top: 12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    lead.title,
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 16,
                    ),
                  ),
                  Text(
                    [
                      lead.company,
                      lead.location,
                    ].where((s) => s.isNotEmpty).join(' · '),
                    style: muted,
                  ),
                  if (lead.summary.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(lead.summary),
                  ],
                  if (lead.reason.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text('Jouw reden: ${lead.reason}', style: muted),
                  ],
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 4,
                    children: [
                      TextButton.icon(
                        onPressed: () => _open(lead),
                        icon: const Icon(Icons.open_in_new, size: 18),
                        label: const Text('Bekijken'),
                      ),
                      if (lead.verdict != 'INTERESSANT')
                        TextButton.icon(
                          key: ValueKey('interessant-${lead.id}'),
                          onPressed: () => _rate(lead, 'INTERESSANT'),
                          icon: const Icon(Icons.thumb_up_outlined, size: 18),
                          label: const Text('Interessant'),
                        ),
                      if (lead.verdict != 'NIET')
                        TextButton.icon(
                          key: ValueKey('niet-${lead.id}'),
                          onPressed: () => _rate(lead, 'NIET'),
                          icon: const Icon(Icons.thumb_down_outlined, size: 18),
                          label: const Text('Niet interessant'),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// De zoekvraag, wat er uit de oordelen geleerd is en de bedrijven die wegblijven; alles is hier
/// zelf aan te passen.
class JobSettingsScreen extends StatefulWidget {
  const JobSettingsScreen({super.key, required this.api, required this.jobs});

  final ApiClient api;
  final JobOverview jobs;

  @override
  State<JobSettingsScreen> createState() => _JobSettingsScreenState();
}

class _JobSettingsScreenState extends State<JobSettingsScreen> {
  late final _description = TextEditingController(
    text: widget.jobs.description,
  );
  late final _preferences = TextEditingController(
    text: widget.jobs.preferences,
  );
  late final _blocked = TextEditingController(
    text: widget.jobs.blockedCompanies.join('\n'),
  );
  bool _saving = false;

  @override
  void dispose() {
    _description.dispose();
    _preferences.dispose();
    _blocked.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final saved = await widget.api.saveJobSettings(
        description: _description.text.trim(),
        preferences: _preferences.text.trim(),
        blockedCompanies: _blocked.text
            .split('\n')
            .map((s) => s.trim())
            .where((s) => s.isNotEmpty)
            .toList(),
      );
      if (mounted) Navigator.of(context).pop(saved);
    } catch (_) {
      if (mounted) {
        setState(() => _saving = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Opslaan is mislukt.')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget field(
      String key,
      TextEditingController c,
      String label,
      String help,
      int lines,
    ) => Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: TextField(
        key: ValueKey(key),
        controller: c,
        minLines: lines,
        maxLines: lines + 8,
        decoration: InputDecoration(
          labelText: label,
          helperText: help,
          helperMaxLines: 3,
          alignLabelWithHint: true,
          border: const OutlineInputBorder(),
        ),
      ),
    );
    return Scaffold(
      appBar: AppBar(title: const Text('Zoekvraag en voorkeuren')),
      body: ListView(
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          16 + MediaQuery.viewPaddingOf(context).bottom,
        ),
        children: [
          field(
            'zoekvraag',
            _description,
            'Wat zoek je?',
            'Soort werk, techniek en het gebied waar het mag zijn.',
            4,
          ),
          field(
            'voorkeuren',
            _preferences,
            'Geleerd uit je oordelen',
            'De AI werkt dit bij na elk oordeel. Je mag het zelf aanpassen.',
            5,
          ),
          field(
            'bedrijven',
            _blocked,
            'Bedrijven die wegblijven',
            'Eén bedrijf per regel. Vacatures van deze bedrijven worden nooit meer getoond.',
            3,
          ),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
  }
}
